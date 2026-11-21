package com.example.biteflight.voice

import android.app.Activity
import android.content.Intent
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Hosts a call bar in the foreground activity and keeps its position for the current call. */
class FloatingCallOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activity: Activity? = null
    private var bar: CallBar? = null
    private var position: PointF? = null
    private var connectedAt: Long? = null
    private val stateListener: (CallState, String) -> Unit = { _, _ ->
        mainHandler.post { render() }
    }

    init {
        VoipManager.listeners.add(stateListener)
    }

    fun onActivityResumed(activity: Activity) {
        onActivityPaused(this.activity)
        if (activity is VoiceCallActivity) return
        this.activity = activity
        render()
    }

    fun onActivityPaused(activity: Activity?) {
        if (this.activity !== activity) return
        removeBar()
        this.activity = null
    }

    private fun render() {
        val state = VoipManager.currentState
        val label = when (state) {
            CallState.INCOMING -> R.string.voice_floating_incoming
            CallState.DIALING -> R.string.voice_floating_dialing
            CallState.CONNECTING -> R.string.voice_floating_connecting
            CallState.ACTIVE -> R.string.voice_floating_active
            CallState.ENDING -> R.string.voice_floating_ending
            CallState.READY, CallState.UNAVAILABLE -> null
        }
        if (label == null) {
            position = null
            connectedAt = null
            removeBar()
            return
        }
        val start = VoipManager.connectedAt
        if (start != null) {
            if (connectedAt != null && connectedAt != start) position = null
            connectedAt = start
        }

        val host = activity ?: return
        val callBar = bar ?: CallBar(host).also { bar = it }
        callBar.render(state, label, start)
    }

    private fun removeBar() {
        bar?.remove()
        bar = null
    }

    private inner class CallBar(activity: Activity) {
        private val content = activity.findViewById<FrameLayout>(android.R.id.content)
        private val view = activity.layoutInflater.inflate(R.layout.view_floating_call, content, false)
        private val status = view.findViewById<TextView>(R.id.tvIslandStatus)
        private val timer = view.findViewById<Chronometer>(R.id.tvIslandTimer)
        private val call = view.findViewById<ImageButton>(R.id.btnIslandCall)
        private val endCall = view.findViewById<ImageButton>(R.id.btnIslandEnd)
        private val density = activity.resources.displayMetrics.density
        private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
        private val onContentLayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateWidth()
            place()
        }

        init {
            view.setOnClickListener { openCallScreen() }
            call.setOnClickListener {
                openCallScreen(answerIncoming = VoipManager.currentState == CallState.INCOMING)
            }
            ViewCompat.replaceAccessibilityAction(
                view, AccessibilityActionCompat.ACTION_CLICK, activity.getString(R.string.voice_floating_open)
            ) { target, _ -> target.performClick() }
            endCall.setOnClickListener { VoipManager.hangUp() }
            enableDragging()
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                updateWidth()
                place()
                insets
            }

            content.addView(view, FrameLayout.LayoutParams(
                dp(272), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT
            ))
            content.addOnLayoutChangeListener(onContentLayout)
            updateWidth()
            ViewCompat.requestApplyInsets(view)
        }

        fun render(state: CallState, label: Int, start: Long?) {
            status.setText(label)
            call.contentDescription = view.context.getString(
                if (state == CallState.INCOMING) R.string.voice_answer else R.string.voice_floating_open
            )
            endCall.isEnabled = state != CallState.ENDING
            endCall.contentDescription = view.context.getString(when (state) {
                CallState.INCOMING -> R.string.voice_decline
                CallState.DIALING -> R.string.voice_cancel
                else -> R.string.voice_end
            })
            if (state == CallState.ACTIVE && start != null) {
                if (timer.base != start) timer.base = start
                timer.visibility = View.VISIBLE
                timer.start()
            } else {
                timer.stop()
                timer.visibility = View.GONE
            }
        }

        fun remove() {
            timer.stop()
            content.removeOnLayoutChangeListener(onContentLayout)
            content.removeView(view)
        }

        private fun openCallScreen(answerIncoming: Boolean = false) {
            val context = view.context
            context.startActivity(Intent(context, VoiceCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (answerIncoming) action = VoiceCallActivity.ACTION_ANSWER_INCOMING
            })
        }

        private fun safeArea(): RectF {
            val insets = ViewCompat.getRootWindowInsets(content)?.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val location = IntArray(2)
            content.getLocationInWindow(location)
            val left = ((insets?.left ?: 0) - location[0]).coerceAtLeast(content.paddingLeft)
            val top = ((insets?.top ?: 0) - location[1]).coerceAtLeast(content.paddingTop)
            val right = (content.rootView.width - (insets?.right ?: 0) - location[0])
                .coerceAtMost(content.width - content.paddingRight)
            val bottom = (content.rootView.height - (insets?.bottom ?: 0) - location[1])
                .coerceAtMost(content.height - content.paddingBottom)
            return RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        }

        private fun updateWidth() {
            val width = safeArea().width().roundToInt()
            if (width <= 0) return
            val params = view.layoutParams
            val desiredWidth = minOf(dp(272), (width - dp(16)).coerceAtLeast(1))
            if (params.width != desiredWidth) {
                params.width = desiredWidth
                view.layoutParams = params
            }
        }

        private fun dragBounds(): RectF {
            val area = safeArea()
            val right = (area.right - view.width).coerceAtLeast(area.left)
            val bottom = (area.bottom - view.height).coerceAtLeast(area.top)
            return RectF(area.left, area.top, right, bottom)
        }

        private fun place() {
            val bounds = dragBounds()
            val saved = position
            view.x = bounds.left + bounds.width() * (saved?.x ?: 0.5f)
            view.y = if (saved == null) {
                (bounds.top + dp(12)).coerceAtMost(bounds.bottom)
            } else {
                bounds.top + bounds.height() * saved.y
            }
        }

        private fun enableDragging() {
            var downX = 0f
            var downY = 0f
            var startX = 0f
            var startY = 0f
            var dragging = false

            view.setOnTouchListener { target, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = target.x
                        startY = target.y
                        dragging = false
                        target.isPressed = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - downX
                        val deltaY = event.rawY - downY
                        if (hypot(deltaX, deltaY) > touchSlop) dragging = true
                        if (dragging) {
                            target.isPressed = false
                            val bounds = dragBounds()
                            target.x = (startX + deltaX).coerceIn(bounds.left, bounds.right)
                            target.y = (startY + deltaY).coerceIn(bounds.top, bounds.bottom)
                            position = PointF(
                                if (bounds.width() > 0) (target.x - bounds.left) / bounds.width() else position?.x ?: 0.5f,
                                if (bounds.height() > 0) (target.y - bounds.top) / bounds.height() else position?.y ?: 0.5f
                            )
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        target.isPressed = false
                        if (!dragging) target.performClick()
                    }
                    MotionEvent.ACTION_CANCEL -> target.isPressed = false
                    else -> return@setOnTouchListener false
                }
                true
            }
        }

        private fun dp(value: Int) = (value * density).roundToInt()
    }
}
