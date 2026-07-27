package com.example.notesketch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条心情日记。
 * mood: 自定义心情标题（用户自由输入）。
 * icon: 狐狸表情图标序号（0~5，对应 mood_fox_1~6）。
 * content: 想写的话，可为空。
 */
@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mood: String,
    val icon: Int = 0,
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
