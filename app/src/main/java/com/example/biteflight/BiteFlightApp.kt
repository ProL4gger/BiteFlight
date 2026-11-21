package com.example.biteflight

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.biteflight.voice.FloatingCallOverlay
import com.example.biteflight.voice.VoipManager
import org.osmdroid.config.Configuration

class BiteFlightApp : Application(), Application.ActivityLifecycleCallbacks {
    private val floatingCall = FloatingCallOverlay()

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("osm_prefs", MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = "BiteFlight/1.0 (Android App; contact@biteflight.app)"

        VoipManager.init(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        floatingCall.onActivityResumed(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) = floatingCall.onActivityPaused(activity)
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
