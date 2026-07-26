package com.example.notesketch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 线条海景 —— 照搬 line-minimal-ui.html：
 * - viewBox 1200×320，preserveAspectRatio=none 拉满全屏
 * - 仅描边波浪 / 地平线，透明底（不是色块海）
 * - 点击底部约 42% 跃出小鱼；随机小鱼 / 落贝壳（入水渐隐，无水花）
 */
class SeaSceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class WaveLayer(
        val baseYSvg: Float,
        val ampSvg: Float,
        val freq: Float,
        val speed: Float,
        val strokeSvg: Float,
        val alpha: Int,
        val useAccent: Boolean,
        val useMuted: Boolean = false
    )

    private data class FishFx(
        var x: Float,
        var baseY: Float,
        var startMs: Long,
        var toRight: Boolean,
        val durationMs: Long = 780L
    )

    private data class ShellFx(
        var x: Float,
        var startY: Float,
        var fallPx: Float,
        var drift: Float,
        var startMs: Long,
        var durationMs: Long,
        var spinStart: Float,
        var spinEnd: Float
    )

    private var palette: ThemePalette = UiPrefs.theme(context)
    private var seaHeight = 50
    private var seaAmp = 45
    private var shellFreq = 45

    private val sceneW = 1200f
    private val sceneH = 320f
    private val horizonSvg = 248f

    /**
     * 小巧精致浪线：间距更密、振幅更小、频率稍高，持续相位动画起伏。
     * baseY 间隔约 9～10（原先约 20）。
     */
    private val waveDefs = listOf(
        WaveLayer(256f, 3.6f, 0.018f, 0.30f, 0.75f, 120, useAccent = false),
        WaveLayer(265f, 4.8f, 0.014f, 0.24f, 0.70f, 78, useAccent = true),
        WaveLayer(274f, 3.2f, 0.022f, 0.36f, 0.65f, 95, useAccent = false),
        WaveLayer(283f, 4.0f, 0.016f, 0.20f, 0.65f, 70, useAccent = false),
        WaveLayer(292f, 2.6f, 0.025f, 0.42f, 0.60f, 82, useAccent = false),
        WaveLayer(301f, 2.2f, 0.020f, 0.16f, 0.55f, 55, useAccent = false, useMuted = true)
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val wavePath = Path()
    private val artPath = Path()

    private val fishes = mutableListOf<FishFx>()
    private val shells = mutableListOf<ShellFx>()

    private val reduceMotion: Boolean
    private var startMs = 0L
    private var running = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rnd = Random(SystemClock.uptimeMillis())

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            pruneFx()
            invalidate()
            postOnAnimation(this)
        }
    }

    private val scheduleFish = object : Runnable {
        override fun run() {
            if (!running || reduceMotion || width <= 0) return
            spawnFish(48f * density() + rnd.nextFloat() * max(1f, width - 96f * density()))
            mainHandler.postDelayed(this, 6000L + rnd.nextLong(0, 11000))
        }
    }

    private val scheduleShell = object : Runnable {
        override fun run() {
            if (!running || reduceMotion || width <= 0) return
            if (shellFreq > 0) dropShell()
            val delay = shellDelayMs()
            if (delay < Long.MAX_VALUE / 4) {
                mainHandler.postDelayed(this, delay)
            }
        }
    }

    init {
        setWillNotDraw(false)
        // 透明底：只画线条，不铺色块海
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        contentDescription = "线条海景，点按海面可让小鱼跃起"
        reduceMotion = animatorDurationScale(context) == 0f
        loadFromPrefs(context)
    }

    fun loadFromPrefs(context: Context) {
        palette = UiPrefs.theme(context)
        seaHeight = UiPrefs.seaHeight(context)
        seaAmp = UiPrefs.seaAmp(context)
        shellFreq = UiPrefs.shellFreq(context)
        restartShellSchedule()
        invalidate()
    }

    fun applyConfig(
        theme: ThemePalette,
        height: Int,
        amp: Int,
        shellFrequency: Int
    ) {
        palette = theme
        seaHeight = height.coerceIn(0, 100)
        seaAmp = amp.coerceIn(0, 100)
        shellFreq = shellFrequency.coerceIn(0, 100)
        restartShellSchedule()
        invalidate()
    }

    /** 供列表空白处等外部触发（坐标为相对本 View） */
    fun spawnFishAt(xInView: Float) {
        spawnFish(xInView)
    }

    private fun restartShellSchedule() {
        mainHandler.removeCallbacks(scheduleShell)
        if (running && !reduceMotion && shellFreq > 0) {
            mainHandler.postDelayed(scheduleShell, 4000L + rnd.nextLong(0, 3000))
        }
    }

    /** 对齐参考默认节奏，并用 shellFreq 缩短/拉长间隔；0 = 关闭 */
    private fun shellDelayMs(): Long {
        if (shellFreq <= 0) return Long.MAX_VALUE / 8
        val base = 9000L + rnd.nextLong(0, 15000)
        val t = shellFreq / 100f
        // 50 ≈ 参考默认；更高更密
        val scale = 1.6f - t
        return (base * scale).toLong().coerceIn(3500L, 28000L)
    }

    private fun yOffSvg(): Float = ((50 - seaHeight) / 50f) * 42f

    /** 起伏幅度整体偏小，设置滑条仍可微调 */
    private fun ampMul(): Float = 0.35f + (seaAmp / 100f) * 0.85f

    /** 海面 Y（像素）：地平线在拉伸后的 scene 中的位置 */
    private fun seaSurfaceY(): Float {
        if (height <= 0) return 0f
        return (horizonSvg + yOffSvg()) / sceneH * height
    }

    /** 点击区：底部 min(42% 高, 320dp)，对齐 scene-tap-zone */
    private fun tapZoneTop(): Float {
        val zoneH = min(height * 0.42f, 320f * density())
        return height - zoneH
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startMs = SystemClock.uptimeMillis()
        running = true
        if (!reduceMotion) {
            postOnAnimation(tick)
            mainHandler.postDelayed(scheduleFish, 2500L)
            if (shellFreq > 0) {
                mainHandler.postDelayed(scheduleShell, 4000L + rnd.nextLong(0, 3000))
            }
        } else {
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(tick)
        mainHandler.removeCallbacks(scheduleFish)
        mainHandler.removeCallbacks(scheduleShell)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                return event.y >= tapZoneTop()
            }
            MotionEvent.ACTION_UP -> {
                if (event.y >= tapZoneTop()) {
                    spawnFish(event.x)
                    performClick()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun spawnFish(x: Float) {
        if (reduceMotion || height <= 0 || width <= 0) return
        val margin = 24f * density()
        fishes += FishFx(
            x = x.coerceIn(margin, width - margin),
            baseY = seaSurfaceY(),
            startMs = SystemClock.uptimeMillis(),
            toRight = rnd.nextBoolean()
        )
        invalidate()
    }

    private fun dropShell() {
        if (height <= 0 || width <= 0) return
        val margin = 24f * density()
        val sizeW = 34f * density()
        val startX = margin + rnd.nextFloat() * max(1f, width - margin * 2 - sizeW)
        val startY = height * (0.04f + rnd.nextFloat() * 0.22f)
        val seaY = seaSurfaceY()
        // 落入水面后再略沉入水中，便于渐隐
        val fall = max(seaY - startY - 8f, 120f) + 22f * density()
        val drift = (rnd.nextFloat() - 0.5f) * 48f * density()
        val dropMs = (1600 + fall * 1.35f + rnd.nextFloat() * 400f).toLong()
        shells += ShellFx(
            x = startX,
            startY = startY,
            fallPx = fall,
            drift = drift,
            startMs = SystemClock.uptimeMillis(),
            durationMs = dropMs,
            spinStart = -20f + rnd.nextFloat() * 40f,
            spinEnd = 120f + rnd.nextFloat() * 120f
        )
    }

    private fun pruneFx() {
        val now = SystemClock.uptimeMillis()
        fishes.removeAll { now - it.startMs > it.durationMs }
        shells.removeAll { now - it.startMs > it.durationMs }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val sx = w / sceneW
        val sy = h / sceneH
        val d = density()
        // 细线：按密度取发丝级描边，避免全屏纵向拉伸把线变粗
        val hair = (0.55f * d).coerceAtLeast(0.8f)
        val t = if (reduceMotion) 0f else (SystemClock.uptimeMillis() - startMs) / 1000f
        val yOff = yOffSvg()
        val mul = ampMul()

        // horizon
        paint.style = Paint.Style.STROKE
        paint.color = palette.line
        paint.alpha = (0.40f * 255).toInt()
        paint.strokeWidth = hair * 0.95f
        val hy = (horizonSvg + yOff) * sy
        canvas.drawLine(0f, hy, w, hy, paint)

        // waves — 连续 Path，相位随时间变化形成动态起伏
        for (layer in waveDefs) {
            val color = when {
                layer.useAccent -> palette.accent
                layer.useMuted -> palette.muted
                else -> palette.line
            }
            paint.color = color
            paint.alpha = layer.alpha
            paint.strokeWidth = hair * (layer.strokeSvg / 0.75f).coerceIn(0.75f, 1.15f)

            val baseY = (layer.baseYSvg + yOff) * sy
            // 振幅按场景高度缩放后再压低，保持小巧
            val amp = layer.ampSvg * mul * sy * 0.55f
            val phase = t * layer.speed * (Math.PI.toFloat() * 2f)

            wavePath.reset()
            var first = true
            var xSvg = 0f
            while (xSvg <= sceneW) {
                val x = xSvg * sx
                val y = baseY + sin(xSvg * layer.freq + phase) * amp
                if (first) {
                    wavePath.moveTo(x, y)
                    first = false
                } else {
                    wavePath.lineTo(x, y)
                }
                xSvg += 12f
            }
            canvas.drawPath(wavePath, paint)
        }

        val now = SystemClock.uptimeMillis()
        fishes.toList().forEach { drawFish(canvas, it, now) }
        shells.toList().forEach { drawShell(canvas, it, now) }
    }

    /**
     * 抛物线跃起：升空 → 沿轨迹下落入水并渐隐（无滞空）。
     * 顶点约在 36% 处，后段加速沉入水面下。
     */
    private fun drawFish(canvas: Canvas, fx: FishFx, now: Long) {
        val p = ((now - fx.startMs).toFloat() / fx.durationMs).coerceIn(0f, 1f)
        val dir = if (fx.toRight) 1f else -1f
        val d = density()
        val pose = fishArc(p, dir, d)

        canvas.save()
        canvas.translate(fx.x + pose.tx, fx.baseY + pose.ty)
        canvas.rotate(pose.rot)
        canvas.scale(if (fx.toRight) -pose.scale else pose.scale, pose.scale)
        canvas.translate(-26f * d * 0.55f, -11f * d * 0.88f)

        paint.style = Paint.Style.STROKE
        paint.color = palette.ink
        paint.alpha = (pose.alpha * 255).toInt().coerceIn(0, 255)
        paint.strokeWidth = 1.2f * d

        // 圆润鱼身：大体仍为左侧鱼头、右侧尾叉，轮廓更饱满
        artPath.reset()
        artPath.moveTo(3.5f * d, 13.5f * d)
        artPath.cubicTo(9f * d, 5.5f * d, 20f * d, 3f * d, 32f * d, 6.5f * d)
        artPath.cubicTo(38f * d, 8.5f * d, 42.5f * d, 11.5f * d, 40.5f * d, 15f * d)
        artPath.cubicTo(38.5f * d, 18.5f * d, 28f * d, 20.5f * d, 16f * d, 19.2f * d)
        artPath.cubicTo(9.5f * d, 18.2f * d, 5f * d, 16.5f * d, 3.5f * d, 13.5f * d)
        canvas.drawPath(artPath, paint)
        // 圆润尾叉
        artPath.reset()
        artPath.moveTo(39.5f * d, 13.2f * d)
        artPath.quadTo(44f * d, 9.5f * d, 48.5f * d, 7.5f * d)
        canvas.drawPath(artPath, paint)
        artPath.reset()
        artPath.moveTo(39.5f * d, 15.2f * d)
        artPath.quadTo(44f * d, 18.5f * d, 48.5f * d, 20.5f * d)
        canvas.drawPath(artPath, paint)
        artPath.reset()
        artPath.moveTo(48.5f * d, 7.5f * d)
        artPath.quadTo(47.2f * d, 14f * d, 48.5f * d, 20.5f * d)
        canvas.drawPath(artPath, paint)
        // 背鳍
        artPath.reset()
        artPath.moveTo(22f * d, 5.2f * d)
        artPath.quadTo(27f * d, 6.5f * d, 30f * d, 9f * d)
        canvas.drawPath(artPath, paint)
        fillPaint.color = palette.ink
        fillPaint.alpha = paint.alpha
        canvas.drawCircle(15f * d, 12f * d, 1.35f * d, fillPaint)
        canvas.restore()
    }

    private data class FishPose(
        val tx: Float,
        val ty: Float,
        val rot: Float,
        val scale: Float,
        val alpha: Float
    )

    /**
     * 弹道抛物线：顶点提前（~28%），上升接近线性、下落 u^1.5 加速，避免顶点滞空。
     */
    private fun fishArc(p: Float, dir: Float, d: Float): FishPose {
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        val peakT = 0.28f
        val peakH = 32f * d
        val sink = 18f * d
        val ty = if (p <= peakT) {
            val u = (p / peakT).coerceIn(0f, 1f)
            // 轻度 ease，到顶几乎不停顿
            -peakH * (0.25f * u + 0.75f * u * u)
        } else {
            val u = ((p - peakT) / (1f - peakT)).coerceIn(0f, 1f)
            val fall = u * sqrt(u) // u^1.5，下落加速
            -peakH + (peakH + sink) * fall
        }
        val tx = dir * 58f * d * p
        val rot = if (p <= peakT) {
            dir * lerp(-8f, -22f, p / peakT)
        } else {
            dir * lerp(-22f, 26f, (p - peakT) / (1f - peakT))
        }
        val scale = when {
            p < 0.08f -> lerp(0.9f, 1f, p / 0.08f)
            p > 0.72f -> lerp(1f, 0.86f, (p - 0.72f) / 0.28f)
            else -> 1f
        }
        val alpha = when {
            p < 0.05f -> p / 0.05f * 0.92f
            p < 0.52f -> 0.92f
            else -> 0.92f * (1f - (p - 0.52f) / 0.48f).coerceAtLeast(0f)
        }
        return FishPose(tx, ty, rot, scale, alpha)
    }

    private fun drawShell(canvas: Canvas, fx: ShellFx, now: Long) {
        val p = ((now - fx.startMs).toFloat() / fx.durationMs).coerceIn(0f, 1f)
        // 下落略加速，入水段继续下沉
        val fallEase = p * p * (1.05f - 0.05f * p)
        val rot = fx.spinStart + (fx.spinEnd - fx.spinStart) * fallEase
        val alpha = when {
            p < 0.07f -> (p / 0.07f) * 0.7f
            p < 0.62f -> 0.7f
            else -> 0.7f * (1f - (p - 0.62f) / 0.38f).coerceAtLeast(0f)
        }
        val d = density()
        val s = d * (34f / 72f)

        canvas.save()
        canvas.translate(fx.x + fx.drift * fallEase, fx.startY + fx.fallPx * fallEase)
        canvas.rotate(rot)
        canvas.translate(-36f * s, -29f * s)

        paint.style = Paint.Style.STROKE
        paint.color = palette.accent
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        paint.strokeWidth = 1.1f * d

        artPath.reset()
        artPath.moveTo(36f * s, 52f * s)
        artPath.lineTo(8f * s, 36f * s)
        artPath.quadTo(4f * s, 22f * s, 12f * s, 12f * s)
        artPath.quadTo(22f * s, 4f * s, 36f * s, 6f * s)
        artPath.quadTo(50f * s, 4f * s, 60f * s, 12f * s)
        artPath.quadTo(68f * s, 22f * s, 64f * s, 36f * s)
        artPath.close()
        canvas.drawPath(artPath, paint)
        canvas.drawLine(36f * s, 52f * s, 36f * s, 8f * s, paint)
        canvas.drawLine(36f * s, 52f * s, 12f * s, 12f * s, paint)
        canvas.drawLine(36f * s, 52f * s, 22f * s, 8f * s, paint)
        canvas.drawLine(36f * s, 52f * s, 50f * s, 8f * s, paint)
        canvas.drawLine(36f * s, 52f * s, 60f * s, 12f * s, paint)
        canvas.restore()
    }

    private fun density() = resources.displayMetrics.density

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
