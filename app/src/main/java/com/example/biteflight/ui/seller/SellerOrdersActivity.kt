package com.example.biteflight.ui.seller

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.Order
import com.example.biteflight.ui.buyer.OrderAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SellerOrdersActivity : AppCompatActivity() {

    private val orderList =
        mutableListOf<Order>()

    private lateinit var adapter:
            OrderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_orders
        )

        SellerNavigation.attach(this, R.id.sellerOrders)
        val toolbar =
            findViewById<MaterialToolbar>(
                R.id.topToolbar
            )

        setSupportActionBar(toolbar)

        supportActionBar?.title =
            "Restaurant Orders"

        supportActionBar
            ?.setDisplayHomeAsUpEnabled(true)


        val recycler =
            findViewById<RecyclerView>(
                R.id.recyclerOrders
            )

        adapter =
            OrderAdapter(orderList)

        recycler.layoutManager =
            LinearLayoutManager(this)

        recycler.adapter =
            adapter

        loadSellerOrders()
    }


    private fun loadSellerOrders() {

        val seller =
            FirebaseAuth
                .getInstance()
                .currentUser
                ?: return

        FirebaseDatabase
            .getInstance()
            .getReference("orders")
            .orderByChild("sellerId")
            .equalTo(seller.uid)
            .get()

            .addOnSuccessListener { snapshot ->

                orderList.clear()

                for (
                orderSnapshot
                in snapshot.children
                ) {

                    val order =
                        orderSnapshot.getValue(
                            Order::class.java
                        )

                    if (order != null) {
                        orderList.add(order)
                    }
                }

                orderList.sortByDescending {
                    it.createdAt
                }

                adapter.notifyDataSetChanged()
            }

            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    error.message,
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    override fun onSupportNavigateUp(): Boolean {

        finish()

        return true
    }
}
