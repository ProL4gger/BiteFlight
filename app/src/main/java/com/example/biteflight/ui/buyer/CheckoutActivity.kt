package com.example.biteflight.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biteflight.R
import com.example.biteflight.data.CartManager
import com.example.biteflight.data.model.Order
import com.example.biteflight.data.model.OrderItem
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class CheckoutActivity : AppCompatActivity() {

    private lateinit var etDeliveryAddress: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_checkout
        )

        // -------------------------
        // TOOLBAR
        // -------------------------

        val toolbar =
            findViewById<MaterialToolbar>(
                R.id.topToolbar
            )

        setSupportActionBar(toolbar)

        supportActionBar?.title =
            "Checkout"

        supportActionBar
            ?.setDisplayHomeAsUpEnabled(true)


        // -------------------------
        // EMPTY CART CHECK
        // -------------------------

        if (CartManager.isEmpty()) {

            Toast.makeText(
                this,
                "Your cart is empty",
                Toast.LENGTH_SHORT
            ).show()

            finish()
            return
        }


        // -------------------------
        // VIEWS
        // -------------------------

        val tvRestaurant =
            findViewById<TextView>(
                R.id.tvCheckoutRestaurant
            )

        val tvOrderSummary =
            findViewById<TextView>(
                R.id.tvOrderSummary
            )

        val tvTotal =
            findViewById<TextView>(
                R.id.tvCheckoutTotal
            )

        etDeliveryAddress =
            findViewById(
                R.id.etDeliveryAddress
            )

        val btnConfirmOrder =
            findViewById<Button>(
                R.id.btnConfirmOrder
            )


        tvRestaurant.text =
            CartManager.restaurantName
                ?: "Restaurant"

        tvTotal.text =
            "Total: EGP ${CartManager.getTotal()}"


        // -------------------------
        // ORDER SUMMARY
        // -------------------------

        val summary =
            StringBuilder()

        for (cartItem in CartManager.getItems()) {

            summary.append(
                "${cartItem.meal.name}  x${cartItem.quantity}"
            )

            summary.append(
                "  - EGP ${cartItem.meal.price * cartItem.quantity}"
            )

            summary.append("\n")
        }

        tvOrderSummary.text =
            summary.toString()


        // -------------------------
        // LOAD BUYER ADDRESS
        // -------------------------

        loadBuyerAddress()


        // -------------------------
        // CONFIRM ORDER
        // -------------------------

        btnConfirmOrder.setOnClickListener {

            val address =
                etDeliveryAddress
                    .text
                    .toString()
                    .trim()

            if (address.isEmpty()) {

                etDeliveryAddress.error =
                    "Enter delivery address"

                return@setOnClickListener
            }

            saveOrder(address)
        }
    }


    private fun loadBuyerAddress() {

        val user =
            FirebaseAuth
                .getInstance()
                .currentUser
                ?: return

        FirebaseDatabase
            .getInstance()
            .getReference("users")
            .child(user.uid)
            .child("address")
            .get()

            .addOnSuccessListener { snapshot ->

                val address =
                    snapshot.getValue(
                        String::class.java
                    )

                if (!address.isNullOrEmpty()) {

                    etDeliveryAddress.setText(
                        address
                    )
                }
            }
    }


    private fun saveOrder(
        deliveryAddress: String
    ) {

        val buyer =
            FirebaseAuth
                .getInstance()
                .currentUser

        if (buyer == null) {

            Toast.makeText(
                this,
                "You are not logged in",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        val sellerId =
            CartManager.restaurantId

        if (sellerId.isNullOrEmpty()) {

            Toast.makeText(
                this,
                "Restaurant not found",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        val ordersReference =
            FirebaseDatabase
                .getInstance()
                .getReference("orders")

        val orderId =
            ordersReference
                .push()
                .key

        if (orderId == null) {

            Toast.makeText(
                this,
                "Could not create order",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        val orderItems =
            CartManager
                .getItems()
                .map { cartItem ->

                    OrderItem(
                        mealId =
                            cartItem.meal.id,

                        name =
                            cartItem.meal.name,

                        price =
                            cartItem.meal.price,

                        quantity =
                            cartItem.quantity
                    )
                }


        val order =
            Order(
                id = orderId,

                buyerId =
                    buyer.uid,

                sellerId =
                    sellerId,

                restaurantName =
                    CartManager.restaurantName
                        ?: "Restaurant",

                deliveryAddress =
                    deliveryAddress,

                items =
                    orderItems,

                total =
                    CartManager.getTotal(),

                status =
                    "PLACED",

                createdAt =
                    System.currentTimeMillis()
            )


        ordersReference
            .child(orderId)
            .setValue(order)

            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Order placed successfully!",
                    Toast.LENGTH_LONG
                ).show()

                CartManager.clearCart()

                val intent =
                    Intent(
                        this,
                        BuyerOrdersActivity::class.java
                    )

                intent.flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP

                startActivity(intent)

                finish()
            }

            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Order failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    override fun onSupportNavigateUp(): Boolean {

        finish()

        return true
    }
}