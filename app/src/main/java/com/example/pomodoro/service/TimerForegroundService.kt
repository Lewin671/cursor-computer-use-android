package com.example.pomodoro.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.pomodoro.R
import com.example.pomodoro.ServiceLocator
import com.example.pomodoro.domain.TimerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimerForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var runningJob: Job? = null
    private val locator by lazy { ServiceLocator.get(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START -> {
                val userId = intent.getStringExtra(EXTRA_USER_ID) ?: return START_NOT_STICKY
                startForeground(NOTIFICATION_ID, buildNotification("00:00"))
                startTicker(userId)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runningJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTicker(userId: String) {
        runningJob?.cancel()
        runningJob = serviceScope.launch {
            while (true) {
                val state = locator.timerRepository.getCurrentState(userId)
                if (state == null || state.status != TimerStatus.Running) {
                    stopSelf()
                    return@launch
                }
                locator.timerRepository.completeIfExpired(userId)
                val remaining = formatRemaining(state.remainingSec)
                val notification = buildNotification(remaining)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
                delay(1_000)
            }
        }
    }

    private fun buildNotification(remaining: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.timer_notification_title))
        .setContentText("剩余时间：$remaining")
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setOngoing(true)
        .build()

    private fun formatRemaining(totalSec: Long): String {
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.timer_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    companion object {
        private const val ACTION_START = "com.example.pomodoro.action.START_TIMER_SERVICE"
        private const val ACTION_STOP = "com.example.pomodoro.action.STOP_TIMER_SERVICE"
        private const val EXTRA_USER_ID = "user_id"
        private const val CHANNEL_ID = "pomodoro_timer_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, userId: String) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_USER_ID, userId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerForegroundService::class.java))
        }
    }
}
