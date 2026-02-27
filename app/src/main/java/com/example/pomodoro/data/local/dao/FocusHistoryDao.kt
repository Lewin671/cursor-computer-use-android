package com.example.pomodoro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pomodoro.data.local.entity.FocusHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusHistoryDao {
    @Query("SELECT * FROM focus_histories WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: String): Flow<List<FocusHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FocusHistoryEntity>)

    @Query("SELECT MAX(createdAt) FROM focus_histories WHERE userId = :userId")
    suspend fun newestCreatedAt(userId: String): Long?

    @Query("DELETE FROM focus_histories WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
