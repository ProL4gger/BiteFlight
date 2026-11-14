package com.example.biteflight.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.biteflight.R
import com.example.biteflight.utils.LocationHelper
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MapPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        private const val CAIRO_LAT = 30.0444
        private const val CAIRO_LNG = 31.2357

        // Free vector map from OpenFreeMap: no API key, no registration.
        // Other free styles on the same server: "bright", "positron".
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    }

    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) centerOnMyLocation()
        else Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Must be called before the MapView is inflated.
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_map_picker)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        // Start where the user already picked before, otherwise Cairo then GPS if allowed
        val startLat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        val startLng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
        val hasStart = startLat != 0.0 || startLng != 0.0

        mapView.getMapAsync { m ->
            map = m
            m.setStyle(STYLE_URL)
            m.cameraPosition = CameraPosition.Builder()
                .target(if (hasStart) LatLng(startLat, startLng) else LatLng(CAIRO_LAT, CAIRO_LNG))
                .zoom(16.0)
                .build()
            if (!hasStart && LocationHelper.hasPermission(this)) centerOnMyLocation()
        }

        findViewById<Button>(R.id.btnMyLocation).setOnClickListener {
            if (LocationHelper.hasPermission(this)) centerOnMyLocation()
            else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        findViewById<Button>(R.id.btnConfirmPin).setOnClickListener {
            // The pin is fixed in the middle of the screen, so the map's center IS the pin.
            val center = map?.cameraPosition?.target
            if (center == null) {
                Toast.makeText(this, "Map is still loading", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(EXTRA_LAT, center.latitude)
                    .putExtra(EXTRA_LNG, center.longitude)
            )
            finish()
        }
    }

    private fun centerOnMyLocation() {
        LocationHelper.getCurrentLocation(this) { result ->
            if (isFinishing || isDestroyed) return@getCurrentLocation
            if (result == null) {
                Toast.makeText(this, "Couldn't get your location. Is GPS on?", Toast.LENGTH_SHORT).show()
                return@getCurrentLocation
            }
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(result.first, result.second), 17.0)
            )
        }
    }

    // MapView needs every lifecycle callback forwarded.
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}