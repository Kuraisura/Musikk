package com.kurai.musikk.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kurai.musikk.ui.theme.*

/**
 * Musikk "M" soundwave mark.
 * Five bars forming the silhouette of an M.
 * Bars 1,2,4,5 = Primary; bar 3 = Accent.
 */
@Composable
fun MusikkMark(
    modifier: Modifier = Modifier,
    size: Int = 32,
) {
    Canvas(modifier = modifier.size(size.dp, (size * 0.875f).dp)) {
        val w = this.size.width
        val h = this.size.height
        val barW = w * 3f / 32f
        val barR = w * 1.5f / 32f
        val baseY = h  // bottom-aligned

        // Heights: 20, 13, 7, 13, 20 (as fraction of max = 20)
        val heights = listOf(1f, 0.65f, 0.35f, 0.65f, 1f)
        // X centres: at positions corresponding to 3,9,15,21,27 in 32-wide
        val xCenters = listOf(3f / 32f, 9f / 32f, 15f / 32f, 21f / 32f, 27f / 32f)
        // Colours: Primary, Primary, Accent, Primary, Primary
        val colors = listOf(Primary, Primary, Accent, Primary, Primary)

        for (i in heights.indices) {
            val barHeight = h * heights[i]
            val cx = w * xCenters[i]
            val color = colors[i]
            drawRoundRect(
                color = color,
                topLeft = Offset(cx - barW / 2f, baseY - barHeight),
                size = Size(barW, barHeight),
                cornerRadius = CornerRadius(barR),
            )
        }
    }
}

/** App-icon variant: mark at 2/3 scale inside a tile */
@Composable
fun MusikkIconTile(modifier: Modifier = Modifier, tileSize: Int = 64) {
    Box(
        modifier = modifier
            .size(tileSize.dp)
            .clip(RoundedCornerShape(Radius3xl))
            .background(Color(0xFF0F1218))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(Radius3xl)),
        contentAlignment = Alignment.Center,
    ) {
        MusikkMark(size = tileSize / 2)
    }
}
