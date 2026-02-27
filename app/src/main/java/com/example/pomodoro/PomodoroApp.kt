package com.example.pomodoro

import android.app.Application
import com.example.pomodoro.data.sync.SyncWorker

class PomodoroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.get(this)
        SyncWorker.enqueue(this)
    }
}
