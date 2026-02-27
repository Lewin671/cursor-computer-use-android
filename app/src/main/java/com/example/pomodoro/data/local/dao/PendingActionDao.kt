package com.example.pomodoro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pomodoro.data.local.entity.PendingActionEntity

@Dao
interface PendingActionDao {
    @Query("SELECT * FROM pending_actions WHERE userId = :userId ORDER BY id ASC")
    suspend fun listForUser(userId: String): List<PendingActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PendingActionEntity): Long

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_actions WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
