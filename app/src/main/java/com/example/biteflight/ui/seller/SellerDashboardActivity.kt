package com.example.biteflight.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.example.biteflight.R
import com.example.biteflight.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class SellerDashboardActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: SellerPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seller_dashboard)

        val topBarHeader = findViewById<View>(R.id.topBarHeader)
        if (topBarHeader != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topBarHeader) { v, insets ->
                val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                v.updatePadding(top = statusBar.top)
                insets
            }
            ViewCompat.requestApplyInsets(topBarHeader)
        }

        val btnEdit = findViewById<ImageButton>(R.id.btnEdit)
        btnEdit?.setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            popupMenu.menu.add("About Us")
            popupMenu.menu.add("Support")
            popupMenu.menu.add("Log Out")

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "About Us" -> Toast.makeText(this, "About Us", Toast.LENGTH_SHORT).show()
                    "Support" -> Toast.makeText(this, "Support", Toast.LENGTH_SHORT).show()
                    "Log Out" -> {
                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                }
                true
            }
            popupMenu.show()
        }

        viewPager = findViewById(R.id.viewPager)
        pagerAdapter = SellerPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        val targetTab = intent.getIntExtra("targetTab", 2) // Home is at index 2 [Menu, Orders, Home, Availability, Profile]
        viewPager.currentItem = targetTab
        viewPager.isUserInputEnabled = true

        val selectedId = when (targetTab) {
            0 -> R.id.sellerMenu
            1 -> R.id.sellerOrders
            2 -> R.id.sellerHome
            3 -> R.id.sellerAvailability
            4 -> R.id.sellerProfile
            else -> R.id.sellerHome
        }

        SellerNavigation.attach(this, selectedId)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetTab = intent?.getIntExtra("targetTab", 2) ?: 2
        setViewPagerItem(targetTab)
    }

    fun setViewPagerItem(index: Int) {
        if (::viewPager.isInitialized) {
            viewPager.currentItem = index
        }
    }
}
