package com.example.pomodoro

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pomodoro.ui.MainViewModel
import com.example.pomodoro.ui.auth.AuthScreen
import com.example.pomodoro.ui.timer.TimerScreen
import com.example.pomodoro.ui.theme.PomodoroTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PomodoroTheme {
                val session by viewModel.session.collectAsStateWithLifecycle()
                val authState by viewModel.authUiState.collectAsStateWithLifecycle()
                val timerState by viewModel.timerUiState.collectAsStateWithLifecycle()

                NotificationPermissionEffect()

                if (session == null) {
                    AuthScreen(
                        uiState = authState,
                        onEmailChange = viewModel::updateEmail,
                        onPasswordChange = viewModel::updatePassword,
                        onSubmit = viewModel::loginOrRegister,
                        onToggleMode = viewModel::toggleAuthMode
                    )
                } else {
                    TimerScreen(
                        uiState = timerState,
                        onModeChange = viewModel::updateSelectedMode,
                        onCustomDurationChange = viewModel::updateCustomDuration,
                        onStart = viewModel::startTimer,
                        onPause = viewModel::pauseTimer,
                        onReset = viewModel::resetTimer,
                        onSync = viewModel::syncNow,
                        onLogout = viewModel::logout
                    )
                    LaunchedEffect(timerState.state?.remainingSec, timerState.state?.status) {
                        viewModel.completeTimerIfNeeded()
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NotificationPermissionEffect() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )
    LaunchedEffect(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
