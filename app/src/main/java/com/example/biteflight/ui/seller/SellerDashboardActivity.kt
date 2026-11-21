package com.example.biteflight.ui.seller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.example.biteflight.R
import com.example.biteflight.ui.auth.LoginActivity
import com.example.biteflight.utils.SellerOrderAlerts
import com.example.biteflight.voice.VoiceCallActivity
import com.google.firebase.auth.FirebaseAuth

class SellerDashboardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTIFICATION_SELLER_ID = "notificationSellerId"
        private const val ASKED_FOR_NOTIFICATIONS = "askedForSellerNotifications"
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: SellerPagerAdapter
    private val orderAlerts by lazy { SellerOrderAlerts(applicationContext) }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.seller_order_notification_permission, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!canOpenNotification(intent)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
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

        findViewById<View>(R.id.btnVoiceCall).setOnClickListener {
            startActivity(Intent(this, VoiceCallActivity::class.java))
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
        requestOrderNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        if (!isFinishing) {
            FirebaseAuth.getInstance().currentUser?.uid?.let(orderAlerts::start)
        }
    }

    override fun onStop() {
        orderAlerts.stop()
        super.onStop()
    }

    private fun requestOrderNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return

        val preferences = getPreferences(MODE_PRIVATE)
        if (preferences.getBoolean(ASKED_FOR_NOTIFICATIONS, false)) return
        preferences.edit().putBoolean(ASKED_FOR_NOTIFICATIONS, true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun canOpenNotification(intent: Intent): Boolean {
        val sellerId = intent.getStringExtra(EXTRA_NOTIFICATION_SELLER_ID) ?: return true
        return FirebaseAuth.getInstance().currentUser?.uid == sellerId
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!canOpenNotification(intent)) return
        setIntent(intent)
        val targetTab = intent.getIntExtra("targetTab", 2)
        setViewPagerItem(targetTab)
    }

    fun setViewPagerItem(index: Int) {
        if (::viewPager.isInitialized) {
            viewPager.currentItem = index
        }
    }
}
