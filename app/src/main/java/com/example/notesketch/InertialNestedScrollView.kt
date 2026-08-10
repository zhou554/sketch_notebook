package com.example.notesketch

import android.content.Context
import android.util.AttributeSet
import androidx.core.widget.NestedScrollView

/** 带增强 fling 的 NestedScrollView。 */
class InertialNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    override fun fling(velocityY: Int) {
        super.fling(InertialFling.boost(context, velocityY))
    }
}
