package com.luna.music.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AlbumArtwork(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    palette: AlbumPalette,
    shape: Shape = RoundedCornerShape(18.dp)
) {
    val context = LocalContext.current
    val bitmap = produceState<Bitmap?>(null, artworkUri) {
        value = if (artworkUri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(artworkUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }
    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(palette.primary, palette.secondary))),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap.value
        if (image != null) {
            Image(
                image.asImageBitmap(),
                contentDescription = "Album artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("♫", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        }
    }
}
