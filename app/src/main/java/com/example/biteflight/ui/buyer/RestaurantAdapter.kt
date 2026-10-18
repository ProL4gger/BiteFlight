package com.example.biteflight.ui.buyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import coil.load
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.User
import com.example.biteflight.utils.RestaurantAvailability

class RestaurantAdapter(
    private val restaurants: List<User>,
    private val onFavouriteChanged: () -> Unit = {},
    private val onRestaurantClick: (User) -> Unit
) : RecyclerView.Adapter<RestaurantAdapter.RestaurantViewHolder>() {

    class RestaurantViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        val tvStatus: TextView = itemView.findViewById(R.id.tvRestaurantStatus)
        val photo: ImageView = itemView.findViewById(R.id.ivRestaurantPhoto)
        val photoStatus: TextView = itemView.findViewById(R.id.tvRestaurantPhotoStatus)
        val photoDim: View = itemView.findViewById(R.id.restaurantPhotoDim)
        val favourite: android.widget.ImageButton = itemView.findViewById(R.id.btnFavourite)
        val illustrative: View = itemView.findViewById(R.id.tvIllustrativePhoto)
        val tvHours: TextView = itemView.findViewById(R.id.tvRestaurantHours)

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
        holder.tvStatus.text = RestaurantAvailability.label(restaurant)
        val availability = RestaurantAvailability.status(restaurant)
        val isOpen = availability == RestaurantAvailability.Status.OPEN
        holder.photoStatus.visibility = if (isOpen) View.GONE else View.VISIBLE
        holder.photoDim.visibility = if (isOpen) View.GONE else View.VISIBLE
        holder.photoStatus.text = when (availability) {
            RestaurantAvailability.Status.BUSY -> "Busy"
            RestaurantAvailability.Status.CLOSED -> "Closed"
            RestaurantAvailability.Status.OPEN -> ""
        }
        holder.photoStatus.contentDescription = when (availability) {
            RestaurantAvailability.Status.BUSY -> "Busy. Not accepting orders right now."
            RestaurantAvailability.Status.CLOSED -> "Closed. Not accepting orders right now."
            RestaurantAvailability.Status.OPEN -> null
        }
        holder.tvStatus.visibility = if (isOpen) View.VISIBLE else View.GONE
        holder.tvHours.text = RestaurantAvailability.hours(restaurant)
        val photoUrl = restaurant.imageUrl
        val fallbackPhoto = R.drawable.ic_logo
        holder.illustrative.visibility = View.GONE
        holder.photo.load(photoUrl.ifBlank { null }) {
            placeholder(fallbackPhoto)
            error(fallbackPhoto)
            fallback(fallbackPhoto)
            crossfade(true)
        }
        val saved = restaurant.uid in BuyerFavourites.ids(holder.itemView.context)
        holder.favourite.setImageResource(if (saved) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        holder.favourite.contentDescription = if (saved) "Unsave ${restaurant.name}" else "Save ${restaurant.name}"
        holder.favourite.setOnClickListener {
            BuyerFavourites.toggle(holder.itemView.context, restaurant.uid)
            onFavouriteChanged()
        }

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
