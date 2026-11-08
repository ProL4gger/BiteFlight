package com.example.biteflight.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biteflight.MainActivity
import com.example.biteflight.R
import com.example.biteflight.data.CartManager
import com.example.biteflight.data.model.Order
import com.example.biteflight.data.model.OrderItem
import com.example.biteflight.utils.RestaurantGate
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

class CheckoutActivity : AppCompatActivity() {

    private lateinit var etDeliveryAddress: EditText
    private lateinit var btnConfirmOrder: Button

    private var placingOrder = false

    // Private snapshot of an order item and its required quantity.
    private data class RequestedItem(
        val mealId: String,
        val sellerId: String,
        val quantity: Int,
        val name: String,
        val price: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_checkout)
        BuyerNavigation.attach(this, R.id.buyerCart)

        val toolbar =
            findViewById<MaterialToolbar>(R.id.topToolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.title = "Checkout"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (CartManager.isEmpty()) {
            showMessage("Your cart is empty")
            finish()
            return
        }

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
            findViewById(R.id.etDeliveryAddress)

        btnConfirmOrder =
            findViewById(R.id.btnConfirmOrder)

        tvRestaurant.text =
            CartManager.restaurantName ?: "Restaurant"

        tvTotal.text =
            "Total: EGP ${CartManager.getTotal()}"

        val summary = StringBuilder()

        for (cartItem in CartManager.getItems()) {
            summary.append(
                "${cartItem.meal.name} x${cartItem.quantity}"
            )
            summary.append(
                " - EGP ${cartItem.meal.price * cartItem.quantity}"
            )
            summary.append("\n")
        }

        tvOrderSummary.text = summary.toString()

        loadBuyerAddress()

        btnConfirmOrder.setOnClickListener {

            if (placingOrder) {
                return@setOnClickListener
            }

            val address =
                etDeliveryAddress.text.toString().trim()

            if (address.isEmpty()) {
                etDeliveryAddress.error =
                    "Enter delivery address"
                return@setOnClickListener
            }

            val sellerId = CartManager.restaurantId

            if (sellerId.isNullOrBlank() ||
                CartManager.isEmpty()
            ) {
                showMessage("Your cart is empty")
                return@setOnClickListener
            }

            val buyer =
                FirebaseAuth.getInstance().currentUser

            if (buyer == null) {
                showMessage("You are not logged in")
                return@setOnClickListener
            }

            // Copy the cart before starting asynchronous work.
            val requestedItems =
                CartManager.getItems().map { cartItem ->
                    RequestedItem(
                        mealId = cartItem.meal.id,
                        sellerId = cartItem.meal.sellerId,
                        quantity = cartItem.quantity,
                        name = cartItem.meal.name,
                        price = cartItem.meal.price
                    )
                }

            if (requestedItems.isEmpty() ||
                requestedItems.any {
                    it.mealId.isBlank() ||
                            it.sellerId != sellerId ||
                            it.quantity <= 0
                } ||
                requestedItems.map { it.mealId }.distinct().size
                != requestedItems.size
            ) {
                showMessage("Invalid cart items")
                return@setOnClickListener
            }

            placingOrder = true
            btnConfirmOrder.isEnabled = false

            RestaurantGate.check(sellerId) { error ->

                if (error != null) {
                    finishWithError(error)
                } else {
                    reserveStockAndPlaceOrder(
                        requestedItems = requestedItems,
                        sellerId = sellerId,
                        buyerId = buyer.uid,
                        deliveryAddress = address
                    )
                }
            }
        }
    }

