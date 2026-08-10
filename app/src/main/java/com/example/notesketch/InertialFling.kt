package com.example.notesketch

import android.content.Context
import android.provider.Settings
import android.view.ViewConfiguration

/**
 * 惯性滚动速度增强，对齐 preview 中「快速滑动后松手继续滑一段再停」的手感。
 * 触摸设备上 Android 本身有 fling；此处适度放大初速度，便于列表一次滑到底。
 */
object InertialFling {

    /** 向下滑（内容向上走 / 接近底部） */
    const val BOOST_DOWN = 1.32f

    /** 向上滑（内容向下走 / 回到顶部） */
    const val BOOST_UP = 1.14f

    fun minVelocity(context: Context): Int =
        ViewConfiguration.get(context).scaledMinimumFlingVelocity

    fun boost(context: Context, velocity: Int): Int {
        if (isReducedMotion(context)) return velocity
        val min = minVelocity(context)
        return when {
            velocity > min -> (velocity * BOOST_DOWN).toInt()
            velocity < -min -> (velocity * BOOST_UP).toInt()
            else -> velocity
        }
    }

    /** 系统关闭过渡/动画时跳过增强（对齐 preview prefers-reduced-motion） */
    fun isReducedMotion(context: Context): Boolean {
        val cr = context.contentResolver
        val transition = Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        val animator = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        return transition == 0f || animator == 0f
    }
}
