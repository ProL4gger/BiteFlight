package com.example.rider

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class RiderDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private val locationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvOrder: TextView
    private lateinit var btnOnline: Button
    private lateinit var btnRefresh: Button

    private var isOnline = false
    private var isBusy = false

    private var latitude: Double? = null
    private var longitude: Double? = null

    private var riderRef: DatabaseReference? = null
    private var assignmentRef: DatabaseReference? = null
    private var assignmentListener: ValueEventListener? = null

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            val granted = result.values.any { it }

            if (granted) {
                getCurrentLocation()
            } else {
                showMessage("Location permission is required")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rider_dashboard)

        val uid = auth.currentUser?.uid

        if (uid == null) {
            openLogin()
            return
        }

        tvStatus = findViewById(R.id.tvStatus)
        tvLocation = findViewById(R.id.tvLocation)
        tvOrder = findViewById(R.id.tvOrder)
        btnOnline = findViewById(R.id.btnOnline)
        btnRefresh = findViewById(R.id.btnRefreshLocation)

        riderRef = database.getReference("riderPresence")
            .child(uid)

        btnOnline.setOnClickListener {
            if (isOnline) {
                goOffline()
            } else {
                goOnline()
            }
        }

        btnRefresh.setOnClickListener {
            getCurrentLocation()
        }

        findViewById<Button>(R.id.btnLogout)
            .setOnClickListener {
                goOffline {
                    auth.signOut()
                    openLogin()
                }
            }

        observeAssignedOrders(uid)
        updateStatusUI()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getCurrentLocation(
        onSuccess: (() -> Unit)? = null
    ) {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        fetchLocation(onSuccess)
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation(
        onSuccess: (() -> Unit)?
    ) {
        val tokenSource = CancellationTokenSource()

        locationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            tokenSource.token
        ).addOnSuccessListener { location ->

            if (location == null) {
                tvLocation.text =
                    "Could not get location. Try again."
                return@addOnSuccessListener
            }

            latitude = location.latitude
            longitude = location.longitude

            tvLocation.text =
                "Latitude: ${location.latitude}\n" +
                        "Longitude: ${location.longitude}"

            if (isOnline) {
                saveRiderPresence()
            }

            onSuccess?.invoke()

        }.addOnFailureListener {
            tvLocation.text = "GPS error: ${it.message}"
        }
    }

    private fun goOnline() {
        if (isBusy) {
            showMessage("You already have an active delivery")
            return
        }

        getCurrentLocation {
            isOnline = true
            saveRiderPresence()
            updateStatusUI()
        }
    }

    private fun goOffline(
        onFinished: (() -> Unit)? = null
    ) {
        if (isBusy) {
            showMessage("Finish your delivery before going offline")
            return
        }

        val ref = riderRef

        if (ref == null) {
            onFinished?.invoke()
            return
        }

        val updates = mapOf<String, Any>(
            "online" to false,
            "updatedAt" to ServerValue.TIMESTAMP
        )

        ref.updateChildren(updates)
            .addOnSuccessListener {
                isOnline = false
                updateStatusUI()
                onFinished?.invoke()
            }
            .addOnFailureListener {
                showMessage("Could not update status")
            }
    }

    private fun saveRiderPresence() {
        val lat = latitude ?: return
        val lng = longitude ?: return
        val ref = riderRef ?: return

        val data = mapOf<String, Any>(
            "online" to isOnline,
            "latitude" to lat,
            "longitude" to lng,
            "updatedAt" to ServerValue.TIMESTAMP
        )

        ref.updateChildren(data)
            .addOnSuccessListener {
                if (isOnline) {
                    ref.onDisconnect().updateChildren(
                        mapOf("online" to false)
                    )
                }
            }
            .addOnFailureListener {
                isOnline = false
                updateStatusUI()
                showMessage("Firebase update failed")
            }
    }

    private fun observeAssignedOrders(uid: String) {
        val ref = database.getReference("delivery")
            .child("riders")
            .child(uid)
            .child("currentOrderId")

        assignmentRef = ref

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                val orderId =
                    snapshot.getValue(String::class.java)

                isBusy = !orderId.isNullOrBlank()

                tvOrder.text = if (isBusy) {
                    "Assigned Order: $orderId"
                } else {
                    "No orders assigned yet"
                }

                updateStatusUI()
            }

            override fun onCancelled(error: DatabaseError) {
                tvOrder.text =
                    "Unable to load assignments: ${error.message}"
            }
        }

        assignmentListener = listener
        ref.addValueEventListener(listener)
    }

    private fun updateStatusUI() {
        tvStatus.text = when {
            isBusy -> "BUSY"
            isOnline -> "ONLINE"
            else -> "OFFLINE"
        }

        btnOnline.text = if (isOnline) {
            "Go Offline"
        } else {
            "Go Online"
        }

        btnOnline.isEnabled = !isBusy
    }

    private fun openLogin() {
        val intent = Intent(
            this,
            RiderLoginActivity::class.java
        )

        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        assignmentListener?.let { listener ->
            assignmentRef?.removeEventListener(listener)
        }
        super.onDestroy()
    }
}
