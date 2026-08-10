package com.example.notesketch

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.notesketch.data.AppDatabase
import com.example.notesketch.data.LedgerEntry
import com.example.notesketch.data.MoodEntry
import com.example.notesketch.data.Note
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

object BackupManager {

    const val FORMAT = "notesketch-backup"
    const val FORMAT_VERSION = 1
    const val APP_ID = "scrapbook-forest"

    private const val POMO_PREFS = "notesketch_pomodoro"
    private const val POMO_DAY = "day"
    private const val POMO_COUNT = "count"
    private const val POMO_MINUTES = "minutes"
    private const val POMO_WORK_MIN = "work_min"
    private const val POMO_SHORT_MIN = "short_min"
    private const val POMO_LONG_MIN = "long_min"

    data class ExportSummary(
        val noteCount: Int,
        val ledgerCount: Int,
        val moodCount: Int,
        val approxBytes: Int
    )

    data class ImportSummary(
        val noteCount: Int,
        val ledgerCount: Int,
        val moodCount: Int,
        val approxBytes: Int
    )

    sealed class BackupError(message: String) : Exception(message) {
        class Invalid(message: String) : BackupError(message)
        class Io(message: String) : BackupError(message)
    }

    enum class ImportMode { MERGE, REPLACE }

    suspend fun buildEnvelope(context: Context): JSONObject {
        val db = AppDatabase.get(context)
        val notes = db.noteDao().getAllOnce().map { noteToJson(context, it) }
        val ledger = db.ledgerDao().getAllOnce().map { ledgerToJson(it) }
        val mood = db.moodDao().getAllOnce().map { moodToJson(context, it) }
        val prefs = UiPrefs.exportToJson(context)
        val aiTutor = AiTutorPrefs.exportToJson(context)
        val pomodoro = readPomodoroStats(context)
        val data = JSONObject()
            .put("notes", JSONArray(notes))
            .put("prefs", prefs)
            .put("ledger", JSONArray(ledger))
            .put("mood", JSONArray(mood))
            .put("aiTutor", aiTutor)
            .put("pomodoro", pomodoro)
        val envelope = JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("app", APP_ID)
            .put("exportedAt", isoNow())
            .put(
                "exporter",
                JSONObject()
                    .put("platform", "android")
                    .put("package", context.packageName)
            )
            .put("data", data)
        val bytes = envelope.toString(2).toByteArray(Charsets.UTF_8).size
        envelope.put(
            "meta",
            JSONObject()
                .put("noteCount", notes.size)
                .put("ledgerCount", ledger.size)
                .put("moodCount", mood.size)
                .put("approxBytes", bytes)
        )
        return envelope
    }

    suspend fun exportSummary(context: Context): ExportSummary {
        val env = buildEnvelope(context)
        val meta = env.optJSONObject("meta") ?: JSONObject()
        return ExportSummary(
            noteCount = meta.optInt("noteCount"),
            ledgerCount = meta.optInt("ledgerCount"),
            moodCount = meta.optInt("moodCount"),
            approxBytes = meta.optInt("approxBytes")
        )
    }

    suspend fun writeToUri(context: Context, uri: Uri) {
        val json = buildEnvelope(context).toString(2)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: throw BackupError.Io("无法写入备份文件")
    }

    fun readFromUri(context: Context, uri: Uri): JSONObject {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw BackupError.Io("无法读取备份文件")
        return parseBackupText(text)
    }

    fun validate(doc: JSONObject): JSONObject {
        if (doc.optString("format") != FORMAT) {
            throw BackupError.Invalid("不是 NoteSketch 备份文件")
        }
        if (doc.optInt("formatVersion") > FORMAT_VERSION) {
            throw BackupError.Invalid("备份版本过新，请更新 App")
        }
        if (doc.optInt("formatVersion") != FORMAT_VERSION) {
            throw BackupError.Invalid("不支持的备份版本")
        }
        val app = doc.optString("app")
        if (app.isNotBlank() && app != APP_ID && app != "notesketch-android") {
            throw BackupError.Invalid("此备份来自「$app」，无法导入当前 App")
        }
        val data = doc.optJSONObject("data")
            ?: throw BackupError.Invalid("备份缺少 data 字段")
        if (data.opt("notes") !is JSONArray) {
            throw BackupError.Invalid("备份 notes 格式错误")
        }
        if (data.has("prefs") && data.opt("prefs") !is JSONObject) {
            throw BackupError.Invalid("备份 prefs 格式错误")
        }
        if (data.has("ledger") && data.opt("ledger") !is JSONArray) {
            throw BackupError.Invalid("备份 ledger 格式错误")
        }
        if (data.has("mood") && data.opt("mood") !is JSONArray) {
            throw BackupError.Invalid("备份 mood 格式错误")
        }
        if (data.has("aiTutor") && data.opt("aiTutor") !is JSONObject) {
            throw BackupError.Invalid("备份 aiTutor 格式错误")
        }
        return doc
    }

