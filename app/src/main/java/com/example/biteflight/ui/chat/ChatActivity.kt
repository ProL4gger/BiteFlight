package com.example.biteflight.ui.chat

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.biteflight.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val adapter = ChatAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep content clear of the status bar, nav bar and keyboard
        ViewCompat.setOnApplyWindowInsetsListener(binding.chatRoot) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = adapter

        binding.btnSend.setOnClickListener {
            viewModel.send(binding.etMessage.text.toString())
            binding.etMessage.text.clear()
        }

        viewModel.messages.observe(this) { list ->
            adapter.submit(list)
            binding.rvMessages.scrollToPosition((list.size - 1).coerceAtLeast(0))
        }
        viewModel.busy.observe(this) { busy ->
            binding.btnSend.isEnabled = !busy
        }
    }
}