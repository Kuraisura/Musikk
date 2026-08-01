package com.kurai.musikk.ui.screens.stems

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kurai.musikk.ui.components.FaderSlider
import com.kurai.musikk.ui.components.MusikkCard
import com.kurai.musikk.ui.components.Waveform
import com.kurai.musikk.ui.theme.*

@Composable
fun TrackStrip(
    channelIndex: Int,
    name: String,
    role: String,
    volume: Int,
    onVolumeChange: (Int) -> Unit,
    isSolo: Boolean,
    onSoloToggle: () -> Unit,
    isMute: Boolean,
    onMuteToggle: () -> Unit,
    isAudible: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val channelColour = channelColor(channelIndex)

    MusikkCard(
        modifier = modifier.then(
            if (!isAudible) Modifier.alpha(0.45f) else Modifier
        ),
        padding = PaddingValues(14.dp),
    ) {
        Column {
            // ── Header row: colour dot · name · role · SOLO · MUTE ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 8 dp colour dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isMute) channelColour.copy(alpha = 0.3f)
                            else channelColour
                        )
                )

                Spacer(Modifier.width(8.dp))

                // Name + role (stacked vertically so role sits below name)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = Foreground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = role,
                        color = MutedForeground,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // ── Trailing controls: SOLO chip + MUTE button (6 dp apart) ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // SOLO chip
                    val soloBg = if (isSolo) channelColour else Color.White.copy(alpha = 0.04f)
                    val soloContent = if (isSolo) OnPrimary else MutedForeground

                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(RadiusFull))
                            .background(soloBg)
                            .clickable { onSoloToggle() }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "Solo",
                                modifier = Modifier.size(12.dp),
                                tint = soloContent,
                            )
                            Text(
                                text = "SOLO",
                                color = soloContent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // MUTE button
                    val muteBg = if (isMute) Destructive.copy(alpha = 0.15f)
                    else Color.White.copy(alpha = 0.04f)
                    val muteRing = Color.White.copy(alpha = 0.08f)
                    val muteIcon = if (isMute) Icons.Default.VolumeOff
                    else Icons.Default.VolumeUp
                    val muteTint = if (isMute) Destructive else MutedForeground

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.dp, muteRing, CircleShape)
                            .background(muteBg)
                            .clickable { onMuteToggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = muteIcon,
                            contentDescription = if (isMute) "Unmute" else "Mute",
                            modifier = Modifier.size(14.dp),
                            tint = muteTint,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Waveform ──
            Waveform(
                seed = channelSeed(channelIndex),
                channelColor = channelColour,
                barCount = 44,
                containerHeight = 36.dp,
                progress = progress,
                volume = volume,
                isMuted = isMute,
                isAudible = isAudible,
            )

            Spacer(Modifier.height(8.dp))

            // ── Fader slider + volume readout ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaderSlider(
                    value = volume.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt()) },
                    channelColor = channelColour,
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.04f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = volume.toString(),
                        color = MutedForeground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.02.em,
                    )
                }
            }
        }
    }
}
