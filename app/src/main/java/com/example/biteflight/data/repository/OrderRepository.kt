package com.example.biteflight.data.repository

import com.example.biteflight.data.model.Order
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

object OrderRepository {
    /** Subscribe to the private buyer index and each canonical order, without polling. */
    fun observeBuyerOrders(
        buyerId: String,
        onOrders: (List<Order>) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        val database = FirebaseDatabase.getInstance()
        val index = database.getReference("buyerOrders").child(buyerId)
        val subscriptions = mutableMapOf<String, Pair<DatabaseReference, ValueEventListener>>()
        val sellers = mutableMapOf<String, String>()
        val orders = mutableMapOf<String, Order>()
        var active = true
        fun publish() {
            if (active) onOrders(orders.values.sortedByDescending { it.createdAt })
        }
        fun clearOrders() {
            subscriptions.values.forEach { (ref, listener) -> ref.removeEventListener(listener) }
            subscriptions.clear()
            sellers.clear()
            orders.clear()
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!active) return
                val entries = snapshot.children.mapNotNull { entry ->
                    val id = entry.key ?: return@mapNotNull null
                    val seller = entry.getValue(String::class.java) ?: return@mapNotNull null
                    id to seller
                }.toMap()
                subscriptions.keys.toList().forEach { id ->
                    if (entries[id] != sellers[id]) {
                        subscriptions.remove(id)?.let { (ref, observer) -> ref.removeEventListener(observer) }
                        sellers.remove(id)
                        orders.remove(id)
                    }
                }
                entries.forEach { (id, seller) ->
                    if (subscriptions.containsKey(id)) return@forEach
                    val ref = database.getReference("orders").child(seller).child(id)
                    val observer = object : ValueEventListener {
                        override fun onDataChange(orderSnapshot: DataSnapshot) {
                            if (!active || sellers[id] != seller) return
                            val order = orderSnapshot.getValue(Order::class.java)
                            if (order != null && order.buyerId == buyerId) orders[id] = order
                            else orders.remove(id)
                            publish()
                        }
                        override fun onCancelled(error: DatabaseError) {
                            if (!active || sellers[id] != seller) return
                            orders.remove(id)
                            publish()
                            onError(error.message)
                        }
                    }
                    sellers[id] = seller
                    subscriptions[id] = ref to observer
                    ref.addValueEventListener(observer)
                }
                publish()
            }
            override fun onCancelled(error: DatabaseError) {
                if (!active) return
                clearOrders()
                publish()
                onError(error.message)
            }
        }
        index.addValueEventListener(listener)
        return {
            active = false
            index.removeEventListener(listener)
            clearOrders()
        }
    }

    fun buyerOrders(buyerId: String): Task<List<Order>> {
        val database = FirebaseDatabase.getInstance()
        return database.getReference("buyerOrders").child(buyerId).get()
            .continueWithTask { indexTask ->
                if (!indexTask.isSuccessful) {
                    throw indexTask.exception ?: IllegalStateException("Could not load order index")
                }
                val reads = indexTask.result.children.map { entry ->
                    val orderId = entry.key ?: throw IllegalStateException("Missing order ID")
                    val sellerId = entry.getValue(String::class.java)
                        ?: throw IllegalStateException("Missing restaurant ID")
                    database.getReference("orders").child(sellerId).child(orderId).get()
                }
                Tasks.whenAllSuccess<DataSnapshot>(reads)
            }.continueWith { ordersTask ->
                if (!ordersTask.isSuccessful) {
                    throw ordersTask.exception ?: IllegalStateException("Could not load orders")
                }
                ordersTask.result.mapNotNull { it.getValue(Order::class.java) }
                    .filter { it.buyerId == buyerId }
                    .sortedByDescending { it.createdAt }
            }
    }
}
