package com.kurai.musikk.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kurai.musikk.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun MusikkToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Primary,
) {
    val trackColor by animateColorAsState(
        if (checked) activeColor else Color.White.copy(alpha = 0.12f),
        animationSpec = tween(200),
        label = "toggleTrack",
    )
    val thumbOffset by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        animationSpec = tween(200),
        label = "toggleThumb",
    )

    val trackWidth = 44.dp
    val trackHeight = 24.dp
    val thumbSize = 20.dp

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(RoundedCornerShape(RadiusFull))
            .background(trackColor)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Foreground)
        )
    }
}
