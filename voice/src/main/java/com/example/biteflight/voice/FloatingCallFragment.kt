package com.example.biteflight.voice

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import java.util.Locale

class FloatingCallFragment : Fragment() {
    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnEndCall: ImageButton

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
                if (::tvTimer.isInitialized) {
                    tvTimer.text = String.format(Locale.US, "%02d:%02d", mins, secs)
                    tvTimer.visibility = View.VISIBLE
                }
            } else {
                if (::tvTimer.isInitialized) {
                    tvTimer.visibility = View.GONE
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_floating_call, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStatus = view.findViewById(R.id.tvIslandStatus)
        tvTimer = view.findViewById(R.id.tvIslandTimer)
        btnEndCall = view.findViewById(R.id.btnIslandEnd)

        btnEndCall.setOnClickListener {
            VoipManager.hangUp()
        }

        view.setOnClickListener {
            val intent = Intent(requireContext(), VoiceCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }

        view.setOnTouchListener { v, event ->
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

        VoipManager.listeners.add(::onVoipStateChanged)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(timerRunnable)
        VoipManager.listeners.remove(::onVoipStateChanged)
        super.onDestroyView()
    }

    private fun onVoipStateChanged(state: CallState, message: String) {
        activity?.runOnUiThread {
            if (state == CallState.READY || state == CallState.UNAVAILABLE) {
                parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
            } else {
                updateState(state, message)
            }
        }
    }

    fun updateState(state: CallState, message: String) {
        if (::tvStatus.isInitialized) {
            tvStatus.text = message
            if (state == CallState.ACTIVE && startTime == 0L) {
                startTime = System.currentTimeMillis()
            } else if (state == CallState.READY || state == CallState.UNAVAILABLE) {
                startTime = 0L
            }
        }
    }

    companion object {
        fun attach(activity: AppCompatActivity) {
            if (VoipManager.currentState == CallState.READY || VoipManager.currentState == CallState.UNAVAILABLE) {
                return
            }
            val existing = activity.supportFragmentManager.findFragmentByTag("voip_island_fragment")
            if (existing != null) return

            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            var containerId = activity.resources.getIdentifier("voip_fragment_container", "id", activity.packageName)
            if (containerId == 0) {
                containerId = 999999
                val container = FrameLayout(activity).apply { id = containerId }
                val density = activity.resources.displayMetrics.density
                val height = (60 * density).toInt()
                val marginB = (80 * density).toInt()
                val params = FrameLayout.LayoutParams(-1, height).apply {
                    gravity = Gravity.BOTTOM
                    setMargins(0, 0, 0, marginB)
                }
                content.addView(container, params)
            }

            activity.supportFragmentManager.beginTransaction()
                .add(containerId, FloatingCallFragment(), "voip_island_fragment")
                .commitAllowingStateLoss()
        }
    }
}
