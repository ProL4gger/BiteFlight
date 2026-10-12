package com.example.biteflight.ui.buyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.User

class RestaurantAdapter(
    private val restaurants: List<User>,
    private val onRestaurantClick: (User) -> Unit
) : RecyclerView.Adapter<RestaurantAdapter.RestaurantViewHolder>() {

    class RestaurantViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val tvRestaurantName: TextView =
            itemView.findViewById(
                R.id.tvRestaurantName
            )

        val tvRestaurantAddress: TextView =
            itemView.findViewById(
                R.id.tvRestaurantAddress
            )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RestaurantViewHolder {

        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_restaurant,
                    parent,
                    false
                )

        return RestaurantViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: RestaurantViewHolder,
        position: Int
    ) {

        val restaurant =
            restaurants[position]

        holder.tvRestaurantName.text =
            restaurant.name

        holder.tvRestaurantAddress.text =
            restaurant.address

        holder.itemView.setOnClickListener {

            onRestaurantClick(
                restaurant
            )
        }
    }

    override fun getItemCount(): Int {

        return restaurants.size
    }
}