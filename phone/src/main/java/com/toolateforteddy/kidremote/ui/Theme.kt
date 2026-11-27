package com.toolateforteddy.kidremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.toolateforteddy.kidremote.Pictures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Palette {
    val Background = Color(0xFF14161A)
    val Tile = Color(0xFF262A31)
    val TileRaised = Color(0xFF313642)
    val Text = Color(0xFFF2F4F7)
    val Muted = Color(0xFF9AA3AF)
    val Accent = Color(0xFF7FB2FF)
    val OnAccent = Color(0xFF0B1830)
    val Danger = Color(0xFFFF8A80)
    val Ok = Color(0xFF7BD88F)
    val Warn = Color(0xFFF2C46D)
    val Gear = Color(0xFF3A404C)
}

@Composable
fun KidRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Palette.OnAccent,
            background = Palette.Background,
            surface = Palette.Background,
            surfaceVariant = Palette.Tile,
            onBackground = Palette.Text,
            onSurface = Palette.Text,
            onSurfaceVariant = Palette.Muted,
            error = Palette.Danger,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Palette.Text) {
            Box(Modifier.fillMaxSize().background(Palette.Background)) { content() }
        }
    }
}

/** Loads a show picture off the main thread; null while loading or if there isn't one. */
@Composable
fun rememberPicture(src: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(src) { mutableStateOf(Pictures.cached(src)?.asImageBitmap()) }
    LaunchedEffect(src) {
        if (bitmap == null && src.isNotBlank()) {
            bitmap = withContext(Dispatchers.IO) { Pictures.load(context, src) }?.asImageBitmap()
        }
    }
    return bitmap
}
