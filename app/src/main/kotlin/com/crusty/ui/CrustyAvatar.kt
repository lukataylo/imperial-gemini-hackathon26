package com.crusty.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.crusty.R

/**
 * Canonical avatar composable for Crusty across the app.
 *
 * All screens must use this composable rather than importing or drawing
 * per-screen variants.
 */
@Composable
fun CrustyAvatar(
    size: Dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(R.drawable.crusty_happy),
        contentDescription = "Crusty",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    )
}
