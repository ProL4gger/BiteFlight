package com.example.biteflight.ui.buyer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Connected boxes wrap naturally on small screens and with larger system fonts. */
class DeliveryProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val stages = listOf("ASSIGNED", "WAITING_AT_RESTAURANT", "PICKED_UP", "IN_AREA", "DELIVERED")
    private val labels = listOf("Rider heading to restaurant", "Preparing at restaurant",
        "On the way", "In your area", "Delivered")
    private val purple = Color.rgb(101, 53, 160)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.rgb(224, 212, 238))
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setStatus(status: String) {
        removeAllViews()
        val current = if (status.equals("COMPLETED", true)) stages.lastIndex
            else stages.indexOf(status.uppercase())
        addView(TextView(context).apply {
            text = "Delivery progress"
            textSize = 16f
            setTextColor(purple)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        })
        labels.forEachIndexed { index, label ->
            val reached = index <= current
            val active = index == current
            addView(TextView(context).apply {
                text = "${if (reached) "✓" else "□"}  $label${if (active) " • Current" else ""}"
                textSize = 14f
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setTextColor(if (reached) purple else Color.rgb(114, 106, 122))
                if (active) setTypeface(typeface, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(if (reached) Color.rgb(243, 235, 253) else Color.rgb(250, 248, 252))
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(if (active) 2 else 1), if (reached) purple else Color.rgb(221, 215, 228))
                }
                contentDescription = "$label, ${if (active) "current stage" else if (reached) "completed" else "upcoming"}"
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            if (index < labels.lastIndex) addView(View(context).apply {
                setBackgroundColor(if (index < current) purple else Color.rgb(221, 215, 228))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(dp(2), dp(14)).apply { marginStart = dp(23) })
        }
    }
}
