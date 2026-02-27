package com.example.pomodoro.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "focus_histories",
    indices = [Index(value = ["userId", "createdAt"])]
)
data class FocusHistoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val mode: String,
    val durationSec: Long,
    val startedAt: Long,
    val endedAt: Long,
    val sourceTimerVersion: Long,
    val createdAt: Long
)
