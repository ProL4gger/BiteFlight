package com.example.biteflight.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.CartManager
import com.google.android.material.appbar.MaterialToolbar

class CartActivity : AppCompatActivity() {

    private lateinit var adapter: CartAdapter

    private lateinit var tvTotal: TextView
    private lateinit var tvEmptyCart: TextView
    private lateinit var tvCartRestaurant: TextView

    private lateinit var btnCheckout: Button
    private lateinit var btnReturnToMenu: Button
    private lateinit var btnClearCart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_cart)

        // -------------------------
        // TOOLBAR
        // -------------------------

        val toolbar =
            findViewById<MaterialToolbar>(
                R.id.topToolbar
            )

        setSupportActionBar(toolbar)

        supportActionBar?.title = "Your Cart"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        // -------------------------
        // VIEWS
        // -------------------------

        val recyclerCart =
            findViewById<RecyclerView>(
                R.id.recyclerCart
            )

        tvTotal =
            findViewById(R.id.tvTotal)

        tvEmptyCart =
            findViewById(R.id.tvEmptyCart)

        tvCartRestaurant =
            findViewById(R.id.tvCartRestaurant)

        btnCheckout =
            findViewById(R.id.btnCheckout)

        btnReturnToMenu =
            findViewById(R.id.btnReturnToMenu)

        btnClearCart =
            findViewById(R.id.btnClearCart)


        // -------------------------
        // ADAPTER
        // -------------------------

        adapter =
            CartAdapter(
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

        recyclerCart.layoutManager =
            LinearLayoutManager(this)

        recyclerCart.adapter =
            adapter


        // -------------------------
        // CHECKOUT
        // -------------------------

        btnCheckout.setOnClickListener {

            if (CartManager.isEmpty()) {

                Toast.makeText(
                    this,
                    "Your cart is empty",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            startActivity(
                Intent(
                    this,
                    CheckoutActivity::class.java
                )
            )
        }


        // -------------------------
        // CONTINUE SHOPPING
        // -------------------------

        btnReturnToMenu.setOnClickListener {

            returnToRestaurantMenu()
        }


        // -------------------------
        // CLEAR CART
        // -------------------------

        btnClearCart.setOnClickListener {

            CartManager.clearCart()

            adapter.notifyDataSetChanged()

            updateCartInfo()

            Toast.makeText(
                this,
                "Cart cleared",
                Toast.LENGTH_SHORT
            ).show()
        }


        updateCartInfo()
    }


    override fun onResume() {
        super.onResume()

        adapter.notifyDataSetChanged()
        updateCartInfo()
    }


    private fun updateCartInfo() {

        if (CartManager.isEmpty()) {

            tvEmptyCart.visibility =
                View.VISIBLE

            tvCartRestaurant.text =
                "Your Cart"

            tvTotal.text =
                "Total: EGP 0"

            btnCheckout.isEnabled =
                false

            btnReturnToMenu.isEnabled =
                false

            btnClearCart.isEnabled =
                false

        } else {

            tvEmptyCart.visibility =
                View.GONE

            tvCartRestaurant.text =
                CartManager.restaurantName
                    ?: "Restaurant"

            tvTotal.text =
                "Total: EGP ${CartManager.getTotal()}"

            btnCheckout.isEnabled =
                true

            btnReturnToMenu.isEnabled =
                true

            btnClearCart.isEnabled =
                true
        }
    }


    private fun returnToRestaurantMenu() {

        val sellerId =
            CartManager.restaurantId

        val restaurantName =
            CartManager.restaurantName

        if (
            sellerId.isNullOrEmpty() ||
            restaurantName.isNullOrEmpty()
        ) {
            finish()
            return
        }

        val intent =
            Intent(
                this,
                RestaurantMenuActivity::class.java
            )

        intent.putExtra(
            "sellerId",
            sellerId
        )

        intent.putExtra(
            "restaurantName",
            restaurantName
        )

        intent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

        startActivity(intent)

        finish()
    }


    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}