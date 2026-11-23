package com.example.biteflight.utils

import android.content.Context
import com.example.biteflight.data.model.orderStatusLabel
import com.example.biteflight.data.repository.OrderRepository
import com.google.firebase.auth.FirebaseAuth

object BuyerOrderMonitor {
    private var unsubscribe: (() -> Unit)? = null
    private val seen = mutableMapOf<String, String>()
    private val stages = listOf("PLACED", "ASSIGNED", "WAITING_AT_RESTAURANT", "PICKED_UP", "IN_AREA", "DELIVERED")

    fun init(context: Context) {
        if (unsubscribe != null) return
        val auth = FirebaseAuth.getInstance()

        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                startObserving(context, uid)
            } else {
                stopObserving()
            }
        }

        val currentUid = auth.currentUser?.uid
        if (currentUid != null) {
            startObserving(context, currentUid)
        }
    }

    private fun startObserving(context: Context, uid: String) {
        if (unsubscribe != null) return
        seen.clear()
        unsubscribe = OrderRepository.observeBuyerOrders(uid, onOrders = { orders ->
            orders.forEach { order ->
                val status = order.status.uppercase()
                val old = seen.put(order.id, status)
                if (old != null && stages.indexOf(status) > stages.indexOf(old)) {
                    val message = "Order #${order.id.takeLast(6).uppercase()}: ${orderStatusLabel(status)}"
                    NotificationHelper.sendNotification(context, "Delivery update", message, order.id)
                } else if (old == null) {
                    seen[order.id] = status
                }
            }
        }, onError = { _ -> })
    }

    private fun stopObserving() {
        unsubscribe?.invoke()
        unsubscribe = null
        seen.clear()
    }
}
