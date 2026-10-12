package com.example.biteflight.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.CartManager
import com.example.biteflight.data.model.Meal
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.database.FirebaseDatabase

class RestaurantMenuActivity : AppCompatActivity() {

    private val mealList =
        mutableListOf<Meal>()

    private lateinit var adapter: BuyerMealAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_restaurant_menu
        )

        // -------------------------
        // GET RESTAURANT DATA
        // -------------------------

        val sellerId =
            intent.getStringExtra("sellerId")

        val restaurantName =
            intent.getStringExtra("restaurantName")
                ?: "Restaurant"

        if (sellerId.isNullOrEmpty()) {

            Toast.makeText(
                this,
                "Restaurant not found",
                Toast.LENGTH_SHORT
            ).show()

            finish()
            return
        }

        // -------------------------
        // TOOLBAR
        // -------------------------

        val toolbar =
            findViewById<MaterialToolbar>(
                R.id.topToolbar
            )

        ViewCompat.setOnApplyWindowInsetsListener(
            toolbar
        ) { view, insets ->

            val statusBarInsets =
                insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                )

            view.updatePadding(
                top = statusBarInsets.top
            )

            insets
        }

        ViewCompat.requestApplyInsets(toolbar)

        setSupportActionBar(toolbar)

        supportActionBar?.title =
            restaurantName

        supportActionBar
            ?.setDisplayHomeAsUpEnabled(true)

        // -------------------------
        // RESTAURANT NAME
        // -------------------------

        val tvRestaurantName =
            findViewById<TextView>(
                R.id.tvRestaurantName
            )

        tvRestaurantName.text =
            restaurantName

        // -------------------------
        // MENU
        // -------------------------

        val recyclerRestaurantMenu =
            findViewById<RecyclerView>(
                R.id.recyclerRestaurantMenu
            )

        adapter =
            BuyerMealAdapter(
                mealList
            ) { meal ->

                addMealToCart(
                    meal,
                    sellerId,
                    restaurantName
                )
            }

        recyclerRestaurantMenu.layoutManager =
            LinearLayoutManager(this)

        recyclerRestaurantMenu.adapter =
            adapter

        // -------------------------
        // LOAD THIS RESTAURANT MENU
        // -------------------------

        loadRestaurantMenu(
            sellerId
        )
    }

    // -------------------------
    // ADD ITEM TO CART
    // -------------------------

    private fun addMealToCart(
        meal: Meal,
        sellerId: String,
        restaurantName: String
    ) {

        if (
            CartManager.canAddFromRestaurant(
                sellerId
            )
        ) {

            CartManager.addItem(
                meal,
                sellerId,
                restaurantName
            )

            Toast.makeText(
                this,
                "${meal.name} added to cart",
                Toast.LENGTH_SHORT
            ).show()

            openCart()

        } else {

            AlertDialog.Builder(this)
                .setTitle("Different Restaurant")
                .setMessage(
                    "Your cart contains items from ${CartManager.restaurantName}. " +
                            "To order from $restaurantName, you must discard your current cart."
                )

                .setNegativeButton(
                    "Cancel",
                    null
                )

                .setPositiveButton(
                    "Start New Cart"
                ) { _, _ ->

                    CartManager.clearCart()

                    CartManager.addItem(
                        meal,
                        sellerId,
                        restaurantName
                    )

                    Toast.makeText(
                        this,
                        "New cart started. ${meal.name} added.",
                        Toast.LENGTH_SHORT
                    ).show()

                    openCart()
                }

                .show()
        }
    }

    // -------------------------
    // OPEN CART
    // -------------------------

    private fun openCart() {

        val intent =
            Intent(
                this,
                CartActivity::class.java
            )

        startActivity(intent)
    }

    // -------------------------
    // BACK BUTTON
    // -------------------------

    override fun onSupportNavigateUp(): Boolean {

        finish()

        return true
    }

    // -------------------------
    // FIREBASE MENU
    // -------------------------

    private fun loadRestaurantMenu(
        sellerId: String
    ) {

        val database =
            FirebaseDatabase
                .getInstance()
                .getReference("menuItems")

        database
            .orderByChild("sellerId")
            .equalTo(sellerId)
            .get()

            .addOnSuccessListener { snapshot ->

                mealList.clear()

                for (
                itemSnapshot
                in snapshot.children
                ) {

                    val meal =
                        itemSnapshot.getValue(
                            Meal::class.java
                        )

                    if (meal != null) {

                        mealList.add(meal)
                    }
                }

                adapter.notifyDataSetChanged()

                if (mealList.isEmpty()) {

                    Toast.makeText(
                        this,
                        "This restaurant has no items yet",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}