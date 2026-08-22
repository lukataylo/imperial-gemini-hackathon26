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

@Composable
fun AppIcon(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val icon = rememberAppIcon(packageName)
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier.size(size)
        )
    }
}
