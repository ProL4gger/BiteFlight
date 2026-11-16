package com.example.biteflight.data.repository

import com.example.biteflight.data.model.Order
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase

object OrderRepository {
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
