package com.kurai.musikk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kurai.musikk.ui.theme.*

enum class PillTone { MUTED, PRIMARY, ACCENT, DESTRUCTIVE }

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.MUTED,
    icon: Painter? = null,
) {
    val (fill, textColor, ringColor) = when (tone) {
        PillTone.MUTED       -> Triple(Color.White.copy(alpha = 0.05f), MutedForeground, Color.White.copy(alpha = 0.10f))
        PillTone.PRIMARY     -> Triple(Primary.copy(alpha = 0.12f), Primary, Primary.copy(alpha = 0.25f))
        PillTone.ACCENT      -> Triple(Accent.copy(alpha = 0.15f), AccentText, Accent.copy(alpha = 0.30f))
        PillTone.DESTRUCTIVE -> Triple(Destructive.copy(alpha = 0.12f), Destructive, Destructive.copy(alpha = 0.25f))
    }

    val shape = RoundedCornerShape(RadiusFull)

    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, ringColor, shape)
            .then(
                Modifier
                    .clip(shape)
                    .then(
                        if (fill != Color.Transparent) Modifier.background(fill, shape)
                        else Modifier
                    )
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = textColor)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.02.em,
        )
    }
}
