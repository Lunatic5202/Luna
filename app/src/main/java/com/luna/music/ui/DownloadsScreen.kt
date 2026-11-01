package com.luna.music.ui

import android.app.DownloadManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.luna.music.data.downloads.LunaDownloadRepository
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun DownloadsScreen(
    palette: AlbumPalette,
    openDrawer: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { LunaDownloadRepository(context) }
    val scope = rememberCoroutineScope()
    var downloads by remember { mutableStateOf(emptyList<LunaDownloadRepository.DownloadItem>()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(repository) {
        while (isActive) {
            downloads = withContext(Dispatchers.IO) { repository.getDownloads() }
            loading = false
            delay(900)
        }
    }

    fun cancel(id: Long) {
        scope.launch(Dispatchers.IO) { repository.cancel(id) }
    }
    fun retry(id: Long) {
        scope.launch(Dispatchers.IO) { repository.retry(id) }
    }

    LunaSettingsScaffold(
        palette = palette,
        title = "Downloads",
        subtitle = "Your music, coming home.",
        openDrawer = openDrawer
    ) {
        if (loading) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = palette.secondary)
            }
        } else if (downloads.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LunaGlassSurface(
                    palette = palette,
                    modifier = Modifier.padding(bottom = 18.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = palette.secondary,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                Text(
                    "Nothing here yet",
                    color = palette.onBackground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your active and completed downloads will show up here.",
                    color = palette.onBackground.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.82f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads, key = { it.id }) { item ->
                    LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.title,
                                        color = palette.onBackground,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        statusLabel(item),
                                        color = palette.onBackground.copy(alpha = 0.70f),
                                        fontSize = 12.sp
                                    )
                                }
                                if (item.isActive) {
                                    IconButton(onClick = { cancel(item.id) }) {
                                        Icon(Icons.Default.Cancel, "Cancel download", tint = palette.onBackground.copy(alpha = 0.82f))
                                    }
                                } else if (item.isFailed) {
                                    IconButton(onClick = { retry(item.id) }) {
                                        Icon(Icons.Default.Refresh, "Retry download", tint = palette.secondary)
                                    }
                                }
                            }
                            if (item.isActive) {
                                LinearProgressIndicator(
                                    progress = { item.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = palette.secondary,
                                    trackColor = palette.onBackground.copy(alpha = 0.12f)
                                )
                                Text(
                                    if (item.totalBytes > 0L) "${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}"
                                    else "${formatBytes(item.downloadedBytes)} downloaded",
                                    color = palette.onBackground.copy(alpha = 0.64f),
                                    fontSize = 11.sp
                                )
                            } else if (item.isFinished) {
                                Text(
                                    item.fileName,
                                    color = palette.onBackground.copy(alpha = 0.64f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(item: LunaDownloadRepository.DownloadItem): String = when (item.status) {
    DownloadManager.STATUS_PENDING -> "Waiting"
    DownloadManager.STATUS_RUNNING -> "Downloading · ${(item.progress * 100).toInt()}%"
    DownloadManager.STATUS_PAUSED -> "Paused"
    DownloadManager.STATUS_SUCCESSFUL -> "Downloaded"
    DownloadManager.STATUS_FAILED -> "Download failed · code ${item.reason}"
    else -> "Checking status"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "${bytes} B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unit = units[0]
    for (index in 1 until units.size) {
        if (value < 1024.0) break
        value /= 1024.0
        unit = units[index]
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, unit)
}
