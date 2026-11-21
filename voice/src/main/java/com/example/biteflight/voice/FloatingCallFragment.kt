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
    }

    override fun onDestroyView() {
        handler.removeCallbacks(timerRunnable)
        super.onDestroyView()
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
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val rootView = content.getChildAt(0) as? ViewGroup ?: content

            val containerId = 777777
            var container = rootView.findViewById<FrameLayout>(containerId)
            val density = activity.resources.displayMetrics.density
            val height = (56 * density).toInt()
            val marginB = (90 * density).toInt()

            if (container == null) {
                container = FrameLayout(activity).apply { id = containerId }
                rootView.addView(container, FrameLayout.LayoutParams(-2, height, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    setMargins(0, 0, 0, marginB)
                })
            }
            container.bringToFront()

            val shouldShow = VoipManager.currentState != CallState.READY && VoipManager.currentState != CallState.UNAVAILABLE
            val existingFrag = activity.supportFragmentManager.findFragmentByTag("voip_island_fragment") as? FloatingCallFragment

            if (shouldShow) {
                if (existingFrag == null) {
                    activity.supportFragmentManager.beginTransaction()
                        .replace(containerId, FloatingCallFragment(), "voip_island_fragment")
                        .commitAllowingStateLoss()
                }
            } else {
                if (existingFrag != null) {
                    activity.supportFragmentManager.beginTransaction()
                        .remove(existingFrag)
                        .commitAllowingStateLoss()
                }
            }

            // Register state listener for this activity
            val listener: (CallState, String) -> Unit = { state, message ->
                activity.runOnUiThread {
                    val f = activity.supportFragmentManager.findFragmentByTag("voip_island_fragment") as? FloatingCallFragment
                    if (state == CallState.READY || state == CallState.UNAVAILABLE) {
                        if (f != null) {
                            activity.supportFragmentManager.beginTransaction().remove(f).commitAllowingStateLoss()
                        }
                    } else {
                        if (f == null) {
                            val frag = FloatingCallFragment()
                            activity.supportFragmentManager.beginTransaction()
                                .replace(containerId, frag, "voip_island_fragment")
                                .commitAllowingStateLoss()
                        } else {
                            f.updateState(state, message)
                        }
                    }
                }
            }

            // Avoid duplicate listeners
            VoipManager.listeners.remove(listener)
            VoipManager.listeners.add(listener)
        }
    }
}
