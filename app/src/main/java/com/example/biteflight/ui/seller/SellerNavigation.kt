package com.example.biteflight.ui.seller

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.example.biteflight.R
import com.example.biteflight.ui.FloatingNavigationBar

object SellerNavigation {
    fun attach(activity: AppCompatActivity, selected: Int) {
        val viewPager = activity.findViewById<ViewPager2>(R.id.viewPager)
        if (viewPager != null && activity is SellerDashboardActivity) {
            val navigation = FloatingNavigationBar(activity).apply {
                inflateMenu(R.menu.seller_navigation)
                selectedItemId = selected
            }

            val initialIndex = when (selected) {
                R.id.sellerMenu -> 0
                R.id.sellerOrders -> 1
                R.id.sellerHome -> 2
                R.id.sellerAvailability -> 3
                R.id.sellerProfile -> 4
                else -> 2
            }
            if (viewPager.currentItem != initialIndex) {
                viewPager.currentItem = initialIndex
            }
            navigation.selectedItemId = selected

            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    val itemId = when (position) {
                        0 -> R.id.sellerMenu
                        1 -> R.id.sellerOrders
                        2 -> R.id.sellerHome
                        3 -> R.id.sellerAvailability
                        4 -> R.id.sellerProfile
                        else -> R.id.sellerHome
                    }
                    if (navigation.selectedItemId != itemId) {
                        navigation.selectedItemId = itemId
                    }
                }
            })

            navigation.setOnItemSelectedListener { item ->
                val targetIndex = when (item.itemId) {
                    R.id.sellerMenu -> 0
                    R.id.sellerOrders -> 1
                    R.id.sellerHome -> 2
                    R.id.sellerAvailability -> 3
                    R.id.sellerProfile -> 4
                    else -> 2
                }
                if (viewPager.currentItem != targetIndex) {
                    viewPager.setCurrentItem(targetIndex, true)
                }
                true
            }
            navigation.setOnItemReselectedListener { item ->
                val targetIndex = when (item.itemId) {
                    R.id.sellerMenu -> 0
                    R.id.sellerOrders -> 1
                    R.id.sellerHome -> 2
                    R.id.sellerAvailability -> 3
                    R.id.sellerProfile -> 4
                    else -> 2
                }
                viewPager.setCurrentItem(targetIndex, true)
            }

            val density = activity.resources.displayMetrics.density
            val params = FrameLayout.LayoutParams(-1, (80 * density).toInt()).apply {
                gravity = Gravity.BOTTOM
                setMargins((12 * density).toInt(), 0, (12 * density).toInt(), (6 * density).toInt())
            }

            val rootView = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? ViewGroup
            if (rootView is FrameLayout) {
                rootView.addView(navigation, params)
            } else {
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                content.addView(navigation, params)
            }

            ViewCompat.setOnApplyWindowInsetsListener(navigation) { view, insets ->
                val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val p = view.layoutParams as? FrameLayout.LayoutParams
                if (p != null) {
                    p.setMargins((12 * density).toInt(), 0, (12 * density).toInt(), (6 * density).toInt() + navBars.bottom)
                    view.layoutParams = p
                }
                insets
            }
            ViewCompat.requestApplyInsets(navigation)
        } else {
            // Sub-flow fallback for seller activities (AddItemActivity, EditItemActivity)
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val page = content.getChildAt(0)
            content.removeView(page)
            page.fitsSystemWindows = false
            val shell = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(ContextCompat.getColor(activity, R.color.background))
            }
            shell.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))

            val navigation = FloatingNavigationBar(activity).apply {
                inflateMenu(R.menu.seller_navigation)
                selectedItemId = selected
                setOnItemSelectedListener { item ->
                    val targetTab = when (item.itemId) {
                        R.id.sellerMenu -> 0
                        R.id.sellerOrders -> 1
                        R.id.sellerHome -> 2
                        R.id.sellerAvailability -> 3
                        R.id.sellerProfile -> 4
                        else -> 2
                    }
                    val intent = Intent(activity, SellerDashboardActivity::class.java).apply {
                        putExtra("targetTab", targetTab)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    activity.startActivity(intent)
                    activity.overridePendingTransition(0, 0)
                    activity.finish()
                    true
                }
            }

            val density = activity.resources.displayMetrics.density
            shell.clipChildren = false
            shell.clipToPadding = false
            shell.addView(navigation, LinearLayout.LayoutParams(-1, (80 * density).toInt()).apply {
                setMargins((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            })
            content.addView(shell, ViewGroup.LayoutParams(-1, -1))
            ViewCompat.setOnApplyWindowInsetsListener(shell) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
                navigation.visibility = if (insets.isVisible(WindowInsetsCompat.Type.ime())) View.GONE else View.VISIBLE
                view.updatePadding(left = bars.left, top = bars.top, right = bars.right,
                    bottom = maxOf(bars.bottom, keyboard.bottom))
                WindowInsetsCompat.CONSUMED
            }
            ViewCompat.requestApplyInsets(activity.findViewById<ViewGroup>(android.R.id.content))
        }
    }
}
