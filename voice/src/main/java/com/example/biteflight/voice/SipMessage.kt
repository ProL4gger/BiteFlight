package com.example.biteflight.voice

import java.net.InetSocketAddress
import java.util.Locale
import java.util.UUID

internal fun sipToken() = UUID.randomUUID().toString()

internal fun InetSocketAddress.authority() = "${address.hostAddress}:$port"

internal data class SipMessage(
    val start: String,
    private val headers: Map<String, String>,
    val body: String = ""
) {
    operator fun get(header: String): String = headers[header.lowercase(Locale.ROOT)].orEmpty()

    val status: Int? get() = if (start.startsWith("SIP/2.0 ")) start.split(' ')[1].toIntOrNull() else null
    val method: String get() = start.substringBefore(' ')

    fun bytes(): ByteArray = buildString {
        append(start).append("\r\n")
        for ((name, value) in headers) append(name).append(": ").append(value).append("\r\n")
        if (body.isNotEmpty()) append("Content-Type: application/sdp\r\n")
        append("Content-Length: ").append(body.toByteArray().size).append("\r\n\r\n").append(body)
    }.toByteArray()

    fun response(code: String, localTag: String, local: InetSocketAddress, sdp: String = ""): SipMessage {
        val to = this["to"]
        return SipMessage(
            "SIP/2.0 $code",
            mapOf(
                "via" to this["via"], "from" to this["from"],
                "to" to if (";tag=" in to || code.startsWith("100")) to else "$to;tag=$localTag",
                "call-id" to this["call-id"], "cseq" to this["cseq"],
                "contact" to "<sip:biteflight@${local.authority()}>"
            ),
            sdp
        )
    }

    fun mediaPort(peer: InetSocketAddress): Int? {
        if (!this["content-type"].equals("application/sdp", ignoreCase = true)) return null
        val lines = body.lineSequence().map(String::trim).toList()
        // The LAN demo accepts media only from the same IP as its signaling peer.
        if (lines.filter { it.startsWith("c=") } != listOf("c=IN IP4 ${peer.address.hostAddress}")) return null
        val audio = lines.firstOrNull { it.startsWith("m=audio ") }?.split(' ') ?: return null
        if (audio.getOrNull(2) != "RTP/AVP" || "0" !in audio.drop(3)) return null
        return audio.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..65535 }
    }

    companion object {
        fun parse(text: String): SipMessage? {
            val parts = text.split("\r\n\r\n", limit = 2)
            if (parts.size != 2) return null
            val lines = parts[0].split("\r\n")
            val headers = lines.drop(1).mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator < 1) null
                else line.substring(0, separator).lowercase(Locale.ROOT) to line.substring(separator + 1).trim()
            }.toMap()
            if (listOf("via", "from", "to", "call-id", "cseq").any { headers[it].isNullOrBlank() }) return null
            val length = headers["content-length"]?.toIntOrNull() ?: return null
            if (length != parts[1].toByteArray().size) return null
            return SipMessage(lines[0], headers, parts[1])
        }

        fun request(
            method: String,
            local: InetSocketAddress,
            peer: InetSocketAddress,
            callId: String,
            from: String,
            to: String,
            sequence: Int,
            branch: String = sipToken(),
            sdp: String = ""
        ) = SipMessage(
            "$method sip:biteflight@${peer.authority()} SIP/2.0",
            mapOf(
                "via" to "SIP/2.0/UDP ${local.authority()};branch=z9hG4bK$branch;rport",
                "max-forwards" to "70", "from" to from, "to" to to,
                "call-id" to callId, "cseq" to "$sequence $method",
                "contact" to "<sip:biteflight@${local.authority()}>"
            ),
            sdp
        )

        fun sdp(local: InetSocketAddress, mediaPort: Int): String {
            val ip = local.address.hostAddress
            val session = System.currentTimeMillis()
            return "v=0\r\no=- $session $session IN IP4 $ip\r\ns=BiteFlight voice\r\n" +
                "c=IN IP4 $ip\r\nt=0 0\r\nm=audio $mediaPort RTP/AVP 0\r\n" +
                "a=rtpmap:0 PCMU/8000\r\na=ptime:20\r\na=sendrecv\r\n"
        }
    }
}
