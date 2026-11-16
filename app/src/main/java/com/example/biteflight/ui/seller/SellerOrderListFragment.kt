package com.example.biteflight.ui.seller

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
import com.example.biteflight.ui.buyer.OrderAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SellerOrderListFragment : Fragment() {

    private val orderList = mutableListOf<Order>()
    private lateinit var adapter: OrderAdapter
    private var ordersRef: DatabaseReference? = null
    private var ordersListener: ValueEventListener? = null

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

        adapter = OrderAdapter(orderList, isForSeller = true) { order ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.ordersFragmentContainer, SellerOrderDetailFragment(order))
                .addToBackStack(null)
                .commit()
        }

        recycler?.layoutManager = LinearLayoutManager(requireContext())
        recycler?.adapter = adapter

    }

    override fun onStart() {
        super.onStart()
        loadSellerOrders()
    }

    private fun loadSellerOrders() {
        val seller = FirebaseAuth.getInstance().currentUser ?: return
        ordersRef = FirebaseDatabase.getInstance().getReference("orders").child(seller.uid)
        ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                orderList.clear()
                for (orderSnapshot in snapshot.children) {
                    val order = orderSnapshot.getValue(Order::class.java)
                    if (order != null) {
                        orderList.add(order)
                    }
                }
                orderList.sortByDescending { it.createdAt }
                if (::adapter.isInitialized) {
                    adapter.notifyDataSetChanged()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                orderList.clear()
                adapter.notifyDataSetChanged()
                context?.let { Toast.makeText(it, error.message, Toast.LENGTH_LONG).show() }
            }
        }
        ordersRef?.addValueEventListener(ordersListener!!)
    }

    override fun onStop() {
        ordersListener?.let { ordersRef?.removeEventListener(it) }
        ordersListener = null
        super.onStop()
    }
}
