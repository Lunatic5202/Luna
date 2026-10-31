package com.luna.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.music.playback.EqualizerBandState
import com.luna.music.playback.EqualizerProfile
import com.luna.music.playback.EqualizerProfileKind
import com.luna.music.playback.EqualizerState
import com.luna.music.playback.LunaEqualizerEngine
import com.luna.music.theme.AlbumPalette
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface ProfileDialog {
    data object SaveNew : ProfileDialog
    data class Rename(val id: String, val current: String) : ProfileDialog
    data class Delete(val id: String, val name: String) : ProfileDialog
}

private val TAB_KINDS = listOf(EqualizerProfileKind.Custom, EqualizerProfileKind.BuiltIn, EqualizerProfileKind.Device)

private fun tabLabel(kind: EqualizerProfileKind) = when (kind) {
    EqualizerProfileKind.Custom -> "My profiles"
    EqualizerProfileKind.BuiltIn -> "Presets"
    EqualizerProfileKind.Device -> "Device"
}

@Composable
fun EqualizerScreen(palette: AlbumPalette, openDrawer: () -> Unit) {
    val equalizer by LunaEqualizerEngine.state.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(-1) }
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }

    LunaSettingsScaffold(palette, "Equalizer", "Shape the sound for your headphones or speakers.", openDrawer) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!equalizer.available) {
                LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Equalizer unavailable", color = palette.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(equalizer.message, color = palette.onBackground.copy(alpha = 0.78f), fontSize = 14.sp)
                        Text("Start a track to connect LUNA to the device audio session. Support depends on the device's system audio effects.", color = palette.onBackground.copy(alpha = 0.68f), fontSize = 12.sp)
                    }
                }
            } else {
                val activeKind = equalizer.activeProfile?.kind
                val shownTab = if (selectedTab in TAB_KINDS.indices) selectedTab else when (activeKind) {
                    EqualizerProfileKind.Custom -> 0
                    EqualizerProfileKind.Device -> 2
                    else -> 1
                }

                EqualizerHeader(palette, equalizer)
                Spacer(Modifier.height(14.dp))
                LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            "RESPONSE",
                            color = palette.onBackground.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                        )
                        EqualizerCurve(equalizer.bands, equalizer.minLevelMb, equalizer.maxLevelMb, palette)
                    }
                }

                Spacer(Modifier.height(14.dp))
                ProfilesCard(
                    palette = palette,
                    equalizer = equalizer,
                    shownTab = shownTab,
                    onTabSelected = { selectedTab = it },
                    onSaveNew = { dialog = ProfileDialog.SaveNew },
                    onRename = { profile -> dialog = ProfileDialog.Rename(profile.id, profile.name) },
                    onDelete = { profile -> dialog = ProfileDialog.Delete(profile.id, profile.name) }
                )

                Spacer(Modifier.height(14.dp))
                LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp)) {
                        Text(
                            "BANDS",
                            color = palette.onBackground.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            equalizer.bands.forEach { band ->
                                BandColumn(
                                    band = band,
                                    minMb = equalizer.minLevelMb,
                                    maxMb = equalizer.maxLevelMb,
                                    dimmed = !equalizer.enabled,
                                    palette = palette,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Drag a band, or tap to jump. Device-supported range · dB",
                            color = palette.onBackground.copy(alpha = 0.68f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    when (val current = dialog) {
        ProfileDialog.SaveNew -> {
            val base = equalizer.activeProfile?.name?.let { if (equalizer.modified) "$it 2" else it }.orEmpty()
            ProfileNameDialog(
                palette = palette,
                title = "Save profile",
                message = "Name this sound so you can bring it back anytime.",
                initial = base.ifBlank { "My sound" },
                confirmLabel = "Save",
                onDismiss = { dialog = null },
                onConfirm = { name ->
                    LunaEqualizerEngine.saveAsCustomProfile(name).also { ok ->
                        if (ok) {
                            selectedTab = 0
                            dialog = null
                        }
                    }
                }
            )
        }
        is ProfileDialog.Rename -> ProfileNameDialog(
            palette = palette,
            title = "Rename profile",
            message = null,
            initial = current.current,
            confirmLabel = "Rename",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                LunaEqualizerEngine.renameCustomProfile(current.id, name).also { ok -> if (ok) dialog = null }
            }
        )
        is ProfileDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = palette.surface,
            titleContentColor = palette.onBackground,
            textContentColor = palette.onBackground.copy(alpha = 0.82f),
            title = { Text("Delete “${current.name}”?") },
            text = { Text("The profile is removed. Your current sound stays as it is until you pick another one.") },
            confirmButton = {
                TextButton(onClick = {
                    LunaEqualizerEngine.deleteCustomProfile(current.id)
                    dialog = null
                }) { Text("Delete", color = Color(0xFFFF8A80)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("Cancel", color = palette.onBackground.copy(alpha = 0.84f)) }
            }
        )
        null -> Unit
    }
}

@Composable
private fun EqualizerHeader(palette: AlbumPalette, equalizer: EqualizerState) {
    val profileLabel = when {
        equalizer.activeProfile == null -> "Custom"
        equalizer.modified -> "${equalizer.activeProfile?.name} · edited"
        else -> equalizer.activeProfile?.name.orEmpty()
    }
    LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Equalizer", color = palette.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    if (equalizer.enabled) profileLabel else "Off · $profileLabel",
                    color = palette.onBackground.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = equalizer.enabled,
                onCheckedChange = LunaEqualizerEngine::setEnabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = palette.primary,
                    checkedThumbColor = readableContent(palette.primary)
                ),
                modifier = Modifier.semantics { contentDescription = "Enable equalizer" }
            )
        }
    }
}

