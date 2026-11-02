package com.example.biteflight.ui

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import kotlin.math.abs

/** Adds horizontal page gestures without changing the navigation bar or vertical scrolling. */
class SwipeNavigationLayout(context: Context) : LinearLayout(context) {
    private var page: View? = null
    private var onSwipe: ((next: Boolean) -> Unit)? = null
    private var startX = 0f
    private var startY = 0f
    private var eligible = false
    private var horizontal = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val distance = 64f * resources.displayMetrics.density
    private val edgeMargin = 24f * resources.displayMetrics.density

    fun enablePageSwipes(page: View, onSwipe: (next: Boolean) -> Unit) {
        this.page = page
        this.onSwipe = onSwipe
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                horizontal = false
                val content = page
                val bounds = Rect()
                eligible = content != null && onSwipe != null &&
                    content.getGlobalVisibleRect(bounds) &&
                    bounds.contains(event.rawX.toInt(), event.rawY.toInt()) &&
                    event.x > edgeMargin && event.x < width - edgeMargin &&
                    !hasHorizontalControl(content, event.rawX.toInt(), event.rawY.toInt())
            }
            MotionEvent.ACTION_POINTER_DOWN -> eligible = false
            MotionEvent.ACTION_MOVE -> {
                if (eligible) {
                    val dx = abs(event.x - startX)
                    val dy = abs(event.y - startY)
                    // Once a vertical scroll starts, never turn it into a page change.
                    if (dy > slop && dy >= dx) eligible = false
                    else if (dx >= distance && dx > dy * 1.7f) {
                        horizontal = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> eligible = false
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Blank areas may have no child touch target, so the parent receives the
        // gesture directly instead of passing through onInterceptTouchEvent.
        if (event.actionMasked == MotionEvent.ACTION_DOWN && eligible) return true
        if (event.actionMasked == MotionEvent.ACTION_MOVE && eligible && !horizontal) {
            val dx = abs(event.x - startX)
            val dy = abs(event.y - startY)
            if (dy > slop && dy >= dx) eligible = false
            else if (dx >= distance && dx > dy * 1.7f) horizontal = true
        }
        if (!horizontal) return eligible || super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> eligible = false
            MotionEvent.ACTION_UP -> {
                val dx = event.x - startX
                val dy = event.y - startY
                val navigate = eligible && abs(dx) >= distance && abs(dx) > abs(dy) * 1.7f
                horizontal = false
                eligible = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (navigate) onSwipe?.invoke(dx < 0)
            }
            MotionEvent.ACTION_CANCEL -> {
                horizontal = false
                eligible = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun hasHorizontalControl(view: View, x: Int, y: Int): Boolean {
        val bounds = Rect()
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(x, y)) return false
        if (view is EditText || view is SeekBar || view.canScrollHorizontally(-1) ||
            view.canScrollHorizontally(1)) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (hasHorizontalControl(view.getChildAt(index), x, y)) return true
            }
        }
        return false
    }
}
