package com.luna.music.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.music.data.downloads.LunaDownloadRepository
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val SPOTDOWN_URL = "https://spotdown.org/en2"
private val supportedAudioExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")

@Composable
fun DiscoverScreen(
    palette: AlbumPalette,
    openDrawer: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var feedback by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadRepository = remember(context) { LunaDownloadRepository(context) }
    val directUri = remember(query) {
        runCatching { Uri.parse(query.trim()) }.getOrNull()
    }
    val extension = directUri?.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
    val canDownload = directUri?.scheme.equals("https", ignoreCase = true) &&
        !directUri?.host.isNullOrBlank() && extension in supportedAudioExtensions

    LunaSettingsScaffold(
        palette = palette,
        title = "Discover",
        subtitle = "Find something worth keeping.",
        openDrawer = openDrawer
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; feedback = "" },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Song, artist or Spotify link") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = palette.muted)
            },
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.getText()?.text?.takeIf(String::isNotBlank)?.let {
                        query = it.trim()
                        feedback = ""
                    }
                }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste link", tint = palette.muted)
                }
            },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.secondary.copy(alpha = 0.8f),
                unfocusedBorderColor = palette.onBackground.copy(alpha = 0.18f),
                focusedTextColor = palette.onBackground,
                unfocusedTextColor = palette.onBackground,
                cursorColor = palette.secondary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )

        Spacer(Modifier.height(12.dp))
        if (canDownload) {
            Button(
                onClick = {
                    val uri = directUri ?: return@Button
                    val fileName = uri.lastPathSegment.orEmpty()
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                downloadRepository.enqueue(uri, fileName.substringBeforeLast('.'), fileName)
                            }
                        }.onSuccess {
                            feedback = "Added to LUNA Downloads"
                            query = ""
                        }.onFailure {
                            feedback = it.message ?: "Unable to start this download"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = readableContent(palette.primary)
                )
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text("  Download audio")
            }
        } else {
            Button(
                onClick = {
                    val searchText = query.trim()
                    if (searchText.isNotBlank()) {
                        clipboard.setText(AnnotatedString(searchText))
                    }
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SPOTDOWN_URL)))
                    }.onSuccess {
                        feedback = if (searchText.isBlank()) {
                            "Spotdown opened in your browser."
                        } else {
                            "Search copied. Paste it into Spotdown, then download your track."
                        }
                    }.onFailure {
                        feedback = "Couldn't open a browser for Spotdown."
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = readableContent(palette.primary)
                )
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                Text(if (query.isBlank()) "  Open Spotdown" else "  Search on Spotdown")
            }
        }

        Spacer(Modifier.height(20.dp))
        LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    if (canDownload) "Direct audio link detected" else "Continue in Spotdown",
                    color = palette.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    feedback.ifBlank {
                        if (canDownload) {
                            "This is a direct audio file, so LUNA can download it into your Music folder."
                        } else {
                            "Search and choose tracks on Spotdown in your browser. When the audio is downloaded, import it from the Downloads screen to add it to your LUNA library."
                        }
                    },
                    color = palette.onBackground.copy(alpha = 0.76f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
