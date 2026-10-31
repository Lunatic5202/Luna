package com.luna.music.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Keep the previous artwork visible while the next image is decoded.
    // This avoids a placeholder flash on every track transition.
    LaunchedEffect(artworkUri) {
        if (artworkUri.isNullOrBlank()) {
            bitmap = null
        } else {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(artworkUri))?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
            }
            bitmap = decoded
        }
    }

    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(palette.primary, palette.secondary))),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = bitmap,
            transitionSpec = {
                (fadeIn(tween(420)) + scaleIn(initialScale = .985f, animationSpec = tween(420))) togetherWith
                    fadeOut(tween(260))
            },
            label = "album-artwork-crossfade"
        ) { image ->
            if (image != null) {
                Image(
                    image.asImageBitmap(),
                    contentDescription = "Album artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("♫", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}