    private fun reserveStockAndPlaceOrder(
        requestedItems: List<RequestedItem>,
        sellerId: String,
        buyerId: String,
        deliveryAddress: String
    ) {

        val database = FirebaseDatabase.getInstance()

        val menuReference =
            database.getReference("menuItems")

        val ordersReference =
            database.getReference("orders")

        val orderId = ordersReference.push().key

        if (orderId == null) {
            finishWithError("Could not create order")
            return
        }

        val orderItems = requestedItems.map {
            OrderItem(
                mealId = it.mealId,
                name = it.name,
                price = it.price,
                quantity = it.quantity
            )
        }

        val order = Order(
            id = orderId,
            buyerId = buyerId,
            sellerId = sellerId,
            restaurantName =
                CartManager.restaurantName ?: "Restaurant",
            deliveryAddress = deliveryAddress,
            items = orderItems,
            total = requestedItems.sumOf {
                it.price * it.quantity
            },
            status = "PLACED",
            createdAt = System.currentTimeMillis()
        )

        var stockFailure = "Not enough quantity in stock"

        // One transaction reserves ALL products in this cart.
        menuReference.runTransaction(
            object : Transaction.Handler {

                override fun doTransaction(
                    currentData: MutableData
                ): Transaction.Result {

                    val quantities = mutableMapOf<String, Long>()

                    // First validate all items, without changing anything.
                    for (item in requestedItems) {

                        val mealData =
                            currentData.child(item.mealId)

                        val storedSellerId =
                            mealData.child("sellerId")
                                .value as? String

                        val stock =
                            (mealData.child("stockQuantity")
                                .value as? Number)?.toLong() ?: 99L

                        if (!storedSellerId.isNullOrBlank() && storedSellerId != item.sellerId) {
                            stockFailure = "Item is no longer available"
                            return Transaction.abort()
                        }

                        if (stock <= 0L) {
                            stockFailure =
                                "${item.name} is Out of Stock"
                            return Transaction.abort()
                        }

                        if (stock < item.quantity.toLong()) {
                            stockFailure =
                                "Not enough quantity in stock for ${item.name}"
                            return Transaction.abort()
                        }

                        quantities[item.mealId] = stock
                    }

                    // All items have enough stock.
                    // Deduct every item in the same transaction.
                    for (item in requestedItems) {

                        val existingStock =
                            quantities[item.mealId]
                                ?: return Transaction.abort()

                        currentData.child(item.mealId)
                            .child("stockQuantity")
                            .value =
                            existingStock - item.quantity.toLong()
                    }

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {

                    if (error != null) {
                        finishWithError(
                            "Could not update stock: ${error.message}"
                        )
                        return
                    }

                    if (!committed) {
                        finishWithError(stockFailure)
                        return
                    }

                    // Stock has been reserved successfully.
                    // Now save the order.
                    ordersReference.child(orderId)
                        .setValue(order)
                        .addOnSuccessListener {

                            CartManager.clearCart()

                            placingOrder = false

                            if (!isFinishing && !isDestroyed) {
                                showMessage(
                                    "Order placed successfully!"
                                )

                                val intent = Intent(
                                    this@CheckoutActivity,
                                    MainActivity::class.java
                                ).apply {
                                    putExtra("targetTab", 2) // Orders tab is at index 2 [Home, Cart, Orders, Profile]
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }

                                startActivity(intent)
                                finish()
                            }
                        }
                        .addOnFailureListener { saveError ->

                            // Attempt to restore reserved stock
                            // if saving the order fails.
                            restoreStock(
                                menuReference,
                                requestedItems
                            ) { restored ->

                                val message = if (restored) {
                                    "Order failed: ${saveError.message}"
                                } else {
                                    "Order save failed and stock recovery " +
                                            "needs administrator review"
                                }

                                finishWithError(message)
                            }
                        }
                }
            },
            false
        )
    }

    // Compensation if the order write fails.
    // This is NOT equivalent to an atomic server-side checkout.
    private fun restoreStock(
        menuReference: DatabaseReference,
        requestedItems: List<RequestedItem>,
        onComplete: (Boolean) -> Unit
    ) {

        menuReference.runTransaction(
            object : Transaction.Handler {

                override fun doTransaction(
                    currentData: MutableData
                ): Transaction.Result {

                    for (item in requestedItems) {

                        val mealData =
                            currentData.child(item.mealId)

                        val currentStock =
                            (mealData.child("stockQuantity")
                                .value as? Number)?.toLong()
                                ?: return Transaction.abort()

                        val currentSellerId =
                            mealData.child("sellerId")
                                .value as? String

                        if (currentSellerId != item.sellerId) {
                            return Transaction.abort()
                        }

                        if (currentStock >
                            Long.MAX_VALUE - item.quantity.toLong()
                        ) {
                            return Transaction.abort()
                        }
                    }

                    for (item in requestedItems) {

                        val mealData =
                            currentData.child(item.mealId)

                        val currentStock =
                            (mealData.child("stockQuantity")
                                .value as Number).toLong()

                        mealData.child("stockQuantity").value =
                            currentStock + item.quantity.toLong()
                    }

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    onComplete(error == null && committed)
                }
            },
            false
        )
    }

    private fun loadBuyerAddress() {

        val user = FirebaseAuth.getInstance()
            .currentUser ?: return

        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(user.uid)
            .child("address")
            .get()
            .addOnSuccessListener { snapshot ->

                val address =
                    snapshot.getValue(String::class.java)

                if (!address.isNullOrEmpty()) {
                    etDeliveryAddress.setText(address)
                }
            }
    }

    private fun finishWithError(message: String) {

        placingOrder = false

        if (!isFinishing && !isDestroyed) {
            btnConfirmOrder.isEnabled = true
            showMessage(message)
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onSupportNavigateUp(): Boolean {

        if (placingOrder) {
            showMessage("Order is being processed")
            return true
        }

        finish()
        return true
    }
}
