package com.luna.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(palette.glow.copy(alpha = .34f), palette.background, palette.backgroundBottom)
            )
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(82.dp)) {
                Text(
                    "LUNA",
                    modifier = Modifier.align(Alignment.Center),
                    color = palette.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                IconButton(onClick = openDrawer, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Default.Menu, contentDescription = "Open navigation", tint = palette.onBackground)
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "Discover",
                modifier = Modifier.fillMaxWidth(),
                color = palette.onBackground,
                fontSize = 27.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Find music to bring into your library.",
                modifier = Modifier.fillMaxWidth(),
                color = palette.muted,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search songs or paste a link") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.takeIf(String::isNotBlank)?.let { query = it }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                    }
                },
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.secondary,
                    unfocusedBorderColor = palette.onBackground.copy(alpha = .24f),
                    focusedTextColor = palette.onBackground,
                    unfocusedTextColor = palette.onBackground,
                    cursorColor = palette.secondary,
                    focusedLeadingIconColor = palette.secondary,
                    unfocusedLeadingIconColor = palette.muted
                )
            )

            Spacer(Modifier.height(18.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = .045f),
                tonalElevation = 0.dp
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Native Discover is taking shape",
                        color = palette.onBackground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "LUNA's own search, results and download flow will live here. The Spotdown website will not be embedded.",
                        color = palette.muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Text(
                        "The provider connection is not enabled yet. Downloads will only be offered through a supported, permitted source.",
                        color = palette.muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Your library stays yours.",
                color = palette.muted.copy(alpha = .72f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
    }
}
