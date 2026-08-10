package com.example.notesketch.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Insert
    suspend fun insert(entry: LedgerEntry): Long

    @Delete
    suspend fun delete(entry: LedgerEntry)

    @Query("SELECT * FROM ledger_entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LedgerEntry>>

    @Query("SELECT COALESCE(SUM(CASE WHEN isExpense = 0 THEN amountCents ELSE 0 END), 0) FROM ledger_entries")
    suspend fun sumIncomeCents(): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LedgerEntry>)

    @Query("DELETE FROM ledger_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM ledger_entries ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<LedgerEntry>
}
