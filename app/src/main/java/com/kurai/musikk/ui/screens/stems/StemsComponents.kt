package com.kurai.musikk.ui.screens.stems

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurai.musikk.ui.components.seededBars
import com.kurai.musikk.ui.theme.*
import kotlin.math.*

/**
 * Rotary knob fader. Drag vertically to change the value. 
 * Rendered as a circular dial with an arc fill that sweeps from 7 o'clock to 5 o'clock. 
 */
@Composable
fun Knob(
    value: Int, // 0..100
    onValueChange: (Int) -> Unit,
    channelColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val sweepMin = -210f
    val sweepMax = 210f
    val sweep = sweepMin + (sweepMax - sweepMin) * (value.coerceIn(0, 100) / 100f)
    var dragStartY = 0f
    var dragStartValue = value

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(channelColor) {
                detectTapGestures(
                    onTap = { offset ->
                        // Tapping the right half bumps +5; left half drops -5. 
                        val midX = this.size.width / 2f
                        val delta = if (offset.x >= midX) 5 else -5
                        onValueChange((dragStartValue + delta).coerceIn(0, 100))
                        dragStartValue = dragStartValue + delta
                    },
                )
            }
            .pointerInput(channelColor) {
                detectDragGestures(
                    onDragStart = { _ ->
                        dragStartY = 0f
                        dragStartValue = value
                    },
                    onDragEnd = { },
                    onDragCancel = { },
                    onDrag = { change, drag ->
                        dragStartY += drag.y
                        change.consume()
                        // 240 px of travel == full range
                        val delta = (-dragStartY / 240f * 100f).toInt()
                        val next = (dragStartValue + delta).coerceIn(0, 100)
                        if (next != value) onValueChange(next)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.10f
            val inset = stroke / 2f + 2f
            val arcSize = Size(w - inset * 2f, h - inset * 2f)
            val topLeft = Offset(inset, inset)
            val centerX = w / 2f
            val centerY = h / 2f
            val radius = (w - inset * 2f) / 2f

            // Track ring - improved appearance
            drawArc(
                color = TrackInactive,
                topLeft = topLeft,
                size = arcSize,
                startAngle = sweepMin + 90f,
                sweepAngle = sweepMax - sweepMin,
                useCenter = false,
                style = Stroke(width = stroke),
            )
            // Value arc — gradient from muted to channel colour
            if (value > 0) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to channelColor.copy(alpha = 0.45f),
                        1f to channelColor,
                    ),
                    topLeft = topLeft,
                    size = arcSize,
                    startAngle = sweepMin + 90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = stroke),
                )
            }
            
            // Draw small ticks for better visual feedback
            val tickCount = 11 // 0%, 10%, 20%, ..., 100%
            for (i in 0..tickCount) {
                val tickAngle = sweepMin + 90f + (sweepMax - sweepMin) * (i / tickCount.toFloat())
                rotate(tickAngle) {
                    val tickStartY = if (i % 5 == 0) inset + stroke / 2f - (stroke * 0.3f) else inset + stroke / 2f - (stroke * 0.1f)
                    val tickEndY = inset + stroke / 2f + (stroke * 0.1f)
                    drawLine(
                        color = if (i % 5 == 0) Foreground.copy(alpha = 0.8f) else TrackInactive,
                        start = Offset(centerX, tickStartY),
                        end = Offset(centerX, tickEndY),
                        strokeWidth = if (i % 5 == 0) 2f else 1f,
                    )
                }
            }
            
            // Indicator notch (white tick at the current value) - improved accuracy
            rotate(sweep + 90f) {
                val notchLen = stroke * 1.6f
                val notchStartY = inset + stroke / 2f - (notchLen - stroke) / 2f
                val notchEndY = inset + stroke / 2f + notchLen / 2f
                drawLine(
                    color = Foreground,
                    start = Offset(centerX, notchStartY),
                    end = Offset(centerX, notchEndY),
                    strokeWidth = 3f,
                )
                
                // Draw a small circle at the end of the notch for better visibility
                drawCircle(
                    color = Foreground,
                    radius = 4f,
                    center = Offset(centerX, notchEndY),
                )
            }
        }
        // Display volume percentage in the center
        Text(
            text = "$value",
            color = Foreground,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Centered transport circle: progress arc around a play/pause disc.
 */
@Composable
fun PlayArcButton(
    isPlaying: Boolean,
    progressPercent: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    arcColor: Color = Primary,
    onColor: Color = OnPrimary,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(20.dp, CircleShape, ambientColor = arcColor.copy(alpha = 0.55f), spotColor = arcColor.copy(alpha = 0.55f))
            .clip(CircleShape)
            .background(SurfaceElevated)
            .border(1.dp, BorderHairline, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = this.size.width * 0.060f
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
            // Backtrack
            drawArc(
                color = TrackInactive,
                topLeft = Offset(inset, inset),
                size = arcSize,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke),
            )
            // Progress arc
            val pct = (progressPercent.coerceIn(0f, 100f)) / 100f
            if (pct > 0f) {
                drawArc(
                    color = arcColor,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    startAngle = -90f,
                    sweepAngle = 360f * pct,
                    useCenter = false,
                    style = Stroke(width = stroke),
                )
            }
        }
        // Inner disc + glyph. NOTE: no clickable/pointerInput here, so tap
        // events on this inner Box fall through to the outer Box's clickable.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.12f)
                .clip(CircleShape)
                .background(arcColor),
            contentAlignment = Alignment.Center,
        ) {
            // Simple play/pause glyph drawn inline so we don't import Material icons here.
            Canvas(modifier = Modifier.size(size * 0.34f)) {
                val col = onColor
                if (isPlaying) {
                    // Pause: two rounded bars
                    val barW = this.size.width * 0.22f
                    val barH = this.size.height * 0.62f
                    val gap = this.size.width * 0.16f
                    val y = (this.size.height - barH) / 2f
                    val x0 = (this.size.width - (barW * 2f + gap)) / 2f
                    drawRoundRect(
                        color = col,
                        topLeft = Offset(x0, y),
                        size = Size(barW, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.4f),
                    )
                    drawRoundRect(
                        color = col,
                        topLeft = Offset(x0 + barW + gap, y),
                        size = Size(barW, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.4f),
                    )
                } else {
                    // Play: triangle
                    val w = this.size.width
                    val h = this.size.height
                    val insetX = w * 0.10f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(insetX, h * 0.14f)
                        lineTo(w - insetX, h * 0.50f)
                        lineTo(insetX, h * 0.86f)
                        close()
                    }
                    drawPath(path, col)
                }
            }
        }
    }
}

