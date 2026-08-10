package com.example.notesketch

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * 列表惯性滚动：增强向下 fling，便于一次滑到页面底部。
 * 配合 MainActivity 在 [SCROLL_STATE_IDLE] 且已到底时才允许上拉新建。
 */
class InertialRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var scrollIdleListener: ((Boolean) -> Unit)? = null
    var atBottomListener: ((Boolean) -> Unit)? = null

    init {
        addOnScrollListener(object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                scrollIdleListener?.invoke(newState == SCROLL_STATE_IDLE)
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                atBottomListener?.invoke(!canScrollVertically(1))
            }
        })
    }

    fun isAtBottom(): Boolean = !canScrollVertically(1)

    fun isScrollIdle(): Boolean = scrollState == SCROLL_STATE_IDLE

    override fun fling(velocityX: Int, velocityY: Int): Boolean {
        val vy = InertialFling.boost(context, velocityY)
        return super.fling(velocityX, vy)
    }
}
