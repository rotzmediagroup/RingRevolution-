package com.shiostudios.dumplingrings.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.shiostudios.dumplingrings.ui.components.AssetImage
import com.shiostudios.dumplingrings.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

/**
 * Splash: the owner's key art (logo, rings, dumpling friends). Portrait uses the 4:5 centre crop fitted to the screen
 * over a dimmed, cropped copy of the same art so no letterbox shows; landscape shows the full painting. Fades in, holds,
 * then fades out; a tap skips the hold.
 */
@Composable
fun SplashScreen(onDone: () -> Unit, holdMs: Long = 1900) {
    val reduce = LocalReduceMotion.current
    var phase by remember { mutableStateOf(0) }   // 0 = fade in, 1 = hold, 2 = fade out
    val alpha by animateFloatAsState(if (phase == 2) 0f else 1f, tween(if (reduce) 150 else if (phase == 2) 450 else 700), label = "splashAlpha")
    val zoom by animateFloatAsState(if (phase >= 1) 1f else 1.06f, tween(if (reduce) 0 else 2600), label = "splashZoom")
    LaunchedEffect(Unit) { delay(if (reduce) 150 else 700); phase = 1; delay(holdMs); phase = 2; delay(if (reduce) 150 else 450); onDone() }
    val src = remember { MutableInteractionSource() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF1E120B)).clickable(src, null) { if (phase == 1) phase = 2 }) {
        val landscape = maxWidth > maxHeight
        Box(Modifier.fillMaxSize().alpha(alpha)) {
            if (landscape) {
                AssetImage("splash/keyart.webp", Modifier.fillMaxSize().scale(zoom), ContentScale.Crop)
            } else {
                AssetImage("splash/keyart_blur.webp", Modifier.fillMaxSize(), ContentScale.Crop)
                AssetImage("splash/keyart_portrait.webp", Modifier.fillMaxSize().scale(zoom), ContentScale.Fit)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x331E120B), 0.5f to Color.Transparent, 1f to Color(0x661E120B))))
        }
    }
}
