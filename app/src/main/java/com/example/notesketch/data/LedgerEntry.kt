package com.example.notesketch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条记账记录。
 * amountCents: 金额（分），始终为正。
 * isExpense: true=支出，false=收入。
 */
@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountCents: Long,
    val isExpense: Boolean = true,
    val category: String,
    val memo: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