    fun importSummary(doc: JSONObject): ImportSummary {
        val data = doc.getJSONObject("data")
        val notes = data.optJSONArray("notes") ?: JSONArray()
        val ledger = data.optJSONArray("ledger") ?: JSONArray()
        val mood = data.optJSONArray("mood") ?: JSONArray()
        val bytes = estimateImportBytes(data)
        return ImportSummary(notes.length(), ledger.length(), mood.length(), bytes)
    }

    suspend fun applyImport(context: Context, doc: JSONObject, mode: ImportMode) {
        validate(doc)
        val data = doc.getJSONObject("data")
        val db = AppDatabase.get(context)
        val incomingNotes = parseNotes(context, data.optJSONArray("notes") ?: JSONArray())
        val incomingLedger = parseLedger(data.optJSONArray("ledger") ?: JSONArray())
        val incomingMood = parseMood(context, data.optJSONArray("mood") ?: JSONArray())
        val incomingPrefs = data.optJSONObject("prefs")
        val incomingPomodoro = data.optJSONObject("pomodoro")
        val incomingAiTutor = data.optJSONObject("aiTutor")

        if (mode == ImportMode.REPLACE) {
            db.noteDao().deleteAll()
            if (incomingNotes.isNotEmpty()) db.noteDao().insertAll(incomingNotes)
            db.ledgerDao().deleteAll()
            if (incomingLedger.isNotEmpty()) db.ledgerDao().insertAll(incomingLedger)
            if (data.has("mood")) {
                db.moodDao().deleteAll()
                if (incomingMood.isNotEmpty()) db.moodDao().insertAll(incomingMood)
            }
            incomingPrefs?.let { UiPrefs.applyImportedPrefs(context, it, replace = true) }
            if (data.has("aiTutor") && incomingAiTutor != null) {
                AiTutorPrefs.applyImported(context, incomingAiTutor, replace = true)
            }
            if (data.has("pomodoro") && incomingPomodoro != null) {
                writePomodoroStats(context, incomingPomodoro, replaceHistory = true)
            }
            return
        }

        val mergedNotes = mergeNotes(db.noteDao().getAllOnce(), incomingNotes)
        val mergedLedger = mergeLedger(db.ledgerDao().getAllOnce(), incomingLedger)
        val mergedMood = mergeMood(db.moodDao().getAllOnce(), incomingMood)
        db.noteDao().deleteAll()
        if (mergedNotes.isNotEmpty()) db.noteDao().insertAll(mergedNotes)
        db.ledgerDao().deleteAll()
        if (mergedLedger.isNotEmpty()) db.ledgerDao().insertAll(mergedLedger)
        db.moodDao().deleteAll()
        if (mergedMood.isNotEmpty()) db.moodDao().insertAll(mergedMood)
        incomingPrefs?.let { UiPrefs.applyImportedPrefs(context, it, replace = false) }
        incomingAiTutor?.let { AiTutorPrefs.applyImported(context, it, replace = false) }
        incomingPomodoro?.let { writePomodoroStats(context, it, replaceHistory = false) }
    }

