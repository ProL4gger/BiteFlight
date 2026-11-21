package com.example.rider

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.biteflight.voice.VoiceCallActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class RiderDashboardActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private lateinit var tvStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvOrder: TextView
    private lateinit var btnOnline: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnLogout: Button
    private var assignmentRef: DatabaseReference? = null
    private var assignmentListener: ValueEventListener? = null
    private var assignmentLoaded = false
    private var isBusy = false
    private var startRequested = false
    private var logoutRequested = false
    private var lastError: String? = null
    private var state = RiderTrackingService.TrackingState()
    private lateinit var btnDeliveryStage: Button
    private var jobRef: DatabaseReference? = null
    private var jobListener: ValueEventListener? = null
    private var currentOrderId: String? = null
    private var currentSellerId: String? = null
    private var deliveries: RiderDeliveryRepository? = null
    private var nextStage: String? = null
    private var updatingStage = false
    private val stages = listOf("ASSIGNED", "WAITING_AT_RESTAURANT", "PICKED_UP", "IN_AREA", "DELIVERED")

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) requestNotificationAndStart()
        else {
            startRequested = false
            showMessage("Location permission is required to go online")
            updateUI()
        }
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showMessage("Enable notifications in Settings to see your tracking notification")
        startTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uid = auth.currentUser?.uid
        if (uid == null) {
            openLogin()
            return
        }
        setContentView(R.layout.activity_rider_dashboard)
        findViewById<Button>(R.id.btnVoiceCall).setOnClickListener {
            startActivity(Intent(this, VoiceCallActivity::class.java))
        }
        tvStatus = findViewById(R.id.tvStatus)
        tvLocation = findViewById(R.id.tvLocation)
        tvOrder = findViewById(R.id.tvOrder)
        btnOnline = findViewById(R.id.btnOnline)
        btnRefresh = findViewById(R.id.btnRefreshLocation)
        btnLogout = findViewById(R.id.btnLogout)
        btnDeliveryStage = findViewById(R.id.btnDeliveryStage)
        deliveries = RiderDeliveryRepository(uid) { message ->
            if (!isFinishing && !isDestroyed) showMessage(message)
        }
        btnDeliveryStage.setOnClickListener {
            val orderId = currentOrderId ?: return@setOnClickListener
            val target = nextStage ?: return@setOnClickListener
            if (updatingStage) return@setOnClickListener
            if (target == "DELIVERED") {
                AlertDialog.Builder(this).setTitle("Confirm delivery")
                    .setMessage("Have you handed the order to the customer?")
                    .setPositiveButton("Delivered") { _, _ -> advanceDelivery(orderId, target) }
                    .setNegativeButton("Cancel", null).show()
            } else advanceDelivery(orderId, target)
        }
        btnOnline.setOnClickListener {
            if (state.running) sendCommand(RiderTrackingService.ACTION_STOP) else requestOnline()
        }
        btnRefresh.setOnClickListener { sendCommand(RiderTrackingService.ACTION_REFRESH) }
        btnLogout.setOnClickListener {
            if (state.running) {
                logoutRequested = true
                sendCommand(RiderTrackingService.ACTION_STOP)
            } else finishLogout()
        }
        assignmentRef = FirebaseDatabase.getInstance()
            .getReference("delivery/riders").child(uid)
        assignmentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orderId = snapshot.child("currentOrderId").getValue(String::class.java)
                currentSellerId = snapshot.child("sellerId").getValue(String::class.java)
                assignmentLoaded = true
                isBusy = !orderId.isNullOrBlank()
                tvOrder.text = if (isBusy) "Assigned Order: $orderId" else "No orders assigned yet"
                observeDelivery(orderId?.takeIf { it.isNotBlank() })
                updateUI()
            }
            override fun onCancelled(error: DatabaseError) {
                assignmentLoaded = false
                observeDelivery(null)
                tvOrder.text = "Unable to load assignments: ${error.message}"
                updateUI()
            }
        }
        assignmentRef?.addValueEventListener(assignmentListener!!)
        RiderTrackingService.state.observe(this) { next ->
            if (next.uid != null && next.uid != auth.currentUser?.uid) return@observe
            state = next
            if (next.running || next.error != null) startRequested = false
            if (next.error != null && next.error != lastError) {
                logoutRequested = false
                showMessage(next.error)
            }
            lastError = next.error
            if (logoutRequested && !next.running) finishLogout() else updateUI()
        }
        updateUI()
    }

    private fun requestOnline() {
        if (!assignmentLoaded || startRequested) return
        startRequested = true
        updateUI()
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else requestNotificationAndStart()
    }

    private fun observeDelivery(orderId: String?) {
        jobListener?.let { jobRef?.removeEventListener(it) }
        currentOrderId = orderId
        nextStage = null
        btnDeliveryStage.visibility = if (orderId == null) View.GONE else View.VISIBLE
        btnDeliveryStage.isEnabled = false
        if (orderId == null) return
        val seller = currentSellerId
        if (seller.isNullOrBlank()) {
            tvOrder.text = "Assignment needs a restaurant ID. Ask the project owner to review it."
            return
        }
        tvOrder.text = "Loading delivery $orderId…"
        jobRef = FirebaseDatabase.getInstance().getReference("orders").child(seller).child(orderId)
        jobListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (currentOrderId != orderId) return
                if (!snapshot.exists() || snapshot.child("riderId").value != auth.currentUser?.uid) {
                    tvOrder.text = "Delivery details unavailable"
                    nextStage = null
                } else {
                    fun field(name: String) = snapshot.child(name).getValue(String::class.java).orEmpty()
                    val status = field("status")
                    if (status == "DELIVERED") deliveries?.refresh()
                    val index = stages.indexOf(status)
                    nextStage = stages.getOrNull(index + 1).takeIf { index >= 0 }
                    val items = snapshot.child("items").children.joinToString("\n") {
                        "${it.child("name").value ?: "Item"} × ${it.child("quantity").value ?: ""}"
                    }
                    tvOrder.text = "Order #${orderId.takeLast(6).uppercase()}\n" +
                        "Status: ${status.replace('_', ' ')}\n\n" +
                        "Pickup: ${field("restaurantName")}\n\n" +
                        "Deliver to: ${field("deliveryAddress")}\n\n$items\n\n" +
                        "Total: EGP ${snapshot.child("total").value ?: "—"}"
                }
                updateDeliveryButton()
            }
            override fun onCancelled(error: DatabaseError) {
                if (currentOrderId != orderId) return
                nextStage = null
                tvOrder.text = "Unable to load delivery: ${error.message}"
                updateDeliveryButton()
            }
        }
        jobRef?.addValueEventListener(jobListener!!)
    }

    private fun updateDeliveryButton() {
        btnDeliveryStage.text = if (updatingStage) "Saving…" else when (nextStage) {
            "WAITING_AT_RESTAURANT" -> "Waiting at Restaurant"
            "PICKED_UP" -> "Confirm Pickup"
            "IN_AREA" -> "Arrived in Customer Area"
            "DELIVERED" -> "Confirm Delivered"
            else -> "No next stage available"
        }
        btnDeliveryStage.isEnabled = nextStage != null && !updatingStage &&
            state.running && state.online && state.connected && !state.stopping
    }

    private fun advanceDelivery(orderId: String, target: String) {
        val repository = deliveries ?: return
        updatingStage = true
        updateDeliveryButton()
        val seller = currentSellerId ?: run {
            updatingStage = false
            updateDeliveryButton()
            return
        }
        repository.advance(seller, orderId, target)
            .addOnCompleteListener(this) { task ->
                updatingStage = false
                if (!task.isSuccessful) showMessage(task.exception?.localizedMessage ?: "Could not save stage")
                updateDeliveryButton()
            }
    }

    private fun requestNotificationAndStart() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startTracking()
    }

    private fun startTracking() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, RiderTrackingService::class.java)
                .setAction(RiderTrackingService.ACTION_START))
        } catch (error: RuntimeException) {
            startRequested = false
            showMessage("Could not start tracking: ${error.localizedMessage}")
            updateUI()
        }
    }

    private fun sendCommand(action: String) {
        if (!state.running) return
        try {
            startService(Intent(this, RiderTrackingService::class.java).setAction(action))
        } catch (error: RuntimeException) {
            logoutRequested = false
            showMessage("Could not update tracking: ${error.localizedMessage}")
        }
    }

    private fun updateUI() {
        if (!::tvStatus.isInitialized) return
        tvStatus.text = when {
            startRequested -> "STARTING TRACKING"
            state.running && state.stopping -> "GOING OFFLINE"
            state.running && !state.connected -> "NO CONNECTION"
            state.running && state.online && isBusy -> "BUSY"
            state.running -> state.status
            else -> "OFFLINE"
        }
        if (state.latitude != null && state.longitude != null) {
            tvLocation.text = "Latitude: ${state.latitude}\nLongitude: ${state.longitude}"
        }
        btnOnline.text = if (state.running) "Go Offline" else "Go Online"
        val ready = assignmentLoaded && !startRequested && !state.stopping
        // Busy riders may restart tracking after an OS stop, but cannot end a shift.
        btnOnline.isEnabled = ready && (!state.running || !isBusy) && (!state.running || state.connected)
        btnLogout.isEnabled = ready && !isBusy && (!state.running || state.connected)
        btnRefresh.isEnabled = state.running && !state.stopping
        deliveries?.setAvailable(state.running && state.online && state.connected && !state.stopping,
            state.latitude, state.longitude)
        updateDeliveryButton()
    }

    private fun finishLogout() {
        logoutRequested = false
        auth.signOut()
        openLogin()
    }
    private fun openLogin() {
        startActivity(Intent(this, RiderLoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    override fun onStart() {
        super.onStart()
        deliveries?.start()
        if (::tvStatus.isInitialized) updateUI()
    }
    override fun onStop() {
        deliveries?.stop()
        super.onStop()
    }
    override fun onDestroy() {
        assignmentListener?.let { assignmentRef?.removeEventListener(it) }
        jobListener?.let { jobRef?.removeEventListener(it) }
        // The foreground service survives dashboard destruction.
        super.onDestroy()
    }
}

