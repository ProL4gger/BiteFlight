package com.example.biteflight.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * The only thing the rest of the app knows about the language model.
 * Swap the implementation (Ollama, your own server, on-device) without touching the UI.
 */
interface LlmClient {
    fun streamChat(messages: List<ChatMessage>): Flow<String>
}