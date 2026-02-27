package com.example.pomodoro.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pomodoro.domain.TimerMode
import com.example.pomodoro.domain.TimerStatus
import com.example.pomodoro.ui.TimerUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TimerScreen(
    uiState: TimerUiState,
    onModeChange: (TimerMode) -> Unit,
    onCustomDurationChange: (String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit
) {
    val input = remember(uiState.customDurationSec) { mutableStateOf((uiState.customDurationSec / 60).toString()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Pomodoro Sync",
                style = MaterialTheme.typography.headlineSmall
            )
            TextButton(onClick = onLogout) {
                Text("退出")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        ModeSelector(selected = uiState.selectedMode, onModeChange = onModeChange)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = input.value,
            onValueChange = {
                input.value = it
                onCustomDurationChange(it)
            },
            label = { Text("时长（分钟）") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("状态：${uiState.state?.status?.raw ?: "idle"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatRemaining(uiState.state?.remainingSec ?: uiState.customDurationSec),
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStart, enabled = !uiState.syncing) { Text("开始") }
                    Button(
                        onClick = onPause,
                        enabled = !uiState.syncing && uiState.state?.status == TimerStatus.Running
                    ) {
                        Text("暂停")
                    }
                    Button(onClick = onReset, enabled = !uiState.syncing) { Text("重置") }
                }
            }
        }

        if (!uiState.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(uiState.errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSync, enabled = !uiState.syncing) {
                Text(if (uiState.syncing) "同步中..." else "立即同步")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("历史记录", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn {
            items(uiState.history) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("模式：${item.mode.raw}")
                        Text("时长：${item.durationSec / 60} 分钟")
                        Text("结束时间：${formatTime(item.endedAt)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(
    selected: TimerMode,
    onModeChange: (TimerMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimerMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onModeChange(mode) },
                label = {
                    Text(
                        when (mode) {
                            TimerMode.Focus -> "专注"
                            TimerMode.ShortBreak -> "短休息"
                            TimerMode.LongBreak -> "长休息"
                        }
                    )
                }
            )
        }
    }
}

private fun formatRemaining(totalSec: Long): String {
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

private fun formatTime(epochSec: Long): String {
    return Instant.ofEpochSecond(epochSec)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}
