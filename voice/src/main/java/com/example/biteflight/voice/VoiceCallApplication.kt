package com.example.biteflight.voice

import android.app.Activity
import android.app.Application
import android.os.Bundle

open class VoiceCallApplication : Application(), Application.ActivityLifecycleCallbacks {
    private val floatingCall = FloatingCallOverlay()

    override fun onCreate() {
        super.onCreate()
        VoipManager.init(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) = floatingCall.onActivityResumed(activity)
    override fun onActivityPaused(activity: Activity) = floatingCall.onActivityPaused(activity)
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
