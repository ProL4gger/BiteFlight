package com.example.biteflight.ui.seller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.biteflight.R
import com.example.biteflight.data.model.Order
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SellerOrderDetailFragment(private val order: Order) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_seller_order_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.btnBackToList)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val orderNum = if (order.id.length >= 6) order.id.takeLast(6).uppercase() else order.id.uppercase()
        view.findViewById<TextView>(R.id.tvDetailOrderId)?.text = "Order #$orderNum"

        val tvStatus = view.findViewById<TextView>(R.id.tvDetailStatus)
        val btnComplete = view.findViewById<MaterialButton>(R.id.btnCompleteOrder)

        tvStatus?.text = "Status: ${order.status}"
        btnComplete?.isEnabled = false
        btnComplete?.text = if (order.status == "DELIVERED" || order.status == "COMPLETED")
            "Order delivered" else "Awaiting rider update"

        val dateStr = if (order.createdAt > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(order.createdAt))
        } else {
            "N/A"
        }
        view.findViewById<TextView>(R.id.tvDetailTime)?.text = "Time: $dateStr"
        view.findViewById<TextView>(R.id.tvDetailAddress)?.text = order.deliveryAddress.ifBlank { "No address provided" }

        val itemsStr = order.items.joinToString("\n") { "${it.name} x${it.quantity} - EGP ${it.price * it.quantity}" }
        view.findViewById<TextView>(R.id.tvDetailItems)?.text = itemsStr.ifBlank { "No items" }
        view.findViewById<TextView>(R.id.tvDetailTotal)?.text = "Total: EGP ${order.total}"
    }
}
