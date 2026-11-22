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

        if (savedInstanceState == null && intent.action == null) {
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
                val seller = intent.getStringExtra(EXTRA_SELLER)
                val order = intent.getStringExtra(EXTRA_ORDER)
                val recipient = intent.getStringExtra(EXTRA_RECIPIENT)
                if (seller == null || order == null || recipient == null ||
                    currentCallBusy()
                ) {
                    Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
                awaitingDial = true
                if (!VoipManager.dialOrder(seller, order, recipient, intent.getStringExtra(EXTRA_NAME).orEmpty())) {
                    awaitingDial = false
                    Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun render() {
        val state = VoipManager.currentState
        val inSession = state != CallState.READY && state != CallState.UNAVAILABLE
        val orderCall = intent.hasExtra(EXTRA_ORDER)
        val status = findViewById<TextView>(R.id.sessionStatus)
        val reopeningSession = intent.action == ACTION_VIEW_SESSION || intent.action == ACTION_ANSWER_INCOMING
        if (!inSession && (sessionShown || reopeningSession) && !isFinishing) {
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
        internal const val ACTION_VIEW_SESSION = "com.example.biteflight.voice.action.VIEW_SESSION"
        internal const val ACTION_ANSWER_INCOMING = "com.example.biteflight.voice.action.ANSWER_INCOMING"
        private const val ACTION_DIAL_ORDER = "com.example.biteflight.voice.action.DIAL_ORDER"
        private const val EXTRA_SELLER = "seller"
        private const val EXTRA_ORDER = "order"
        private const val EXTRA_RECIPIENT = "recipient"
        private const val EXTRA_NAME = "orderPeerName"
        private const val KEY_PENDING_ACTION = "pendingAction"
        private const val KEY_SESSION_SHOWN = "sessionShown"
        private fun currentCallBusy() =
            VoipManager.currentState != CallState.READY && VoipManager.currentState != CallState.UNAVAILABLE

        fun startOrderCall(context: Context, seller: String, order: String, recipient: String, name: String) {
            context.startActivity(Intent(context, VoiceCallActivity::class.java).apply {
                action = ACTION_DIAL_ORDER
                putExtra(EXTRA_SELLER, seller)
                putExtra(EXTRA_ORDER, order)
                putExtra(EXTRA_RECIPIENT, recipient)
                putExtra(EXTRA_NAME, name)
            })
        }
    }
}
