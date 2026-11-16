package com.example.biteflight.utils

import android.content.Context
import android.widget.Toast
import com.example.biteflight.data.model.orderStatusLabel
import com.example.biteflight.data.repository.OrderRepository

/** Local alerts only while the buyer activity is visible. No server or FCM token. */
class BuyerOrderAlerts(private val context: Context) {
    private var unsubscribe: (() -> Unit)? = null
    private val seen = mutableMapOf<String, String>()
    private val stages = listOf("PLACED", "ASSIGNED", "WAITING_AT_RESTAURANT", "PICKED_UP", "IN_AREA", "DELIVERED")

    fun start(uid: String) {
        if (unsubscribe != null) return
        seen.clear()
        unsubscribe = OrderRepository.observeBuyerOrders(uid, onOrders = { orders ->
            orders.forEach { order ->
                val status = order.status.uppercase()
                val old = seen.put(order.id, status)
                if (old != null && stages.indexOf(status) > stages.indexOf(old)) {
                    val message = "Order #${order.id.takeLast(6).uppercase()}: ${orderStatusLabel(status)}"
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    NotificationHelper.sendNotification(context, "Delivery update", message, order.id)
                }
            }
        }, onError = { message ->
            Toast.makeText(context, "Delivery alerts unavailable: $message", Toast.LENGTH_LONG).show()
        })
    }

    fun stop() {
        unsubscribe?.invoke()
        unsubscribe = null
        seen.clear()
    }
}
