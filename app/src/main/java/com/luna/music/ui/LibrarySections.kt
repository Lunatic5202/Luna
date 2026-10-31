package com.luna.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luna.music.data.MusicCombo
import com.luna.music.data.Track
import com.luna.music.theme.AlbumPalette
import com.luna.music.theme.LunaTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun CombosScreen(
    palette: AlbumPalette,
    tracks: List<Track>,
    combos: List<MusicCombo>,
    openDrawer: () -> Unit,
    onCreate: (String, List<Long>) -> Unit,
    onDelete: (String) -> Unit,
    onPlay: (MusicCombo) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    LunaSettingsScaffold(palette, "Combos", "Your playlists, made your way.", openDrawer) {
        Button(
            onClick = { showCreateDialog = true },
            enabled = tracks.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.primary,
                contentColor = readableContent(palette.primary)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Create a combo", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))

        if (combos.isEmpty()) {
            LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No combos yet", color = palette.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (tracks.isEmpty()) "Add music to your device to start making playlists."
                        else "Create your first combo from the songs in your library.",
                        color = palette.onBackground.copy(alpha = 0.76f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(combos, key = { it.id }) { combo ->
                    val comboTracks = combo.trackIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
                    LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 13.dp, bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(combo.name, color = palette.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${comboTracks.size} ${if (comboTracks.size == 1) "track" else "tracks"}", color = palette.onBackground.copy(alpha = 0.76f), fontSize = 12.sp)
                                }
                                IconButton(onClick = { onDelete(combo.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${combo.name}", tint = palette.onBackground.copy(alpha = 0.8f))
                                }
                                IconButton(onClick = { if (comboTracks.isNotEmpty()) onPlay(combo) }, enabled = comboTracks.isNotEmpty()) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play ${combo.name}", tint = palette.primary)
                                }
                            }
                            val preview = comboTracks.take(3).joinToString(" · ") { it.title }
                            if (preview.isNotBlank()) {
                                Text(preview, color = palette.onBackground.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateComboDialog(
            palette = palette,
            tracks = tracks,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, ids ->
                onCreate(name, ids)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CreateComboDialog(
    palette: AlbumPalette,
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onCreate: (String, List<Long>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 680.dp),
            shape = RoundedCornerShape(28.dp),
            color = palette.surface,
            contentColor = palette.onBackground,
            tonalElevation = 12.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("New combo", color = palette.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Choose a name and the songs to include.", color = palette.onBackground.copy(alpha = 0.76f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    label = { Text("Combo name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 330.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        val selected = track.id in selectedIds
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (selected) selectedIds.remove(track.id) else selectedIds.add(track.id)
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { isSelected ->
                                    if (isSelected) selectedIds.add(track.id) else selectedIds.remove(track.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = palette.primary,
                                    checkmarkColor = readableContent(palette.primary),
                                    uncheckedColor = palette.onBackground.copy(alpha = 0.62f)
                                )
                            )
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = palette.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                Text(track.artist, color = palette.onBackground.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = palette.onBackground.copy(alpha = 0.84f)) }
                    Button(
                        onClick = { onCreate(name, selectedIds.toList()) },
                        enabled = name.isNotBlank() && selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = readableContent(palette.primary))
                    ) { Text("Create") }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(palette: AlbumPalette, openDrawer: () -> Unit) {
    LunaSettingsScaffold(palette, "About", "A quiet home for the music on your device.", openDrawer) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("L U N A", color = palette.onBackground, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Text("Built by Lunatic", color = palette.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Version 0.1.0", color = palette.onBackground.copy(alpha = 0.78f), fontSize = 13.sp)
                Text("An offline-first music player for songs stored on your Android device.", color = palette.onBackground.copy(alpha = 0.88f), fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        AboutInfoCard(palette, "Privacy", "Your library and combos stay on this device. LUNA does not upload your music.")
        Spacer(Modifier.height(12.dp))
        AboutInfoCard(palette, "Permissions", "Music access lets LUNA find and play local audio. The optional visualizer reads playback audio only; LUNA does not record microphone input.")
        Spacer(Modifier.height(12.dp))
        AboutInfoCard(palette, "Audio effects", "Equalizer support and available presets depend on the Android device and its system audio effects.")
        Spacer(Modifier.height(12.dp))
        AboutInfoCard(palette, "Glass", "Surfaces use the Backdrop library for refraction. The tilt-reactive highlight is inspired by Liquid-Glass-Android by QWEA0 (MIT licence).")
        Spacer(Modifier.height(12.dp))
        AboutInfoCard(palette, "Made for music", "Album artwork colors shape the interface throughout the app, while playback remains local and controlled by Android's media session.")
        }
    }
}

@Composable
private fun AboutInfoCard(palette: AlbumPalette, title: String, body: String) {
    LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = palette.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = palette.onBackground.copy(alpha = 0.82f), fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
internal fun LunaSettingsScaffold(
    palette: AlbumPalette,
    title: String,
    subtitle: String,
    openDrawer: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val liquidBackdrop = rememberLayerBackdrop()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalLunaLiquidBackdrop provides liquidBackdrop) {
        Box(Modifier.fillMaxSize().background(palette.background)) {
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(palette.glow.copy(alpha = 0.55f), palette.background, Color(0xFF07080D))))
                    .layerBackdrop(liquidBackdrop)
            )
            Column(
                Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset).padding(horizontal = 22.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = openDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open navigation", tint = palette.onBackground)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = palette.onBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, color = palette.onBackground.copy(alpha = 0.76f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(Modifier.fillMaxSize(), content = content)
            }
        }
    }
}

internal fun readableContent(background: Color): Color =
    if (background.luminance() > 0.48f) Color(0xFF101318) else Color.White

