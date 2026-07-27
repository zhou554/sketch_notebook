package com.example.notesketch

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 拍照 / 相册多选，每张图回调相对文件名。
 * 相册使用 GetMultipleContents（比 Photo Picker 在更多机型上可用）。
 */
class NoteImageActions(
    private val activity: ComponentActivity,
    private val onImagesReady: (List<String>) -> Unit
) {
    private var cameraFile: File? = null

    private val takePicture = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = cameraFile
        cameraFile = null
        if (!success || file == null || !file.exists()) return@registerForActivityResult
        activity.lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                NoteImageStore.importFile(activity, file).also { file.delete() }
            }
            if (name != null) onImagesReady(listOf(name))
            else Toast.makeText(activity, "照片保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImages = activity.registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        activity.lifecycleScope.launch {
            val names = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    try {
                        NoteImageStore.importImage(activity, uri)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            when {
                names.isNotEmpty() -> onImagesReady(names)
                else -> Toast.makeText(
                    activity,
                    "图片导入失败（格式可能不支持）",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val requestCamera = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(activity, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
    }

    fun takePhoto() {
        val granted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }

    fun pickFromGallery() {
        pickImages.launch("image/*")
    }

    private fun launchCamera() {
        val (uri, file) = NoteImageStore.createCameraUri(activity)
        cameraFile = file
        takePicture.launch(uri)
    }
}
