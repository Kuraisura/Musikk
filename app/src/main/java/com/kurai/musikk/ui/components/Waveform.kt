package com.kurai.musikk.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kurai.musikk.ui.theme.*
import kotlin.math.*

/**
 * Deterministic waveform bars using the spec's LCG + envelope algorithm.
 * Channel colour drives the bar fill. Volume shrinks the wave.
 * Muted channels render flat white-14%.
 */
@Composable
fun Waveform(
    seed: Int,
    channelColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 44,
    containerHeight: Dp = 36.dp,
    gapWidth: Dp = 2.dp,
    progress: Float = 0f,     // 0..100
    volume: Int = 80,          // 0..100
    isMuted: Boolean = false,
    isAudible: Boolean = true,
) {
    val animProgress by animateFloatAsState(progress, tween(200), label = "waveProgress")
    val animVolume by animateFloatAsState(volume / 100f, tween(200), label = "waveVolume")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clip(RoundedCornerShape(4.dp))
    ) {
        val bars = seededBars(seed, barCount)
        val barW = (size.width - gapWidth.toPx() * (barCount - 1)) / barCount
        val level = 0.45f + animVolume * 0.55f
        val playProgress = animProgress / 100f

        bars.forEachIndexed { i, rawValue ->
            val barHeight = max(0.08f, rawValue * level * size.height)
            val isPlayed = (i.toFloat() / barCount) <= playProgress
            val barAlpha = if (isMuted) 1f else if (isPlayed) 1f else 0.32f
            val barColor = if (isMuted) WaveMuted else channelColor.copy(alpha = barAlpha)

            val x = i * (barW + gapWidth.toPx())
            val y = size.height - barHeight

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barW.coerceAtLeast(1f), barHeight),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

/** LCG-based seeded bar heights per spec */
fun seededBars(seed: Int, count: Int, floor: Float = 0.18f): FloatArray {
    var s = seed.toLong()
    return FloatArray(count) { i ->
        s = (s * 1664525L + 1013904223L) % 4294967296L
        val r = s.toFloat() / 4294967296f
        val envelope = 0.55f + 0.45f * sin((i.toFloat() / count) * PI.toFloat() * 3f + seed)
        (r * 0.75f * envelope + 0.25f * envelope).coerceIn(floor, 1f)
    }
}
