package com.example.notesketch

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.max

/**
 * 记住最近一次有效光标，避免打开相机/相册失焦后 selection 变成 -1 导致插图跑到顶部。
 */
class CursorRememberEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    /** 最近一次有效的插入点（选区末端）；-1 表示尚未记录。 */
    var lastCursor: Int = -1
        private set

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (selStart >= 0 && selEnd >= 0) {
            lastCursor = max(selStart, selEnd)
        }
    }

    /** 在打开系统选择器前主动快照当前选区。 */
    fun snapshotCursor() {
        val s = selectionStart
        val e = selectionEnd
        if (s >= 0 && e >= 0) {
            lastCursor = max(s, e)
        }
    }

    fun insertPos(fallbackToEnd: Boolean = true): Int {
        val len = text?.length ?: 0
        val live = max(selectionStart, selectionEnd)
        val pos = when {
            live >= 0 -> live
            lastCursor in 0..len -> lastCursor
            fallbackToEnd -> len
            else -> 0
        }
        return pos.coerceIn(0, len)
    }
}
