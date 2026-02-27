package com.example.pomodoro.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.pomodoro.data.local.dao.FocusHistoryDao
import com.example.pomodoro.data.local.dao.PendingActionDao
import com.example.pomodoro.data.local.dao.TimerStateDao
import com.example.pomodoro.data.local.entity.FocusHistoryEntity
import com.example.pomodoro.data.local.entity.PendingActionEntity
import com.example.pomodoro.data.local.entity.TimerStateEntity

@Database(
    entities = [TimerStateEntity::class, FocusHistoryEntity::class, PendingActionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timerStateDao(): TimerStateDao
    abstract fun focusHistoryDao(): FocusHistoryDao
    abstract fun pendingActionDao(): PendingActionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pomodoro_sync.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
