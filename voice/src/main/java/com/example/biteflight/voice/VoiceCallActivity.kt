package com.example.biteflight.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

class VoiceCallActivity : AppCompatActivity() {
    private var pendingAction: String? = null
    private var sessionShown = false
    private var awaitingDial = false
    private val stateListener: (CallState, String) -> Unit = { _, _ ->
        runOnUiThread {
            if (awaitingDial) {
                awaitingDial = false
                sessionShown = true
            }
            if (started) render()
        }
    }
    private var started = false

    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) {
            performCallAction(action)
        } else {
            Toast.makeText(this, R.string.voice_microphone_required, Toast.LENGTH_LONG).show()
            if (action == ACTION_DIAL_ORDER) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_call_host)
        VoipManager.init(applicationContext)
        pendingAction = savedInstanceState?.getString(KEY_PENDING_ACTION)
        sessionShown = savedInstanceState?.getBoolean(KEY_SESSION_SHOWN) == true

        val root = findViewById<View>(R.id.voiceCallRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        findViewById<MaterialToolbar>(R.id.topToolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.callSessionBack).setOnClickListener { finish() }
        findViewById<View>(R.id.sessionAnswer).setOnClickListener { requestCallAction(ACTION_ANSWER_INCOMING) }
        findViewById<View>(R.id.sessionEnd).setOnClickListener { VoipManager.hangUp() }
        findViewById<View>(R.id.sessionMute).setOnClickListener { VoipManager.setMuted(!VoipManager.isMuted) }
        findViewById<View>(R.id.sessionSpeaker).setOnClickListener { VoipManager.setSpeaker(!VoipManager.isSpeakerOn) }

        if (savedInstanceState == null && intent.action != ACTION_DIAL_ORDER) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.voiceFragmentContainer, VoiceCallFragment())
                .commit()
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        started = true
        VoipManager.listeners.add(stateListener)
        render()
    }

    override fun onResume() {
        super.onResume()
        consumeCallAction()
    }

    override fun onStop() {
        started = false
        VoipManager.listeners.remove(stateListener)
        findViewById<Chronometer>(R.id.sessionTimer).stop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_PENDING_ACTION, pendingAction)
        outState.putBoolean(KEY_SESSION_SHOWN, sessionShown)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (started) consumeCallAction()
    }

    private fun consumeCallAction() {
        val action = intent.action
        if (action != ACTION_ANSWER_INCOMING && action != ACTION_DIAL_ORDER) return
        intent.action = null
        requestCallAction(action)
    }

    private fun requestCallAction(action: String) {
        if (pendingAction != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            performCallAction(action)
        } else {
            pendingAction = action
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun performCallAction(action: String?) {
        when (action) {
            ACTION_ANSWER_INCOMING -> if (VoipManager.currentState == CallState.INCOMING) VoipManager.answer()
            ACTION_DIAL_ORDER -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                val peer = address?.let(::parseAddress)
                if (peer == null || VoipManager.currentState != CallState.READY) {
                    Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
                awaitingDial = true
                VoipManager.dial(InetSocketAddress(peer, LISTEN_PORT), intent.getStringExtra(EXTRA_NAME))
            }
        }
    }

    private fun render() {
        val state = VoipManager.currentState
        val inSession = state != CallState.READY && state != CallState.UNAVAILABLE
        val orderCall = intent.hasExtra(EXTRA_ADDRESS)
        val status = findViewById<TextView>(R.id.sessionStatus)
        if (!inSession && sessionShown && !isFinishing) {
            Toast.makeText(this, VoipManager.currentMessage, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (inSession) sessionShown = true
        val showSession = inSession || orderCall
        findViewById<View>(R.id.topToolbar).visibility = if (showSession) View.GONE else View.VISIBLE
        findViewById<View>(R.id.voiceFragmentContainer).visibility = if (showSession) View.GONE else View.VISIBLE
        findViewById<View>(R.id.callSession).visibility = if (showSession) View.VISIBLE else View.GONE
        if (!showSession) return

        val recipient = if (inSession) VoipManager.peerName else intent.getStringExtra(EXTRA_NAME)
        findViewById<TextView>(R.id.sessionName).text = recipient ?: getString(R.string.voice_title)
        status.setText(when (state) {
            CallState.INCOMING -> R.string.voice_floating_incoming
            CallState.DIALING -> R.string.voice_floating_dialing
            CallState.ACTIVE -> R.string.voice_floating_active
            CallState.ENDING -> R.string.voice_floating_ending
            else -> R.string.voice_floating_connecting
        })
        val active = state == CallState.ACTIVE
        val startTime = VoipManager.connectedAt
        findViewById<Chronometer>(R.id.sessionTimer).apply {
            if (active && startTime != null) {
                base = startTime
                visibility = View.VISIBLE
                start()
            } else {
                visibility = View.GONE
                stop()
            }
        }
        findViewById<View>(R.id.sessionAudioControls).visibility = if (active) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.sessionMute).apply {
            isChecked = VoipManager.isMuted
            setText(if (isChecked) R.string.voice_unmute else R.string.voice_mute)
        }
        findViewById<MaterialButton>(R.id.sessionSpeaker).isChecked = VoipManager.isSpeakerOn
        findViewById<View>(R.id.sessionAnswer).visibility = if (state == CallState.INCOMING) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.sessionEnd).apply {
            isEnabled = inSession && state != CallState.ENDING
            setText(when (state) {
                CallState.INCOMING -> R.string.voice_decline
                CallState.DIALING -> R.string.voice_cancel
                else -> R.string.voice_end
            })
        }
    }

    companion object {
        internal const val ACTION_ANSWER_INCOMING = "com.example.biteflight.voice.action.ANSWER_INCOMING"
        private const val ACTION_DIAL_ORDER = "com.example.biteflight.voice.action.DIAL_ORDER"
        private const val EXTRA_ADDRESS = "orderPeerAddress"
        private const val EXTRA_NAME = "orderPeerName"
        private const val KEY_PENDING_ACTION = "pendingAction"
        private const val KEY_SESSION_SHOWN = "sessionShown"
        private const val LISTEN_PORT = 5060

        fun startOrderCall(context: Context, name: String, addresses: List<String>): Boolean {
            val interfaces = runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.interfaceAddresses }
                    .filter { it.address is Inet4Address && it.address.isSiteLocalAddress }
            }.getOrDefault(emptyList())
            val localAddresses = interfaces.map { it.address }.toSet()
            val address = addresses.asSequence().mapNotNull(::parseAddress)
                .filter { it.isSiteLocalAddress && it !in localAddresses }
                .firstOrNull { remote ->
                    interfaces.any { local ->
                        val prefix = local.networkPrefixLength.toInt()
                        prefix in 1..32 && (ipv4Bits(local.address) xor ipv4Bits(remote)) ushr (32 - prefix) == 0
                    }
                } ?: return false
            context.startActivity(Intent(context, VoiceCallActivity::class.java).apply {
                action = ACTION_DIAL_ORDER
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_ADDRESS, address.hostAddress)
            })
            return true
        }

        private fun parseAddress(value: String): InetAddress? {
            val parts = value.split('.')
            val octets = parts.map { it.toIntOrNull() }
            if (parts.size != 4 || octets.any { it == null || it !in 0..255 }) return null
            return InetAddress.getByAddress(octets.map { checkNotNull(it).toByte() }.toByteArray())
        }

        private fun ipv4Bits(address: InetAddress): Int =
            address.address.fold(0) { value, octet -> (value shl 8) or (octet.toInt() and 255) }
    }
}
