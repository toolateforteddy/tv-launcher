package com.toolateforteddy.kidremote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolateforteddy.kidremote.Show
import com.toolateforteddy.kidremote.TvStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun KidScreen(
    shows: List<Show>,
    status: TvStatus,
    notice: String?,
    onShow: (Show) -> Unit,
    onPlayPause: () -> Unit,
    onOpenGrownUp: () -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
        if (shows.isEmpty()) {
            Text(
                "No shows yet.\nGrown-ups: hold the gear for 2 seconds.",
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = insets.calculateTopPadding() + 16.dp,
                    bottom = insets.calculateBottomPadding() + 160.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shows, key = { it.id }) { show -> ShowCard(show) { onShow(show) } }
            }
        }

        StatusDot(status, Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(6.dp))

        PlayPauseButton(
            onPlayPause,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 20.dp),
        )

        GearHold(
            onOpenGrownUp,
            Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
        )

        if (notice != null) {
            Notice(notice, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing))
        }
    }
}

@Composable
private fun ShowCard(show: Show, onTap: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")
    // A bright ring for a moment after a tap, so she can see it "worked" while the TV catches up.
    var flash by remember { mutableIntStateOf(0) }
    LaunchedEffect(flash) {
        if (flash > 0) {
            delay(1200)
            flash = 0
        }
    }
    val picture = rememberPicture(show.image)
    Column(
        Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Tile)
            .then(if (flash > 0) Modifier.border(4.dp, Palette.Accent, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                flash++
                onTap()
            },
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(Palette.TileRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (picture != null) {
                Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    show.name.take(1).uppercase(),
                    color = Palette.Accent,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // Room for two lines everywhere, so cards in a row line up.
        Box(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(show.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlayPauseButton(onTap: () -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    Box(
        modifier
            .size(112.dp)
            .scale(scale)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(Palette.Accent)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onTap()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(56.dp)) {
            val w = size.width
            val h = size.height
            val triangle = Path().apply {
                moveTo(w * 0.02f, h * 0.18f)
                lineTo(w * 0.48f, h * 0.5f)
                lineTo(w * 0.02f, h * 0.82f)
                close()
            }
            drawPath(triangle, Palette.OnAccent)
            val barW = w * 0.15f
            val r = CornerRadius(barW / 3)
            drawRoundRect(Palette.OnAccent, Offset(w * 0.56f, h * 0.18f), Size(barW, h * 0.64f), r)
            drawRoundRect(Palette.OnAccent, Offset(w * 0.80f, h * 0.18f), Size(barW, h * 0.64f), r)
        }
    }
}

/** Hold for 2 seconds to open grown-up settings; a quick tap does nothing. */
@Composable
private fun GearHold(onOpen: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    Box(
        modifier
            .size(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(2000, easing = LinearEasing))
                        onOpen()
                    }
                    tryAwaitRelease()
                    job.cancel()
                    scope.launch { progress.snapTo(0f) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (progress.value > 0f) {
            CircularProgressIndicator(
                progress = { progress.value },
                color = Palette.Accent,
                trackColor = Palette.Tile,
                modifier = Modifier.size(44.dp),
            )
        }
        Icon(
            Icons.Filled.Settings,
            contentDescription = "Grown-up settings (hold)",
            tint = if (progress.value > 0f) Palette.Accent else Palette.Gear,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun StatusDot(status: TvStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        is TvStatus.Ready -> Palette.Ok
        TvStatus.Connecting -> Palette.Warn
        else -> Palette.Danger
    }
    Box(modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = 0.7f)))
}

@Composable
fun Notice(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.TileRaised)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(text, fontSize = 16.sp)
    }
}
