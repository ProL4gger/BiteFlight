package com.example.biteflight.voice

import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

enum class CallState { READY, DIALING, INCOMING, CONNECTING, ACTIVE, ENDING, UNAVAILABLE }

/** One direct IPv4 SIP dialog over UDP. All signaling state is owned by the executor. */
internal class SipCall(
    private val listenPort: Int,
    private val audioManager: AudioManager,
    private val update: (CallState, String) -> Unit
) : AutoCloseable {
    private class Dialog(
        val peer: InetSocketAddress,
        val local: InetSocketAddress,
        val invite: SipMessage,
        val media: RtpAudio,
        val incoming: Boolean,
        val tag: String = sipToken()
    ) {
        var remoteMedia = 0
        var remoteParty = if (incoming) invite["from"] else invite["to"]
        var reply: SipMessage? = null
        var ack: SipMessage? = null
        var audioMode: Int? = null
        val callId get() = invite["call-id"]

        fun request(method: String, sequence: Int, useInviteBranch: Boolean = false): SipMessage =
            SipMessage.request(
                method, local, peer, callId,
                if (incoming) "${invite["to"]};tag=$tag" else invite["from"],
                if (method == "CANCEL") invite["to"] else remoteParty,
                sequence,
                if (useInviteBranch) invite["via"].substringAfter("branch=z9hG4bK").substringBefore(';')
                else sipToken()
            )
    }

    private data class Transaction(
        val peer: InetSocketAddress,
        val message: SipMessage,
        var retryAt: Long,
        var interval: Long,
        val expiresAt: Long
    )

    private data class RequestKey(val peer: InetSocketAddress, val callId: String, val sequence: String)
    private data class CachedReply(val message: SipMessage, val expiresAt: Long)
    private val replies = mutableMapOf<RequestKey, CachedReply>()
    private val executor = ScheduledThreadPoolExecutor(1).apply {
        setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
    }
    private var socket: DatagramSocket? = null
    private var dialog: Dialog? = null
    private var transaction: Transaction? = null
    private var state = CallState.UNAVAILABLE

    init {
        submit { openListener(retries = 4) }
        executor.scheduleWithFixedDelay({
            safely { tick() }
        }, 250, 250, TimeUnit.MILLISECONDS)
    }

    private fun openListener(retries: Int) {
        val listener = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(listenPort))
            }
        } catch (error: BindException) {
            if (retries == 0) throw error
            synchronized(executor) {
                if (!executor.isShutdown) {
                    executor.schedule({ safely { openListener(retries - 1) } }, 150, TimeUnit.MILLISECONDS)
                }
            }
            return
        }
        socket = listener
        thread(name = "BiteFlight SIP receive") { receive(listener) }
        report(CallState.READY, "Ready for calls")
    }

    fun dial(peer: InetSocketAddress) = submit {
        if (state != CallState.READY) return@submit
        val local = localEndpoint(peer)
        val media = RtpAudio()
        val invite = SipMessage.request(
            "INVITE", local, peer, sipToken(),
            "<sip:biteflight@${local.authority()}>;tag=${sipToken()}",
            "<sip:biteflight@${peer.authority()}>", 1,
            sdp = SipMessage.sdp(local, media.port)
        )
        dialog = Dialog(peer, local, invite, media, incoming = false)
        report(CallState.DIALING, "Calling ${peer.authority()}…")
        repeatUntilAnswered(peer, invite, 32_000)
    }

    fun answer() = submit {
        val call = dialog ?: return@submit
        if (state != CallState.INCOMING) return@submit
        val ok = respondToInvite(call, "200 OK", SipMessage.sdp(call.local, call.media.port))
        report(CallState.CONNECTING, "Connecting…")
        repeatUntilAnswered(call.peer, ok, 32_000, sendNow = true)
    }

    fun hangUp() = submit { endCall() }

    private fun endCall() {
        val call = dialog ?: return
        when (state) {
            CallState.INCOMING -> {
                respondToInvite(call, "603 Decline")
                reset("Call declined")
            }
            CallState.DIALING -> {
                report(CallState.ENDING, "Cancelling call…")
                repeatUntilAnswered(call.peer, call.request("CANCEL", 1, useInviteBranch = true), 5_000)
            }
            CallState.CONNECTING, CallState.ACTIVE -> sendBye(call)
            else -> Unit
        }
    }

    private fun sendBye(call: Dialog) {
        closeMedia(call)
        report(CallState.ENDING, "Ending call…")
        repeatUntilAnswered(call.peer, call.request("BYE", 2), 5_000)
    }

    private fun closeMedia(call: Dialog) {
        call.media.close()
        call.audioMode?.let { audioManager.mode = it }
        call.audioMode = null
    }

    private fun receive(listener: DatagramSocket) {
        val data = ByteArray(4096)
        val packet = DatagramPacket(data, data.size)
        while (!listener.isClosed) {
            try {
                packet.length = data.size
                listener.receive(packet)
                val peer = packet.socketAddress as InetSocketAddress
                val message = SipMessage.parse(String(data, 0, packet.length, Charsets.UTF_8)) ?: continue
                submit {
                    if (message.status == null) receiveRequest(peer, message) else receiveResponse(peer, message)
                }
            } catch (error: Exception) {
                if (!listener.isClosed) submit { throw error }
                break
            }
        }
    }

    private fun receiveRequest(peer: InetSocketAddress, message: SipMessage) {
        if (!message.start.endsWith(" SIP/2.0") || !message["cseq"].endsWith(" ${message.method}")) return
        replies[key(peer, message)]?.let { send(peer, it.message); return }
        val call = dialog?.takeIf { it.peer == peer && it.callId == message["call-id"] }
        when (message.method) {
            "INVITE" -> receiveInvite(peer, message)
            "ACK" -> if (call?.incoming == true && state == CallState.CONNECTING &&
                message["cseq"] == "${call.invite["cseq"].substringBefore(' ')} ACK" &&
                message["to"] == call.reply?.get("to")
            ) {
                startMedia(call)
            }
            "CANCEL" -> if (call?.incoming == true && message["via"] == call.invite["via"]) {
                respond(peer, message, if (state == CallState.INCOMING) "200 OK" else "481 Call Does Not Exist", call.tag, call.local)
                if (state == CallState.INCOMING) {
                    respondToInvite(call, "487 Request Terminated")
                    reset("Caller cancelled")
                }
            }
            "BYE" -> if (call != null && state in setOf(CallState.ACTIVE, CallState.CONNECTING, CallState.ENDING)) {
                respond(peer, message, "200 OK", call.tag, call.local)
                reset("Call ended")
            }
        }
    }

    private fun receiveInvite(peer: InetSocketAddress, invite: SipMessage) {
        val call = dialog
        if (call?.incoming == true && call.peer == peer && call.callId == invite["call-id"]) {
            call.reply?.let { send(peer, it) }
            return
        }
        val local = localEndpoint(peer)
        if (call != null) {
            respond(peer, invite, "486 Busy Here", sipToken(), local)
            return
        }
        val mediaPort = invite.mediaPort(peer)
        if (mediaPort == null) {
            respond(peer, invite, "488 Not Acceptable Here", sipToken(), local)
            return
        }
        val incoming = Dialog(peer, local, invite, RtpAudio(), incoming = true)
        incoming.remoteMedia = mediaPort
        dialog = incoming
        respondToInvite(incoming, "180 Ringing")
        report(CallState.INCOMING, "Incoming call from ${peer.authority()}")
        transaction = Transaction(peer, incoming.reply!!, Long.MAX_VALUE, 0, SystemClock.elapsedRealtime() + 32_000)
    }

    private fun receiveResponse(peer: InetSocketAddress, message: SipMessage) {
        val call = dialog ?: return
        if (call.peer != peer || message["call-id"] != call.callId) return
        val status = message.status ?: return
        if (message["cseq"] == "2 BYE" && status >= 200 && state == CallState.ENDING) {
            reset("Call ended")
            return
        }
        if (call.incoming || message["cseq"] != call.invite["cseq"]) return
        if (state in setOf(CallState.ACTIVE, CallState.ENDING) && status in 200..299 && call.ack != null) {
            send(peer, call.ack!!)
            return
        }
        if (state != CallState.DIALING && state != CallState.ENDING) return
        when (status) {
            in 100..199 -> if (state == CallState.DIALING) {
                transaction?.retryAt = Long.MAX_VALUE
                if (status == 180) report(CallState.DIALING, "Ringing ${peer.authority()}…")
            }
            in 200..299 -> {
                call.remoteParty = message["to"]
                val ack = call.request("ACK", 1)
                call.ack = ack
                send(peer, ack)
                val mediaPort = message.mediaPort(peer)
                if (state == CallState.ENDING || mediaPort == null) {
                    sendBye(call)
                } else {
                    call.remoteMedia = mediaPort
                    startMedia(call)
                }
            }
            in 300..699 -> {
                call.remoteParty = message["to"]
                send(peer, call.request("ACK", 1, useInviteBranch = true))
                reset(when (status) {
                    486 -> "The other device is busy"
                    487 -> "Call cancelled"
                    603 -> "Call declined"
                    else -> "Call could not connect (SIP $status)"
                })
            }
        }
    }

    private fun startMedia(call: Dialog) {
        transaction = null
        call.audioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        report(CallState.ACTIVE, "In call with ${call.peer.authority()}")
        try {
            call.media.start(InetSocketAddress(call.peer.address, call.remoteMedia)) {
                submit {
                    if (dialog === call && state == CallState.ACTIVE) sendBye(call)
                }
            }
        } catch (error: Exception) {
            Log.w("SipCall", "Could not start RTP audio", error)
            sendBye(call)
        }
    }

    private fun respondToInvite(call: Dialog, code: String, sdp: String = ""): SipMessage {
        return respond(call.peer, call.invite, code, call.tag, call.local, sdp).also { call.reply = it }
    }

    private fun respond(
        peer: InetSocketAddress,
        request: SipMessage,
        code: String,
        tag: String,
        local: InetSocketAddress,
        sdp: String = ""
    ): SipMessage {
        val reply = request.response(code, tag, local, sdp)
        if (code.first() >= '2') {
            replies[key(peer, request)] = CachedReply(reply, SystemClock.elapsedRealtime() + 32_000)
        }
        send(peer, reply)
        return reply
    }

    private fun key(peer: InetSocketAddress, request: SipMessage) =
        RequestKey(peer, request["call-id"], request["cseq"])

    private fun localEndpoint(peer: InetSocketAddress): InetSocketAddress = DatagramSocket().use { route ->
        route.connect(peer)
        InetSocketAddress(route.localAddress, listenPort)
    }

    private fun send(peer: InetSocketAddress, message: SipMessage) {
        val bytes = message.bytes()
        checkNotNull(socket).send(DatagramPacket(bytes, bytes.size, peer))
    }

    private fun repeatUntilAnswered(peer: InetSocketAddress, message: SipMessage, timeout: Long, sendNow: Boolean = true) {
        val now = SystemClock.elapsedRealtime()
        transaction = Transaction(peer, message, now + 500, 500, now + timeout)
        if (sendNow) send(peer, message)
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        replies.entries.removeAll { it.value.expiresAt <= now }
        val pending = transaction ?: return
        if (now >= pending.expiresAt) {
            if (state == CallState.INCOMING) dialog?.let { respondToInvite(it, "480 Temporarily Unavailable") }
            reset(if (state == CallState.ENDING) "Call ended" else "Call timed out")
        } else if (now >= pending.retryAt) {
            send(pending.peer, pending.message)
            pending.interval = (pending.interval * 2).coerceAtMost(4_000)
            pending.retryAt = now + pending.interval
        }
    }

    private fun reset(message: String) {
        dialog?.let(::closeMedia)
        dialog = null
        transaction = null
        report(if (socket?.isClosed == false) CallState.READY else CallState.UNAVAILABLE, message)
    }

    private fun report(next: CallState, message: String) {
        state = next
        update(next, message)
    }

    private fun safely(action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            Log.w("SipCall", "Voice call operation failed", error)
            reset("Could not use the voice connection. Check the network and local port.")
        }
    }

    private fun submit(action: () -> Unit) {
        synchronized(executor) {
            if (!executor.isShutdown) executor.execute { safely(action) }
        }
    }

    override fun close() {
        synchronized(executor) {
            if (executor.isShutdown) return
            executor.execute {
                try {
                    endCall()
                } finally {
                    socket?.close()
                    reset("Voice screen closed")
                }
            }
            executor.shutdown()
        }
    }
}
