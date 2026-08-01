package com.kurai.musikk.ui.screens.profile

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kurai.musikk.ui.components.*
import com.kurai.musikk.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val scrollState = rememberScrollState()
    var selectedQuality by remember { mutableStateOf("Ultra BS-RoFormer") }

    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val creditsDefault = 0
    val creditsLimit = 20
    var creditsUsed by remember { mutableStateOf(creditsDefault) }
    var creditsResetAt by remember { mutableStateOf<Date?>(null) }

    LaunchedEffect(uid) {
        if (uid.isNullOrBlank()) return@LaunchedEffect
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                creditsUsed = (snapshot.getLong("creditsUsed") ?: 0L).toInt()
                creditsResetAt = snapshot.getTimestamp("creditsResetAt")?.toDate()
            }
    }

    val creditsRemaining = (creditsLimit - creditsUsed).coerceAtLeast(0)
    val creditsProgress = creditsUsed.toFloat() / creditsLimit.toFloat()
    val resetLabel = creditsResetAt?.let {
        "Free monthly credits reset on ${SimpleDateFormat("MMM d", Locale.US).format(it)}."
    } ?: "Free monthly credits refresh monthly."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ═══════════════════════════════════════════
        // 1. IDENTITY
        // ═══════════════════════════════════════════
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .border(2.dp, Primary.copy(alpha = 0.40f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MutedForeground,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = FirebaseAuth.getInstance().currentUser?.displayName
                        ?: FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")
                        ?: "Musician",
                    color = Foreground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = FirebaseAuth.getInstance().currentUser?.email ?: "",
                    color = MutedForeground,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Pill(text = "Free Tier Member", tone = PillTone.MUTED)
            }
        }

        // ═══════════════════════════════════════════
        // 2. CREDITS CARD
        // ═══════════════════════════════════════════
        MusikkCard(padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CreditRing(used = creditsUsed, total = creditsLimit)

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remaining AI Processing Time",
                        color = Foreground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = creditsRemaining.toString(),
                            style = MonoStyle.small,
                            color = Primary,
                        )
                        Text(
                            text = " Mins",
                            color = Primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = " / $creditsLimit Mins",
                            color = MutedForeground,
                            fontSize = 14.sp,
                        )
                    }

                    Text(
                        text = resetLabel,
                        color = MutedForeground,
                        fontSize = 10.sp,
                    )

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(RadiusFull))
                            .background(Color.White.copy(alpha = 0.10f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(creditsProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(RadiusFull))
                                .background(Primary),
                        )
                    }
                }
            }
        }

        // ═══════════════════════════════════════════
        // 3. UPGRADE BANNER
        // ═══════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius2xl))
                .background(Accent.copy(alpha = 0.15f))
                .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(Radius2xl))
                .padding(16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Icon tile
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(RadiusLg))
                            .background(Accent.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = AccentText,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Upgrade to Musikk Pro",
                            color = Foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Unlimited AI Splits & HD Export",
                            color = MutedForeground,
                            fontSize = 11.sp,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // CTA button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(RadiusXl))
                        .background(Accent)
                        .clickable { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "See Pro plans",
                        color = OnAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // ═══════════════════════════════════════════
        // 4. TIER GRID (2 columns)
        // ═══════════════════════════════════════════
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Monthly Pass
            MusikkCard(
                modifier = Modifier.weight(1f),
                padding = PaddingValues(16.dp),
            ) {
                TierCardContent(
                    title = "Monthly Pass",
                    price = "$4.99",
                    hint = "Cancel anytime \u00B7 300 AI mins",
                    isPro = false,
                )
            }

            // Pro Musician
            MusikkCard(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Primary.copy(alpha = 0.35f), RoundedCornerShape(Radius2xl)),
                padding = PaddingValues(16.dp),
            ) {
                TierCardContent(
                    title = "Pro Musician",
                    price = "$11.99",
                    hint = "Best value \u00B7 Unlimited splits",
                    isPro = true,
                )
            }
        }

        // ═══════════════════════════════════════════
        // 5. QUALITY SWITCH CARD
        // ═══════════════════════════════════════════
        MusikkCard(padding = PaddingValues(14.dp)) {
            Column {
                Text(
                    text = "Audio Processing Quality",
                    color = Foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(Modifier.height(10.dp))

                // 2-up segmented row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SegmentedOption(
                        label = "Standard",
                        selected = selectedQuality == "Standard",
                        onClick = { selectedQuality = "Standard" },
                        modifier = Modifier.weight(1f),
                    )
                    SegmentedOption(
                        label = "Ultra BS-RoFormer",
                        selected = selectedQuality == "Ultra BS-RoFormer",
                        onClick = { selectedQuality = "Ultra BS-RoFormer" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ═══════════════════════════════════════════
        // 6. SETTINGS LIST CARD
        // ═══════════════════════════════════════════
        MusikkCard(padding = PaddingValues(6.dp)) {
            Column {
                SettingsRow(
                    icon = Icons.Default.Person,
                    label = "Edit Profile",
                    hint = "Name, avatar, instruments",
                )
                SettingsRow(
                    icon = Icons.Default.Tune,
                    label = "Audio Processing Quality",
                    hint = "Standard vs. Ultra BS-RoFormer",
                )
                SettingsRow(
                    icon = Icons.Default.Bolt,
                    label = "Firebase Account & Storage",
                    hint = "Sync, cache, cloud storage",
                )
                SettingsRow(
                    icon = Icons.Default.VerifiedUser,
                    label = "Privacy & Terms",
                    hint = "Policies and data controls",
                )
            }
        }

        // ═══════════════════════════════════════════
        // 7. LOGOUT
        // ═══════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(Radius2xl))
                .background(Destructive.copy(alpha = 0.12f))
                .border(1.dp, Destructive.copy(alpha = 0.25f), RoundedCornerShape(Radius2xl))
                .clickable { onLogout() },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    tint = Destructive,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Log Out",
                    color = Destructive,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(0.dp))
    }
}

// ── CREDIT RING ──
@Composable
fun CreditRing(used: Int, total: Int, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = used.toFloat() / total.toFloat(),
        animationSpec = tween(600),
        label = "creditRing",
    )

    val circumference = (2 * PI * 34).toFloat()

    Box(
        modifier = modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(80.dp)) {
            val strokeWidth = 7.dp.toPx()
            val topLeft = (size.width - 68.dp.toPx()) / 2f

            // Track
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(topLeft, topLeft),
                size = Size(68.dp.toPx(), 68.dp.toPx()),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Progress
            drawArc(
                color = Primary,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(topLeft, topLeft),
                size = Size(68.dp.toPx(), 68.dp.toPx()),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${total - used}",
                style = MonoStyle.body,
                color = Primary,
            )
            Text(
                text = "mins left",
                fontSize = 9.sp,
                color = MutedForeground,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── TIER CARD CONTENT ──
@Composable
private fun TierCardContent(
    title: String,
    price: String,
    hint: String,
    isPro: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (isPro) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Price
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = price,
                style = MonoStyle.title,
                color = Foreground,
            )
            Text(
                text = "/mo",
                fontSize = 11.sp,
                color = MutedForeground,
                modifier = Modifier.offset(y = (-2).dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        // Hint
        Text(
            text = hint,
            fontSize = 10.sp,
            color = MutedForeground,
        )

        Spacer(Modifier.height(12.dp))

        // Button
        val buttonFill: Color
        val buttonText: Color
        val buttonRing: Color

        if (isPro) {
            buttonFill = Primary
            buttonText = OnPrimary
            buttonRing = Color.Transparent
        } else {
            buttonFill = Color.White.copy(alpha = 0.06f)
            buttonText = Foreground
            buttonRing = Color.White.copy(alpha = 0.10f)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(RadiusXl))
                .background(buttonFill)
                .border(1.dp, buttonRing, RoundedCornerShape(RadiusXl))
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Choose",
                color = buttonText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── SEGMENTED QUALITY OPTION ──
@Composable
private fun SegmentedOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = if (selected) Primary.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (selected) Primary else MutedForeground
    val ring = if (selected) Primary.copy(alpha = 0.40f) else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusXl))
            .background(fill)
            .border(1.dp, ring, RoundedCornerShape(RadiusXl))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

// ── SETTINGS ROW ──
@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    hint: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .clickable { }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon tile
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(RadiusLg))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = hint,
                color = MutedForeground,
                fontSize = 10.sp,
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MutedForeground,
            modifier = Modifier.size(16.dp),
        )
    }
}
