package com.luna.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.luna.music.R

@Composable
fun LunaBrandLogo(
    modifier: Modifier = Modifier,
    contentDescription: String = "Luna"
) {
    Image(
        painter = painterResource(R.drawable.luna_brand_logo),
        contentDescription = contentDescription,
        modifier = modifier.clip(RoundedCornerShape(20.dp)),
        contentScale = ContentScale.Fit
    )
}
