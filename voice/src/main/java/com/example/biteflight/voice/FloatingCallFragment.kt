package com.example.biteflight.voice

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.fragment.app.Fragment
import kotlin.math.hypot
import kotlin.math.roundToInt

class FloatingCallFragment : Fragment() {
    private val stateListener: (CallState, String) -> Unit = { _, _ ->
        activity?.runOnUiThread {
            if (isStarted) view?.let(::render)
        }
    }
    private var isStarted = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_floating_call, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ImageButton>(R.id.btnIslandEnd).setOnClickListener {
            VoipManager.hangUp()
        }

        ViewCompat.replaceAccessibilityAction(
            view, AccessibilityActionCompat.ACTION_CLICK, getString(R.string.voice_floating_open)
        ) { target, _ -> target.performClick() }
        view.setOnClickListener {
            startActivity(Intent(requireContext(), VoiceCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }

        val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false

        view.setOnTouchListener { target, event ->
            val cardContainer = target.parent as? View ?: return@setOnTouchListener false
            val content = cardContainer.parent as? View ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = cardContainer.x
                    startY = cardContainer.y
                    dragging = false
                    target.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (hypot(deltaX, deltaY) > touchSlop) dragging = true
                    if (dragging) {
                        target.isPressed = false
                        val insets = ViewCompat.getRootWindowInsets(content)?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                        )
                        val minX = (insets?.left ?: 0).toFloat()
                        val minY = (insets?.top ?: 0).toFloat()
                        val maxX = (content.width - cardContainer.width - (insets?.right ?: 0)).toFloat()
                        val maxY = (content.height - cardContainer.height - (insets?.bottom ?: 0)).toFloat()
                        cardContainer.x = (startX + deltaX).coerceIn(minX, maxX.coerceAtLeast(minX))
                        cardContainer.y = (startY + deltaY).coerceIn(minY, maxY.coerceAtLeast(minY))
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    target.isPressed = false
                    if (!dragging) target.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    target.isPressed = false
                    true
                }
                else -> true
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isStarted = true
        VoipManager.listeners.add(stateListener)
        view?.let(::render)
    }

    override fun onStop() {
        isStarted = false
        VoipManager.listeners.remove(stateListener)
        view?.findViewById<Chronometer>(R.id.tvIslandTimer)?.stop()
        super.onStop()
    }

    private fun render(view: View) {
        val state = VoipManager.currentState
        val label = when (state) {
            CallState.INCOMING -> R.string.voice_floating_incoming
            CallState.DIALING -> R.string.voice_floating_dialing
            CallState.CONNECTING -> R.string.voice_floating_connecting
            CallState.ACTIVE -> R.string.voice_floating_active
            CallState.ENDING -> R.string.voice_floating_ending
            CallState.READY, CallState.UNAVAILABLE -> null
        }
        view.visibility = if (label == null) View.GONE else View.VISIBLE
        val timer = view.findViewById<Chronometer>(R.id.tvIslandTimer)
        val connectedAt = VoipManager.connectedAt
        if (state == CallState.ACTIVE && connectedAt != null) {
            timer.base = connectedAt
            timer.visibility = View.VISIBLE
            timer.start()
        } else {
            timer.stop()
            timer.visibility = View.GONE
        }
        if (label == null) {
            (view.parent as? View)?.apply {
                translationX = 0f
                translationY = 0f
            }
            return
        }
        view.findViewById<TextView>(R.id.tvIslandStatus).setText(label)
        view.findViewById<ImageButton>(R.id.btnIslandEnd).apply {
            isEnabled = state != CallState.ENDING
            contentDescription = context.getString(when (state) {
                CallState.INCOMING -> R.string.voice_decline
                CallState.DIALING -> R.string.voice_cancel
                else -> R.string.voice_end
            })
        }
    }

    companion object {
        fun attach(activity: AppCompatActivity) {
            if (activity is VoiceCallActivity || activity.supportFragmentManager.isStateSaved) return
            val content = activity.findViewById<FrameLayout>(android.R.id.content)
            val containerId = R.id.floating_call_container
            if (content.findViewById<View>(containerId) != null) return

            val density = activity.resources.displayMetrics.density
            val widthDp = (activity.resources.configuration.screenWidthDp - 32).coerceIn(1, 304)
            val container = activity.layoutInflater.inflate(R.layout.view_floating_call_container, content, false)
                as FrameLayout
            container.apply {
                clipChildren = false
                elevation = 8 * density
            }
            content.addView(container, FrameLayout.LayoutParams(
                (widthDp * density).roundToInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = (90 * density).roundToInt()
            })

            val fragments = activity.supportFragmentManager
            if (fragments.findFragmentById(containerId) == null) {
                fragments.beginTransaction()
                    .add(containerId, FloatingCallFragment())
                    .commitNow()
            }
        }
    }
}
