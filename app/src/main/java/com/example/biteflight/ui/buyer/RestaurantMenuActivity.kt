package com.example.biteflight.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.biteflight.data.model.User
import com.example.biteflight.utils.RestaurantGate
import com.example.biteflight.utils.RestaurantAvailability
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class RestaurantMenuActivity : AppCompatActivity() {

    private val mealList = mutableListOf<Meal>()
    private lateinit var adapter: BuyerMealAdapter

    private var checkingAvailability = false
    private var sellerProfile: User? = null

    private var statusListener: ValueEventListener? = null
    private var menuListener: ValueEventListener? = null
    private var menuQuery: Query? = null

    private val statusHandler = Handler(Looper.getMainLooper())

    private val refreshStatus = object : Runnable {
        override fun run() {

            val status = sellerProfile?.let {
                "${RestaurantAvailability.label(it)}\n" +
                        RestaurantAvailability.hours(it)
            } ?: "Restaurant unavailable"

            findViewById<TextView>(
                R.id.tvRestaurantAvailability
            ).text = status

            val availability = sellerProfile?.let {
                RestaurantAvailability.status(it)
            }

            adapter.orderingEnabled =
                availability == RestaurantAvailability.Status.OPEN

            adapter.orderButtonLabel = when (availability) {

                RestaurantAvailability.Status.OPEN ->
                    "Add to Cart"

                RestaurantAvailability.Status.BUSY ->
                    "Busy — orders paused"

                RestaurantAvailability.Status.CLOSED ->
                    "Closed"

                null -> "Unavailable"
            }

            adapter.notifyDataSetChanged()

            statusHandler.postDelayed(
                this,
                60_000L - System.currentTimeMillis() % 60_000L
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_restaurant_menu)

        BuyerNavigation.attach(this, R.id.buyerHome)

        val sellerId = intent.getStringExtra("sellerId")

        val restaurantName =
            intent.getStringExtra("restaurantName") ?: "Restaurant"

        if (sellerId.isNullOrEmpty()) {
            Toast.makeText(
                this,
                "Restaurant not found",
                Toast.LENGTH_SHORT
            ).show()

            finish()
            return
        }

        // TOOLBAR
        val toolbar =
            findViewById<MaterialToolbar>(R.id.topToolbar)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->

            val statusBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars()
            )

            view.updatePadding(top = statusBarInsets.top)
            insets
        }

        ViewCompat.requestApplyInsets(toolbar)

        setSupportActionBar(toolbar)

        supportActionBar?.title = restaurantName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<TextView>(
            R.id.tvRestaurantName
        ).text = restaurantName

        // RESTAURANT MENU
        val recyclerRestaurantMenu =
            findViewById<RecyclerView>(
                R.id.recyclerRestaurantMenu
            )

        adapter = BuyerMealAdapter(mealList) { meal ->

            addMealToCart(
                meal,
                sellerId,
                restaurantName
            )
        }

        recyclerRestaurantMenu.layoutManager =
            LinearLayoutManager(this)

        recyclerRestaurantMenu.adapter = adapter
    }

    override fun onStart() {
        super.onStart()

        val sellerId =
            intent.getStringExtra("sellerId") ?: return

        // Listen to restaurant opening status.
        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                sellerProfile =
                    snapshot.getValue(User::class.java)

                statusHandler.removeCallbacks(refreshStatus)
                refreshStatus.run()
            }

            override fun onCancelled(error: DatabaseError) {

                sellerProfile = null

                statusHandler.removeCallbacks(refreshStatus)

                adapter.orderingEnabled = false
                adapter.orderButtonLabel = "Unavailable"

                adapter.notifyDataSetChanged()

                findViewById<TextView>(
                    R.id.tvRestaurantAvailability
                ).text = "Could not check restaurant availability"
            }
        }

        statusListener = listener

        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(sellerId)
            .addValueEventListener(listener)

        loadRestaurantMenu(sellerId)
    }

    override fun onStop() {

        val sellerId =
            intent.getStringExtra("sellerId")

        if (sellerId != null) {
            statusListener?.let { listener ->

                FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(sellerId)
                    .removeEventListener(listener)
            }
        }

        menuListener?.let { listener ->
            menuQuery?.removeEventListener(listener)
        }

        menuListener = null
        menuQuery = null
        statusListener = null

        statusHandler.removeCallbacks(refreshStatus)

        super.onStop()
    }

    // ADD MEAL TO CART
    private fun addMealToCart(
        meal: Meal,
        sellerId: String,
        restaurantName: String
    ) {

        if (checkingAvailability) return

        if (meal.stockQuantity <= 0) {
            showOutOfStock(meal.name)
            return
        }

        checkingAvailability = true

        // First check whether restaurant accepts orders.
        RestaurantGate.check(sellerId) { error ->

            if (isFinishing || isDestroyed) {
                checkingAvailability = false
                return@check
            }

            if (error != null) {

                checkingAvailability = false

                Toast.makeText(
                    this,
                    error,
                    Toast.LENGTH_LONG
                ).show()

                return@check
            }

            // Then retrieve current stock from Firebase.
            checkCurrentStock(meal) { currentMeal ->

                checkingAvailability = false

                if (isFinishing || isDestroyed) {
                    return@checkCurrentStock
                }

                if (currentMeal == null) {
                    return@checkCurrentStock
                }

                addAvailableMeal(
                    currentMeal,
                    sellerId,
                    restaurantName
                )
            }
        }
    }

    // CHECK LATEST STOCK
    private fun checkCurrentStock(
        meal: Meal,
        onResult: (Meal?) -> Unit
    ) {

        FirebaseDatabase.getInstance()
            .getReference("menuItems")
            .child(meal.id)
            .get()
            .addOnSuccessListener { snapshot ->

                val latestMeal =
                    snapshot.getValue(Meal::class.java)

                if (latestMeal == null ||
                    latestMeal.sellerId != meal.sellerId ||
                    latestMeal.stockQuantity <= 0
                ) {

                    showOutOfStock(meal.name)
                    onResult(null)

                } else {

                    onResult(latestMeal)
                }
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "Could not check stock. Please try again.",
                    Toast.LENGTH_LONG
                ).show()

                onResult(null)
            }
    }

    private fun showOutOfStock(mealName: String) {

        Toast.makeText(
            this,
            "$mealName is Out of Stock",
            Toast.LENGTH_LONG
        ).show()
    }

    // HANDLE ADDING AN AVAILABLE PRODUCT
    private fun addAvailableMeal(
        meal: Meal,
        sellerId: String,
        restaurantName: String
    ) {

        if (meal.stockQuantity <= 0) {
            showOutOfStock(meal.name)
            return
        }

        if (CartManager.canAddFromRestaurant(sellerId)) {

            // CartManager checks the cart quantity
            // against the latest stock fetched above.
            val result = CartManager.addItem(
                meal,
                sellerId,
                restaurantName
            )

            handleAddResult(result, meal)

        } else {

            AlertDialog.Builder(this)
                .setTitle("Different Restaurant")
                .setMessage(
                    "Your cart contains items from " +
                            "${CartManager.restaurantName}. " +
                            "To order from $restaurantName, " +
                            "you must discard your current cart."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start New Cart") { _, _ ->

                    if (checkingAvailability) {
                        return@setPositiveButton
                    }

                    checkingAvailability = true

                    RestaurantGate.check(sellerId) { error ->

                        if (isFinishing || isDestroyed) {
                            checkingAvailability = false
                            return@check
                        }

                        if (error != null) {
                            checkingAvailability = false

                            Toast.makeText(
                                this,
                                error,
                                Toast.LENGTH_LONG
                            ).show()

                            return@check
                        }

                        // Recheck stock before replacing cart.
                        checkCurrentStock(meal) { latestMeal ->

                            checkingAvailability = false

                            if (isFinishing || isDestroyed) {
                                return@checkCurrentStock
                            }

                            if (latestMeal == null) {
                                return@checkCurrentStock
                            }

                            // Stock is available, so replace cart.
                            CartManager.clearCart()

                            val result = CartManager.addItem(
                                latestMeal,
                                sellerId,
                                restaurantName
                            )

                            handleAddResult(result, latestMeal)
                        }
                    }
                }
                .show()
        }
    }

    // HANDLE CART MANAGER RESULTS
    private fun handleAddResult(
        result: CartManager.AddResult,
        meal: Meal
    ) {

        when (result) {

            CartManager.AddResult.SUCCESS -> {

                Toast.makeText(
                    this,
                    "${meal.name} added to cart",
                    Toast.LENGTH_SHORT
                ).show()

                openCart()
            }

            CartManager.AddResult.OUT_OF_STOCK -> {

                showOutOfStock(meal.name)
            }

            CartManager.AddResult.NOT_ENOUGH_STOCK -> {

                Toast.makeText(
                    this,
                    "Not enough quantity in stock",
                    Toast.LENGTH_LONG
                ).show()
            }

            CartManager.AddResult.DIFFERENT_RESTAURANT -> {

                Toast.makeText(
                    this,
                    "Your cart contains items from another restaurant",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openCart() {

        val cartIntent = Intent(
            this,
            CartActivity::class.java
        )

        startActivity(cartIntent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // LIVE FIREBASE MENU
    private fun loadRestaurantMenu(sellerId: String) {

        val query = FirebaseDatabase.getInstance()
            .getReference("menuItems")
            .orderByChild("sellerId")
            .equalTo(sellerId)

        menuQuery = query

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                mealList.clear()

                for (itemSnapshot in snapshot.children) {

                    val meal =
                        itemSnapshot.getValue(Meal::class.java)

                    if (meal != null) {
                        mealList.add(meal)
                    }
                }

                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {

                mealList.clear()
                adapter.notifyDataSetChanged()

                Toast.makeText(
                    this@RestaurantMenuActivity,
                    "Error loading menu: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        menuListener = listener
        query.addValueEventListener(listener)
    }
}
