package com.example.biteflight.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

class VoiceCallFragment : Fragment() {
    private var onMicrophoneGranted: (() -> Unit)? = null
    private lateinit var address: EditText
    private lateinit var port: EditText
    private lateinit var localAddress: TextView
    private lateinit var status: TextView
    private lateinit var call: MaterialButton
    private lateinit var answer: MaterialButton
    private lateinit var end: MaterialButton

    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = onMicrophoneGranted
        onMicrophoneGranted = null
        if (granted) action?.invoke()
        else status.setText(R.string.voice_microphone_required)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        VoipManager.init(requireContext().applicationContext)
        return inflater.inflate(R.layout.activity_voice_call, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.topToolbar)?.visibility = View.GONE

        localAddress = view.findViewById(R.id.tvLocalAddress)
        address = view.findViewById(R.id.etRemoteAddress)
        port = view.findViewById(R.id.etRemotePort)
        port.setText(LISTEN_PORT.toString())
        if (VoipManager.activePeerAddress.isNotBlank()) {
            address.setText(VoipManager.activePeerAddress)
            port.setText(VoipManager.activePeerPort.toString())
        }

        status = view.findViewById(R.id.tvCallStatus)
        call = view.findViewById(R.id.btnCall)
        answer = view.findViewById(R.id.btnAnswer)
        end = view.findViewById(R.id.btnEndCall)

        call.setOnClickListener {
            val destination = destination() ?: return@setOnClickListener
            withMicrophone { VoipManager.dial(destination) }
        }
        answer.setOnClickListener { answerIncoming() }
        end.setOnClickListener { VoipManager.hangUp() }
        view.findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            requireActivity().finish()
        }

        render(VoipManager.currentState, VoipManager.currentMessage)
    }

    override fun onStart() {
        super.onStart()
        localAddress.text = getString(R.string.voice_local_address, localIpv4Addresses(), LISTEN_PORT)
        VoipManager.listeners.add(::onVoipStateChanged)
        render(VoipManager.currentState, VoipManager.currentMessage)
    }

    override fun onStop() {
        VoipManager.listeners.remove(::onVoipStateChanged)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if ((activity as? VoiceCallActivity)?.consumeAnswerRequest() == true) {
            answerIncoming()
        }
    }

    private fun answerIncoming() {
        if (VoipManager.currentState != CallState.INCOMING) return
        withMicrophone {
            if (VoipManager.currentState == CallState.INCOMING) VoipManager.answer()
        }
    }

    private fun onVoipStateChanged(state: CallState, message: String) {
        activity?.runOnUiThread { render(state, message) }
    }

    private fun render(state: CallState, message: String) {
        status.text = message
        address.isEnabled = state == CallState.READY || state == CallState.UNAVAILABLE
        port.isEnabled = state == CallState.READY || state == CallState.UNAVAILABLE
        call.isEnabled = state == CallState.READY || state == CallState.UNAVAILABLE || state == CallState.ENDING
        answer.isEnabled = state == CallState.INCOMING
        end.isEnabled = state in setOf(CallState.DIALING, CallState.INCOMING, CallState.CONNECTING, CallState.ACTIVE)
        end.setText(when (state) {
            CallState.INCOMING -> R.string.voice_decline
            CallState.DIALING -> R.string.voice_cancel
            else -> R.string.voice_end
        })
    }

    private fun withMicrophone(action: () -> Unit) {
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
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
