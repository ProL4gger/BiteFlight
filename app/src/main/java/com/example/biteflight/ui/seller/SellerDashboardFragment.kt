package com.example.biteflight.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.biteflight.R

class SellerDashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_seller_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnAddItem = view.findViewById<View>(R.id.btnAddItem)
        val btnMenu = view.findViewById<View>(R.id.btnMenu)
        val btnOrders = view.findViewById<View>(R.id.btnOrders)

        btnAddItem?.setOnClickListener {
            startActivity(Intent(requireContext(), AddItemActivity::class.java))
        }

        btnMenu?.setOnClickListener {
            val act = activity
            if (act is SellerDashboardActivity) {
                act.setViewPagerItem(0) // Switch to Menu tab (index 0)
            }
        }

        btnOrders?.setOnClickListener {
            val act = activity
            if (act is SellerDashboardActivity) {
                act.setViewPagerItem(1) // Switch to Orders tab (index 1)
            }
        }
    }
}
