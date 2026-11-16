package com.example.rider

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/** Owns the online shift; only this service writes rider presence. */
class RiderTrackingService : Service() {
    data class TrackingState(
        val uid: String? = null,
        val running: Boolean = false,
        val connected: Boolean = false,
        val online: Boolean = false,
        val stopping: Boolean = false,
        val status: String = "OFFLINE",
        val latitude: Double? = null,
        val longitude: Double? = null,
        val error: String? = null
    )

    companion object {
        const val ACTION_START = "com.example.rider.START_TRACKING"
        const val ACTION_STOP = "com.example.rider.STOP_TRACKING"
        const val ACTION_REFRESH = "com.example.rider.REFRESH_TRACKING"
        private const val CHANNEL_ID = "rider_tracking"
        private const val NOTIFICATION_ID = 1001
        private val mutableState = MutableLiveData(TrackingState())
        val state: LiveData<TrackingState> = mutableState
    }

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()
    private var uid: String? = null
    private var presence: DatabaseReference? = null
    private var connected = false
    private var approved = false
    private var initialized = false
    private var destroyed = false
    private var failed = false
    private var stopping = false
    private var endingShift = false
    private var stopChecking = false
    private var writing = false
    private var generation = 0
    private var presenceMayExist = false
    private var lastFix: Location? = null
    private var currentLocationToken: CancellationTokenSource? = null
    private var current = TrackingState()

