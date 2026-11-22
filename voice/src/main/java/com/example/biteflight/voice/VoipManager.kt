package com.example.biteflight.voice

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArraySet

object VoipManager {
    private var sipCall: SipCall? = null
    private var internet: InternetCalls? = null
    @Volatile
    private var sipState = CallState.READY
    @Volatile
    var currentState = CallState.READY
        private set
    var currentMessage = "Ready for calls"
        private set
    @Volatile
    var peerName: String? = null
        private set
    internal val isMuted get() = if (internet?.busy == true) internet?.muted == true else sipCall?.isMuted == true
    internal val isSpeakerOn get() = if (internet?.busy == true) internet?.speakerOn == true else sipCall?.isSpeakerOn == true
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
        val main = Handler(Looper.getMainLooper())
        sipCall = SipCall(5060, audio) { state, message ->
            main.post {
                sipState = state
                if (internet?.busy != true) publish(context, state, message, null)
                else if (state == CallState.INCOMING) sipCall?.hangUp()
            }
        }
        internet = InternetCalls(context, audio,
            canRing = { sipState == CallState.READY || sipState == CallState.UNAVAILABLE },
            update = { state, message, name -> publish(context, state, message, name) }
        )
    }

    private fun publish(context: Context, state: CallState, message: String, name: String?) {
        connectedAt = when (state) {
            CallState.ACTIVE -> connectedAt ?: SystemClock.elapsedRealtime()
            CallState.CONNECTING -> connectedAt
            else -> null
        }
        peerName = name
        currentMessage = message
        currentState = state
        listeners.forEach { it(state, message) }
        if (state == CallState.INCOMING || state == CallState.ACTIVE ||
            state == CallState.DIALING || state == CallState.CONNECTING
        ) {
            VoipNotification.showCallNotification(context, message, active = true)
        } else {
            VoipNotification.cancelNotification(context)
        }
    }

    internal fun dialOrder(seller: String, order: String, recipient: String, name: String): Boolean =
        internet?.dial(seller, order, recipient, name) == true

    fun dial(peer: InetSocketAddress) {
        if (currentState != CallState.READY) return
        activePeerAddress = peer.address.hostAddress ?: ""
        activePeerPort = peer.port
        sipCall?.dial(peer)
    }

    internal fun setMuted(muted: Boolean) {
        if (internet?.busy == true) internet?.mute(muted) else sipCall?.setMuted(muted)
    }

    internal fun setSpeaker(enabled: Boolean) {
        if (internet?.busy == true) internet?.speaker(enabled) else sipCall?.setSpeaker(enabled)
    }

    fun answer() {
        if (internet?.busy == true) internet?.answer() else sipCall?.answer()
    }

    fun hangUp() {
        if (internet?.busy == true) internet?.hangUp() else sipCall?.hangUp()
        activePeerAddress = ""
    }

    fun close() {
        internet?.close()
        internet = null
        sipCall?.close()
        sipCall = null
    }
}
