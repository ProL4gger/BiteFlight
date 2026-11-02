package com.example.biteflight.ui.seller

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SellerPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SellerMenuFragment()
            1 -> SellerOrdersFragment()
            2 -> SellerDashboardFragment()
            3 -> SellerAvailabilityFragment()
            4 -> SellerProfileFragment()
            else -> SellerDashboardFragment()
        }
    }
}
