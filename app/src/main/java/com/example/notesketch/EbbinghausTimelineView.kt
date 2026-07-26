package com.example.notesketch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * 水平艾宾浩斯时间轴：上方可点方框、中间薰衣草蓝渐变进度条 + 狐狸头、下方天数。
 */
class EbbinghausTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val stageCount = Ebbinghaus.stageCount
    private var stage = 0
    private var finished = false
    private var createdAt = 0L

    var onCheckStage: ((Int) -> Unit)? = null

    private val d get() = resources.displayMetrics.density

    private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFE8E2D8.toInt()
    }
    private val trackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFB8AEA0.toInt()
        strokeWidth = 2f * d
    }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * d
    }
    private val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7A6F62.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 11f * d
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3D3428.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 12f * d
        isFakeBoldText = true
    }

    private val trackRect = RectF()
    private val boxRects = Array(stageCount) { RectF() }
    private val foxBitmap: Bitmap? by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.fox_deco)
    }

    fun bind(note: com.example.notesketch.data.Note) {
        stage = note.stage.coerceIn(0, stageCount)
        finished = note.finished
        createdAt = note.createdAt
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (164f * d).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val padX = 14f * d
        val usable = w - padX * 2
        if (usable <= 0f) return

        val boxSize = 18f * d
        val boxTop = 8f * d
        val trackH = 10f * d
        val trackTop = boxTop + boxSize + 16f * d
        val trackBottom = trackTop + trackH
        trackRect.set(padX, trackTop, w - padX, trackBottom)

        canvas.drawRoundRect(trackRect, trackH / 2, trackH / 2, trackBgPaint)

        val progress = progressFraction()
        val fillRight = trackRect.left + trackRect.width() * progress
        if (fillRight > trackRect.left + 1f) {
            trackFillPaint.shader = LinearGradient(
                trackRect.left,
                0f,
                trackRect.right,
                0f,
                intArrayOf(0xFFC9B8F0.toInt(), 0xFF8BB4E8.toInt(), 0xFF6A9FD4.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            val fill = RectF(trackRect.left, trackRect.top, fillRight, trackRect.bottom)
            canvas.drawRoundRect(fill, trackH / 2, trackH / 2, trackFillPaint)
            trackFillPaint.shader = null
        }

        for (i in 0 until stageCount) {
            val cx = padX + usable * (if (stageCount == 1) 0.5f else i.toFloat() / (stageCount - 1))
            val done = finished || i < stage
            val current = !finished && i == stage

            canvas.drawLine(cx, trackTop - 2f * d, cx, trackBottom + 2f * d, tickPaint)

            val left = cx - boxSize / 2
            val top = boxTop
            boxRects[i].set(left, top, left + boxSize, top + boxSize)
            when {
                done -> {
                    boxFillPaint.color = 0xFF7AAB9E.toInt()
                    canvas.drawRoundRect(boxRects[i], 3f * d, 3f * d, boxFillPaint)
                    boxPaint.style = Paint.Style.STROKE
                    boxPaint.color = 0xFF5C8F82.toInt()
                    canvas.drawRoundRect(boxRects[i], 3f * d, 3f * d, boxPaint)
                    boxPaint.color = 0xFFFFFFFF.toInt()
                    boxPaint.strokeWidth = 2f * d
                    canvas.drawLine(
                        cx - 4f * d, top + boxSize * 0.52f,
                        cx - 1f * d, top + boxSize * 0.7f,
                        boxPaint
                    )
                    canvas.drawLine(
                        cx - 1f * d, top + boxSize * 0.7f,
                        cx + 5f * d, top + boxSize * 0.32f,
                        boxPaint
                    )
                    boxPaint.strokeWidth = 1.6f * d
                }
                current -> {
                    boxFillPaint.color = 0xFFFFFFFF.toInt()
                    canvas.drawRoundRect(boxRects[i], 3f * d, 3f * d, boxFillPaint)
                    boxPaint.color = 0xFF6A9FD4.toInt()
                    canvas.drawRoundRect(boxRects[i], 3f * d, 3f * d, boxPaint)
                }
                else -> {
                    boxFillPaint.color = 0x66FFFFFF
                    canvas.drawRoundRect(boxRects[i], 3f * d, 3f * d, boxFillPaint)
                    boxPaint.color = 0xFFC4B8A8.toInt()
                    canvas.drawRoundRect(boxRects[i], 3f * d, 3f * d, boxPaint)
                }
            }

            val day = Ebbinghaus.INTERVAL_DAYS[i].toString()
            canvas.drawText(day, cx, trackBottom + 18f * d, dayPaint)
            canvas.drawText("天", cx, trackBottom + 30f * d, labelPaint)
        }

        // 狐狸头进度指示（略放大）
        val fox = foxBitmap
        if (fox != null && !fox.isRecycled) {
            val foxSize = 80f * d
            val fx = (fillRight - foxSize / 2f).coerceIn(0f, w - foxSize)
            val fy = trackTop + trackH / 2f - foxSize / 2f
            val src = android.graphics.Rect(0, 0, fox.width, fox.height)
            val dst = RectF(fx, fy, fx + foxSize, fy + foxSize)
            canvas.drawBitmap(fox, src, dst, null)
        }
    }

    /** 进度：已完成阶段落在对应节点；当前待复习略偏该节点前一点 */
    private fun progressFraction(): Float {
        if (finished || stage >= stageCount) return 1f
        if (stageCount <= 1) return 0f
        // 狐狸停在「当前待勾选」节点上（已完成的已走过）
        return stage.toFloat() / (stageCount - 1).toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) {
            return event.action == MotionEvent.ACTION_DOWN || super.onTouchEvent(event)
        }
        val x = event.x
        val y = event.y
        for (i in 0 until stageCount) {
            val r = boxRects[i]
            if (r.contains(x, y) || hypot(x - r.centerX(), y - r.centerY()) < 22f * d) {
                if (!finished && i == stage) {
                    onCheckStage?.invoke(i)
                    performClick()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
