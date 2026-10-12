package com.example.biteflight.ui.buyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.Order

class OrderAdapter(
    private val orders: List<Order>
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    class OrderViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val tvRestaurant: TextView =
            itemView.findViewById(
                R.id.tvOrderRestaurant
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

        val order =
            orders[position]

        holder.tvRestaurant.text =
            order.restaurantName

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
    }


    override fun getItemCount(): Int {

        return orders.size
    }
}