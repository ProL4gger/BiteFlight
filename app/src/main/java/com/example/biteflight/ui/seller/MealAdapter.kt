package com.example.biteflight.ui.seller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.biteflight.R
import com.example.biteflight.data.model.Meal

class MealAdapter(
    private val meals: List<Meal>,
    private val onEditClick: (Meal) -> Unit,
    private val onDeleteClick: (Meal) -> Unit
) : RecyclerView.Adapter<MealAdapter.MealViewHolder>() {

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

        // NEW: Stock status
        val tvMealStock: TextView =
            itemView.findViewById(R.id.tvMealStock)

        val btnEditMeal: ImageButton =
            itemView.findViewById(R.id.btnEditMeal)

        val btnDeleteMeal: ImageButton =
            itemView.findViewById(R.id.btnDeleteMeal)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MealViewHolder {

        val view = LayoutInflater
            .from(parent.context)
            .inflate(
                R.layout.item_meal,
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

        holder.photo.contentDescription = "Photo of ${meal.name}"

        holder.photo.load(meal.imageUrl.ifBlank { null }) {
            placeholder(R.drawable.ic_logo)
            error(R.drawable.ic_logo)
            fallback(R.drawable.ic_logo)
            crossfade(true)
        }

        holder.tvMealName.text = meal.name

        holder.tvMealPrice.text = "EGP ${meal.price}"

        holder.tvMealTags.text = meal.tags.joinToString(", ")

        // NEW: Display current stock
        if (meal.stockQuantity > 0) {

            holder.tvMealStock.text =
                "Available: ${meal.stockQuantity}"

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

        holder.btnEditMeal.setOnClickListener {
            onEditClick(meal)
        }

        holder.btnDeleteMeal.setOnClickListener {
            onDeleteClick(meal)
        }
    }

    override fun getItemCount(): Int {
        return meals.size
    }
}
