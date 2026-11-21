package com.example.biteflight

import com.example.biteflight.voice.VoiceCallApplication
import org.osmdroid.config.Configuration

class BiteFlightApp : VoiceCallApplication() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("osm_prefs", MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = "BiteFlight/1.0 (Android App; contact@biteflight.app)"
    }
}
