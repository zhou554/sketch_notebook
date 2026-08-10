package com.example.notesketch

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

object DebugLog {
    private const val TAG = "Debug819bd6"
    private const val SESSION = "819bd6"
    private const val FILE = "debug-819bd6.log"

    fun log(
        context: Context,
        location: String,
        message: String,
        hypothesisId: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix"
    ) {
        val payload = JSONObject().apply {
            put("sessionId", SESSION)
            put("runId", runId)
            put("hypothesisId", hypothesisId)
            put("location", location)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
            put("data", JSONObject(data))
        }
        val line = payload.toString()
        Log.d(TAG, line)
        try {
            File(context.filesDir, FILE).appendText("$line\n")
        } catch (_: Exception) {
        }
    }
}
