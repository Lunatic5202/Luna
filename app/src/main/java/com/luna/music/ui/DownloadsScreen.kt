package com.luna.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    LunaSettingsScaffold(
        palette = palette,
        title = "Downloads",
        subtitle = "Your music, coming home.",
        openDrawer = openDrawer
    ) {
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
    }
}
