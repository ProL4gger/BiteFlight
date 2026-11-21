package com.example.biteflight

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.example.biteflight.data.model.User
import com.example.biteflight.data.repository.AuthRepository
import com.example.biteflight.ui.auth.LoginActivity
import com.example.biteflight.ui.buyer.BuyerNavigation
import com.example.biteflight.ui.buyer.BuyerPagerAdapter
import com.example.biteflight.ui.seller.SellerDashboardActivity
import com.example.biteflight.utils.BuyerOrderAlerts
import com.example.biteflight.voice.VoiceCallActivity
import androidx.lifecycle.Lifecycle
import com.example.biteflight.utils.Resource
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: BuyerPagerAdapter
    private lateinit var deliveryContainer: FrameLayout
    private val orderAlerts by lazy { BuyerOrderAlerts(this) }
    private var buyerReady = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Enable notifications in Settings for delivery alerts",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        deliveryContainer = findViewById(R.id.deliveryFragmentContainer)

        val topBarHeader = findViewById<View>(R.id.topBarHeader)

        if (topBarHeader != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topBarHeader) { view, insets ->
                val statusBar = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                )
                view.updatePadding(top = statusBar.top)
                insets
            }

            ViewCompat.requestApplyInsets(topBarHeader)
        }

        setupSettingsMenu()
        findViewById<View>(R.id.btnVoiceCall).setOnClickListener {
            startActivity(Intent(this, VoiceCallActivity::class.java))
        }
        checkUserRoleAndRoute()
    }

    private fun checkUserRoleAndRoute() {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            redirectToLogin()
            return
        }

        AuthRepository().fetchUserProfile(currentUser.uid) { result ->
            if (isFinishing || isDestroyed) {
                return@fetchUserProfile
            }

            when (result) {
                is Resource.Loading -> Unit

                is Resource.Success -> {
                    when (result.data.role.uppercase()) {
                        User.ROLE_BUYER -> setupBuyerWorkflow()

                        User.ROLE_SELLER -> {
                            startActivity(
                                Intent(this, SellerDashboardActivity::class.java)
                            )
                            finish()
                        }

                        else -> {
                            FirebaseAuth.getInstance().signOut()

                            Toast.makeText(
                                this,
                                "Please use the rider app for this account",
                                Toast.LENGTH_LONG
                            ).show()

                            redirectToLogin()
                        }
                    }
                }

                is Resource.Error -> {
                    FirebaseAuth.getInstance().signOut()

                    Toast.makeText(
                        this,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()

                    redirectToLogin()
                }
            }
        }
    }

    private fun setupBuyerWorkflow() {
        buyerReady = true
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startOrderAlerts()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        deliveryContainer.visibility = View.GONE
        viewPager.visibility = View.VISIBLE

        pagerAdapter = BuyerPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.isUserInputEnabled = true

        val targetTab = intent.getIntExtra("targetTab", 0)
            .coerceIn(0, 3)

        setViewPagerItem(targetTab)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val targetTab = intent.getIntExtra("targetTab", -1)

        if (targetTab in 0..3) {
            setViewPagerItem(targetTab)
        }
    }

    private fun startOrderAlerts() {
        if (buyerReady) FirebaseAuth.getInstance().currentUser?.uid?.let { orderAlerts.start(it) }
    }

    override fun onStart() {
        super.onStart()
        startOrderAlerts()
    }

    override fun onStop() {
        orderAlerts.stop()
        super.onStop()
    }

    fun setViewPagerItem(index: Int) {
        if (index !in 0..3) return

        if (
            !::pagerAdapter.isInitialized ||
            !::viewPager.isInitialized ||
            viewPager.visibility != View.VISIBLE
        ) {
            return
        }

        viewPager.currentItem = index

        val selectedId = when (index) {
            0 -> R.id.buyerHome
            1 -> R.id.buyerCart
            2 -> R.id.buyerOrders
            else -> R.id.buyerProfile
        }

        BuyerNavigation.attach(this, selectedId)
    }

    private fun setupSettingsMenu() {
        findViewById<View>(R.id.btnSettings)?.setOnClickListener { button ->
            PopupMenu(this, button).apply {
                menu.add("Contact Us")
                menu.add("Support")
                menu.add("Log Out")

                setOnMenuItemClickListener { item ->
                    when (item.title.toString()) {
                        "Log Out" -> {
                            FirebaseAuth.getInstance().signOut()
                            redirectToLogin()
                        }

                        else -> {
                            Toast.makeText(
                                this@MainActivity,
                                item.title,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    true
                }

                show()
            }
        }
    }

    private fun redirectToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )

        finish()
    }
}
