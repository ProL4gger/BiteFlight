package com.example.biteflight.voice

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class FloatingCallOverlay(context: Context) : LinearLayout(context) {
    private val tvStatus = TextView(context)
    private val tvTimer = TextView(context)
    private val btnEndCall = ImageButton(context)

    private var dX = 0f
    private var dY = 0f
    private var startTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (startTime > 0 && VoipManager.currentState == CallState.ACTIVE) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val mins = elapsed / 60
                val secs = elapsed % 60
                tvTimer.text = String.format(Locale.US, "%02d:%02d", mins, secs)
                tvTimer.visibility = VISIBLE
            } else {
                tvTimer.visibility = GONE
            }
            handler.postDelayed(this, 1000)
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(8), dp(14), dp(8))

        val shape = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            cornerRadius = dp(28).toFloat()
            setColor(Color.parseColor("#38235D"))
        }
        background = shape
        elevation = dp(16).toFloat()

        tvStatus.apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            text = "Call"
        }
        addView(tvStatus)

        tvTimer.apply {
            textSize = 13f
            setTextColor(Color.parseColor("#AE89DD"))
            setPadding(dp(8), 0, dp(8), 0)
            visibility = GONE
        }
        addView(tvTimer)

        btnEndCall.apply {
            setImageResource(R.drawable.ic_end_call)
            background = GradientDrawable().apply {
                setShape(GradientDrawable.OVAL)
                setColor(Color.parseColor("#B00020"))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                VoipManager.hangUp()
            }
        }
        val iconSize = dp(32)
        addView(btnEndCall, LayoutParams(iconSize, iconSize).apply { setMargins(dp(8), 0, 0, 0) })

        setOnClickListener {
            val intent = Intent(context, VoiceCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }

        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate()
                        .x(event.rawX + dX)
                        .y(event.rawY + dY)
                        .setDuration(0)
                        .start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.performClick()
                    true
                }
                else -> false
            }
        }

        startTime = System.currentTimeMillis()
        handler.post(timerRunnable)
        updateState(VoipManager.currentState, VoipManager.currentMessage)
    }

    fun updateState(state: CallState, message: String) {
        tvStatus.text = message
        if (state == CallState.ACTIVE && startTime == 0L) {
            startTime = System.currentTimeMillis()
        } else if (state == CallState.READY || state == CallState.UNAVAILABLE) {
            startTime = 0L
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(timerRunnable)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun attach(activity: AppCompatActivity) {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            if (content.findViewWithTag<View>("voip_island") != null) return

            val listener: (CallState, String) -> Unit = { state, message ->
                activity.runOnUiThread {
                    val existing = content.findViewWithTag<View>("voip_island")
                    if (state == CallState.READY || state == CallState.UNAVAILABLE) {
                        if (existing != null) content.removeView(existing)
                    } else {
                        if (existing == null) {
                            val island = FloatingCallOverlay(activity).apply { tag = "voip_island" }
                            val density = activity.resources.displayMetrics.density
                            val height = (48 * density).toInt()
                            val marginB = (90 * density).toInt()

                            val params = FrameLayout.LayoutParams(-2, height).apply {
                                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                setMargins(0, 0, 0, marginB)
                            }
                            content.addView(island, params)
                        } else {
                            (existing as? FloatingCallOverlay)?.updateState(state, message)
                        }
                    }
                }
            }

            VoipManager.listeners.add(listener)

            if (VoipManager.currentState != CallState.READY && VoipManager.currentState != CallState.UNAVAILABLE) {
                listener(VoipManager.currentState, VoipManager.currentMessage)
            }
        }
    }
}
