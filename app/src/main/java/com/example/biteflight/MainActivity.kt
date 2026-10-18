package com.example.biteflight
import android.widget.ImageButton

import android.content.Intent
import android.os.Bundle

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.data.model.User

import com.example.biteflight.ui.buyer.RestaurantAdapter
import com.example.biteflight.ui.buyer.RestaurantMenuActivity


import com.google.firebase.database.FirebaseDatabase

import android.os.Handler
import android.os.Looper
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {


    private val restaurantList =
        mutableListOf<User>()

    private lateinit var adapter: RestaurantAdapter
    private val restaurantsReference = FirebaseDatabase.getInstance().getReference("users")
    private var restaurantsListener: ValueEventListener? = null
    private val statusHandler = Handler(Looper.getMainLooper())
    private val refreshStatus = object : Runnable {
        override fun run() {
            adapter.notifyDataSetChanged()
            statusHandler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        com.example.biteflight.ui.buyer.BuyerNavigation.attach(this, R.id.buyerHome)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // -------------------------
        // RESTAURANT LIST
        // -------------------------

        val recyclerRestaurants =
            findViewById<RecyclerView>(
                R.id.recyclerRestaurants
            )

        adapter =
            RestaurantAdapter(
                restaurantList
            ) { restaurant ->

                val intent =
                    Intent(
                        this,
                        RestaurantMenuActivity::class.java
                    )

                intent.putExtra(
                    "sellerId",
                    restaurant.uid
                )

                intent.putExtra(
                    "restaurantName",
                    restaurant.name
                )

                startActivity(intent)
            }

        recyclerRestaurants.layoutManager =
            LinearLayoutManager(this)

        recyclerRestaurants.adapter =
            adapter

    }

    override fun onStart() {
        super.onStart()
        loadRestaurants()
        statusHandler.post(refreshStatus)
    }

    override fun onStop() {
        restaurantsListener?.let { restaurantsReference.removeEventListener(it) }
        restaurantsListener = null
        statusHandler.removeCallbacks(refreshStatus)
        super.onStop()
    }


    // -------------------------
    // LOAD SELLERS
    // -------------------------

    private fun loadRestaurants() {

        val database =
            FirebaseDatabase
                .getInstance()
                .getReference("users")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                restaurantList.clear()

                for (userSnapshot in snapshot.children) {

                    val user =
                        userSnapshot.getValue(
                            User::class.java
                        )

                    if (
                        user != null &&
                        user.role.uppercase() == "SELLER"
                    ) {

                        val restaurantWithId =
                            user.copy(
                                uid =
                                    userSnapshot.key ?: ""
                            )

                        restaurantList.add(
                            restaurantWithId
                        )
                    }
                }

                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {

                Toast.makeText(
                    this@MainActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        restaurantsListener = listener
        database.addValueEventListener(listener)
    }
}
