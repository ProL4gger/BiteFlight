package com.example.biteflight

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.example.biteflight.data.model.User
import com.example.biteflight.ui.auth.LoginActivity
import com.example.biteflight.ui.buyer.BuyerNavigation
import com.example.biteflight.ui.buyer.BuyerPagerAdapter
import com.example.biteflight.ui.delivery.DeliveryHomeFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: BuyerPagerAdapter
    private lateinit var deliveryContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        deliveryContainer = findViewById(R.id.deliveryFragmentContainer)

        val topBarHeader = findViewById<View>(R.id.topBarHeader)
        if (topBarHeader != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topBarHeader) { v, insets ->
                val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                v.updatePadding(top = statusBar.top)
                insets
            }
            ViewCompat.requestApplyInsets(topBarHeader)
        }

        setupSettingsMenu()
        checkUserRoleAndRoute()
    }

    private fun checkUserRoleAndRoute() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            redirectToLogin()
            return
        }

        FirebaseDatabase.getInstance().getReference("users")
            .child(currentUser.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.getValue(User::class.java)
                val role = user?.role ?: User.ROLE_BUYER

                when (role) {
                    User.ROLE_DELIVERY -> {
                        viewPager.visibility = View.GONE
                        deliveryContainer.visibility = View.VISIBLE

                        supportFragmentManager.beginTransaction()
                            .replace(R.id.deliveryFragmentContainer, DeliveryHomeFragment())
                            .commit()
                    }
                    User.ROLE_SELLER -> {
                        setupBuyerWorkflow()
                    }
                    else -> {
                        setupBuyerWorkflow()
                    }
                }
            }
            .addOnFailureListener {
                setupBuyerWorkflow()
            }
    }

    private fun setupBuyerWorkflow() {
        deliveryContainer.visibility = View.GONE
        viewPager.visibility = View.VISIBLE

        pagerAdapter = BuyerPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        val targetTab = intent.getIntExtra("targetTab", 0)
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

    private fun setupSettingsMenu() {
        findViewById<View>(R.id.btnSettings)?.setOnClickListener { btn ->
            PopupMenu(this, btn).apply {
                menu.add("Contact Us")
                menu.add("Support")
                menu.add("Log Out")
                setOnMenuItemClickListener { item ->
                    when (item.title.toString()) {
                        "Log Out" -> {
                            FirebaseAuth.getInstance().signOut()
                            redirectToLogin()
                        }
                        else -> Toast.makeText(this@MainActivity, item.title, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                show()
            }
        }
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetTab = intent.getIntExtra("targetTab", 0)
        setViewPagerItem(targetTab)
    }

    fun setViewPagerItem(index: Int) {
        if (::viewPager.isInitialized && viewPager.visibility == View.VISIBLE) {
            viewPager.currentItem = index
        }
    }
}