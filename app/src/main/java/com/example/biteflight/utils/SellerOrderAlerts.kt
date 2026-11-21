package com.example.biteflight.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/** Posts local notifications for orders added while the seller dashboard is visible. */
class SellerOrderAlerts(private val context: Context) {
    private var unsubscribe: (() -> Unit)? = null

    fun start(sellerId: String) {
        if (unsubscribe != null) return

        val auth = FirebaseAuth.getInstance()
        val orders = FirebaseDatabase.getInstance().getReference("orders").child(sellerId)
        var previousIds: Set<String>? = null
        var active = true
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!active || auth.currentUser?.uid != sellerId) return

                val currentIds = snapshot.children.mapNotNull { it.key }.toSet()
                // The first snapshot is order history. Alert only for later additions.
                val newIds = previousIds?.let { currentIds - it }.orEmpty()
                previousIds = currentIds
                newIds.forEach { orderId ->
                    NotificationHelper.notifySellerNewOrder(context, sellerId, orderId)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (active) Log.w("SellerOrderAlerts", "Could not watch seller orders", error.toException())
            }
        }
        orders.addValueEventListener(listener)
        unsubscribe = {
            active = false
            orders.removeEventListener(listener)
        }
    }

    fun stop() {
        unsubscribe?.invoke()
        unsubscribe = null
    }
}
