package com.example.biteflight.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.android.gms.tasks.Task
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

/** Processes signaling and owns one WebRTC session on the main thread. */
internal class InternetCalls(
    private val context: Context,
    private val audio: AudioManager,
    private val canRing: () -> Boolean,
    private val update: (CallState, String, String?) -> Unit
) {
    private class Call(
        val uid: String,
        val recipient: String,
        val ref: DatabaseReference,
        val outgoing: Boolean,
        val name: String?,
        var offer: String? = null
    ) {
        var observer: ValueEventListener? = null
        var media: WebRtcAudio? = null
        var answerApplied = false
        val remoteCandidates = mutableMapOf<String, IceCandidate>()
        val appliedCandidates = mutableSetOf<String>()
        var claimed = false
        var created = !outgoing
        var timeoutBeforeAnswer = outgoing
        var active = false
        var timeout: Runnable? = null
        var audioMode: Int? = null
        var previousSpeaker = false
    }

    private val main = Handler(Looper.getMainLooper())
    private val auth = FirebaseAuth.getInstance()
    private val calls get() = FirebaseDatabase.getInstance().getReference("internetCalls")
    private var inbox: Query? = null
    private var inboxObserver: ValueEventListener? = null
    private var session: Call? = null
    private var currentUid: String? = null
    private var serverOffset = 0L
    private val offsetRef = FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset")
    private val offsetListener = observe { serverOffset = it.getValue(Long::class.java) ?: 0L }
    private val authListener = FirebaseAuth.AuthStateListener { listenForCalls(it.currentUser?.uid) }

    val busy get() = session != null
    var muted = false
        private set
    var speakerOn = false
        private set

    init {
        auth.addAuthStateListener(authListener)
        offsetRef.addValueEventListener(offsetListener)
    }

    fun dial(seller: String, order: String, recipient: String, name: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        if (busy || !canRing() || recipient == uid) return false
        val ref = calls.child(recipient).push()
        val call = Call(uid, recipient, ref, outgoing = true, name)
        session = call
        report(call, CallState.DIALING, "Calling...")
        timeOut(call)
        ref.setValue(mapOf(
            "seller" to seller, "order" to order, "caller" to uid,
            "state" to "ringing", "createdAt" to ServerValue.TIMESTAMP
        )).addOnCompleteListener { result ->
            if (session !== call) {
                if (result.isSuccessful) ref.removeValue()
                return@addOnCompleteListener
            }
            if (!result.isSuccessful) {
                val err = result.exception?.localizedMessage ?: "Unknown error"
                Log.e("InternetCalls", "Firebase call dial failed: $err")
                finishCall("Couldn't start the call: $err", remove = false)
                return@addOnCompleteListener
            }
            call.created = true
            observeCall(call)
            registerDisconnect(call) { prepareMedia(call) { it.offer() } }
        }
        return true
    }

    fun answer() {
        val call = session ?: return
        val offer = call.offer ?: return
        if (call.outgoing || call.claimed) return
        call.claimed = true
        timeOut(call)
        report(call, CallState.CONNECTING, "Connecting...")
        call.ref.child("state").runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                if (data.getValue(String::class.java) != "ringing") return Transaction.abort()
                data.value = "accepted"
                return Transaction.success(data)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (session !== call) return
                if (!committed || error != null) {
                    finishCall("Call is no longer available", remove = false)
                    return
                }
                registerDisconnect(call) { prepareMedia(call) { it.answer(offer) } }
            }
        }, false)
    }

    fun hangUp() {
        val call = session ?: return
        val wasActive = call.active || call.claimed || call.outgoing
        if (wasActive) {
            call.ref.child("state").setValue("ended")
        } else {
            call.ref.child("state").setValue("declined")
        }
        finishCall("Call ended")
    }

    fun mute(enabled: Boolean) {
        val call = session ?: return
        if (!call.active) return
        muted = enabled
        call.media?.mute(enabled)
        report(call, CallState.ACTIVE, "Call in progress")
    }

    @Suppress("DEPRECATION")
    fun speaker(enabled: Boolean) {
        val call = session ?: return
        if (!call.active) return
        speakerOn = if (Build.VERSION.SDK_INT >= 31) {
            if (enabled) {
                val device = audio.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                device != null && audio.setCommunicationDevice(device)
            } else {
                audio.clearCommunicationDevice()
                false
            }
        } else {
            audio.isSpeakerphoneOn = enabled
            audio.isSpeakerphoneOn
        }
        report(call, CallState.ACTIVE, "Call in progress")
    }

    fun close() {
        auth.removeAuthStateListener(authListener)
        offsetRef.removeEventListener(offsetListener)
        listenForCalls(null)
    }

    private fun listenForCalls(uid: String?) {
        if (currentUid == uid) return
        currentUid = uid
        inboxObserver?.let { inbox?.removeEventListener(it) }
        inbox = null
        inboxObserver = null
        if (session?.uid != uid) finishCall("Call ended")
        if (uid == null) return
        val query = calls.child(uid).orderByChild("createdAt").limitToLast(8)
        val observer = observe { snapshot ->
            if (uid != currentUid || busy || !canRing()) return@observe
            val now = System.currentTimeMillis() + serverOffset
            val invite = snapshot.children.toList().asReversed().firstOrNull {
                val created = it.child("createdAt").getValue(Long::class.java) ?: 0L
                val age = now - created
                it.child("state").value == "ringing" && it.child("offer").exists() && age in -5000L..TIMEOUT
            } ?: return@observe
            val offer = invite.child("offer").getValue(String::class.java) ?: return@observe
            val call = Call(uid, uid, invite.ref, outgoing = false, name = null, offer)
            session = call
            report(call, CallState.INCOMING, "Incoming order call")
            observeCall(call)
            timeOut(call)
        }
        inbox = query
        inboxObserver = observer
        query.addValueEventListener(observer)
    }

    private fun registerDisconnect(call: Call, ready: () -> Unit) {
        call.ref.onDisconnect().removeValue().addOnCompleteListener { result ->
            if (session !== call) return@addOnCompleteListener
            if (result.isSuccessful) ready() else finishCall("Couldn't connect the call")
        }
    }

    private fun observeCall(call: Call) {
        val observer = observe { snapshot ->
            if (session !== call) return@observe
            if (!snapshot.exists()) {
                finishCall("Call ended", remove = false)
                return@observe
            }
            val state = snapshot.child("state").getValue(String::class.java)
            if (state == "declined" || state == "ended") {
                finishCall(if (state == "declined") "Call declined" else "Call ended", remove = !call.outgoing)
                return@observe
            }
            val accepted = state == "accepted"
            if (!call.outgoing && accepted && !call.claimed) {
                finishCall("Answered on another device", remove = false)
                return@observe
            }
            collectRemoteCandidates(call, snapshot)
            if (call.outgoing && accepted && !call.active) {
                if (call.timeoutBeforeAnswer) {
                    call.timeoutBeforeAnswer = false
                    timeOut(call)
                    report(call, CallState.CONNECTING, "Connecting...")
                }
                val answer = snapshot.child("answer").getValue(String::class.java)
                val media = call.media
                if (answer != null && !call.answerApplied && media != null) {
                    call.answerApplied = true
                    media.applyAnswer(answer)
                }
            }
        }
        call.observer = observer
        call.ref.addValueEventListener(observer)
    }

    private fun collectRemoteCandidates(call: Call, snapshot: DataSnapshot) {
        val branch = if (call.outgoing) "recipientIce" else "callerIce"
        for (entry in snapshot.child(branch).children) {
            val key = entry.key ?: continue
            if (key in call.remoteCandidates) continue
            val mid = entry.child("mid").getValue(String::class.java) ?: continue
            val line = entry.child("line").getValue(Int::class.java) ?: continue
            val sdp = entry.child("sdp").getValue(String::class.java) ?: continue
            call.remoteCandidates[key] = IceCandidate(mid, line, sdp)
        }
        applyRemoteCandidates(call)
    }

    private fun applyRemoteCandidates(call: Call) {
        val media = call.media ?: return
        call.remoteCandidates.forEach { (key, candidate) ->
            if (call.appliedCandidates.add(key)) media.addRemoteCandidate(candidate)
        }
    }

    private fun publishCandidate(call: Call, candidate: IceCandidate) {
        val branch = if (call.outgoing) "callerIce" else "recipientIce"
        call.ref.child(branch).push().setValue(mapOf(
            "mid" to candidate.sdpMid.orEmpty(),
            "line" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp
        )).addOnFailureListener {
            if (session === call) Log.w("BiteFlightRtc", "Couldn’t publish a network candidate")
        }
    }

    @Suppress("DEPRECATION")
    private fun prepareMedia(call: Call, start: (WebRtcAudio) -> Unit) {
        val callId = checkNotNull(call.ref.key)
        TurnServers.load(call.recipient, callId) { result ->
            if (session !== call) return@load
            val servers = result.getOrElse {
                finishCall(TurnServers.failureMessage(it))
                return@load
            }
            call.audioMode = audio.mode
            if (Build.VERSION.SDK_INT < 31) call.previousSpeaker = audio.isSpeakerphoneOn
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            call.media = WebRtcAudio(context, servers, onLocalSdp = { description ->
                call.ref.child(if (call.outgoing) "offer" else "answer").setValue(description)
                    .addOnFailureListener { if (session === call) finishCall("Couldn't connect the call") }
            }, onLocalCandidate = { candidate ->
                if (session === call) publishCandidate(call, candidate)
            }, onConnection = { state ->
                if (session !== call) return@WebRtcAudio
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        call.active = true
                        call.timeout?.let(main::removeCallbacks)
                        call.timeout = null
                        report(call, CallState.ACTIVE, "Call in progress")
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED,
                    PeerConnection.PeerConnectionState.FAILED,
                    PeerConnection.PeerConnectionState.CLOSED ->
                        finishCall("Call ended")
                    else -> Unit
                }
            }, onError = { if (session === call) finishCall("Couldn't connect the call") })
            applyRemoteCandidates(call)
            call.media?.let(start)
        }
    }

    private fun timeOut(call: Call) {
        call.timeout?.let(main::removeCallbacks)
        val timeout = Runnable {
            if (session === call && !call.active) {
                val message = if (call.media != null && !call.timeoutBeforeAnswer && (call.outgoing || call.claimed)) {
                    "The audio connection timed out."
                } else {
                    "Call timed out"
                }
                finishCall(message)
            }
        }
        call.timeout = timeout
        main.postDelayed(timeout, TIMEOUT)
    }

    @Suppress("DEPRECATION")
    private fun finishCall(message: String, remove: Boolean = true) {
        val call = session ?: return
        session = null
        call.timeout?.let(main::removeCallbacks)
        call.observer?.let(call.ref::removeEventListener)
        call.media?.close()
        call.audioMode?.let {
            if (Build.VERSION.SDK_INT >= 31) {
                if (speakerOn) audio.clearCommunicationDevice()
            } else {
                audio.isSpeakerphoneOn = call.previousSpeaker
            }
            audio.mode = it
        }
        if (remove && call.created) call.ref.removeValue()
        muted = false
        speakerOn = false
        update(CallState.READY, message, null)
    }

    private fun report(call: Call, state: CallState, message: String) = update(state, message, call.name)

    private fun observe(onData: (DataSnapshot) -> Unit) = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)
        override fun onCancelled(error: DatabaseError) = Unit
    }

    private companion object {
        const val TIMEOUT = 60_000L
    }
}
