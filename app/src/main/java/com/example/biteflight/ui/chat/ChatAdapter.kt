package com.example.biteflight.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.ai.ChatMessage
import com.example.biteflight.databinding.ItemMessageBinding

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {

    private var items: List<ChatMessage> = emptyList()

    fun submit(list: List<ChatMessage>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val message = items[position]
        val isUser = message.role == ChatMessage.USER
        val tv = holder.binding.tvText

        tv.text = message.content.ifEmpty { "…" }
        tv.setBackgroundResource(
            if (isUser) R.drawable.bg_bubble_user else R.drawable.bg_bubble_assistant
        )
        tv.setTextColor(
            ContextCompat.getColor(tv.context, if (isUser) R.color.white else R.color.text_primary)
        )

        val params = tv.layoutParams as FrameLayout.LayoutParams
        params.gravity = if (isUser) Gravity.END else Gravity.START
        tv.layoutParams = params
    }
}