package com.example.biteflight

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.example.biteflight.ui.auth.LoginActivity
import com.example.biteflight.ui.buyer.BuyerNavigation
import com.example.biteflight.ui.buyer.BuyerPagerAdapter
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: BuyerPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val topBarHeader = findViewById<View>(R.id.topBarHeader)
        if (topBarHeader != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topBarHeader) { v, insets ->
                val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                v.updatePadding(top = statusBar.top)
                insets
            }
            ViewCompat.requestApplyInsets(topBarHeader)
        }

        findViewById<View>(R.id.btnSettings)?.setOnClickListener { btn ->
            PopupMenu(this, btn).apply {
                menu.add("Contact Us")
                menu.add("Support")
                menu.add("Log Out")
                setOnMenuItemClickListener { item ->
                    when (item.title.toString()) {
                        "Log Out" -> {
                            FirebaseAuth.getInstance().signOut()
                            startActivity(Intent(this@MainActivity, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        }
                        else -> Toast.makeText(this@MainActivity, item.title, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                show()
            }
        }

        viewPager = findViewById(R.id.viewPager)
        pagerAdapter = BuyerPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        val targetTab = intent.getIntExtra("targetTab", 0) // Default to Home (index 0)
        viewPager.currentItem = targetTab
        viewPager.isUserInputEnabled = true

        val selectedId = when (targetTab) {
            0 -> R.id.buyerHome
            1 -> R.id.buyerCart
            2 -> R.id.buyerOrders
            3 -> R.id.buyerProfile
            else -> R.id.buyerHome
        }

        BuyerNavigation.attach(this, selectedId)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetTab = intent?.getIntExtra("targetTab", 0) ?: 0
        setViewPagerItem(targetTab)
    }

    fun setViewPagerItem(index: Int) {
        if (::viewPager.isInitialized) {
            viewPager.currentItem = index
        }
    }
}
