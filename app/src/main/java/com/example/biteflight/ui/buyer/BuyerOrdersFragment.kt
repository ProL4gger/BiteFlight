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
        adapter = OrderAdapter(orderList)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        loadOrders()
    }

    override fun onResume() {
        super.onResume()
        loadOrders()
    }

    private fun loadOrders() {
        val buyer = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseDatabase.getInstance().getReference("orders")
            .orderByChild("buyerId")
            .equalTo(buyer.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                orderList.clear()
                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java)
                    if (order != null) {
                        orderList.add(order)
                    }
                }
                orderList.sortByDescending { it.createdAt }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { error ->
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show()
            }
    }
}
