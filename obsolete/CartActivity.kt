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
import com.google.firebase.database.FirebaseDatabase

class CartActivity : AppCompatActivity() {

    private lateinit var adapter: CartAdapter

    private lateinit var tvTotal: TextView
    private lateinit var tvEmptyCart: TextView
    private lateinit var tvCartRestaurant: TextView

    private lateinit var btnCheckout: Button
    private lateinit var btnReturnToMenu: Button
    private lateinit var btnClearCart: Button

    private var checkingStock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_cart)
        BuyerNavigation.attach(this, R.id.buyerCart)

        val toolbar =
            findViewById<MaterialToolbar>(R.id.topToolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.title = "Your Cart"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recyclerCart =
            findViewById<RecyclerView>(R.id.recyclerCart)

        tvTotal = findViewById(R.id.tvTotal)
        tvEmptyCart = findViewById(R.id.tvEmptyCart)
        tvCartRestaurant = findViewById(R.id.tvCartRestaurant)

        btnCheckout = findViewById(R.id.btnCheckout)
        btnReturnToMenu = findViewById(R.id.btnReturnToMenu)
        btnClearCart = findViewById(R.id.btnClearCart)

        adapter = CartAdapter(
            CartManager.getItems(),

            onPlus = { position ->
                checkStockAndIncrease(position)
            },

            onMinus = { position ->
                if (!checkingStock) {
                    CartManager.decreaseQuantity(position)
                    adapter.notifyDataSetChanged()
                    updateCartInfo()
                }
            },

            onRemove = { position ->
                if (!checkingStock) {
                    CartManager.removeItem(position)
                    adapter.notifyDataSetChanged()
                    updateCartInfo()
                }
            }
        )

        recyclerCart.layoutManager =
            LinearLayoutManager(this)

        recyclerCart.adapter = adapter

        btnCheckout.setOnClickListener {
            if (checkingStock) return@setOnClickListener

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

        btnReturnToMenu.setOnClickListener {
            if (!checkingStock) {
                returnToRestaurantMenu()
            }
        }

        btnClearCart.setOnClickListener {
            if (!checkingStock) {
                CartManager.clearCart()

                adapter.notifyDataSetChanged()
                updateCartInfo()

                Toast.makeText(
                    this,
                    "Cart cleared",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        updateCartInfo()
    }

    private fun checkStockAndIncrease(position: Int) {

        if (checkingStock) return

        val cartItem = CartManager.getItems()
            .getOrNull(position) ?: return

        val mealId = cartItem.meal.id

        if (mealId.isBlank()) {
            Toast.makeText(
                this,
                "Invalid item",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        checkingStock = true
        btnCheckout.isEnabled = false
        btnClearCart.isEnabled = false
        btnReturnToMenu.isEnabled = false

        FirebaseDatabase.getInstance()
            .getReference("menuItems")
            .child(mealId)
            .get()
            .addOnSuccessListener { snapshot ->

                if (isFinishing || isDestroyed) {
                    return@addOnSuccessListener
                }

                val stockQuantity = snapshot
                    .child("stockQuantity")
                    .getValue(Int::class.java) ?: 0

                val currentPosition =
                    CartManager.getItems().indexOfFirst {
                        it.meal.id == mealId
                    }

                if (currentPosition == -1) {
                    finishStockCheck()
                    return@addOnSuccessListener
                }

                val currentItem =
                    CartManager.getItems()[currentPosition]

                when {

                    !snapshot.exists() || stockQuantity <= 0 -> {

                        Toast.makeText(
                            this,
                            "Out of Stock",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    currentItem.quantity >= stockQuantity -> {

                        Toast.makeText(
                            this,
                            "Not enough quantity in stock",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    else -> {

                        CartManager.increaseQuantity(
                            currentPosition,
                            stockQuantity
                        )

                        adapter.notifyDataSetChanged()

                        Toast.makeText(
                            this,
                            "Quantity updated",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                finishStockCheck()
            }
            .addOnFailureListener {

                if (isFinishing || isDestroyed) {
                    return@addOnFailureListener
                }

                Toast.makeText(
                    this,
                    "Could not check stock. Please try again.",
                    Toast.LENGTH_LONG
                ).show()

                finishStockCheck()
            }
    }

    private fun finishStockCheck() {
        checkingStock = false
        updateCartInfo()
    }

    override fun onResume() {
        super.onResume()

        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
            updateCartInfo()
        }
    }

    private fun updateCartInfo() {

        if (CartManager.isEmpty()) {

            tvEmptyCart.visibility = View.VISIBLE
            tvCartRestaurant.text = "Your Cart"
            tvTotal.text = "Total: EGP 0"

            btnCheckout.isEnabled = false
            btnReturnToMenu.isEnabled = false
            btnClearCart.isEnabled = false

        } else {

            tvEmptyCart.visibility = View.GONE

            tvCartRestaurant.text =
                CartManager.restaurantName ?: "Restaurant"

            tvTotal.text =
                "Total: EGP ${CartManager.getTotal()}"

            btnCheckout.isEnabled = !checkingStock
            btnReturnToMenu.isEnabled = !checkingStock
            btnClearCart.isEnabled = !checkingStock
        }
    }

    private fun returnToRestaurantMenu() {

        val sellerId = CartManager.restaurantId
        val restaurantName = CartManager.restaurantName

        if (
            sellerId.isNullOrEmpty() ||
            restaurantName.isNullOrEmpty()
        ) {
            finish()
            return
        }

        val intent = Intent(
            this,
            RestaurantMenuActivity::class.java
        )

        intent.putExtra("sellerId", sellerId)
        intent.putExtra("restaurantName", restaurantName)

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
