package com.luna.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.music.theme.AlbumPalette

@Composable
fun DownloadsScreen(
    palette: AlbumPalette,
    openDrawer: () -> Unit
) {
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

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = palette.primary.copy(alpha = .12f)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = palette.secondary,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Downloads",
                    color = palette.onBackground,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nothing here yet",
                    color = palette.onBackground.copy(alpha = .86f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your active, completed and failed downloads will appear here.",
                    color = palette.muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(.82f)
                )
            }
        }
    }
}
