package com.example.biteflight.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.InetSocketAddress

class VoiceCallService : Service() {
    internal inner class Connection : Binder() {
        fun observe(observer: (CallState, String) -> Unit): () -> Unit {
            observers.add(observer)
            observer(state, message)
            return { observers.remove(observer) }
        }

        fun dial(peer: InetSocketAddress) = begin(CallState.READY) { it.dial(peer) }
        fun answer() = begin(CallState.INCOMING) { it.answer() }
        fun hangUp() { session?.hangUp() }
    }

    private val binder = Connection()
    private val main = Handler(Looper.getMainLooper())
    private val notifications by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private val observers = mutableSetOf<(CallState, String) -> Unit>()
    private var session: SipCall? = null
    private var state = CallState.UNAVAILABLE
    private var message = ""
    private var foreground = false
    private var destroyed = false

    override fun onBind(intent: Intent): Binder {
        if (session == null) {
            message = getString(R.string.voice_starting)
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            session = SipCall(LISTEN_PORT, audio) { next, text ->
                main.post { if (!destroyed) update(next, text) }
            }
        }
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) session?.hangUp()
        if (!foreground) stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun begin(requiredState: CallState, action: (SipCall) -> Unit) {
        val call = session ?: return
        if (state != requiredState || foreground) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                notifications.createNotificationChannel(NotificationChannel(
                    CHANNEL, getString(R.string.voice_notification_channel), NotificationManager.IMPORTANCE_LOW
                ))
            }
            // The bound activity invokes this while visible and after microphone permission is granted.
            startService(Intent(this, VoiceCallService::class.java))
            val indicator = notification(getString(R.string.voice_starting))
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTIFICATION_ID, indicator, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, indicator)
            }
            foreground = true
            action(call)
        } catch (error: RuntimeException) {
            Log.w("VoiceCallService", "Could not keep the call running", error)
            stopSelf()
            update(state, getString(R.string.voice_background_unavailable))
        }
    }

    private fun update(next: CallState, text: String) {
        state = next
        message = text
        if (foreground) {
            if (next == CallState.READY || next == CallState.UNAVAILABLE) {
                foreground = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                notifications.notify(NOTIFICATION_ID, notification(text))
            }
        }
        observers.toList().forEach { it(next, text) }
    }

    private fun notification(text: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val reopen = PendingIntent.getActivity(this, 0, Intent(this, VoiceCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val end = PendingIntent.getService(this, 1, Intent(this, VoiceCallService::class.java)
            .setAction(ACTION_END), flags)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_voice_call)
            .setContentTitle(getString(R.string.voice_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(reopen)
            .addAction(R.drawable.ic_voice_call, getString(R.string.voice_end), end)
            .build()
    }

    override fun onDestroy() {
        destroyed = true
        observers.clear()
        session?.close()
        session = null
        super.onDestroy()
    }

    internal companion object {
        const val LISTEN_PORT = 5060
        private const val CHANNEL = "voice_calls"
        private const val NOTIFICATION_ID = 5060
        private const val ACTION_END = "com.example.biteflight.voice.END_CALL"
    }
}
