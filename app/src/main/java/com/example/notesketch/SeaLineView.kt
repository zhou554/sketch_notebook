package com.example.notesketch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.sin

/**
 * 线条海景背景：对齐 line-minimal-ui.html 的 scene-sea 波浪。
 */
class SeaLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Layer(
        val amp: Float,
        val freq: Float,
        val speed: Float,
        val baseRatio: Float,
        val color: Int,
        val strokeWidth: Float,
        val alpha: Int
    )

    private val lineColor = ContextCompat.getColor(context, R.color.line)
    private val accentColor = ContextCompat.getColor(context, R.color.accent)
    private val mutedColor = ContextCompat.getColor(context, R.color.text_secondary)

    private val layers = listOf(
        Layer(9f, 0.011f, 0.32f, 0.72f, lineColor, 1.25f * resources.displayMetrics.density, 128),
        Layer(12f, 0.0085f, 0.26f, 0.78f, accentColor, 1f * resources.displayMetrics.density, 82),
        Layer(7f, 0.014f, 0.38f, 0.84f, lineColor, 1f * resources.displayMetrics.density, 92),
        Layer(5f, 0.017f, 0.20f, 0.90f, mutedColor, 1f * resources.displayMetrics.density, 62)
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = lineColor
        alpha = 90
    }

    private val reduceMotion: Boolean
    private var startMs = 0L
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postOnAnimation(this)
        }
    }

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        reduceMotion = animatorDurationScale(context) == 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startMs = SystemClock.uptimeMillis()
        if (!reduceMotion) {
            running = true
            postOnAnimation(tick)
        }
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val step = 10f * density
        val t = if (reduceMotion) 0f else (SystemClock.uptimeMillis() - startMs) / 1000f
        val horizonY = h * 0.68f
        canvas.drawLine(0f, horizonY, w, horizonY, horizonPaint)

        for (layer in layers) {
            paint.color = layer.color
            paint.alpha = layer.alpha
            paint.strokeWidth = layer.strokeWidth
            val baseY = h * layer.baseRatio
            val amp = layer.amp * density
            val phase = t * layer.speed * Math.PI.toFloat() * 2f
            var x = 0f
            var prevY = baseY + sin(phase).toFloat() * amp
            while (x < w) {
                val nextX = (x + step).coerceAtMost(w)
                val nextY = baseY + sin(nextX * layer.freq + phase).toFloat() * amp
                canvas.drawLine(x, prevY, nextX, nextY, paint)
                x = nextX
                prevY = nextY
            }
        }
    }

    private fun animatorDurationScale(context: Context): Float {
        return try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (_: Exception) {
            1f
        }
    }
}
