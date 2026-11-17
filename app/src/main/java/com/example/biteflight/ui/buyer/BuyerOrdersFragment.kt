package com.example.biteflight.ui.buyer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class BuyerOrdersFragment : Fragment() {

    private val orderList = mutableListOf<Order>()
    private lateinit var adapter: OrderAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_orders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.topToolbar)?.visibility = View.GONE

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerOrders)
        recycler?.clipToPadding = false
        recycler?.setPadding(
            recycler.paddingLeft,
            recycler.paddingTop,
            recycler.paddingRight,
            (130 * resources.displayMetrics.density).toInt()
        )

        adapter = OrderAdapter(orderList, isForSeller = false)
        recycler?.layoutManager = LinearLayoutManager(requireContext())
        recycler?.adapter = adapter

        loadOrders()
    }

    override fun onResume() {
        super.onResume()
        loadOrders()
    }

    private fun loadOrders() {
        val buyer = FirebaseAuth.getInstance().currentUser ?: return
        val database = FirebaseDatabase.getInstance()
        database.getReference("buyerOrders").child(buyer.uid).get()
            .addOnSuccessListener { index ->
                val reads = index.children.mapNotNull { entry ->
                    val sellerId = entry.getValue(String::class.java) ?: return@mapNotNull null
                    val orderId = entry.key ?: return@mapNotNull null
                    database.getReference("orders").child(sellerId).child(orderId).get()
                }
                com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.database.DataSnapshot>(reads)
                    .addOnSuccessListener ordersLoaded@{ snapshots ->
                        if (view == null) return@ordersLoaded
                        orderList.clear()
                        snapshots.mapNotNullTo(orderList) { it.getValue(Order::class.java) }
                        orderList.sortByDescending { it.createdAt }
                        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
                    }
                    .addOnFailureListener { error ->
                        context?.let { Toast.makeText(it, error.message, Toast.LENGTH_LONG).show() }
                    }
            }
            .addOnFailureListener { error ->
                context?.let { Toast.makeText(it, error.message, Toast.LENGTH_LONG).show() }
            }
    }
}
