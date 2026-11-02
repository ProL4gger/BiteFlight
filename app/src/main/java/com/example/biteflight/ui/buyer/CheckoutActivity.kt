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
import com.example.biteflight.utils.RestaurantGate
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.UUID

class CheckoutActivity : AppCompatActivity() {
    private lateinit var etDeliveryAddress: EditText
    private lateinit var confirmButton: Button
    private var requestId = UUID.randomUUID().toString()
    private var pendingRequest: HashMap<String, Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)
        BuyerNavigation.attach(this, R.id.buyerCart)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.topToolbar))
        supportActionBar?.apply {
            title = "Checkout"
            setDisplayHomeAsUpEnabled(true)
        }
        requestId = savedInstanceState?.getString("requestId") ?: requestId
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val restored = savedInstanceState?.getSerializable("pendingRequest") as? HashMap<String, Any>
        pendingRequest = restored
        if (CartManager.isEmpty() && pendingRequest == null) {
            Toast.makeText(this, "Your cart is empty", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        etDeliveryAddress = findViewById(R.id.etDeliveryAddress)
        confirmButton = findViewById(R.id.btnConfirmOrder)
        findViewById<TextView>(R.id.tvCheckoutRestaurant).text = CartManager.restaurantName ?: "Restaurant"
        findViewById<TextView>(R.id.tvCheckoutTotal).text = "Total: EGP ${CartManager.getTotal()}"
        findViewById<TextView>(R.id.tvOrderSummary).text = CartManager.getItems().joinToString("\n") {
            "${it.meal.name} x${it.quantity} - EGP ${it.meal.price * it.quantity}"
        }
        pendingRequest?.let {
            etDeliveryAddress.setText(it["deliveryAddress"] as? String)
            etDeliveryAddress.isEnabled = false
            confirmButton.text = "Retry order confirmation"
        }
        if (savedInstanceState == null && pendingRequest == null) loadBuyerAddress()
        confirmButton.setOnClickListener { placeOrder() }
    }

    private fun loadBuyerAddress() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("address").get()
            .addOnSuccessListener { snapshot ->
                if (!isFinishing && !isDestroyed && pendingRequest == null && etDeliveryAddress.text.isBlank()) {
                    etDeliveryAddress.setText(snapshot.getValue(String::class.java).orEmpty())
                }
            }
    }

    private fun placeOrder() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_LONG).show()
            return
        }
        if (pendingRequest == null) {
            val address = etDeliveryAddress.text.toString().trim()
            if (address.isEmpty() || address.length > 1000) {
                etDeliveryAddress.error = "Enter a delivery address (up to 1000 characters)"
                return
            }
            val sellerId = CartManager.restaurantId
            val items = CartManager.getItems()
            if (sellerId.isNullOrBlank() || items.isEmpty()) return
            if (items.size > 100 || items.any { it.quantity !in 1..99 }) {
                Toast.makeText(this, "Use up to 100 items with quantities from 1 to 99", Toast.LENGTH_LONG).show()
                return
            }
            pendingRequest = hashMapOf(
                "id" to "${uid}_$requestId", "buyerId" to uid, "sellerId" to sellerId,
                "restaurantName" to (CartManager.restaurantName ?: "Restaurant"),
                "deliveryAddress" to address,
                "items" to ArrayList(items.map {
                    hashMapOf<String, Any>("mealId" to it.meal.id, "name" to it.meal.name,
                        "price" to it.meal.price, "quantity" to it.quantity)
                }),
                "status" to "PLACED", "createdAt" to HashMap(ServerValue.TIMESTAMP),
                "schemaVersion" to 2
            )
        }
        val payload = pendingRequest!!
        if (payload["buyerId"] != uid) {
            Toast.makeText(this, "Sign in to the account that started this checkout", Toast.LENGTH_LONG).show()
            return
        }
        confirmButton.isEnabled = false
        etDeliveryAddress.isEnabled = false
        val ref = FirebaseDatabase.getInstance().getReference("orders").child(payload["id"] as String)
        // Check for a previous successful attempt before checking current availability.
        ref.get().addOnSuccessListener { existing ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            if (existing.exists()) {
                orderSaved()
            } else {
                RestaurantGate.check(payload["sellerId"] as String) { error ->
                    if (isFinishing || isDestroyed) return@check
                    if (error != null) showFailure(error)
                    else writeOrder(ref, payload)
                }
            }
        }.addOnFailureListener {
            showFailure("Could not check order confirmation. Please retry.")
        }
    }

    private fun writeOrder(ref: DatabaseReference, payload: HashMap<String, Any>) {
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.value != null) return Transaction.abort()
                currentData.value = payload
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (isFinishing || isDestroyed) return
                if (error == null && (committed || snapshot?.exists() == true)) {
                    orderSaved()
                } else if (error?.code == DatabaseError.PERMISSION_DENIED) {
                    // Rules may reject a changed menu price, deleted item or busy restaurant.
                    pendingRequest = null
                    etDeliveryAddress.isEnabled = true
                    showFailure("Order was not accepted. Check restaurant availability and refresh your cart.")
                    confirmButton.text = "Confirm order"
                } else {
                    showFailure("Could not confirm the order. Retry here before starting another checkout.")
                }
            }
        }, false)
    }

    private fun orderSaved() {
        CartManager.clearCart()
        Toast.makeText(this, "Order placed successfully!", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, BuyerOrdersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
        finish()
    }

    private fun showFailure(message: String) {
        if (isFinishing || isDestroyed) return
        confirmButton.isEnabled = true
        confirmButton.text = "Retry order confirmation"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("requestId", requestId)
        pendingRequest?.let { outState.putSerializable("pendingRequest", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
