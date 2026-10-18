package com.example.biteflight.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.biteflight.R

/** Shared floating navigation with a full-height active capsule and selection dot. */
class FloatingNavigationBar(context: Context) : LinearLayout(context) {
    private val items = mutableListOf<MenuItem>()
    private var selectedListener: ((MenuItem) -> Boolean)? = null
    private var reselectedListener: ((MenuItem) -> Unit)? = null
    var selectedItemId: Int = 0
        set(value) { field = value; render() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(5), dp(6), dp(5))
        background = ContextCompat.getDrawable(context, R.drawable.nav_floating)
        elevation = dp(8).toFloat()
        if (Build.VERSION.SDK_INT >= 28) {
            outlineSpotShadowColor = Color.parseColor("#AA89CD")
            outlineAmbientShadowColor = Color.parseColor("#D3B7ED")
        }
        clipChildren = false
    }

    fun inflateMenu(resource: Int) {
        val menu = PopupMenu(context, this).menu
        android.view.MenuInflater(context).inflate(resource, menu)
        items.clear()
        for (i in 0 until menu.size()) items.add(menu.getItem(i))
        render()
    }

    fun setOnItemSelectedListener(listener: (MenuItem) -> Boolean) { selectedListener = listener }
    fun setOnItemReselectedListener(listener: (MenuItem) -> Unit) { reselectedListener = listener }

    private fun render() {
        removeAllViews()
        items.forEach { item ->
            val active = item.itemId == selectedItemId
            val tint = if (active) Color.WHITE else ContextCompat.getColor(context, R.color.primary)
            val cell = LinearLayout(context).apply {
                id = item.itemId
                orientation = VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                isSelected = active
                contentDescription = item.title.toString()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                if (active) background = ContextCompat.getDrawable(context, R.drawable.nav_active)
                setPadding(dp(1), dp(8), dp(1), dp(5))
            }
            cell.addView(ImageView(context).apply {
                setImageDrawable(item.icon?.mutate())
                imageTintList = ColorStateList.valueOf(tint)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(dp(25), dp(25)))
            cell.addView(TextView(context).apply {
                text = item.title
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(tint)
                typeface = ResourcesCompat.getFont(context, R.font.inter)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(-1, -2).apply { topMargin = dp(3) })
            cell.addView(TextView(context).apply {
                text = if (active) "●" else ""
                textSize = 8f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(-1, dp(10)))
            cell.setOnClickListener {
                cell.animate().scaleX(.94f).scaleY(.94f).setDuration(70).withEndAction {
                    cell.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (active && reselectedListener != null) reselectedListener?.invoke(item)
                    else if (selectedListener?.invoke(item) == true) selectedItemId = item.itemId
                }.start()
            }
            addView(cell, LayoutParams(0, -1, 1f))
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

