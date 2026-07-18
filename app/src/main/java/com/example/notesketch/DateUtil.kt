package com.example.notesketch

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtil {

    private val fmt = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())

    /** 把复习时间描述成友好文案 */
    fun reviewText(nextReviewTime: Long, finished: Boolean): String {
        if (finished) return "已完成全部复习 🎉"
        val now = System.currentTimeMillis()
        val diff = nextReviewTime - now
        val dayMs = 24L * 60 * 60 * 1000
        return when {
            diff <= 0 -> "⏰ 待复习"
            diff < dayMs -> "下次复习：今天 " + fmt.format(Date(nextReviewTime))
            diff < 2 * dayMs -> "下次复习：明天"
            else -> "下次复习：" + fmt.format(Date(nextReviewTime))
        }
    }

    fun isDue(nextReviewTime: Long, finished: Boolean): Boolean {
        return !finished && nextReviewTime <= System.currentTimeMillis()
    }
}
