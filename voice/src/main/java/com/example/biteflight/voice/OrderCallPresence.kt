package com.example.biteflight.voice

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.net.Inet4Address
import java.net.NetworkInterface

/** Publishes reachable SIP addresses only under orders the recipient can serve. */
class OrderCallPresence(private val uid: String, private val role: String) {
    private val database = FirebaseDatabase.getInstance()
    private val source = database.getReference(if (role == RESTAURANT) "orders" else "delivery/riders").child(uid)
    private val connection = database.getReference(".info/connected")
    private val registrations = mutableMapOf<Pair<String, String>, DatabaseReference>()
    private var orders = emptySet<Pair<String, String>>()
    private var connected = false

    private val orderListener = listener { snapshot ->
        orders = if (role == RESTAURANT) {
            snapshot.children.mapNotNull { order ->
                order.key?.takeIf { order.child("status").value in currentStatuses }?.let { uid to it }
            }.toSet()
        } else {
            val seller = snapshot.child("sellerId").getValue(String::class.java)
            val order = snapshot.child("currentOrderId").getValue(String::class.java)
            if (seller.isNullOrBlank() || order.isNullOrBlank()) emptySet() else setOf(seller to order)
        }
        publish()
    }
    private val connectionListener = listener { snapshot ->
        connected = snapshot.getValue(Boolean::class.java) == true
        if (connected) publish() else registrations.clear()
    }

    init {
        source.addValueEventListener(orderListener)
        connection.addValueEventListener(connectionListener)
    }

    fun close() {
        source.removeEventListener(orderListener)
        connection.removeEventListener(connectionListener)
        connected = false
        registrations.values.forEach { it.removeValue() }
        registrations.clear()
    }

    private fun publish() {
        if (!connected) return
        (registrations.keys - orders).forEach { registrations.remove(it)?.removeValue() }
        val addresses = localAddresses().joinToString(",")
        if (addresses.isEmpty()) return
        for (key in orders - registrations.keys) {
            val (seller, order) = key
            val ref = contacts(seller, order, role, uid).push()
            registrations[key] = ref
            ref.onDisconnect().removeValue().addOnCompleteListener { task ->
                if (registrations[key] != ref) return@addOnCompleteListener
                if (task.isSuccessful) ref.setValue(addresses) else registrations.remove(key)
            }
        }
    }

    private fun listener(onData: (DataSnapshot) -> Unit) = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)
        override fun onCancelled(error: DatabaseError) = Unit
    }

    companion object {
        const val RESTAURANT = "restaurant"
        const val DRIVER = "driver"
        private val currentStatuses = setOf("PLACED", "ASSIGNED", "WAITING_AT_RESTAURANT", "PICKED_UP", "IN_AREA")

        fun contacts(seller: String, order: String, role: String, uid: String): DatabaseReference =
            FirebaseDatabase.getInstance().getReference("orderCallContacts")
                .child(seller).child(order).child(role).child(uid)

        internal fun localAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
        }.getOrDefault(emptyList())
    }
}
