package com.example.biteflight.ui.buyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.Meal

class BuyerMealAdapter(
    private val meals: List<Meal>,
    private val onAddToCart: (Meal) -> Unit
) : RecyclerView.Adapter<BuyerMealAdapter.MealViewHolder>() {

    class MealViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val tvMealName: TextView =
            itemView.findViewById(
                R.id.tvMealName
            )

        val tvMealPrice: TextView =
            itemView.findViewById(
                R.id.tvMealPrice
            )

        val tvMealTags: TextView =
            itemView.findViewById(
                R.id.tvMealTags
            )

        val btnAddToCart: Button =
            itemView.findViewById(
                R.id.btnAddToCart
            )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MealViewHolder {

        val view =
            LayoutInflater
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

        val meal =
            meals[position]

        holder.tvMealName.text =
            meal.name

        holder.tvMealPrice.text =
            "EGP ${meal.price}"

        holder.tvMealTags.text =
            meal.tags.joinToString(", ")

        holder.btnAddToCart.setOnClickListener {

            onAddToCart(meal)
        }
    }

    override fun getItemCount(): Int {

        return meals.size
    }
}