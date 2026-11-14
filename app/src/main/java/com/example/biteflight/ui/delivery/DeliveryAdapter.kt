package com.example.biteflight.ui.delivery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.Delivery

class DeliveryAdapter(
    private val deliveries: List<Delivery>,
    private val onAcceptClick: (Delivery) -> Unit
) : RecyclerView.Adapter<DeliveryAdapter.DeliveryViewHolder>() {

    class DeliveryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDeliveryFee: TextView = itemView.findViewById(R.id.tvDeliveryFee)
        val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        val tvRestaurantAddress: TextView = itemView.findViewById(R.id.tvRestaurantAddress)
        val tvBuyerAddress: TextView = itemView.findViewById(R.id.tvBuyerAddress)
        val btnAcceptDelivery: Button = itemView.findViewById(R.id.btnAcceptDelivery)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeliveryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_delivery, parent, false)
        return DeliveryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeliveryViewHolder, position: Int) {
        val delivery = deliveries[position]
        holder.tvDeliveryFee.text = "EGP ${String.format("%.2f", delivery.deliveryFee)}"
        holder.tvDistance.text = "Distance: ${String.format("%.1f", delivery.distanceKm)} km"
        holder.tvRestaurantAddress.text = "Restaurant: ${delivery.restaurantLocation.address}"
        holder.tvBuyerAddress.text = "Dropoff: ${delivery.buyerLocation.address}"

        holder.btnAcceptDelivery.setOnClickListener {
            onAcceptClick(delivery)
        }
    }

    override fun getItemCount(): Int = deliveries.size
}