package com.example.biteflight.voice

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

internal object TurnServers {
    private class RelayException(val messageForUser: String) : Exception()
    private val stun = PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()

    fun load(recipient: String, callId: String, done: (Result<List<PeerConnection.IceServer>>) -> Unit) {
        if (BuildConfig.TURN_ENDPOINT.isEmpty()) {
            done(Result.success(listOf(stun)))
            return
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            done(Result.failure(RelayException("Sign in again to make a call.")))
            return
        }
        user.getIdToken(false).addOnCompleteListener { result ->
            val token = if (result.isSuccessful) result.result.token else null
            if (token == null) {
                done(Result.failure(RelayException("Sign in again to make a call.")))
                return@addOnCompleteListener
            }
            thread(name = "BiteFlight TURN configuration") {
                val result = runCatching { request(token, recipient, callId) }
                Handler(Looper.getMainLooper()).post { done(result) }
            }
        }
    }

    fun failureMessage(error: Throwable): String =
        (error as? RelayException)?.messageForUser ?: "The voice relay could not be reached. Try again."

    private fun request(token: String, recipient: String, callId: String): List<PeerConnection.IceServer> {
        val connection = URL(BuildConfig.TURN_ENDPOINT).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 6000
            connection.readTimeout = 26000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(JSONObject().put("recipient", recipient).put("callId", callId).toString().toByteArray())
            }
            val code = connection.responseCode
            if (code != 200) {
                val reason = connection.getHeaderField("X-BiteFlight-Relay-Error")
                Log.w("BiteFlightTurn", "Relay request returned HTTP $code")
                throw RelayException(when (reason) {
                    "configuration" -> "The voice relay setup is incomplete."
                    "firebase" -> "The voice relay could not reach Firebase. Try again."
                    "call-access" -> "Firebase did not authorize this call."
                    "metered" -> "Metered did not provide TURN credentials."
                    else -> "The voice relay is unavailable (HTTP $code)."
                })
            }
            val payload = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val servers = payload.getJSONArray("iceServers")
            return buildList {
                add(stun)
                for (index in 0 until servers.length()) {
                    val server = servers.getJSONObject(index)
                    val urls = when (val entry = server.get("urls")) {
                        is JSONArray -> List(entry.length()) { entry.getString(it) }
                        is String -> listOf(entry)
                        else -> emptyList()
                    }.filter { it.startsWith("turn:") || it.startsWith("turns:") }
                    if (urls.isNotEmpty()) add(PeerConnection.IceServer.builder(urls)
                        .setUsername(server.getString("username"))
                        .setPassword(server.getString("credential"))
                        .createIceServer())
                }
            }.also { check(it.size > 1) }
        } finally {
            connection.disconnect()
        }
    }
}
