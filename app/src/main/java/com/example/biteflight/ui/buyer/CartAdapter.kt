package com.example.biteflight.ui.buyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.CartItem

class CartAdapter(
    private val items: MutableList<CartItem>,
    private val onPlus: (Int) -> Unit,
    private val onMinus: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    class CartViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val tvName: TextView =
            itemView.findViewById(R.id.tvCartMealName)

        val tvPrice: TextView =
            itemView.findViewById(R.id.tvCartMealPrice)

        val tvQuantity: TextView =
            itemView.findViewById(R.id.tvQuantity)

        val btnPlus: Button =
            itemView.findViewById(R.id.btnPlus)

        val btnMinus: Button =
            itemView.findViewById(R.id.btnMinus)

        val btnRemove: ImageButton =
            itemView.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CartViewHolder {

        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_cart,
                    parent,
                    false
                )

        return CartViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: CartViewHolder,
        position: Int
    ) {

        val item = items[position]

        holder.tvName.text =
            item.meal.name

        holder.tvPrice.text =
            "EGP ${item.meal.price}"

        holder.tvQuantity.text =
            item.quantity.toString()

        holder.btnPlus.setOnClickListener {

            val currentPosition =
                holder.bindingAdapterPosition

            if (currentPosition != RecyclerView.NO_POSITION) {
                onPlus(currentPosition)
            }
        }

        holder.btnMinus.setOnClickListener {

            val currentPosition =
                holder.bindingAdapterPosition

            if (currentPosition != RecyclerView.NO_POSITION) {
                onMinus(currentPosition)
            }
        }

        holder.btnRemove.setOnClickListener {

            val currentPosition =
                holder.bindingAdapterPosition

            if (currentPosition != RecyclerView.NO_POSITION) {
                onRemove(currentPosition)
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}