package com.example.notesketch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.notesketch.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 设备重启后重新为所有未完成笔记设置复习闹钟
 * （AlarmManager 的闹钟在重启后会被系统清除）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(appContext).noteDao()
                val now = System.currentTimeMillis()
                // 复用 getDue 之外也需要未来的，这里取全部未完成：用较大时间上界
                val all = dao.getDue(Long.MAX_VALUE)
                all.filter { !it.finished && it.nextReviewTime > now }
                    .forEach { ReviewScheduler.schedule(appContext, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
