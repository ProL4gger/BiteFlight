package com.example.biteflight.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

class VoiceCallActivity : AppCompatActivity() {
    private var session: SipCall? = null
    private var onMicrophoneGranted: (() -> Unit)? = null
    private var currentCallback: Any? = null
    private lateinit var address: EditText
    private lateinit var port: EditText
    private lateinit var localAddress: TextView
    private lateinit var status: TextView
    private lateinit var call: Button
    private lateinit var answer: Button
    private lateinit var end: Button

    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = onMicrophoneGranted
        onMicrophoneGranted = null
        if (granted) action?.invoke()
        else status.setText(R.string.voice_microphone_required)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val inset = (24 * resources.displayMetrics.density).toInt()
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            content.updatePadding(inset + bars.left, inset + bars.top, inset + bars.right,
                inset + maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(scroll)

        fun label(text: Int) = TextView(this).apply {
            if (text != 0) setText(text)
            textSize = 16f
            content.addView(this)
        }
        fun button(text: Int, action: () -> Unit) = Button(this).apply {
            setText(text)
            setOnClickListener { action() }
            content.addView(this)
        }

        label(R.string.voice_title).textSize = 28f
        label(R.string.voice_instructions)
        localAddress = label(0).apply { setTextIsSelectable(true) }
        address = EditText(this).apply {
            id = R.id.voice_remote_address
            setSingleLine(true)
            setHint(R.string.voice_remote_address)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            content.addView(this)
        }
        port = EditText(this).apply {
            id = R.id.voice_remote_port
            setSingleLine(true)
            setHint(R.string.voice_remote_port)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(LISTEN_PORT.toString())
            content.addView(this)
        }
        status = label(R.string.voice_starting).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        call = button(R.string.voice_call) {
            val destination = destination() ?: return@button
            val active = session ?: return@button
            withMicrophone { active.dial(destination) }
        }
        answer = button(R.string.voice_answer) {
            val active = session ?: return@button
            withMicrophone { active.answer() }
        }
        end = button(R.string.voice_end) { session?.hangUp() }
        button(R.string.voice_back) { finish() }
        render(CallState.UNAVAILABLE, getString(R.string.voice_starting))
    }

    override fun onStart() {
        super.onStart()
        localAddress.text = getString(R.string.voice_local_address, localIpv4Addresses(), LISTEN_PORT)
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val callbackToken = Any()
        currentCallback = callbackToken
        session = SipCall(LISTEN_PORT, audio) { next, message ->
            runOnUiThread {
                if (currentCallback === callbackToken) render(next, message)
            }
        }
    }

    override fun onStop() {
        currentCallback = null
        onMicrophoneGranted = null
        session?.close()
        session = null
        render(CallState.UNAVAILABLE, getString(R.string.voice_starting))
        super.onStop()
    }

    private fun render(state: CallState, message: String) {
        status.text = message
        address.isEnabled = state == CallState.READY
        port.isEnabled = state == CallState.READY
        call.isEnabled = state == CallState.READY
        answer.isEnabled = state == CallState.INCOMING
        end.isEnabled = state in setOf(CallState.DIALING, CallState.INCOMING, CallState.CONNECTING, CallState.ACTIVE)
        end.setText(when (state) {
            CallState.INCOMING -> R.string.voice_decline
            CallState.DIALING -> R.string.voice_cancel
            else -> R.string.voice_end
        })
    }

    private fun withMicrophone(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else if (onMicrophoneGranted == null) {
            onMicrophoneGranted = action
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun destination(): InetSocketAddress? {
        val parts = address.text.toString().trim().split('.')
        val octets = parts.map { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it.isEmpty() || it.any { digit -> digit !in '0'..'9' } } ||
            octets.any { it == null || it !in 0..255 } || octets[0] == 0 || octets[0]!! >= 224
        ) {
            address.error = getString(R.string.voice_invalid_address)
            return null
        }
        val number = port.text.toString().toIntOrNull()
        if (number == null || number !in 1..65535) {
            port.error = getString(R.string.voice_invalid_port)
            return null
        }
        val ip = InetAddress.getByAddress(octets.map { checkNotNull(it).toByte() }.toByteArray())
        if (number == LISTEN_PORT && (ip.isLoopbackAddress || NetworkInterface.getByInetAddress(ip) != null)) {
            address.error = getString(R.string.voice_self_call)
            return null
        }
        return InetSocketAddress(ip, number)
    }

    private fun localIpv4Addresses(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
            .joinToString(", ")
    }.getOrDefault("").ifEmpty { getString(R.string.voice_no_address) }

    private companion object {
        const val LISTEN_PORT = 5060
    }
}
