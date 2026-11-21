package com.example.biteflight.voice

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArraySet

object VoipManager {
    private var sipCall: SipCall? = null
    @Volatile
    var currentState = CallState.READY
        private set
    var currentMessage = "Ready for calls"
        private set
    var activePeerAddress: String = ""
    var activePeerPort: Int = 5060
    @Volatile
    internal var connectedAt: Long? = null
        private set

    val listeners = CopyOnWriteArraySet<(CallState, String) -> Unit>()

    fun init(context: Context) {
        if (sipCall != null) return
        currentState = CallState.READY
        currentMessage = "Ready for calls"
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sipCall = SipCall(5060, audio) { state, message ->
            connectedAt = if (state == CallState.ACTIVE) {
                connectedAt ?: SystemClock.elapsedRealtime()
            } else {
                null
            }
            currentMessage = message
            currentState = state
            listeners.forEach { it(state, message) }

            if (state == CallState.INCOMING || state == CallState.ACTIVE || state == CallState.DIALING || state == CallState.CONNECTING) {
                VoipNotification.showCallNotification(context, message, active = true)
            } else {
                VoipNotification.cancelNotification(context)
            }
        }
    }

    fun dial(peer: InetSocketAddress) {
        activePeerAddress = peer.address.hostAddress ?: ""
        activePeerPort = peer.port
        sipCall?.dial(peer)
    }

    fun answer() {
        sipCall?.answer()
    }

    fun hangUp() {
        sipCall?.hangUp()
        activePeerAddress = ""
    }

    fun close() {
        sipCall?.close()
        sipCall = null
    }
}
