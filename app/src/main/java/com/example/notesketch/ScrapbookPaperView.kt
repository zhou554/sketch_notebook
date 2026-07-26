package com.example.notesketch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 拼贴手账方格纸背景（对齐 scrapbook-forest-fox-ui）。
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
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFDDD5C8.toInt()
        strokeWidth = 1f
    }

    var paperColor: Int
        get() = fillPaint.color
        set(value) {
            fillPaint.color = value
            invalidate()
        }

    var gridColor: Int
        get() = gridPaint.color
        set(value) {
            gridPaint.color = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        val step = 18f * resources.displayMetrics.density
        var x = 0f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x += step
        }
        var y = 0f
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, gridPaint)
            y += step
        }
    }
}