    fun defaultExportFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(Date())
        return "notesketch-backup-scrapbook-forest-$stamp.json"
    }

    fun formatBytes(bytes: Int): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }

    private fun parseBackupText(text: String): JSONObject {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) {
            val notes = JSONArray(trimmed)
            return JSONObject()
                .put("format", FORMAT)
                .put("formatVersion", FORMAT_VERSION)
                .put("app", APP_ID)
                .put(
                    "data",
                    JSONObject()
                        .put("notes", notes)
                        .put("prefs", defaultPrefsJson())
                        .put("ledger", JSONArray())
                )
        }
        return JSONObject(trimmed)
    }

    private fun defaultPrefsJson() = JSONObject()
        .put("brightness", 100)
        .put("themeId", "forest")
        .put("paperType", "grid")
        .put("customThemes", JSONArray())
        .put("hiddenThemes", JSONArray())

    private fun estimateImportBytes(data: JSONObject): Int =
        (data.optJSONArray("notes")?.toString()?.length ?: 0) +
            (data.optJSONObject("prefs")?.toString()?.length ?: 0) +
            (data.optJSONArray("ledger")?.toString()?.length ?: 0) +
            (data.optJSONArray("mood")?.toString()?.length ?: 0) +
            (data.optJSONObject("aiTutor")?.toString()?.length ?: 0) +
            (data.optJSONObject("pomodoro")?.toString()?.length ?: 0)

    private fun inlineImagesToJson(context: Context, content: String): JSONObject? {
        val images = JSONObject()
        NoteInlineImages.listedImages(content).forEach { fileName ->
            val file = NoteImageStore.fileFor(context, fileName) ?: return@forEach
            val bytes = file.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            images.put(fileName, "data:image/jpeg;base64,$b64")
        }
        return if (images.length() > 0) images else null
    }

    private fun noteToJson(context: Context, note: Note): JSONObject {
        val obj = JSONObject()
            .put("id", note.id)
            .put("title", note.title)
            .put("content", note.content)
            .put("createdAt", note.createdAt)
            .put("updatedAt", note.createdAt)
            .put("stage", note.stage)
            .put("nextReviewTime", note.nextReviewTime)
            .put("finished", note.finished)
            .put("colorId", note.colorId)
        inlineImagesToJson(context, note.content)?.let { obj.put("images", it) }
        return obj
    }

    private fun ledgerToJson(entry: LedgerEntry): JSONObject =
        JSONObject()
            .put("id", entry.id)
            .put("amountCents", entry.amountCents)
            .put("isExpense", entry.isExpense)
            .put("category", entry.category)
            .put("memo", entry.memo)
            .put("createdAt", entry.createdAt)
            .put("updatedAt", entry.createdAt)

    private fun moodToJson(context: Context, entry: MoodEntry): JSONObject {
        val obj = JSONObject()
            .put("id", entry.id)
            .put("mood", entry.mood)
            .put("icon", entry.icon)
            .put("content", entry.content)
            .put("createdAt", entry.createdAt)
            .put("updatedAt", entry.createdAt)
        inlineImagesToJson(context, entry.content)?.let { obj.put("images", it) }
        return obj
    }

    private suspend fun parseNotes(context: Context, arr: JSONArray): List<Note> {
        val out = mutableListOf<Note>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var content = o.optString("content", "")
            val images = o.optJSONObject("images")
            if (images != null) {
                val keys = images.keys()
                while (keys.hasNext()) {
                    val oldKey = keys.next()
                    val dataUrl = images.optString(oldKey)
                    if (dataUrl.isBlank()) continue
                    val fileName = NoteImageStore.importFromDataUrl(context, dataUrl) ?: continue
                    content = content.replace("{{img:$oldKey}}", NoteInlineImages.marker(fileName))
                }
            }
            val imagePath = NoteInlineImages.firstImage(content)
            out.add(
                Note(
                    id = o.optLong("id").takeIf { it > 0 } ?: 0L,
                    title = o.optString("title", ""),
                    content = content,
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    stage = o.optInt("stage", 0),
                    nextReviewTime = o.optLong("nextReviewTime", System.currentTimeMillis()),
                    finished = o.optBoolean("finished", false),
                    colorId = o.optString("colorId", "parchment").ifBlank { "parchment" },
                    imagePath = imagePath
                )
            )
        }
        return out
    }

    private fun parseLedger(arr: JSONArray): List<LedgerEntry> {
        val out = mutableListOf<LedgerEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                LedgerEntry(
                    id = o.optLong("id").takeIf { it > 0 } ?: 0L,
                    amountCents = o.optLong("amountCents"),
                    isExpense = o.optBoolean("isExpense", true),
                    category = o.optString("category", "其他"),
                    memo = o.optString("memo", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return out
    }

    private suspend fun parseMood(context: Context, arr: JSONArray): List<MoodEntry> {
        val out = mutableListOf<MoodEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var content = o.optString("content", "")
            val images = o.optJSONObject("images")
            if (images != null) {
                val keys = images.keys()
                while (keys.hasNext()) {
                    val oldKey = keys.next()
                    val dataUrl = images.optString(oldKey)
                    if (dataUrl.isBlank()) continue
                    val fileName = NoteImageStore.importFromDataUrl(context, dataUrl) ?: continue
                    content = content.replace("{{img:$oldKey}}", NoteInlineImages.marker(fileName))
                }
            }
            out.add(
                MoodEntry(
                    id = o.optLong("id").takeIf { it > 0 } ?: 0L,
                    mood = o.optString("mood", MoodDiaryActivity.DEFAULT_TITLE),
                    icon = o.optInt("icon", 0),
                    content = content,
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return out
    }

    private fun mergeNotes(local: List<Note>, incoming: List<Note>): List<Note> {
        val byId = local.associateBy { it.id }.toMutableMap()
        var maxId = byId.keys.maxOrNull() ?: 0L
        incoming.forEach { inc ->
            val existing = byId[inc.id]
            if (existing != null) {
                byId[inc.id] = if (inc.createdAt >= existing.createdAt) inc else existing
                return@forEach
            }
            var id = inc.id
            if (id <= 0L || byId.containsKey(id)) {
                maxId += 1
                id = maxId
            } else {
                maxId = max(maxId, id)
            }
            byId[id] = inc.copy(id = id)
        }
        return byId.values.sortedBy { it.createdAt }
    }

    private fun mergeLedger(local: List<LedgerEntry>, incoming: List<LedgerEntry>): List<LedgerEntry> {
        val byId = local.associateBy { it.id }.toMutableMap()
        var maxId = byId.keys.maxOrNull() ?: 0L
        incoming.forEach { inc ->
            val existing = byId[inc.id]
            if (existing != null) {
                byId[inc.id] = if (inc.createdAt >= existing.createdAt) inc else existing
                return@forEach
            }
            var id = inc.id
            if (id <= 0L || byId.containsKey(id)) {
                maxId += 1
                id = maxId
            } else {
                maxId = max(maxId, id)
            }
            byId[id] = inc.copy(id = id)
        }
        return byId.values.sortedByDescending { it.createdAt }
    }

    private fun mergeMood(local: List<MoodEntry>, incoming: List<MoodEntry>): List<MoodEntry> {
        val byId = local.associateBy { it.id }.toMutableMap()
        var maxId = byId.keys.maxOrNull() ?: 0L
        incoming.forEach { inc ->
            val existing = byId[inc.id]
            if (existing != null) {
                byId[inc.id] = if (inc.createdAt >= existing.createdAt) inc else existing
                return@forEach
            }
            var id = inc.id
            if (id <= 0L || byId.containsKey(id)) {
                maxId += 1
                id = maxId
            } else {
                maxId = max(maxId, id)
            }
            byId[id] = inc.copy(id = id)
        }
        return byId.values.sortedByDescending { it.createdAt }
    }

    private fun readPomodoroStats(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(POMO_PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("day", prefs.getString(POMO_DAY, "") ?: "")
            .put("count", prefs.getInt(POMO_COUNT, 0))
            .put("minutes", prefs.getInt(POMO_MINUTES, 0))
            .put("workMin", prefs.getInt(POMO_WORK_MIN, 25))
            .put("shortMin", prefs.getInt(POMO_SHORT_MIN, 5))
            .put("longMin", prefs.getInt(POMO_LONG_MIN, 15))
            .put("history", PomodoroStatsStore.exportToJson(context))
    }

    private fun writePomodoroStats(context: Context, obj: JSONObject, replaceHistory: Boolean) {
        context.getSharedPreferences(POMO_PREFS, Context.MODE_PRIVATE).edit()
            .putString(POMO_DAY, obj.optString("day", todayKey()))
            .putInt(POMO_COUNT, obj.optInt("count", 0))
            .putInt(POMO_MINUTES, obj.optInt("minutes", 0))
            .putInt(POMO_WORK_MIN, obj.optInt("workMin", 25).coerceIn(1, 180))
            .putInt(POMO_SHORT_MIN, obj.optInt("shortMin", 5).coerceIn(1, 60))
            .putInt(POMO_LONG_MIN, obj.optInt("longMin", 15).coerceIn(1, 60))
            .apply()
        PomodoroStatsStore.applyImported(
            context,
            obj.optJSONObject("history"),
            replace = replaceHistory
        )
    }

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
}
