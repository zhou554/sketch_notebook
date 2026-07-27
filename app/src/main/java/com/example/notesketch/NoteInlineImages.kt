package com.example.notesketch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.widget.EditText
import android.widget.TextView

/**
 * 正文内联图片：存库用 {{img:filename.jpg}}，编辑/展示用 ImageSpan。
 */
object NoteInlineImages {

    private val MARKER = Regex("""\{\{img:([^}]+)\}\}""")
    private const val OBJ = '\uFFFC'
    private const val MAX_DISPLAY_EDGE = 720

    fun marker(fileName: String): String = "{{img:$fileName}}"

    fun migrateLegacy(content: String, imagePath: String): String {
        if (imagePath.isBlank()) return content
        if (content.contains(marker(imagePath))) return content
        val trimmed = content.trimEnd()
        return if (trimmed.isEmpty()) marker(imagePath)
        else "$trimmed\n${marker(imagePath)}"
    }

    fun listedImages(content: String): List<String> =
        MARKER.findAll(content).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()

    fun firstImage(content: String): String =
        listedImages(content).firstOrNull().orEmpty()

    fun plainPreview(content: String): String =
        MARKER.replace(content, " ").replace(Regex("\\s+"), " ").trim()

    fun toSpannable(context: Context, content: String, maxWidthPx: Int): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        var last = 0
        for (match in MARKER.findAll(content)) {
            if (match.range.first > last) {
                builder.append(content.substring(last, match.range.first))
            }
            val fileName = match.groupValues[1].trim()
            val start = builder.length
            builder.append(OBJ)
            attachSpan(context, builder, start, start + 1, fileName, maxWidthPx)
            last = match.range.last + 1
        }
        if (last < content.length) {
            builder.append(content.substring(last))
        }
        return builder
    }

    fun serialize(editable: CharSequence): String {
        val spanned = editable as? Spannable ?: return editable.toString()
        val out = StringBuilder()
        var i = 0
        while (i < spanned.length) {
            val spans = spanned.getSpans(i, i + 1, NoteImageSpan::class.java)
            val span = spans.firstOrNull { spanned.getSpanStart(it) == i }
            if (span != null) {
                out.append(marker(span.fileName))
                i = spanned.getSpanEnd(span)
            } else {
                out.append(spanned[i])
                i++
            }
        }
        return out.toString()
    }

    fun insertAtCursor(editText: EditText, context: Context, fileName: String) {
        val editable = editText.text ?: return
        if (editText is CursorRememberEditText) {
            editText.snapshotCursor()
        }
        val pos = when (editText) {
            is CursorRememberEditText -> editText.insertPos(fallbackToEnd = true)
            else -> {
                val live = maxOf(editText.selectionStart, editText.selectionEnd)
                if (live >= 0) live.coerceAtMost(editable.length)
                else editable.length
            }
        }
        val width = (editText.width - editText.paddingLeft - editText.paddingRight)
            .coerceAtLeast((editText.resources.displayMetrics.widthPixels * 0.7f).toInt())
        editable.insert(pos, OBJ.toString())
        attachSpan(context, editable, pos, pos + 1, fileName, width)
        editText.setSelection(pos + 1)
        if (editText is CursorRememberEditText) {
            // setSelection 会再走 onSelectionChanged，刷新 lastCursor
        }
        editText.requestFocus()
    }

    fun bindToTextView(textView: TextView, context: Context, content: String) {
        textView.post {
            if (content.isBlank()) {
                textView.text = "（无正文）"
                return@post
            }
            val width = (textView.width - textView.paddingLeft - textView.paddingRight)
                .coerceAtLeast((context.resources.displayMetrics.widthPixels * 0.7f).toInt())
            textView.text = toSpannable(context, content, width)
        }
    }

    fun bindToEditText(editText: EditText, context: Context, content: String) {
        editText.post {
            val width = (editText.width - editText.paddingLeft - editText.paddingRight)
                .coerceAtLeast((context.resources.displayMetrics.widthPixels * 0.7f).toInt())
            editText.setText(toSpannable(context, content, width))
            val end = editText.text?.length ?: 0
            editText.setSelection(end)
            if (editText is CursorRememberEditText) {
                // setSelection 已更新 lastCursor
            }
        }
    }

    fun deleteUnreferenced(
        context: Context,
        previousContent: String,
        previousImagePath: String,
        newContent: String
    ) {
        val old = (
            listedImages(previousContent) +
                listOfNotNull(previousImagePath.takeIf { it.isNotBlank() })
            ).toSet()
        val keep = listedImages(newContent).toSet()
        (old - keep).forEach { NoteImageStore.delete(context, it) }
    }

    private fun attachSpan(
        context: Context,
        editable: Spannable,
        start: Int,
        end: Int,
        fileName: String,
        maxWidthPx: Int
    ) {
        val bmp = loadScaled(context, fileName, maxWidthPx) ?: return
        val drawable = BitmapDrawable(context.resources, bmp).apply {
            setBounds(0, 0, bmp.width, bmp.height)
        }
        editable.setSpan(
            NoteImageSpan(drawable, fileName),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun loadScaled(context: Context, fileName: String, maxWidthPx: Int): Bitmap? {
        val file = NoteImageStore.fileFor(context, fileName) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        val maxEdge = maxOf(bounds.outWidth, bounds.outHeight, 1)
        val target = maxOf(maxWidthPx, 1).coerceAtMost(MAX_DISPLAY_EDGE)
        while (maxEdge / sample > target * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        if (decoded.width <= maxWidthPx) return decoded
        val h = (decoded.height * (maxWidthPx.toFloat() / decoded.width)).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(decoded, maxWidthPx, h, true).also {
            if (it !== decoded && !decoded.isRecycled) decoded.recycle()
        }
    }
}

class NoteImageSpan(
    drawable: Drawable,
    val fileName: String
) : ImageSpan(drawable, ALIGN_BOTTOM)
