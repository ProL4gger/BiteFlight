package com.example.biteflight

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.biteflight.utils.BuyerPushRegistration
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
import com.google.firebase.auth.FirebaseAuth
import com.example.biteflight.data.repository.AuthRepository
import com.example.biteflight.utils.Resource
import com.example.biteflight.ui.seller.SellerDashboardActivity

class MainActivity : AppCompatActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this,
            "Enable notifications in Settings for delivery alerts", Toast.LENGTH_LONG).show()
    }

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

        AuthRepository().fetchUserProfile(currentUser.uid) { result ->
            when (result) {
                is Resource.Loading -> Unit
                is Resource.Success -> when (result.data.role.uppercase()) {
                    User.ROLE_BUYER -> setupBuyerWorkflow()
                    User.ROLE_SELLER -> {
                        startActivity(Intent(this, SellerDashboardActivity::class.java))
                        finish()
                    }
                    else -> {
                        FirebaseAuth.getInstance().signOut()
                        Toast.makeText(this, "Please use the rider app for this account", Toast.LENGTH_LONG).show()
                        redirectToLogin()
                    }
                }
                is Resource.Error -> {
                    FirebaseAuth.getInstance().signOut()
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    redirectToLogin()
                }
            }
        }
    }

    private fun setupBuyerWorkflow() {
        BuyerPushRegistration.register()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::pagerAdapter.isInitialized && intent.getIntExtra("targetTab", -1) == 2) {
            viewPager.currentItem = 2
            BuyerNavigation.attach(this, R.id.buyerOrders)
        }
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
