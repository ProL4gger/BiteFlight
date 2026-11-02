package com.example.biteflight.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.CartManager

class BuyerCartFragment : Fragment() {

    private lateinit var adapter: CartAdapter
    private lateinit var tvTotal: TextView
    private lateinit var tvEmptyCart: TextView
    private lateinit var tvCartRestaurant: TextView
    private lateinit var btnCheckout: Button
    private lateinit var btnReturnToMenu: Button
    private lateinit var btnClearCart: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_cart, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.topToolbar)?.visibility = View.GONE

        val recyclerCart = view.findViewById<RecyclerView>(R.id.recyclerCart)
        tvTotal = view.findViewById(R.id.tvTotal)
        tvEmptyCart = view.findViewById(R.id.tvEmptyCart)
        tvCartRestaurant = view.findViewById(R.id.tvCartRestaurant)
        btnCheckout = view.findViewById(R.id.btnCheckout)
        btnReturnToMenu = view.findViewById(R.id.btnReturnToMenu)
        btnClearCart = view.findViewById(R.id.btnClearCart)

        adapter = CartAdapter(
            CartManager.getItems(),
            onPlus = { position ->
                CartManager.increaseQuantity(position)
                adapter.notifyItemChanged(position)
                updateCartInfo()
            },
            onMinus = { position ->
                CartManager.decreaseQuantity(position)
                adapter.notifyDataSetChanged()
                updateCartInfo()
            },
            onRemove = { position ->
                CartManager.removeItem(position)
                adapter.notifyDataSetChanged()
                updateCartInfo()
            }
        )

        recyclerCart.layoutManager = LinearLayoutManager(requireContext())
        recyclerCart.adapter = adapter

        btnCheckout.setOnClickListener {
            if (CartManager.isEmpty()) {
                Toast.makeText(requireContext(), "Your cart is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(requireContext(), CheckoutActivity::class.java))
        }

        btnReturnToMenu.setOnClickListener {
            val sellerId = CartManager.restaurantId
            val restaurantName = CartManager.restaurantName
            if (!sellerId.isNullOrEmpty() && !restaurantName.isNullOrEmpty()) {
                startActivity(Intent(requireContext(), RestaurantMenuActivity::class.java).apply {
                    putExtra("sellerId", sellerId)
                    putExtra("restaurantName", restaurantName)
                })
            }
        }

        btnClearCart.setOnClickListener {
            CartManager.clearCart()
            adapter.notifyDataSetChanged()
            updateCartInfo()
            Toast.makeText(requireContext(), "Cart cleared", Toast.LENGTH_SHORT).show()
        }

        updateCartInfo()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
        updateCartInfo()
    }

    private fun updateCartInfo() {
        val view = view ?: return
        if (CartManager.isEmpty()) {
            tvEmptyCart.visibility = View.VISIBLE
            tvCartRestaurant.text = "Your Cart"
            tvTotal.text = "Total: EGP 0"
            btnCheckout.isEnabled = false
            btnReturnToMenu.isEnabled = false
            btnClearCart.isEnabled = false
        } else {
            tvEmptyCart.visibility = View.GONE
            tvCartRestaurant.text = CartManager.restaurantName ?: "Restaurant"
            tvTotal.text = "Total: EGP ${CartManager.getTotal()}"
            btnCheckout.isEnabled = true
            btnReturnToMenu.isEnabled = true
            btnClearCart.isEnabled = true
        }
    }
}