    private val authListener = FirebaseAuth.AuthStateListener {
        if (initialized && it.currentUser?.uid != uid) fail("Your session ended. Sign in again to go online.")
    }
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { acceptLocation(it) }
        }
    }
    private val freshnessWatch = object : Runnable {
        override fun run() {
            if (destroyed || stopping) return
            if (!hasLocationPermission()) {
                fail("Location permission was removed. Enable it to go online again.")
                return
            }
            if (lastFix?.let { isFresh(it) } != true) {
                if (current.online && connected && !writing) pauseForGps()
                requestFreshLocation()
            }
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!initialized) beginShift()
            ACTION_STOP -> if (initialized) requestStop()
            ACTION_REFRESH -> if (initialized && !stopping && !failed) requestFreshLocation()
            else -> if (!initialized) stopSelf()
        }
        // A killed app must be reopened by the rider, never silently restarted.
        return START_NOT_STICKY
    }

    private fun beginShift() {
        uid = auth.currentUser?.uid
        if (uid == null || !hasLocationPermission()) {
            fail("Sign in and allow location access before going online")
            return
        }
        current = TrackingState(uid = uid, running = true, status = "VERIFYING RIDER")
        createChannel()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
        } catch (error: RuntimeException) {
            fail("Could not start background location: ${error.localizedMessage}")
            return
        }
        initialized = true
        publish()
        presence = database.getReference("riderPresence").child(uid!!)
        auth.addAuthStateListener(authListener)
        listen(database.getReference("users/riders").child(uid!!).child("role")) { snapshot ->
            approved = snapshot.getValue(String::class.java)?.trim()?.lowercase() == "rider"
            if (!approved) fail("Your rider account must be approved before going online")
            else if (!stopping && !endingShift) {
                current = current.copy(status = "GETTING LOCATION", error = null)
                publish()
                startLocations()
            }
        }
        listen(database.getReference(".info/connected")) { snapshot ->
            connected = snapshot.getValue(Boolean::class.java) == true
            generation++
            writing = false
            current = current.copy(connected = connected, online = false,
                status = if (connected) "GETTING LOCATION" else "NO CONNECTION")
            publish()
            if (connected && approved && !stopping && !endingShift) requestFreshLocation()
        }
        handler.postDelayed(freshnessWatch, 15_000L)
    }

    private fun listen(ref: DatabaseReference, onData: (DataSnapshot) -> Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!destroyed && !failed) onData(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {
                if (!destroyed) fail("Could not verify rider tracking: ${error.message}")
            }
        }
        listeners += ref to listener
        ref.addValueEventListener(listener)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startLocations() {
        if (!hasLocationPermission()) {
            fail("Location permission was removed. Enable it before going online again.")
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .setMaxUpdateAgeMillis(0L)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            locationClient.removeLocationUpdates(callback)
            locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { if (!destroyed) fail("GPS updates failed: ${it.localizedMessage}") }
            requestFreshLocation()
        } catch (error: SecurityException) {
            fail("Location permission is required: ${error.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation() {
        if (destroyed || failed || stopping || endingShift || !approved) return
        if (!hasLocationPermission()) {
            fail("Location permission was removed. Enable it to go online again.")
            return
        }
        currentLocationToken?.cancel()
        val token = CancellationTokenSource()
        currentLocationToken = token
        try {
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { location ->
                    if (currentLocationToken !== token || destroyed || stopping) return@addOnSuccessListener
                    if (location != null) acceptLocation(location)
                    else {
                        current = current.copy(error = "Could not get GPS location. Check device location settings.")
                        publish()
                    }
                }
                .addOnFailureListener { error ->
                    if (!destroyed && !stopping && currentLocationToken === token) {
                        current = current.copy(error = "Could not refresh GPS: ${error.localizedMessage}")
                        publish()
                    }
                }
        } catch (error: SecurityException) {
            fail("Location access is no longer available")
        }
    }

    private fun isFresh(location: Location): Boolean =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000 in 0L..30_000L

    private fun acceptLocation(location: Location) {
        if (destroyed || failed || stopping || endingShift || !approved || !isFresh(location)) return
        lastFix = location
        current = current.copy(latitude = location.latitude, longitude = location.longitude, error = null)
        publish()
        if (connected && !writing) saveOnline(location)
    }

    private fun presenceData(location: Location, online: Boolean): Map<String, Any> = mapOf(
        "online" to online, "latitude" to location.latitude,
        "longitude" to location.longitude, "updatedAt" to ServerValue.TIMESTAMP
    )

    private fun saveOnline(location: Location) {
        val ref = presence ?: return
        val version = generation
        writing = true
        // Register before publishing, including after reconnection.
        ref.onDisconnect().setValue(presenceData(location, false))
            .addOnSuccessListener disconnectReady@{
                if (destroyed || stopping || version != generation) return@disconnectReady
                if (!isFresh(location)) {
                    writing = false
                    requestFreshLocation()
                    return@disconnectReady
                }
                presenceMayExist = true
                ref.updateChildren(presenceData(location, true))
                    .addOnSuccessListener presenceSaved@{
                        if (!destroyed && !stopping && version == generation) {
                            writing = false
                            if (!isFresh(location)) {
                                pauseForGps()
                                requestFreshLocation()
                                return@presenceSaved
                            }
                            current = current.copy(online = true, status = "ONLINE", error = null)
                            publish()
                        }
                    }
                    .addOnFailureListener {
                        if (!destroyed && !stopping && version == generation)
                            fail("Could not save rider location: ${it.localizedMessage}")
                    }
            }
            .addOnFailureListener {
                if (!destroyed && !stopping && version == generation)
                    fail("Could not prepare offline cleanup: ${it.localizedMessage}")
            }
    }

    private fun pauseForGps() {
        val location = lastFix ?: return
        val version = generation
        writing = true
        current = current.copy(online = false, status = "WAITING FOR GPS")
        publish()
        presence?.updateChildren(presenceData(location, false))
            ?.addOnCompleteListener { task ->
                if (!destroyed && !stopping && version == generation) {
                    writing = false
                    if (!task.isSuccessful) fail("Could not pause availability while GPS is unavailable")
                }
            }
    }

    private fun requestStop() {
        if (destroyed || stopping || stopChecking) return
        if (!connected) {
            current = current.copy(error = "Reconnect before going offline so your status can be saved")
            publish()
            return
        }
        stopChecking = true
        // Publish offline before checking the lock: rules reject any new reservation
        // once presence is offline. Existing assignments still block ending a shift.
        endingShift = true
        generation++
        writing = false
        val offline = presence?.updateChildren(mapOf("online" to false, "updatedAt" to ServerValue.TIMESTAMP))
        if (offline == null) {
            stopChecking = false
            endingShift = false
            return
        }
        offline.continueWithTask { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Could not save offline status")
            database.getReference("delivery/riders").child(uid!!).child("currentOrderId").get()
        }
            .addOnSuccessListener {
                stopChecking = false
                if (destroyed) return@addOnSuccessListener
                if (!it.getValue(String::class.java).isNullOrBlank()) {
                    endingShift = false
                    current = current.copy(error = "Finish your delivery before going offline")
                    publish()
                    requestFreshLocation()
                } else stopShift()
            }
            .addOnFailureListener {
                stopChecking = false
                if (!destroyed) {
                    endingShift = false
                    current = current.copy(error = "Could not verify delivery status: ${it.localizedMessage}")
                    publish()
                    requestFreshLocation()
                }
            }
    }

    private fun stopShift() {
        stopping = true
        endingShift = true
        generation++
        locationClient.removeLocationUpdates(callback)
        currentLocationToken?.cancel()
        handler.removeCallbacks(freshnessWatch)
        current = current.copy(stopping = true, status = "GOING OFFLINE", error = null)
        publish()
        val location = lastFix
        if (presenceMayExist && location != null) {
            writeOfflineAndStop(presenceData(location, false))
        } else {
            // A previous app session may have left a presence record. Clear it
            // without creating an invalid record when GPS has not arrived yet.
            presence?.get()?.addOnSuccessListener { snapshot ->
                if (destroyed || failed) return@addOnSuccessListener
                if (snapshot.exists()) writeOfflineAndStop(mapOf(
                    "online" to false, "updatedAt" to ServerValue.TIMESTAMP
                )) else stopSelf()
            }?.addOnFailureListener { if (!destroyed && !failed) offlineSaveFailed() }
        }
    }

    private fun writeOfflineAndStop(data: Map<String, Any>) {
        presence?.updateChildren(data)?.addOnCompleteListener { task ->
            if (destroyed) return@addOnCompleteListener
            if (task.isSuccessful) {
                presenceMayExist = false
                stopSelf()
            } else offlineSaveFailed()
        }
    }

    private fun offlineSaveFailed() {
        stopping = false
        current = current.copy(stopping = false, online = false,
            status = "STATUS SAVE FAILED", error = "Could not save offline status. Tap Go Offline to retry.")
        publish()
    }

    private fun fail(message: String) {
        if (destroyed || failed) return
        failed = true
        stopping = true
        generation++
        handler.removeCallbacks(freshnessWatch)
        currentLocationToken?.cancel()
        locationClient.removeLocationUpdates(callback)
        current = current.copy(online = false, error = message, status = "OFFLINE")
        publish()
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Rider online tracking", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(): Notification {
        val openDashboard = PendingIntent.getActivity(this, 0,
            Intent(this, RiderDashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = when {
            current.stopping -> "Saving offline status"
            !current.connected -> "Waiting for connection; sharing resumes when connected"
            current.online -> "Sharing your location for deliveries. Tap to open your dashboard."
            else -> "${current.status.lowercase().replaceFirstChar { it.uppercase() }}. Tap to open your dashboard."
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rider_location)
            .setContentTitle("BiteFlight Rider")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openDashboard)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun publish() {
        if (destroyed) return
        mutableState.value = current
        if (initialized) getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification())
    }

    override fun onDestroy() {
        destroyed = true
        generation++
        handler.removeCallbacksAndMessages(null)
        currentLocationToken?.cancel()
        locationClient.removeLocationUpdates(callback)
        auth.removeAuthStateListener(authListener)
        listeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        // Best effort for service teardown; process death uses onDisconnect.
        lastFix?.let { location ->
            if (presenceMayExist) presence?.updateChildren(presenceData(location, false))
                ?.addOnFailureListener { Log.w("RiderTracking", "Offline cleanup failed", it) }
        }
        mutableState.value = current.copy(running = false, online = false, stopping = false, status = "OFFLINE")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

