package com.example.biteflight.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to an Ollama server. 10.0.2.2 is the emulator's alias for the laptop's localhost.
 * Ollama streams one JSON object per line; each line carries a piece of the reply.
 */
class OllamaClient(
    private val baseUrl: String = "http://10.0.2.2:11434",
    private val model: String = "qwen2.5:1.5b",
    private val http: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) : LlmClient {

    override fun streamChat(messages: List<ChatMessage>): Flow<String> = flow {
        val msgArray = JSONArray()
        messages.forEach {
            msgArray.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        val payload = JSONObject()
            .put("model", model)
            .put("messages", msgArray)
            .put("stream", true)

        val request = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("LLM server error ${response.code}")
            val source = (response.body ?: throw IOException("Empty response")).source()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val obj = JSONObject(line)
                val token = obj.optJSONObject("message")?.optString("content").orEmpty()
                if (token.isNotEmpty()) emit(token)
                if (obj.optBoolean("done")) break
            }
        }
    }.flowOn(Dispatchers.IO)
}