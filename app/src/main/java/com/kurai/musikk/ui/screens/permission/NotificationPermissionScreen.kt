package com.kurai.musikk.ui.screens.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurai.musikk.ui.components.MusikkIconTile
import com.kurai.musikk.ui.theme.*

@Composable
fun NotificationPermissionScreen(onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        // ── Blurred background preview ──────────────────────────────────
        Text(
            text = "MUSIKK",
            style = BrandLockupStyle,
            color = Foreground,
            modifier = Modifier
                .align(Alignment.Center)
                .blur(6.dp)
                .graphicsLayer(alpha = 0.55f),
        )

        // ── Black scrim overlay ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
        )

        // ── Dialog card ─────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .shadow(
                    elevation = 60.dp,
                    shape = RoundedCornerShape(RadiusDialog),
                    ambientColor = Color.Black.copy(alpha = 0.90f),
                    spotColor = Color.Black.copy(alpha = 0.90f),
                ),
            shape = RoundedCornerShape(RadiusDialog),
            color = SystemDialogBg,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 28.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Icon tile
                MusikkIconTile(tileSize = 56)

                Spacer(Modifier.height(20.dp))

                // Title
                Text(
                    text = "Allow Musikk to send you notifications?",
                    color = SystemDialogText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = (17 * 1.35).sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                // Body
                Text(
                    text = "Get alerted the moment your AI stem separation finishes and when practice reminders are due.",
                    color = SystemDialogBody,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = (13 * 1.6).sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                // Allow button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(RadiusFull))
                        .background(Primary, RoundedCornerShape(RadiusFull))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDone,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Allow",
                        color = OnPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Don't Allow button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(RadiusFull))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDone,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Don't Allow",
                        color = Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
