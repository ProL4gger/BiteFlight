package com.example.biteflight.ui.seller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
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

        val tvMealName: TextView =
            itemView.findViewById(R.id.tvMealName)

        val tvMealPrice: TextView =
            itemView.findViewById(R.id.tvMealPrice)

        val tvMealTags: TextView =
            itemView.findViewById(R.id.tvMealTags)

        val btnEditMeal: ImageButton =
            itemView.findViewById(R.id.btnEditMeal)

        val btnDeleteMeal: ImageButton =
            itemView.findViewById(R.id.btnDeleteMeal)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MealViewHolder {

        val view =
            LayoutInflater
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

        val meal =
            meals[position]

        holder.tvMealName.text =
            meal.name

        holder.tvMealPrice.text =
            "EGP ${meal.price}"

        holder.tvMealTags.text =
            meal.tags.joinToString(", ")

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