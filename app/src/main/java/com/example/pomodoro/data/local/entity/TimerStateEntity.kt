package com.example.pomodoro.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timer_states")
data class TimerStateEntity(
    @PrimaryKey val userId: String,
    val mode: String,
    val status: String,
    val durationSec: Long,
    val remainingSec: Long,
    val startedAt: Long?,
    val endAt: Long?,
    val version: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long
)
