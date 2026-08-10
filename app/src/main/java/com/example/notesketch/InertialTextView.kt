package com.example.notesketch

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatTextView

/**
 * 便签只读正文：增强惯性滚动（详情页浏览模式）。
 */
class InertialTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val velocityTracker = VelocityTracker.obtain()
    private val fallbackScroller = OverScroller(context)
    private val maxFlingVelocity =
        ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()

    private var useFallbackScroller = false

    init {
        isVerticalScrollBarEnabled = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker.clear()
                velocityTracker.addMovement(event)
                fallbackScroller.abortAnimation()
            }
            MotionEvent.ACTION_MOVE -> velocityTracker.addMovement(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> velocityTracker.addMovement(event)
        }

        val handled = super.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            applyInertialFlingIfNeeded()
            velocityTracker.clear()
        }
        return handled
    }

    override fun computeScroll() {
        super.computeScroll()
        if (useFallbackScroller && fallbackScroller.computeScrollOffset()) {
            scrollTo(fallbackScroller.currX, fallbackScroller.currY)
            postInvalidateOnAnimation()
        }
    }

    private fun applyInertialFlingIfNeeded() {
        if (InertialFling.isReducedMotion(context)) return

        val range = maxScrollY()
        if (range <= 0) return
        if (!canScrollVertically(1) && !canScrollVertically(-1)) return

        velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity)
        val vy = velocityTracker.yVelocity
        val min = InertialFling.minVelocity(context)
        if (kotlin.math.abs(vy) <= min) return

        val boosted = InertialFling.boost(context, vy.toInt())
        val scrollerVy = -boosted

        TextViewScrollerHelper.abort(this)
        fallbackScroller.abortAnimation()

        if (TextViewScrollerHelper.fling(this, scrollerVy, range)) {
            useFallbackScroller = false
        } else {
            useFallbackScroller = true
            fallbackScroller.fling(scrollX, scrollY, 0, scrollerVy, 0, 0, 0, range)
            postInvalidateOnAnimation()
        }
    }

    private fun maxScrollY(): Int {
        val textLayout = layout ?: return 0
        val range = textLayout.height + compoundPaddingTop + compoundPaddingBottom - height
        return range.coerceAtLeast(0)
    }
}
