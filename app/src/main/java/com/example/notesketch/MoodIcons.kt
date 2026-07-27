package com.example.notesketch

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.LinearLayout

/** 心情日记的 6 只狐狸表情图标（mood_fox_1 ~ mood_fox_6，透明底贴纸） */
object MoodIcons {

    val DRAWABLES = intArrayOf(
        R.drawable.mood_fox_1,
        R.drawable.mood_fox_2,
        R.drawable.mood_fox_3,
        R.drawable.mood_fox_4,
        R.drawable.mood_fox_5,
        R.drawable.mood_fox_6
    )

    /** 每个图标对应一张贴纸底色 */
    private val COLOR_IDS = arrayOf("parchment", "sage", "forest", "rose", "parchment", "ink")

    fun drawableOf(icon: Int): Int = DRAWABLES[icon.coerceIn(0, DRAWABLES.lastIndex)]

    fun colorIdOf(icon: Int): String = COLOR_IDS[icon.coerceIn(0, COLOR_IDS.lastIndex)]

    /** 在横排容器里铺开 6 个可点选的狐狸图标，选中的带白底描边高亮 */
    fun bindPicker(row: LinearLayout, sizeDp: Int, selected: Int, onSelect: (Int) -> Unit) {
        row.removeAllViews()
        val d = row.resources.displayMetrics.density
        val size = (sizeDp * d).toInt()
        val pad = (4 * d).toInt()
        DRAWABLES.forEachIndexed { index, resId ->
            val iv = ImageView(row.context).apply {
                setImageResource(resId)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(pad, pad, pad, pad)
                contentDescription = "心情图标${index + 1}"
                background = GradientDrawable().apply {
                    cornerRadius = 10 * d
                    if (index == selected) {
                        setColor(Color.parseColor("#FFFEF8"))
                        setStroke((1.5f * d).toInt().coerceAtLeast(2), Color.parseColor("#3D5C4A"))
                    } else {
                        setColor(Color.TRANSPARENT)
                    }
                }
                setOnClickListener { onSelect(index) }
            }
            val lp = LinearLayout.LayoutParams(size, size)
            if (index > 0) lp.marginStart = (6 * d).toInt()
            row.addView(iv, lp)
        }
    }
}
