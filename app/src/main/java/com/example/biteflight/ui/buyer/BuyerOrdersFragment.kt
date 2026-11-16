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

class BuyerOrdersFragment : Fragment() {

    private val orderList = mutableListOf<Order>()
    private lateinit var adapter: OrderAdapter
    private var stopObserving: (() -> Unit)? = null

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

    }

    override fun onStart() {
        super.onStart()
        loadOrders()
    }

    private fun loadOrders() {
        val buyer = FirebaseAuth.getInstance().currentUser ?: return
        stopObserving?.invoke()
        stopObserving = com.example.biteflight.data.repository.OrderRepository.observeBuyerOrders(buyer.uid,
            onOrders = { orders ->
                orderList.clear()
                orderList.addAll(orders)
                if (::adapter.isInitialized) adapter.notifyDataSetChanged()
            },
            onError = { message ->
                context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
            }
        )
    }

    override fun onStop() {
        stopObserving?.invoke()
        stopObserving = null
        super.onStop()
    }
}
