package com.example.biteflight.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

object LocationHelper {

    private val main = Handler(Looper.getMainLooper())

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(context: Context, onResult: (Pair<Double, Double>?) -> Unit) {
        if (!hasPermission(context)) { onResult(null); return }
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc -> onResult(loc?.let { it.latitude to it.longitude }) }
            .addOnFailureListener { onResult(null) }
    }

    /** Turns a typed address into coordinates. Calls onResult on the main thread; null if not found. */
    fun geocode(context: Context, address: String, onResult: (Pair<Double, Double>?) -> Unit) {
        if (address.isBlank() || !Geocoder.isPresent()) { onResult(null); return }
        val query = if (address.contains("egypt", ignoreCase = true)) address else "$address, Egypt"
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocationName(query, 1) { list ->
                    val r = list.firstOrNull()?.let { it.latitude to it.longitude }
                    main.post { onResult(r) }
                }
            } else {
                Thread {
                    val r = try {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocationName(query, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
                    } catch (e: Exception) { null }
                    main.post { onResult(r) }
                }.start()
            }
        } catch (e: Exception) {
            onResult(null)
        }
    }
}