/**
 * Stacked waveform timeline combining all stem channels. A single progress line
 * sweeps left-to-right; the entire strip is draggable to seek. 
 */
@Composable
fun StackedTimeline(
    seeds: List<Int>,
    channelColors: List<Color>,
    volumes: List<Int>,
    mutes: List<Boolean>,
    audible: List<Boolean>,
    progressPercent: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 110.dp,
    barCount: Int = 96,
) {
    val rows = seeds.size
    val rowH = (height / rows) * 0.92f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(RadiusLg))
            .background(Color(0xFF0F1218))
            .border(1.dp, BorderHairline, RoundedCornerShape(RadiusLg))
            .pointerInput(onSeek) {
                detectDragGestures(
                    onDragStart = { off ->
                        val pct = (off.x / this.size.width).coerceIn(0f, 1f)
                        onSeek(pct * 100f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pct = (change.position.x / this.size.width).coerceIn(0f, 1f)
                        onSeek(pct * 100f)
                    },
                )
            }
            .pointerInput(onSeek) {
                detectTapGestures(
                    onTap = { off ->
                        val pct = (off.x / this.size.width).coerceIn(0f, 1f)
                        onSeek(pct * 100f)
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val rowHeight = h / rows
            val gapWidth = 2f
            val barW = (w - gapWidth * (barCount - 1)) / barCount
            val playPct = (progressPercent.coerceIn(0f, 100f)) / 100f

            // Create more dynamic waveforms based on progress and volume
            seeds.forEachIndexed { row, seed ->
                val baseY = row * rowHeight
                val audibleRow = audible.getOrElse(row) { true }
                val muted = mutes.getOrElse(row) { false }
                val level = 0.45f + (volumes.getOrElse(row) { 80 } / 100f) * 0.55f
                val color = channelColors.getOrElse(row) { MutedForeground }
                
                // Generate bars that respond to the playback position
                // This creates a more dynamic waveform that appears to sync with playback
                val bars = FloatArray(barCount) { i ->
                    val progressPos = (i.toFloat() / barCount) - playPct
                    // Create a pulse effect around the playhead
                    val pulseEffect = if (abs(progressPos) < 0.05f) {
                        0.7f + 0.3f * (1f - abs(progressPos) * 10f)
                    } else {
                        0.3f + 0.4f * (1f - abs(progressPos).coerceAtMost(0.5f) * 2f)
                    }
                    
                    // Use the seed to create consistent but varied waveforms
                    val seededValue = seededBars(seed, barCount)[i]
                    
                    // Combine with volume and pulse effect
                    (seededValue * pulseEffect * level).coerceIn(0.1f, 1f)
                }
                
                bars.forEachIndexed { i, raw ->
                    val barHeightMax = rowHeight * 0.92f
                    val barHeight = max(barHeightMax * 0.10f, raw * barHeightMax)
                    val played = (i.toFloat() / barCount) <= playPct
                    val colorFinal = if (muted || !audibleRow) WaveMuted
                    else if (played) color else color.copy(alpha = 0.30f)
                    val x = i * (barW + gapWidth)
                    val y = baseY + (rowHeight - barHeight) / 2f
                    drawRoundRect(
                        color = colorFinal,
                        topLeft = Offset(x, y),
                        size = Size(barW.coerceAtLeast(1f), barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f),
                    )
                }
            }
            // Playhead line
            val playX = w * playPct
            drawLine(
                color = Foreground,
                start = Offset(playX, 0f),
                end = Offset(playX, h),
                strokeWidth = 2f,
            )
            // Playhead knob
            drawCircle(
                color = Primary,
                radius = 5f,
                center = Offset(playX, 0f),
            )
        }
    }
}
