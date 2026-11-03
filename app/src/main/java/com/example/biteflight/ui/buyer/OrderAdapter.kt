package com.example.biteflight.ui.buyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.Order

class OrderAdapter(
    private val orders: List<Order>,
    private val isForSeller: Boolean = false,
    private val onItemClick: ((Order) -> Unit)? = null
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    class OrderViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val tvRestaurant: TextView =
            itemView.findViewById(
                R.id.tvOrderRestaurant
            )

        val tvOrderId: TextView? =
            itemView.findViewById(
                R.id.tvOrderId
            )

        val tvItems: TextView =
            itemView.findViewById(
                R.id.tvOrderItems
            )

        val tvTotal: TextView =
            itemView.findViewById(
                R.id.tvOrderTotal
            )

        val tvStatus: TextView =
            itemView.findViewById(
                R.id.tvOrderStatus
            )

        val tvAddress: TextView =
            itemView.findViewById(
                R.id.tvOrderAddress
            )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): OrderViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_order,
                    parent,
                    false
                )
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: OrderViewHolder,
        position: Int
    ) {
        val order = orders[position]
        val orderNum = if (order.id.length >= 6) order.id.takeLast(6).uppercase() else order.id.uppercase()

        if (isForSeller) {
            holder.tvRestaurant.text = "Order #$orderNum"
            holder.tvOrderId?.visibility = View.GONE
        } else {
            holder.tvRestaurant.text = order.restaurantName
            holder.tvOrderId?.visibility = View.VISIBLE
            holder.tvOrderId?.text = "Order #$orderNum"
        }

        holder.tvItems.text =
            order.items.joinToString("\n") {
                "${it.name} x${it.quantity}"
            }

        holder.tvTotal.text =
            "Total: EGP ${order.total}"

        holder.tvStatus.text =
            "Status: ${order.status}"

        holder.tvAddress.text =
            "Address: ${order.deliveryAddress}"

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(order)
        }
    }

    override fun getItemCount(): Int {
        return orders.size
    }
}