@Composable
private fun ProfilesCard(
    palette: AlbumPalette,
    equalizer: EqualizerState,
    shownTab: Int,
    onTabSelected: (Int) -> Unit,
    onSaveNew: () -> Unit,
    onRename: (EqualizerProfile) -> Unit,
    onDelete: (EqualizerProfile) -> Unit
) {
    val tabs = TAB_KINDS.filter { kind -> kind != EqualizerProfileKind.Device || equalizer.profiles.any { it.kind == kind } }
    val tabKind = TAB_KINDS[shownTab].takeIf { it in tabs } ?: EqualizerProfileKind.BuiltIn
    val visibleProfiles = equalizer.profiles.filter { it.kind == tabKind }
    val active = equalizer.activeProfile

    LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "PROFILES",
                color = palette.onBackground.copy(alpha = 0.72f),
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEach { kind ->
                    val selected = kind == tabKind
                    Text(
                        tabLabel(kind),
                        color = if (selected) palette.onBackground else palette.onBackground.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier
                            .bouncyClickable(
                                RoundedCornerShape(50),
                                if (selected) palette.primary.copy(alpha = 0.26f) else Color.Transparent
                            ) { onTabSelected(TAB_KINDS.indexOf(kind)) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            if (visibleProfiles.isEmpty()) {
                Text(
                    "Nothing saved yet. Shape the bands below, then choose Save as new.",
                    color = palette.onBackground.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleProfiles, key = { it.id }) { profile ->
                        ProfileChip(
                            label = profile.name,
                            selected = profile.id == active?.id,
                            edited = profile.id == active?.id && equalizer.modified,
                            palette = palette,
                            onClick = { LunaEqualizerEngine.selectProfile(profile.id) }
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (active?.kind == EqualizerProfileKind.Custom) {
                    item {
                        ProfileAction("Update", Icons.Default.Save, enabled = equalizer.modified, palette = palette) {
                            LunaEqualizerEngine.updateActiveCustomProfile()
                        }
                    }
                    item { ProfileAction("Rename", Icons.Default.Edit, enabled = true, palette = palette) { onRename(active) } }
                }
                item { ProfileAction("Save as new", Icons.Default.Add, enabled = true, palette = palette, onClick = onSaveNew) }
                if (active?.kind == EqualizerProfileKind.Custom) {
                    item { ProfileAction("Delete", Icons.Default.DeleteOutline, enabled = true, palette = palette) { onDelete(active) } }
                }
                item { ProfileAction("Flat", Icons.Default.RestartAlt, enabled = true, palette = palette) { LunaEqualizerEngine.resetToFlat() } }
            }
            Text(
                "Device presets are provided by Android and may vary by device.",
                color = palette.onBackground.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
    }
}

@Composable
private fun ProfileChip(
    label: String,
    selected: Boolean,
    edited: Boolean,
    palette: AlbumPalette,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    val text: @Composable () -> Unit = {
        Text(
            if (edited) "$label •" else label,
            color = palette.onBackground,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp)
        )
    }
    if (selected) {
        LunaGlassSurface(
            palette = palette,
            shape = shape,
            refraction = false,
            modifier = Modifier.bouncyClickable(shape, palette.primary.copy(alpha = 0.18f), onClick)
        ) { text() }
    } else {
        Box(Modifier.bouncyClickable(shape, Color.White.copy(alpha = 0.07f), onClick)) { text() }
    }
}

@Composable
private fun ProfileAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    palette: AlbumPalette,
    onClick: () -> Unit
) {
    val tint = palette.onBackground.copy(alpha = if (enabled) 0.9f else 0.35f)
    Row(
        Modifier
            .bouncyClickable(RoundedCornerShape(14.dp), Color.White.copy(alpha = if (enabled) 0.06f else 0.03f)) {
                if (enabled) onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun ProfileNameDialog(
    palette: AlbumPalette,
    title: String,
    message: String?,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    var name by remember { mutableStateOf(initial) }
    var rejected by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.onBackground,
        textContentColor = palette.onBackground.copy(alpha = 0.82f),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (message != null) Text(message, fontSize = 13.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(LunaEqualizerEngine.MAX_PROFILE_NAME_LENGTH)
                        rejected = false
                    },
                    label = { Text("Profile name") },
                    singleLine = true,
                    isError = rejected,
                    supportingText = if (rejected) {
                        { Text("That name is already used by another profile.") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { if (!onConfirm(name)) rejected = true }
            ) { Text(confirmLabel, color = palette.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.onBackground.copy(alpha = 0.84f)) }
        }
    )
}

@Composable
private fun EqualizerCurve(
    bands: List<EqualizerBandState>,
    minMb: Int,
    maxMb: Int,
    palette: AlbumPalette
) {
    val line = palette.primary
    val grid = palette.onBackground
    Canvas(Modifier.fillMaxWidth().height(116.dp)) {
        val pad = 12.dp.toPx()
        val range = (maxMb - minMb).toFloat().coerceAtLeast(1f)
        fun yFor(level: Int) = pad + (1f - (level - minMb) / range) * (size.height - 2 * pad)
        fun xFor(index: Int) =
            if (bands.size <= 1) size.width / 2f else pad + index * (size.width - 2 * pad) / (bands.size - 1)

        val zeroY = yFor(0)
        drawLine(
            color = grid.copy(alpha = 0.22f),
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        )
        if (bands.isEmpty()) return@Canvas

        val curve = Path().apply {
            moveTo(xFor(0), yFor(bands[0].levelMb))
            for (i in 1 until bands.size) {
                val midX = (xFor(i - 1) + xFor(i)) / 2f
                cubicTo(midX, yFor(bands[i - 1].levelMb), midX, yFor(bands[i].levelMb), xFor(i), yFor(bands[i].levelMb))
            }
        }
        val fill = Path().apply {
            addPath(curve)
            lineTo(xFor(bands.lastIndex), size.height)
            lineTo(xFor(0), size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.34f), Color.Transparent)))
        drawPath(curve, line, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        bands.forEachIndexed { index, band ->
            drawCircle(grid, radius = 3.6.dp.toPx(), center = Offset(xFor(index), yFor(band.levelMb)))
        }
    }
}

@Composable
private fun BandColumn(
    band: EqualizerBandState,
    minMb: Int,
    maxMb: Int,
    dimmed: Boolean,
    palette: AlbumPalette,
    modifier: Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatDecibels(band.levelMb),
            color = palette.onBackground.copy(alpha = 0.86f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        EqBandSlider(
            levelMb = band.levelMb,
            minMb = minMb,
            maxMb = maxMb,
            dimmed = dimmed,
            palette = palette,
            description = "${formatFrequency(band.centerFrequencyHz)} frequency band",
            onLevelChange = { LunaEqualizerEngine.setBandLevel(band.index, it) }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            formatFrequency(band.centerFrequencyHz),
            color = palette.onBackground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** Vertical band slider: drag anywhere on the strip, tap to jump, snaps to 0 dB near the middle. */
@Composable
private fun EqBandSlider(
    levelMb: Int,
    minMb: Int,
    maxMb: Int,
    dimmed: Boolean,
    palette: AlbumPalette,
    description: String,
    onLevelChange: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentOnChange by rememberUpdatedState(onLevelChange)
    val shownLevel by animateFloatAsState(
        targetValue = levelMb.toFloat(),
        animationSpec = spring(dampingRatio = 1f, stiffness = 800f),
        label = "eq-band-level"
    )
    val primary = palette.primary
    val inactive = palette.onBackground.copy(alpha = 0.18f)
    val onBackground = palette.onBackground
    Box(
        Modifier
            .width(44.dp)
            .height(190.dp)
            .semantics {
                contentDescription = description
                stateDescription = formatDecibels(levelMb) + " decibels"
                progressBarRangeInfo = ProgressBarRangeInfo(levelMb.toFloat(), minMb.toFloat()..maxMb.toFloat())
                setProgress { target ->
                    onLevelChange(target.roundToInt().coerceIn(minMb, maxMb))
                    true
                }
            }
            .pointerInput(minMb, maxMb) {
                val pad = 14.dp.toPx()
                fun levelAt(y: Float): Int {
                    val fraction = ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                    val raw = maxMb - fraction * (maxMb - minMb)
                    val stepped = (raw / 10f).roundToInt() * 10
                    return if (abs(stepped) < 35) 0 else stepped
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var last = levelAt(down.position.y)
                    currentOnChange(last)
                    drag(down.id) { change ->
                        change.consume()
                        val level = levelAt(change.position.y)
                        if (level != last) {
                            if (level == 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            last = level
                            currentOnChange(level)
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 14.dp.toPx()
            val centerX = size.width / 2f
            val range = (maxMb - minMb).toFloat().coerceAtLeast(1f)
            fun yFor(level: Float) = pad + (1f - (level - minMb) / range) * (size.height - 2 * pad)
            val zeroY = yFor(0f)
            val thumbY = yFor(shownLevel)
            val strokeWidth = 4.dp.toPx()
            val alpha = if (dimmed) 0.55f else 1f

            drawLine(inactive, Offset(centerX, pad), Offset(centerX, size.height - pad), strokeWidth, StrokeCap.Round)
            drawLine(primary.copy(alpha = alpha), Offset(centerX, zeroY), Offset(centerX, thumbY), strokeWidth, StrokeCap.Round)
            drawLine(
                onBackground.copy(alpha = 0.42f),
                Offset(centerX - 9.dp.toPx(), zeroY),
                Offset(centerX + 9.dp.toPx(), zeroY),
                1.5.dp.toPx()
            )
            drawCircle(Color.White.copy(alpha = alpha), 10.dp.toPx(), Offset(centerX, thumbY))
            drawCircle(primary.copy(alpha = alpha), 10.dp.toPx(), Offset(centerX, thumbY), style = Stroke(2.5.dp.toPx()))
        }
    }
}

private fun formatFrequency(frequencyHz: Int): String =
    if (frequencyHz >= 1000) {
        val khz = frequencyHz / 1000f
        if (khz % 1f == 0f) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
    } else {
        "$frequencyHz Hz"
    }

private fun formatDecibels(levelMb: Int): String =
    "${if (levelMb > 0) "+" else ""}${"%.1f".format(levelMb / 100f)}"
