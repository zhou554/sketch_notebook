package com.example.notesketch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

enum class PaperPattern {
    GRID,
    BLANK,
    LINED,
    DOTS;

    companion object {
        fun fromId(id: String?): PaperPattern = when (id) {
            "blank" -> BLANK
            "lined" -> LINED
            "dots" -> DOTS
            else -> GRID
        }
    }

    val id: String
        get() = when (this) {
            GRID -> "grid"
            BLANK -> "blank"
            LINED -> "lined"
            DOTS -> "dots"
        }

    val label: String
        get() = when (this) {
            GRID -> "网格"
            BLANK -> "空白"
            LINED -> "横线"
            DOTS -> "网点"
        }
}

/**
 * 拼贴手账纸张背景：网格 / 空白 / 横线 / 网点。
 */
class ScrapbookPaperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFF6F0E4.toInt()
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFDDD5C8.toInt()
        strokeWidth = 1f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFDDD5C8.toInt()
    }

    var paperColor: Int
        get() = fillPaint.color
        set(value) {
            fillPaint.color = value
            invalidate()
        }

    var gridColor: Int
        get() = linePaint.color
        set(value) {
            linePaint.color = value
            dotPaint.color = value
            invalidate()
        }

    var pattern: PaperPattern = PaperPattern.GRID
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        when (pattern) {
            PaperPattern.BLANK -> Unit
            PaperPattern.DOTS -> {
                val step = 14f * resources.displayMetrics.density
                val radius = 1.1f * resources.displayMetrics.density
                var y = step / 2f
                while (y <= h) {
                    var x = step / 2f
                    while (x <= w) {
                        canvas.drawCircle(x, y, radius, dotPaint)
                        x += step
                    }
                    y += step
                }
            }
            PaperPattern.GRID -> {
                val step = 18f * resources.displayMetrics.density
                var x = 0f
                while (x <= w) {
                    canvas.drawLine(x, 0f, x, h, linePaint)
                    x += step
                }
                var y = 0f
                while (y <= h) {
                    canvas.drawLine(0f, y, w, y, linePaint)
                    y += step
                }
            }
            PaperPattern.LINED -> {
                val step = 18f * resources.displayMetrics.density
                var y = 0f
                while (y <= h) {
                    canvas.drawLine(0f, y, w, y, linePaint)
                    y += step
                }
            }
        }
    }
}
