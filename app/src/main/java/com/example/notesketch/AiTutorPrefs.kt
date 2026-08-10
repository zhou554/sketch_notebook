package com.example.notesketch

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

object AiTutorPrefs {

    private const val PREFS = "notesketch_ai_tutor"
    private const val KEY_BASE_URL = "api_base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "api_model"
    private const val KEY_WEB_URL = "web_url"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"

    private fun plainPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun securePrefs(context: Context): SharedPreferences {
        val app = context.applicationContext
        return try {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                "${PREFS}_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            plainPrefs(app)
        }
    }

    fun apiBaseUrl(context: Context): String =
        plainPrefs(context).getString(KEY_BASE_URL, "").orEmpty()

    fun setApiBaseUrl(context: Context, value: String) {
        plainPrefs(context).edit().putString(KEY_BASE_URL, value.trim()).apply()
    }

    fun apiKey(context: Context): String =
        securePrefs(context).getString(KEY_API_KEY, "").orEmpty()

    fun setApiKey(context: Context, value: String) {
        securePrefs(context).edit().putString(KEY_API_KEY, value.trim()).apply()
    }

    fun apiModel(context: Context): String =
        plainPrefs(context).getString(KEY_MODEL, "gpt-4o-mini").orEmpty()

    fun setApiModel(context: Context, value: String) {
        plainPrefs(context).edit().putString(KEY_MODEL, value.trim()).apply()
    }

    fun webUrl(context: Context): String =
        plainPrefs(context).getString(KEY_WEB_URL, "").orEmpty()

    fun setWebUrl(context: Context, value: String) {
        plainPrefs(context).edit().putString(KEY_WEB_URL, value.trim()).apply()
    }

    fun systemPrompt(context: Context): String =
        plainPrefs(context).getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT).orEmpty()

    fun setSystemPrompt(context: Context, value: String) {
        plainPrefs(context).edit().putString(KEY_SYSTEM_PROMPT, value.trim()).apply()
    }

    fun isApiConfigured(context: Context): Boolean {
        return apiBaseUrl(context).isNotBlank() &&
            apiKey(context).isNotBlank() &&
            apiModel(context).isNotBlank()
    }

    fun exportToJson(context: Context): JSONObject =
        JSONObject()
            .put("baseUrl", apiBaseUrl(context))
            .put("apiKey", apiKey(context))
            .put("model", apiModel(context))
            .put("webUrl", webUrl(context))
            .put("systemPrompt", systemPrompt(context))

    fun applyImported(context: Context, incoming: JSONObject, replace: Boolean) {
        val current = if (replace) JSONObject() else exportToJson(context)
        val out = mergeJson(current, incoming)
        setApiBaseUrl(context, out.optString("baseUrl", ""))
        setApiKey(context, out.optString("apiKey", ""))
        setApiModel(context, out.optString("model", "gpt-4o-mini"))
        setWebUrl(context, out.optString("webUrl", ""))
        val prompt = out.optString("systemPrompt", DEFAULT_SYSTEM_PROMPT)
        setSystemPrompt(context, if (prompt.isBlank()) DEFAULT_SYSTEM_PROMPT else prompt)
    }

    private fun mergeJson(local: JSONObject, incoming: JSONObject): JSONObject {
        fun pick(key: String, default: String = ""): String {
            return if (incoming.has(key)) incoming.optString(key, default)
            else local.optString(key, default)
        }
        return JSONObject()
            .put("baseUrl", pick("baseUrl"))
            .put("apiKey", pick("apiKey"))
            .put("model", pick("model", "gpt-4o-mini"))
            .put("webUrl", pick("webUrl"))
            .put("systemPrompt", pick("systemPrompt", DEFAULT_SYSTEM_PROMPT))
    }

    const val DEFAULT_SYSTEM_PROMPT =
        "你是一位耐心、清晰的 AI 学习导师。用简洁的中文回答，必要时给出步骤与例子。"
}
