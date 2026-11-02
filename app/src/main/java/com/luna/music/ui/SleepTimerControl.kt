package com.luna.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.music.playback.LunaSleepTimer
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.delay

private val timerChoices = listOf(15, 30, 45, 60)

/** Moon button for the player header. Shows the time left once a timer is running. */
@Composable
fun SleepTimerButton(palette: AlbumPalette, modifier: Modifier = Modifier) {
    val state by LunaSleepTimer.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state) {
        while (state is LunaSleepTimer.State.Countdown) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val active = state != LunaSleepTimer.State.Off
    Row(modifier.clickable { showDialog = true }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { showDialog = true }) {
            Icon(
                Icons.Default.Bedtime,
                contentDescription = "Sleep timer",
                tint = if (active) palette.secondary else palette.onBackground
            )
        }
        when (val s = state) {
            is LunaSleepTimer.State.Countdown -> {
                val left = ((s.endsAtMillis - now) / 1000L).coerceAtLeast(0L)
                Text(
                    "%d:%02d".format(left / 60, left % 60),
                    color = palette.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium
                )
            }
            LunaSleepTimer.State.EndOfTrack ->
                Text("END", color = palette.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            LunaSleepTimer.State.Off -> Unit
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = palette.surface,
            title = { Text("Sleep timer", color = palette.onBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    timerChoices.forEach { minutes ->
                        TimerRow("$minutes minutes", palette) {
                            LunaSleepTimer.startCountdown(minutes)
                            showDialog = false
                        }
                    }
                    TimerRow("End of this track", palette) {
                        LunaSleepTimer.startEndOfTrack()
                        showDialog = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (active) {
                    TextButton(onClick = { LunaSleepTimer.cancel(); showDialog = false }) {
                        Text("Turn off", color = palette.secondary)
                    }
                } else {
                    TextButton(onClick = { showDialog = false }) { Text("Cancel", color = palette.secondary) }
                }
            }
        )
    }
}

@Composable
private fun TimerRow(label: String, palette: AlbumPalette, onClick: () -> Unit) {
    Text(
        label,
        color = palette.onBackground,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)
    )
}
