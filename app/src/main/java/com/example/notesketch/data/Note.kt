package com.example.notesketch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条学习笔记。
 * stage: 当前复习阶段索引，对应艾宾浩斯间隔表；复习完成后 +1。
 * nextReviewTime: 下次需要复习的时间戳(毫秒)。
 * colorId: 便签底色，对应设置里的主题 id。
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long,
    val stage: Int = 0,
    val nextReviewTime: Long,
    val finished: Boolean = false,
    val colorId: String = "parchment"
)
