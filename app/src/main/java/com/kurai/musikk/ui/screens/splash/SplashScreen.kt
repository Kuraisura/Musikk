package com.kurai.musikk.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kurai.musikk.ui.components.MusikkIconTile
import com.kurai.musikk.ui.components.MusikkMark
import com.kurai.musikk.ui.components.Pill
import com.kurai.musikk.ui.components.PillTone
import com.kurai.musikk.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashDone: (Boolean) -> Unit) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(2000)
        val isSignedIn = FirebaseAuth.getInstance().currentUser != null
        onSplashDone(isSignedIn)
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "splashAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha),
        ) {
            // Animated logo mark
            val scale by animateFloatAsState(
                targetValue = if (visible) 1f else 0.6f,
                animationSpec = tween(600, easing = FastOutSlowInEasing),
                label = "splashScale",
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                MusikkIconTile(tileSize = 80)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "MUSIKK",
                style = BrandLockupStyle,
                color = Foreground,
            )

            Spacer(Modifier.height(8.dp))

            Pill(text = "AI STEM STUDIO", tone = PillTone.PRIMARY)
        }
    }
}
