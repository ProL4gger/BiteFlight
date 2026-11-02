package com.example.biteflight.ui.buyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.biteflight.R
import com.example.biteflight.data.model.Meal

class BuyerMealAdapter(
    private val meals: List<Meal>,
    private val onAddToCart: (Meal) -> Unit
) : RecyclerView.Adapter<BuyerMealAdapter.MealViewHolder>() {

    var orderingEnabled = false
    var orderButtonLabel = "Checking availability"

    class MealViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val photo: ImageView =
            itemView.findViewById(R.id.ivMealPhoto)

        val tvMealName: TextView =
            itemView.findViewById(R.id.tvMealName)

        val tvMealPrice: TextView =
            itemView.findViewById(R.id.tvMealPrice)

        val tvMealTags: TextView =
            itemView.findViewById(R.id.tvMealTags)

        val tvMealStock: TextView =
            itemView.findViewById(R.id.tvMealStock)

        val btnAddToCart: Button =
            itemView.findViewById(R.id.btnAddToCart)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MealViewHolder {

        val view = LayoutInflater
            .from(parent.context)
            .inflate(
                R.layout.item_buyer_meal,
                parent,
                false
            )

        return MealViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: MealViewHolder,
        position: Int
    ) {

        val meal = meals[position]

        holder.photo.contentDescription =
            "Photo of ${meal.name}"

        holder.photo.load(
            meal.imageUrl.ifBlank { null }
        ) {
            placeholder(R.drawable.ic_logo)
            error(R.drawable.ic_logo)
            fallback(R.drawable.ic_logo)
            crossfade(true)
        }

        holder.tvMealName.text = meal.name

        holder.tvMealPrice.text =
            "EGP ${meal.price}"

        holder.tvMealTags.text =
            meal.tags.joinToString(", ")

        val isInStock = meal.stockQuantity > 0

        // Show availability only, never the quantity.
        if (isInStock) {

            holder.tvMealStock.text = "Available"

            holder.tvMealStock.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    android.R.color.holo_green_dark
                )
            )

        } else {

            holder.tvMealStock.text = "Out of Stock"

            holder.tvMealStock.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    android.R.color.holo_red_dark
                )
            )
        }

        // Disable ordering when the item is unavailable
        // or the restaurant is closed/busy.
        holder.btnAddToCart.isEnabled =
            orderingEnabled && isInStock

        holder.btnAddToCart.text = when {
            !isInStock -> "Out of Stock"
            !orderingEnabled -> orderButtonLabel
            else -> "Add to Cart"
        }

        // Fade only the image of unavailable products.
        holder.photo.alpha =
            if (isInStock) 1.0f else 0.5f

        holder.btnAddToCart.setOnClickListener {

            if (orderingEnabled && meal.stockQuantity > 0) {
                onAddToCart(meal)
            }
        }
    }

    override fun getItemCount(): Int {
        return meals.size
    }
}
