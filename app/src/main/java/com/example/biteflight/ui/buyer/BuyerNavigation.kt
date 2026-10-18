package com.example.biteflight.ui.buyer

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.biteflight.MainActivity
import com.example.biteflight.R
import com.example.biteflight.ui.auth.LoginActivity
import com.example.biteflight.ui.profile.EditProfileActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

object BuyerNavigation {
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
        val navigation = BottomNavigationView(activity).apply {
            setBackgroundColor(ContextCompat.getColor(activity, R.color.primary_light))
            inflateMenu(R.menu.buyer_navigation)
            labelVisibilityMode = 1
            isItemHorizontalTranslationEnabled = false
            selectedItemId = selected
        }
        fun navigate(id: Int) {
            if (id == R.id.buyerSettings) {
                showSettings(activity, navigation.findViewById<View>(id) ?: navigation)
                return
            }
            val destination = when (id) {
                R.id.buyerCart -> CartActivity::class.java
                R.id.buyerOrders -> BuyerOrdersActivity::class.java
                R.id.buyerProfile -> EditProfileActivity::class.java
                else -> MainActivity::class.java
            }
            if (activity.javaClass == destination) return
            activity.startActivity(Intent(activity, destination).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            if (activity !is MainActivity) activity.finish()
        }
        navigation.setOnItemSelectedListener { item ->
            navigate(item.itemId)
            false
        }
        // Home also returns from a restaurant menu; Cart returns from checkout.
        navigation.setOnItemReselectedListener { navigate(it.itemId) }
        shell.addView(navigation, LinearLayout.LayoutParams(-1, -2))
        content.addView(shell, ViewGroup.LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(shell) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            navigation.visibility = if (insets.isVisible(WindowInsetsCompat.Type.ime())) View.GONE else View.VISIBLE
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right,
                bottom = maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(shell)
    }

    private fun showSettings(activity: AppCompatActivity, anchor: View) {
        PopupMenu(activity, anchor).apply {
            menu.add("Contact Us")
            menu.add("Support")
            menu.add("Log Out")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Log Out" -> {
                        FirebaseAuth.getInstance().signOut()
                        activity.startActivity(Intent(activity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        activity.finish()
                    }
                    else -> Toast.makeText(activity, item.title, Toast.LENGTH_SHORT).show()
                }
                true
            }
            show()
        }
    }
}
