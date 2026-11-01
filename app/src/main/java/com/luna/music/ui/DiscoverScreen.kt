package com.luna.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.music.theme.AlbumPalette

@Composable
fun DiscoverScreen(
    palette: AlbumPalette,
    openDrawer: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    LunaSettingsScaffold(
        palette = palette,
        title = "Discover",
        subtitle = "Find something worth keeping.",
        openDrawer = openDrawer
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Songs, artists or a music link") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = palette.muted)
            },
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.getText()?.text?.takeIf(String::isNotBlank)?.let { query = it }
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

        Spacer(Modifier.height(24.dp))
        LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "Search is getting ready",
                    color = palette.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "LUNA's native search and results will appear here. No website is embedded.",
                    color = palette.onBackground.copy(alpha = 0.76f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
