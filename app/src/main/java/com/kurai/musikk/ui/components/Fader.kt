package com.kurai.musikk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kurai.musikk.ui.theme.*

// Slider-based Fader with actual dragging
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaderSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    channelColor: Color,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    displayFormat: (Float) -> String = { it.toInt().toString() },
    scaleLabels: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().height(20.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Foreground,
                activeTrackColor = channelColor,
                inactiveTrackColor = TrackInactive,
            ),
            thumb = {
                Box(
                    Modifier
                        .size(16.dp)
                        .shadow(12.dp, CircleShape, ambientColor = channelColor.copy(alpha = 0.55f), spotColor = channelColor.copy(alpha = 0.55f))
                        .clip(CircleShape)
                        .background(Foreground)
                        .border(3.dp, channelColor, CircleShape)
                )
            },
        )
        if (scaleLabels != null) {
            scaleLabels()
        }
    }
}
