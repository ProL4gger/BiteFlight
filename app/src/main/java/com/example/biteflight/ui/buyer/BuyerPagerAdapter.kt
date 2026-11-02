package com.example.biteflight.ui.buyer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class BuyerPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> BuyerHomeFragment()
            1 -> BuyerCartFragment()
            2 -> BuyerOrdersFragment()
            3 -> BuyerProfileFragment()
            else -> BuyerHomeFragment()
        }
    }
}
