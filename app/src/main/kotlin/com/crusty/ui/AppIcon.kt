package com.crusty.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background

/** True if the package is actually installed on this device. */
fun isAppInstalled(pm: PackageManager, packageName: String): Boolean = try {
    pm.getApplicationInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/** The app's real launcher icon, or null if it isn't installed. */
@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(width = 144, height = 144)
                .asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * The app's launcher icon. Apps that aren't installed get a lettered placeholder rather
 * than a blank gap — the picker lists supported apps whether or not you have them.
 */
@Composable
fun AppIcon(
    packageName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackLabel: String? = null,
) {
    val icon = rememberAppIcon(packageName)
    when {
        icon != null -> Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier.size(size)
        )
        fallbackLabel != null -> Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackLabel.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> Spacer(modifier = modifier.size(size))
    }
}
