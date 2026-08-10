package com.example.notesketch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 便签图片存到 app 私有目录 note_images/，库里只存相对文件名。
 */
object NoteImageStore {

    private const val DIR = "note_images"
    private const val CAMERA_DIR = "camera"
    private const val MAX_EDGE = 1600

    fun dir(context: Context): File =
        File(context.filesDir, DIR).also { if (!it.exists()) it.mkdirs() }

    fun fileFor(context: Context, relativeName: String): File? {
        if (relativeName.isBlank()) return null
        val f = File(dir(context), relativeName)
        return if (f.exists()) f else null
    }

    fun createCameraUri(context: Context): Pair<Uri, File> {
        val camDir = File(context.cacheDir, CAMERA_DIR).also { if (!it.exists()) it.mkdirs() }
        val file = File(camDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    /** 将任意 Uri / 临时拍照文件压缩写入 note_images，返回相对文件名。 */
    fun importImage(context: Context, source: Uri): String? {
        val bitmap = decodeScaled(context, source) ?: return null
        return persistBitmap(context, toSoftware(bitmap))
    }

    fun importFile(context: Context, source: File): String? {
        val bitmap = decodeScaledFile(source) ?: return null
        return persistBitmap(context, toSoftware(bitmap))
    }

    fun loadInto(imageView: ImageView, context: Context, relativeName: String?) {
        if (relativeName.isNullOrBlank()) {
            imageView.setImageDrawable(null)
            imageView.visibility = android.view.View.GONE
            return
        }
        val file = fileFor(context, relativeName)
        if (file == null) {
            imageView.setImageDrawable(null)
            imageView.visibility = android.view.View.GONE
            return
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        if (bmp == null) {
            imageView.setImageDrawable(null)
            imageView.visibility = android.view.View.GONE
            return
        }
        imageView.setImageBitmap(bmp)
        imageView.visibility = android.view.View.VISIBLE
    }

    fun delete(context: Context, relativeName: String?) {
        if (relativeName.isNullOrBlank()) return
        fileFor(context, relativeName)?.delete()
    }

    /** 从网页备份的 data URL / base64 写入 note_images，返回相对文件名。 */
    fun importFromDataUrl(context: Context, dataUrl: String): String? {
        val payload = when {
            dataUrl.startsWith("data:", ignoreCase = true) -> {
                val comma = dataUrl.indexOf(',')
                if (comma < 0) return null
                dataUrl.substring(comma + 1)
            }
            else -> dataUrl
        }
        val bytes = try {
            android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        } catch (_: Exception) {
            return null
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return persistBitmap(context, toSoftware(bitmap))
    }

    private fun persistBitmap(context: Context, bitmap: Bitmap): String {
        val name = "${UUID.randomUUID()}.jpg"
        val outFile = File(dir(context), name)
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
        }
        if (!bitmap.isRecycled) bitmap.recycle()
        return name
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(context, uri)?.let { return it }
        }
        return decodeWithBitmapFactory(context, uri)
    }

    private fun decodeWithImageDecoder(context: Context, uri: Uri): Bitmap? {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width.coerceAtLeast(1)
                val h = info.size.height.coerceAtLeast(1)
                val maxEdge = maxOf(w, h)
                if (maxEdge > MAX_EDGE) {
                    val scale = MAX_EDGE.toFloat() / maxEdge
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1)
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeWithBitmapFactory(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun decodeScaledFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun toSoftware(bitmap: Bitmap): Bitmap {
        if (bitmap.config != Bitmap.Config.HARDWARE) return bitmap
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        if (!bitmap.isRecycled) bitmap.recycle()
        return copy ?: bitmap
    }

    private fun sampleSize(w: Int, h: Int): Int {
        var sample = 1
        val max = maxOf(w, h)
        while (max / sample > MAX_EDGE) sample *= 2
        return sample.coerceAtLeast(1)
    }
}
