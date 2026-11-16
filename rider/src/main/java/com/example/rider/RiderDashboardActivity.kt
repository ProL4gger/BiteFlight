package com.example.rider

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        tvStatus = findViewById(R.id.tvStatus)
        tvLocation = findViewById(R.id.tvLocation)
        tvOrder = findViewById(R.id.tvOrder)
        btnOnline = findViewById(R.id.btnOnline)
        btnRefresh = findViewById(R.id.btnRefreshLocation)
        btnLogout = findViewById(R.id.btnLogout)
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
            .getReference("delivery/riders").child(uid).child("currentOrderId")
        assignmentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orderId = snapshot.getValue(String::class.java)
                assignmentLoaded = true
                isBusy = !orderId.isNullOrBlank()
                tvOrder.text = if (isBusy) "Assigned Order: $orderId" else "No orders assigned yet"
                updateUI()
            }
            override fun onCancelled(error: DatabaseError) {
                assignmentLoaded = false
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
    override fun onDestroy() {
        assignmentListener?.let { assignmentRef?.removeEventListener(it) }
        // The foreground service survives dashboard destruction.
        super.onDestroy()
    }
}

