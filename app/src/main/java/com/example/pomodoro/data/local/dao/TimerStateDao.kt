package com.example.pomodoro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pomodoro.data.local.entity.TimerStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerStateDao {
    @Query("SELECT * FROM timer_states WHERE userId = :userId LIMIT 1")
    fun observeByUser(userId: String): Flow<TimerStateEntity?>

    @Query("SELECT * FROM timer_states WHERE userId = :userId LIMIT 1")
    suspend fun getByUser(userId: String): TimerStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: TimerStateEntity)

    @Query("DELETE FROM timer_states WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
