package com.example.biteflight.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

internal class WebRtcAudio(
    context: Context,
    servers: List<PeerConnection.IceServer>,
    private val onLocalSdp: (String) -> Unit,
    private val onLocalCandidate: (IceCandidate) -> Unit,
    private val onConnection: (PeerConnection.PeerConnectionState) -> Unit,
    private val onError: () -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val factory = getFactory(context)
    private val source: AudioSource = factory.createAudioSource(MediaConstraints())
    private val track: AudioTrack = factory.createAudioTrack("voice", source)
    private var closed = false
    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private val peer = factory.createPeerConnection(PeerConnection.RTCConfiguration(servers).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
    }, object : PeerConnection.Observer {
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            dispatch {
                Log.d(TAG, "Media connection: $state")
                onConnection(state)
            }
        }

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            Log.d(TAG, "Network address gathering: $state")
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.d(TAG, "ICE connection: $state")
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceCandidate(candidate: IceCandidate) {
            dispatch {
                val type = candidate.sdp.substringAfter(" typ ", "").substringBefore(' ')
                Log.d(TAG, "Local network candidate type: $type")
                onLocalCandidate(candidate)
            }
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
    })

    init {
        if (peer == null) dispatch(onError)
        else peer.addTrack(track, listOf("biteflight"))
    }

    fun offer() {
        peer?.createOffer(createLocalDescription, MediaConstraints())
    }

    fun answer(offer: String) {
        setRemoteDescription(SessionDescription.Type.OFFER, offer) {
            peer?.createAnswer(createLocalDescription, MediaConstraints())
        }
    }

    fun applyAnswer(answer: String) {
        setRemoteDescription(SessionDescription.Type.ANSWER, answer) {}
    }

    fun addRemoteCandidate(candidate: IceCandidate) {
        if (remoteDescriptionSet) addCandidate(candidate) else pendingRemoteCandidates.add(candidate)
    }

    private fun setRemoteDescription(type: SessionDescription.Type, sdp: String, ready: () -> Unit) {
        peer?.setRemoteDescription(observer(onSet = {
            remoteDescriptionSet = true
            pendingRemoteCandidates.forEach(::addCandidate)
            pendingRemoteCandidates.clear()
            Log.d(TAG, "Applied remote ${type.name.lowercase()}")
            ready()
        }), SessionDescription(type, sdp))
    }

    private fun addCandidate(candidate: IceCandidate) {
        if (peer?.addIceCandidate(candidate) != true) Log.w(TAG, "Couldn’t apply remote network candidate")
    }

    fun mute(muted: Boolean) {
        track.setEnabled(!muted)
    }

    fun close() {
        closed = true
        peer?.dispose()
        track.dispose()
        source.dispose()
    }

    private val createLocalDescription = observer(onCreate = { description ->
        peer?.setLocalDescription(observer(onSet = {
            onLocalSdp(description.description)
        }), description)
    })

    private fun dispatch(action: () -> Unit) {
        main.post { if (!closed) action() }
    }

    private fun observer(
        onCreate: (SessionDescription) -> Unit = {},
        onSet: () -> Unit = {}
    ) = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = dispatch { onCreate(description) }
        override fun onSetSuccess() = dispatch(onSet)
        override fun onCreateFailure(message: String) = dispatch(onError)
        override fun onSetFailure(message: String) = dispatch(onError)
    }

    private companion object {
        const val TAG = "BiteFlightRtc"
        var factory: PeerConnectionFactory? = null

        fun getFactory(context: Context): PeerConnectionFactory {
            factory?.let { return it }
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
            return PeerConnectionFactory.builder().createPeerConnectionFactory().also { factory = it }
        }
    }
}
