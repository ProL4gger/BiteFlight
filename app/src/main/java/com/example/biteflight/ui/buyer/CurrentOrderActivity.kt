package com.example.biteflight.ui.buyer

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.biteflight.R
import com.example.biteflight.data.model.Order
import com.example.biteflight.data.model.isCurrentOrder
import com.example.biteflight.data.model.orderStatusLabel
import com.example.biteflight.data.repository.OrderRepository
import com.example.biteflight.voice.VoiceCallActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import java.text.NumberFormat

class CurrentOrderActivity : AppCompatActivity() {
    private var stopObserving: (() -> Unit)? = null
    private var current: Order? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_current_order)
        val root = findViewById<View>(R.id.currentOrderRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        findViewById<MaterialToolbar>(R.id.currentOrderToolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.callRestaurant).setOnClickListener { callContact(driver = false) }
        findViewById<View>(R.id.callDriver).setOnClickListener { callContact(driver = true) }
    }

    override fun onStart() {
        super.onStart()
        render(null, R.string.current_order_loading)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            finish()
            return
        }
        stopObserving = OrderRepository.observeBuyerOrders(uid,
            onOrders = { orders -> render(orders.firstOrNull { isCurrentOrder(it.status) }) },
            onError = { if (current == null) render(null, R.string.current_order_error) }
        )
    }

    override fun onStop() {
        stopObserving?.invoke()
        stopObserving = null
        super.onStop()
    }

    private fun render(order: Order?, emptyText: Int = R.string.no_current_order) {
        current = order
        findViewById<View>(R.id.currentOrderContent).visibility = if (order == null) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.currentOrderEmpty).apply {
            setText(emptyText)
            visibility = if (order == null) View.VISIBLE else View.GONE
        }
        if (order == null) return
        findViewById<TextView>(R.id.currentRestaurant).text =
            order.restaurantName.ifBlank { getString(R.string.current_order_restaurant) }
        findViewById<TextView>(R.id.currentStatus).text = orderStatusLabel(order.status)
        findViewById<TextView>(R.id.currentAddress).text = order.deliveryAddress
        findViewById<TextView>(R.id.currentItems).text = order.items.joinToString("\n") {
            getString(R.string.current_order_item, it.quantity, it.name)
        }
        findViewById<TextView>(R.id.currentTotal).text = NumberFormat.getCurrencyInstance().apply {
            currency = java.util.Currency.getInstance("EGP")
        }.format(order.total)
        findViewById<View>(R.id.driverPending).visibility =
            if (order.riderId.isBlank()) View.VISIBLE else View.GONE
        updateButtons()
    }

    private fun updateButtons() {
        findViewById<View>(R.id.callRestaurant).isEnabled = current != null
        findViewById<View>(R.id.callDriver).isEnabled = !current?.riderId.isNullOrBlank()
    }

    private fun callContact(driver: Boolean) {
        val order = current ?: return
        val recipient = if (driver) order.riderId else order.sellerId
        if (recipient.isBlank() || order.sellerId.isBlank() || order.id.isBlank()) return
        val name = if (driver) getString(R.string.current_order_driver)
            else order.restaurantName.ifBlank { getString(R.string.current_order_restaurant) }
        VoiceCallActivity.startOrderCall(this, order.sellerId, order.id, recipient, name)
    }
}
