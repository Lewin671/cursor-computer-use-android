package com.example.pomodoro.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val actionType: String,
    val mode: String?,
    val durationSec: Long?,
    val baseVersion: Long,
    val createdAt: Long
)
