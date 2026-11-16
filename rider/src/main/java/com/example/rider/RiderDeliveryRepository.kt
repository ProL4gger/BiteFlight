package com.example.rider

import android.location.Location
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.firebase.database.*

/** Foreground-only dispatcher. Locks are durable so interrupted claims resume on reopening. */
class RiderDeliveryRepository(private val uid: String, private val onError: (String) -> Unit) {
    private val database = FirebaseDatabase.getInstance()
    private val lock = database.getReference("delivery/riders").child(uid)
    private val queue = database.getReference("deliveryQueue")
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var available = false
    private var working = false
    private var lockLoaded = false
    private var assignment: DataSnapshot? = null
    private var waiting: DataSnapshot? = null
    private var latitude = 0.0
    private var longitude = 0.0
    private val retry = Runnable { pump() }
    private val lockListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            assignment = snapshot
            lockLoaded = true
            pump()
        }
        override fun onCancelled(error: DatabaseError) { onError(error.message) }
    }
    private val queueListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) { waiting = snapshot; pump() }
        override fun onCancelled(error: DatabaseError) { onError("Cannot load waiting orders: ${error.message}") }
    }

    fun start() {
        if (active) return
        active = true
        lockLoaded = false
        lock.addValueEventListener(lockListener)
        queue.addValueEventListener(queueListener)
    }

    fun stop() {
        active = false
        available = false
        handler.removeCallbacks(retry)
        lock.removeEventListener(lockListener)
        queue.removeEventListener(queueListener)
    }

    fun setAvailable(value: Boolean, lat: Double?, lng: Double?) {
        available = value && lat != null && lng != null
        if (lat != null) latitude = lat
        if (lng != null) longitude = lng
        pump()
    }

    fun refresh() { pump() }

    private fun finished(error: String? = null) {
        working = false
        if (active) {
            if (error != null) onError(error)
            handler.removeCallbacks(retry)
            handler.postDelayed(retry, 5000)
        }
    }

    private fun pump() {
        if (!active || !available || working || !lockLoaded) return
        handler.removeCallbacks(retry)
        val orderId = assignment?.child("currentOrderId")?.getValue(String::class.java)
        val sellerId = assignment?.child("sellerId")?.getValue(String::class.java)
        if (!orderId.isNullOrBlank()) {
            if (sellerId.isNullOrBlank()) {
                onError("Old assignment has no restaurant ID. Ask the project owner to review delivery/riders/$uid.")
                return
            }
            working = true
            recover(sellerId, orderId)
            return
        }
        val candidate = waiting?.children?.filter {
            it.child("sellerId").value is String && it.child("pickupLat").value is Number &&
                it.child("pickupLng").value is Number
        }?.minByOrNull {
            val distance = FloatArray(1)
            Location.distanceBetween(latitude, longitude,
                (it.child("pickupLat").value as Number).toDouble(),
                (it.child("pickupLng").value as Number).toDouble(), distance)
            distance[0]
        } ?: return
        val id = candidate.key ?: return
        val seller = candidate.child("sellerId").getValue(String::class.java) ?: return
        working = true
        lock.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                if (!data.child("currentOrderId").getValue(String::class.java).isNullOrBlank())
                    return Transaction.abort()
                data.value = mapOf("currentOrderId" to id, "sellerId" to seller)
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) {
                    // Another rider may have won before this reservation reached
                    // the server. Only already-claimed queue entries can be deleted.
                    queue.child(id).removeValue().addOnCompleteListener {
                        finished(if (it.isSuccessful) null else "Could not reserve delivery: ${error.message}")
                    }
                }
                else if (committed && snapshot?.child("currentOrderId")?.value == id) recover(seller, id)
                else finished()
            }
        }, false)
    }

    private fun recover(seller: String, id: String) {
        val order = database.getReference("orders").child(seller).child(id)
        order.get().addOnSuccessListener { snapshot ->
            val owner = snapshot.child("riderId").getValue(String::class.java).orEmpty()
            val status = snapshot.child("status").getValue(String::class.java).orEmpty()
            when {
                !snapshot.exists() || status == "DELIVERED" || status == "COMPLETED" ||
                    (owner.isNotBlank() && owner != uid) -> release()
                owner == uid && status == "PLACED" -> markAssigned(order, id)
                owner == uid -> {
                    if (waiting?.hasChild(id) == true) {
                        queue.child(id).removeValue().addOnCompleteListener { working = false }
                    } else working = false
                }
                status == "PLACED" -> order.child("riderId").runTransaction(object : Transaction.Handler {
                    override fun doTransaction(data: MutableData): Transaction.Result {
                        val current = data.getValue(String::class.java)
                        if (!current.isNullOrBlank() && current != uid) return Transaction.abort()
                        data.value = uid
                        return Transaction.success(data)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
                        if (committed && data?.value == uid) markAssigned(order, id)
                        else release()
                    }
                }, false)
                else -> release()
            }
        }.addOnFailureListener {
            // Losing a claim can revoke the private order read. Release is allowed
            // by rules only if this rider does not own an unfinished order.
            release()
        }
    }

    private fun markAssigned(order: DatabaseReference, id: String) {
        order.updateChildren(mapOf("status" to "ASSIGNED", "updatedAt" to ServerValue.TIMESTAMP))
            .addOnSuccessListener { queue.child(id).removeValue().addOnCompleteListener { finished() } }
            .addOnFailureListener { finished("Could not finish assignment: ${it.localizedMessage}") }
    }

    private fun release() {
        lock.removeValue().addOnCompleteListener {
            finished(if (it.isSuccessful) null else "Could not recover assignment: ${it.exception?.localizedMessage}")
        }
    }

    fun advance(seller: String, order: String, status: String): Task<Void> {
        val task = database.getReference("orders").child(seller).child(order).updateChildren(
            mapOf("status" to status, "updatedAt" to ServerValue.TIMESTAMP))
        task.addOnSuccessListener { pump() }
        return task
    }
}
