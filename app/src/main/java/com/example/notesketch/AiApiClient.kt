package com.example.notesketch

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val role: String, val content: String)

sealed class AiApiResult {
    data class Success(val content: String) : AiApiResult()
    data class Error(val message: String, val code: Int? = null) : AiApiResult()
}

object AiApiClient {

    private const val TIMEOUT_MS = 60_000

    fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>
    ): AiApiResult {
        val root = baseUrl.trim().trimEnd('/')
        if (root.isBlank()) return AiApiResult.Error("请填写 API Base URL")
        if (apiKey.isBlank()) return AiApiResult.Error("请填写 API Key")
        if (model.isBlank()) return AiApiResult.Error("请填写 Model")

        val endpoint = if (root.endsWith("/chat/completions")) root else "$root/chat/completions"

        return try {
            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    messages.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                })
            }

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
            }

            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()

            if (code !in 200..299) {
                val msg = parseErrorMessage(raw) ?: "请求失败 (HTTP $code)"
                return AiApiResult.Error(msg, code)
            }

            val json = JSONObject(raw)
            val content = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isBlank()) {
                AiApiResult.Error("响应中没有 AI 回答内容")
            } else {
                AiApiResult.Success(content.trim())
            }
        } catch (e: Exception) {
            AiApiResult.Error(e.message ?: "网络请求失败")
        }
    }

    private fun parseErrorMessage(raw: String): String? {
        if (raw.isBlank()) return null
        return try {
            val json = JSONObject(raw)
            json.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            raw.take(200).ifBlank { null }
        }
    }
}
