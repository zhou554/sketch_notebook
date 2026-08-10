package com.example.notesketch

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/** 带增强 fling 的 ScrollView。 */
class InertialScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.scrollViewStyle
) : ScrollView(context, attrs, defStyleAttr) {

    override fun fling(velocityY: Int) {
        super.fling(InertialFling.boost(context, velocityY))
    }
}
