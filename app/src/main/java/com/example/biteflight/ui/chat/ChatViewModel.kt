package com.example.biteflight.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.biteflight.data.ai.ChatMessage
import com.example.biteflight.data.ai.LlmClient
import com.example.biteflight.data.ai.OllamaClient
import com.example.biteflight.data.model.Product
import com.example.biteflight.data.repository.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ChatViewModel(
    private val llm: LlmClient = OllamaClient(),
    private val products: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy

    private val history = mutableListOf(
        ChatMessage(
            ChatMessage.ASSISTANT,
            "Hi! I can help you find food, suggest meals, or check what's available. What are you in the mood for?"
        )
    )

    init {
        _messages.value = history.toList()
    }

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || _busy.value == true) return

        _busy.value = true
        history += ChatMessage(ChatMessage.USER, question)
        _messages.value = history + ChatMessage(ChatMessage.ASSISTANT, "")

        products.fetchAll { all ->
            val prompt = listOf(ChatMessage(ChatMessage.SYSTEM, systemPrompt(retrieve(question, all)))) +
                    history.takeLast(8)

            viewModelScope.launch {
                val reply = StringBuilder()
                try {
                    llm.streamChat(prompt).collect { token ->
                        reply.append(token)
                        _messages.value = history + ChatMessage(ChatMessage.ASSISTANT, reply.toString())
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (reply.isEmpty()) {
                        reply.append("Sorry, the assistant isn't available right now.")
                    }
                }
                history += ChatMessage(ChatMessage.ASSISTANT, reply.toString())
                _messages.value = history.toList()
                _busy.value = false
            }
        }
    }

    /** Picks the products most related to the question; falls back to a general in-stock menu. */
    private fun retrieve(query: String, all: List<Product>): List<Product> {
        val inStock = all.filter { it.quantity > 0 }
        val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
        val matches = inStock
            .map { product -> product to words.count { w -> product.name.lowercase().contains(w) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
        return matches.ifEmpty { inStock }.take(10)
    }

    private fun systemPrompt(context: List<Product>): String = buildString {
        append("You are the shopping assistant of BiteFlight, a food and grocery delivery app. ")
        append("Be friendly and concise: 1 to 3 sentences. ")
        append("Only recommend products from the list below. ")
        append("If nothing fits, say so honestly. Never invent products or prices.\n")
        append("Available products:\n")
        if (context.isEmpty()) {
            append("(none)\n")
        } else {
            context.forEach {
                append("- ${it.name}, ${it.price} EGP, ${it.quantity} in stock, shop: ${it.shopName}\n")
            }
        }
    }
}