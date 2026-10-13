package com.example.dotrecorder

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs

/**
 * The on-screen UI: a small, draggable black dot. Tapping it expands a compact
 * control row (stop button); tapping again collapses back to the dot. This is
 * the only overlay the app draws, so it is the only thing that appears in the
 * footage — deliberately minimal, unlike the system's bulky default controls.
 *
 * It does not and cannot hide the system recording indicator; that stays put.
 */
class FloatingControl(
    private val context: Context,
    private val onToggleStop: () -> Unit
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val root: View =
        LayoutInflater.from(context).inflate(R.layout.floating_control, null)

    private val dot: View = root.findViewById(R.id.dot)
    private val panel: View = root.findViewById(R.id.panel)

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 24
        y = 240
    }

    private var expanded = false

    init {
        panel.visibility = View.GONE
        root.findViewById<View>(R.id.stopButton).setOnClickListener { onToggleStop() }
        dot.setOnTouchListener(DragTapListener())
    }

    fun show() = windowManager.addView(root, layoutParams)

    fun hide() {
        try {
            windowManager.removeView(root)
        } catch (_: Exception) {
        }
    }

    private fun toggleExpanded() {
        expanded = !expanded
        panel.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    /** Distinguishes a tap (toggle panel) from a drag (reposition the dot). */
    private inner class DragTapListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) dragging = true
                    if (dragging) {
                        layoutParams.x = startX + dx
                        layoutParams.y = startY + dy
                        windowManager.updateViewLayout(root, layoutParams)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) toggleExpanded()
                }
            }
            return true
        }
    }
}
