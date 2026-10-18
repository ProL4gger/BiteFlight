package com.example.biteflight

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.data.model.Meal
import com.example.biteflight.data.model.User
import com.example.biteflight.ui.buyer.*
import com.example.biteflight.ui.profile.EditProfileActivity
import com.example.biteflight.utils.RestaurantAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {
    private val allRestaurants = mutableListOf<User>()
    private val visibleRestaurants = mutableListOf<User>()
    private val meals = mutableListOf<Meal>()
    private lateinit var adapter: RestaurantAdapter
    private val database = FirebaseDatabase.getInstance()
    private var usersListener: ValueEventListener? = null
    private var mealsListener: ValueEventListener? = null
    private var openOnly = false
    private var favouritesOnly = false
    private var loaded = false
    private var loadError: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            filterRestaurants()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        BuyerNavigation.attach(this, R.id.buyerHome)
        openOnly = savedInstanceState?.getBoolean("openOnly") ?: false
        favouritesOnly = savedInstanceState?.getBoolean("favouritesOnly") ?: false
        adapter = RestaurantAdapter(visibleRestaurants, { filterRestaurants() }) { restaurant ->
            startActivity(Intent(this, RestaurantMenuActivity::class.java)
                .putExtra("sellerId", restaurant.uid).putExtra("restaurantName", restaurant.name))
        }
        findViewById<RecyclerView>(R.id.recyclerRestaurants).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        findViewById<EditText>(R.id.etRestaurantSearch).apply {
            setText(savedInstanceState?.getString("query") ?: "")
            doAfterTextChanged { filterRestaurants() }
        }
        findViewById<ImageButton>(R.id.btnNotifications).setOnClickListener {
            startActivity(Intent(this, BuyerOrdersActivity::class.java))
        }
        findViewById<TextView>(R.id.tvBuyerLocation).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnRestaurantFilters).setOnClickListener {
            val choices = booleanArrayOf(openOnly, favouritesOnly)
            AlertDialog.Builder(this).setTitle("Find your next favourite")
                .setMultiChoiceItems(arrayOf("Accepting orders now", "Saved restaurants"), choices) { _, index, checked ->
                    choices[index] = checked
                }.setPositiveButton("Apply") { _, _ ->
                    openOnly = choices[0]; favouritesOnly = choices[1]; filterRestaurants()
                }.setNegativeButton("Cancel", null).show()
        }
        findViewById<TextView>(R.id.btnSeeAll).setOnClickListener {
            openOnly = false; favouritesOnly = false
            findViewById<EditText>(R.id.etRestaurantSearch).text.clear()
            filterRestaurants()
        }
    }

    override fun onStart() {
        super.onStart()
        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allRestaurants.clear()
                for (child in snapshot.children) {
                    val user = child.getValue(User::class.java) ?: continue
                    if (user.role.equals(User.ROLE_SELLER, true)) {
                        allRestaurants.add(user.copy(uid = child.key ?: continue))
                    }
                    if (child.key == FirebaseAuth.getInstance().currentUser?.uid) {
                        findViewById<TextView>(R.id.tvBuyerLocation).text =
                            user.address.ifBlank { "Set your delivery address" } + "  ⌄"
                    }
                }
                loaded = true; loadError = null
                filterRestaurants()
            }
            override fun onCancelled(error: DatabaseError) {
                loaded = true
                loadError = "Could not load restaurants. Please reopen this page to retry."
                filterRestaurants()
            }
        }
        mealsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                meals.clear()
                snapshot.children.mapNotNullTo(meals) { it.getValue(Meal::class.java) }
                filterRestaurants()
            }
            override fun onCancelled(error: DatabaseError) {
                // Restaurant name/address search remains available if menu access fails.
                meals.clear()
                filterRestaurants()
            }
        }
        database.getReference("users").addValueEventListener(usersListener!!)
        database.getReference("menuItems").addValueEventListener(mealsListener!!)
        handler.post(refresh)
    }

    override fun onStop() {
        usersListener?.let { database.getReference("users").removeEventListener(it) }
        mealsListener?.let { database.getReference("menuItems").removeEventListener(it) }
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    private fun filterRestaurants() {
        if (!::adapter.isInitialized) return
        val query = findViewById<EditText>(R.id.etRestaurantSearch).text.toString().trim()
        val matchingSellers = meals.filter {
            it.name.contains(query, true) || it.tags.any { tag -> tag.contains(query, true) }
        }.map { it.sellerId }.toSet()
        val favourites = BuyerFavourites.ids(this)
        visibleRestaurants.clear()
        visibleRestaurants.addAll(allRestaurants.filter { user ->
            (query.isBlank() || user.name.contains(query, true) ||
                user.address.contains(query, true) || user.uid in matchingSellers) &&
            (!openOnly || RestaurantAvailability.status(user) == RestaurantAvailability.Status.OPEN) &&
            (!favouritesOnly || user.uid in favourites)
        })
        adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.tvRestaurantHeading).text =
            if (query.isNotBlank() || openOnly || favouritesOnly) "Your matches" else "Discover kitchens"
        findViewById<TextView>(R.id.tvRestaurantEmpty).apply {
            visibility = if (visibleRestaurants.isEmpty() || loadError != null) View.VISIBLE else View.GONE
            text = loadError ?: if (!loaded) "Loading restaurants…" else
                if (allRestaurants.isEmpty()) "No restaurants available yet." else "No restaurants match. Try See all."
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("openOnly", openOnly)
        outState.putBoolean("favouritesOnly", favouritesOnly)
        outState.putString("query", findViewById<EditText>(R.id.etRestaurantSearch).text.toString())
        super.onSaveInstanceState(outState)
    }
}
