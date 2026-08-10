package com.example.notesketch

import android.content.Context
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

data class PomodoroDayRecord(
    val date: String,
    val count: Int,
    val minutes: Int
)

data class PomodoroPeriodRecord(
    val label: String,
    val count: Int,
    val minutes: Int
)

object PomodoroStatsStore {

    private const val PREFS = "notesketch_pomodoro_history"
    private const val KEY_HISTORY = "history"

    fun addFocus(context: Context, minutes: Int, count: Int = 1) {
        if (minutes <= 0 && count <= 0) return
        val map = loadHistoryMap(context)
        val today = todayKey()
        val existing = map[today] ?: PomodoroDayRecord(today, 0, 0)
        map[today] = existing.copy(
            count = existing.count + count,
            minutes = existing.minutes + minutes
        )
        saveHistoryMap(context, map)
    }

    fun syncToday(context: Context, count: Int, minutes: Int) {
        val map = loadHistoryMap(context)
        val today = todayKey()
        map[today] = PomodoroDayRecord(today, count.coerceAtLeast(0), minutes.coerceAtLeast(0))
        saveHistoryMap(context, map)
    }

    fun archiveDay(context: Context, date: String, count: Int, minutes: Int) {
        if (count <= 0 && minutes <= 0) return
        val map = loadHistoryMap(context)
        val existing = map[date] ?: PomodoroDayRecord(date, 0, 0)
        map[date] = existing.copy(
            count = maxOf(existing.count, count),
            minutes = maxOf(existing.minutes, minutes)
        )
        saveHistoryMap(context, map)
    }

    fun dayRecords(context: Context, limit: Int = 30): List<PomodoroDayRecord> =
        loadHistoryMap(context).values
            .sortedByDescending { it.date }
            .take(limit)

    fun monthRecords(context: Context): List<PomodoroPeriodRecord> =
        aggregate(loadHistoryMap(context).values) { date ->
            val parts = date.split("-")
            if (parts.size != 3) null
            else "${parts[0]}-${parts[1]}"
        }.entries
            .sortedByDescending { it.key }
            .map { (key, list) ->
                PomodoroPeriodRecord(
                    label = formatMonthLabel(key),
                    count = list.sumOf { it.count },
                    minutes = list.sumOf { it.minutes }
                )
            }

    fun yearRecords(context: Context): List<PomodoroPeriodRecord> =
        aggregate(loadHistoryMap(context).values) { date ->
            date.substringBefore("-").takeIf { it.length == 4 }
        }.entries
            .sortedByDescending { it.key }
            .map { (key, list) ->
                PomodoroPeriodRecord(
                    label = "${key}年",
                    count = list.sumOf { it.count },
                    minutes = list.sumOf { it.minutes }
                )
            }

    fun summaryForDay(context: Context, date: String = todayKey()): PomodoroPeriodRecord {
        val record = loadHistoryMap(context)[date]
        return PomodoroPeriodRecord(
            label = formatDayLabel(date),
            count = record?.count ?: 0,
            minutes = record?.minutes ?: 0
        )
    }

    fun summaryForMonth(context: Context, year: Int, month: Int): PomodoroPeriodRecord {
        val prefix = String.format(Locale.US, "%04d-%02d", year, month)
        val list = loadHistoryMap(context).values.filter { it.date.startsWith(prefix) }
        return PomodoroPeriodRecord(
            label = formatMonthLabel(prefix),
            count = list.sumOf { it.count },
            minutes = list.sumOf { it.minutes }
        )
    }

    fun summaryForYear(context: Context, year: Int): PomodoroPeriodRecord {
        val prefix = year.toString()
        val list = loadHistoryMap(context).values.filter { it.date.startsWith(prefix) }
        return PomodoroPeriodRecord(
            label = "${year}年",
            count = list.sumOf { it.count },
            minutes = list.sumOf { it.minutes }
        )
    }

    fun exportToJson(context: Context): JSONObject {
        val obj = JSONObject()
        loadHistoryMap(context).values.sortedBy { it.date }.forEach { record ->
            obj.put(
                record.date,
                JSONObject()
                    .put("count", record.count)
                    .put("minutes", record.minutes)
            )
        }
        return obj
    }

    fun applyImported(context: Context, incoming: JSONObject?, replace: Boolean) {
        if (incoming == null) return
        val current = if (replace) linkedMapOf() else loadHistoryMap(context)
        incoming.keys().forEach { date ->
            if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return@forEach
            val o = incoming.optJSONObject(date) ?: return@forEach
            val inc = PomodoroDayRecord(
                date = date,
                count = o.optInt("count", 0),
                minutes = o.optInt("minutes", 0)
            )
            val existing = current[date]
            current[date] = if (existing == null || replace) {
                inc
            } else {
                existing.copy(
                    count = maxOf(existing.count, inc.count),
                    minutes = maxOf(existing.minutes, inc.minutes)
                )
            }
        }
        saveHistoryMap(context, current)
    }

    fun todayKey(): String {
        val cal = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun formatDayLabel(date: String): String {
        val parts = date.split("-")
        if (parts.size != 3) return date
        return "${parts[0]}年${parts[1].toInt()}月${parts[2].toInt()}日"
    }

    private fun formatMonthLabel(key: String): String {
        val parts = key.split("-")
        if (parts.size != 2) return key
        return "${parts[0]}年${parts[1].toInt()}月"
    }

    private fun aggregate(
        records: Collection<PomodoroDayRecord>,
        keyOf: (String) -> String?
    ): Map<String, List<PomodoroDayRecord>> {
        val out = linkedMapOf<String, MutableList<PomodoroDayRecord>>()
        records.forEach { record ->
            val key = keyOf(record.date) ?: return@forEach
            out.getOrPut(key) { mutableListOf() }.add(record)
        }
        return out
    }

    private fun loadHistoryMap(context: Context): LinkedHashMap<String, PomodoroDayRecord> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "{}")
            .orEmpty()
        val map = linkedMapOf<String, PomodoroDayRecord>()
        try {
            val obj = JSONObject(raw)
            obj.keys().forEach { date ->
                val o = obj.optJSONObject(date) ?: return@forEach
                map[date] = PomodoroDayRecord(
                    date = date,
                    count = o.optInt("count", 0),
                    minutes = o.optInt("minutes", 0)
                )
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun saveHistoryMap(context: Context, map: Map<String, PomodoroDayRecord>) {
        val obj = JSONObject()
        map.values.sortedBy { it.date }.forEach { record ->
            obj.put(
                record.date,
                JSONObject()
                    .put("count", record.count)
                    .put("minutes", record.minutes)
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, obj.toString())
            .apply()
    }
}
