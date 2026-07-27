package com.example.notesketch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 简易色谱选色：上方饱和度/明度面板 + 下方色相条。
 */
class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onColorChanged: ((Int) -> Unit)? = null

    private var hue = 30f
    private var sat = 0.35f
    private var value = 0.92f

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = Color.WHITE
    }
    private val cursorRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        color = Color.parseColor("#3D3428")
    }
    private val panelRect = RectF()
    private val hueRect = RectF()
    private val d = resources.displayMetrics.density
    private val gap = 12f * d
    private val hueH = 28f * d
    private var draggingPanel = false
    private var draggingHue = false

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        invalidate()
    }

    fun currentColor(): Int = Color.HSVToColor(floatArrayOf(hue, sat, value))

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast((220 * d).toInt())
        val h = (w * 0.72f + hueH + gap).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        panelRect.set(0f, 0f, w, h - hueH - gap)
        hueRect.set(0f, h - hueH, w, h)

        // SV panel: left white→hue, top transparent→black overlay via two gradients
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        panelPaint.shader = LinearGradient(
            panelRect.left, 0f, panelRect.right, 0f,
            Color.WHITE, hueColor, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(panelRect, 8f * d, 8f * d, panelPaint)
        panelPaint.shader = LinearGradient(
            0f, panelRect.top, 0f, panelRect.bottom,
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(panelRect, 8f * d, 8f * d, panelPaint)
        panelPaint.shader = null

        val cx = panelRect.left + sat * panelRect.width()
        val cy = panelRect.top + (1f - value) * panelRect.height()
        canvas.drawCircle(cx, cy, 8f * d, cursorPaint)
        canvas.drawCircle(cx, cy, 8f * d, cursorRing)

        // Hue bar
        val colors = IntArray(7) { i -> Color.HSVToColor(floatArrayOf(i * 60f, 1f, 1f)) }
        huePaint.shader = LinearGradient(
            hueRect.left, 0f, hueRect.right, 0f,
            colors, null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(hueRect, 6f * d, 6f * d, huePaint)
        huePaint.shader = null
        val hx = hueRect.left + (hue / 360f) * hueRect.width()
        canvas.drawCircle(hx, hueRect.centerY(), 9f * d, cursorPaint)
        canvas.drawCircle(hx, hueRect.centerY(), 9f * d, cursorRing)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                when {
                    panelRect.contains(event.x, event.y) -> {
                        draggingPanel = true
                        updateSv(event.x, event.y)
                    }
                    hueRect.contains(event.x, event.y) -> {
                        draggingHue = true
                        updateHue(event.x)
                    }
                    else -> return false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingPanel) updateSv(event.x, event.y)
                if (draggingHue) updateHue(event.x)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingPanel = false
                draggingHue = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun updateSv(x: Float, y: Float) {
        sat = ((x - panelRect.left) / panelRect.width()).coerceIn(0f, 1f)
        value = (1f - (y - panelRect.top) / panelRect.height()).coerceIn(0f, 1f)
        invalidate()
        onColorChanged?.invoke(currentColor())
    }

    private fun updateHue(x: Float) {
        hue = (((x - hueRect.left) / hueRect.width()).coerceIn(0f, 1f) * 360f)
        invalidate()
        onColorChanged?.invoke(currentColor())
    }
}
