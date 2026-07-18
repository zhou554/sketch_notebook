package com.example.notesketch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.notesketch.data.Note

/**
 * 负责用 AlarmManager 在笔记的下次复习时间发起精确闹钟。
 */
object ReviewScheduler {

    const val EXTRA_NOTE_ID = "note_id"
    const val EXTRA_NOTE_TITLE = "note_title"
    const val EXTRA_STAGE = "note_stage"

    fun schedule(context: Context, note: Note) {
        if (note.finished || note.nextReviewTime <= 0) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, note)

        val triggerAt = note.nextReviewTime
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pending
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            // 无精确闹钟权限时降级为非精确闹钟
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, note: Note) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, note))
    }

    private fun pendingIntent(context: Context, note: Note): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_NOTE_ID, note.id)
            putExtra(EXTRA_NOTE_TITLE, note.title)
            putExtra(EXTRA_STAGE, note.stage)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, note.id.toInt(), intent, flags)
    }
}
