package com.example.biteflight.ui.seller

import android.content.Intent
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.biteflight.R
import com.example.biteflight.ui.FloatingNavigationBar
import com.example.biteflight.ui.profile.EditProfileActivity

object SellerNavigation {
    fun attach(activity: AppCompatActivity, selected: Int) {
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
                if (item.itemId == selected) return@setOnItemSelectedListener true
                val destination = when (item.itemId) {
                    R.id.sellerMenu -> SellerMenuActivity::class.java
                    R.id.sellerOrders -> SellerOrdersActivity::class.java
                    R.id.sellerAvailability -> SellerAvailabilityActivity::class.java
                    R.id.sellerProfile -> EditProfileActivity::class.java
                    else -> SellerDashboardActivity::class.java
                }
                activity.startActivity(Intent(activity, destination).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                if (activity !is SellerDashboardActivity) activity.finish()
                false
            }
        }
        val density = activity.resources.displayMetrics.density
        shell.clipChildren = false
        shell.clipToPadding = false
        shell.addView(navigation, LinearLayout.LayoutParams(-1, (80 * density).toInt()).apply {
            setMargins((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
        })
        content.addView(shell, ViewGroup.LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(shell) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(shell)
    }
}
