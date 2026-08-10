package com.example.notesketch

import android.widget.OverScroller
import android.widget.TextView
import java.lang.reflect.Field

/**
 * 访问 TextView 内部 [OverScroller]，用于在系统 fling 之后替换为增强速度，
 * 避免与自定义 scroller 叠加造成双重惯性。
 */
internal object TextViewScrollerHelper {

    private val scrollerField: Field? = run {
        try {
            TextView::class.java.getDeclaredField("mScroller").apply { isAccessible = true }
        } catch (_: ReflectiveOperationException) {
            null
        }
    }

    fun abort(view: TextView) {
        scroller(view)?.abortAnimation()
    }

    /** @return 是否成功写入 TextView 内部 scroller */
    fun fling(view: TextView, velocityY: Int, maxScrollY: Int): Boolean {
        val scroller = scroller(view) ?: return false
        scroller.fling(
            view.scrollX,
            view.scrollY,
            0,
            velocityY,
            0,
            0,
            0,
            maxScrollY.coerceAtLeast(0)
        )
        view.postInvalidateOnAnimation()
        return true
    }

    private fun scroller(view: TextView): OverScroller? =
        scrollerField?.get(view) as? OverScroller
}
