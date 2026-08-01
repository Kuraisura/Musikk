package com.kurai.musikk.ui.screens.onboarding

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.kurai.musikk.ui.components.MusikkIconTile
import com.kurai.musikk.ui.components.Pill
import com.kurai.musikk.ui.components.PillTone
import com.kurai.musikk.ui.theme.*
import com.kurai.musikk.R

// Simple glass field composable
@Composable
private fun GlassField(
    value: String, onValueChange: (String) -> Unit,
    placeholder: String, leadingIcon: @Composable () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(Radius2xl))
            .background(Surface.copy(alpha = 0.70f))
            .border(1.dp, InputRing, RoundedCornerShape(Radius2xl))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(16.dp)) { leadingIcon() }
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Foreground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 21.sp,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = visualTransformation,
                singleLine = true,
                cursorBrush = SolidColor(Primary),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            color = MutedForeground.copy(alpha = 0.70f),
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val scrollState = rememberScrollState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var marketingConsent by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    // Google Sign-In launcher
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { task ->
                    isLoading = false
                    if (task.isSuccessful) {
                        ensureUserDocument(auth)
                        onContinue()
                    }
                    else Toast.makeText(context, task.exception?.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                isLoading = false
                Toast.makeText(context, "Google sign-in failed", Toast.LENGTH_SHORT).show()
            }
        } else {
            isLoading = false
        }
    }

    fun googleSignIn() {
        isLoading = true
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("871391575584-pb0gkpkckm5sga726n1ik6q68cs3eejf.apps.googleusercontent.com")
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        // Force account picker — prevents auto-sign-in with last-used account
        client.signOut().addOnCompleteListener {
            googleLauncher.launch(client.signInIntent)
        }
    }

    fun emailAuth() {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            Toast.makeText(context, "Please enter a valid email", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 8) {
            Toast.makeText(context, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    ensureUserDocument(auth)
                    isLoading = false
                    onContinue()
                }
                else {
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnCompleteListener { signIn ->
                            isLoading = false
                            if (signIn.isSuccessful) {
                                ensureUserDocument(auth)
                                onContinue()
                            }
                            else Toast.makeText(context, signIn.exception?.message, Toast.LENGTH_LONG).show()
                        }
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Brand block
        MusikkIconTile(tileSize = 64)
        Spacer(Modifier.height(12.dp))
        Text("MUSIKK", style = BrandLockupStyle, color = Foreground)
        Spacer(Modifier.height(8.dp))
        Pill(text = "AI STEM STUDIO", tone = PillTone.PRIMARY)

        // Headline
        Spacer(Modifier.height(32.dp))
        Text(
            buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(color = Foreground, fontWeight = FontWeight.SemiBold)) {
                    append("Unleash Your Music.\n")
                }
                withStyle(androidx.compose.ui.text.SpanStyle(color = Primary, fontWeight = FontWeight.SemiBold)) {
                    append("Isolate.")
                }
                append(" ")
                withStyle(androidx.compose.ui.text.SpanStyle(color = AccentText, fontWeight = FontWeight.SemiBold)) {
                    append("Practice.")
                }
                append(" ")
                withStyle(androidx.compose.ui.text.SpanStyle(color = Foreground, fontWeight = FontWeight.SemiBold)) {
                    append("Master.")
                }
            },
            textAlign = TextAlign.Center, fontSize = 27.sp, lineHeight = 31.sp, letterSpacing = (-0.02).em,
        )

        // Description
        Spacer(Modifier.height(12.dp))
        Text(
            "Split any track into vocals, guitars, drums and bass \u2014 then loop, slow down and learn it note for note.",
            color = MutedForeground, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            lineHeight = (14 * 1.6).sp, textAlign = TextAlign.Center,
        )

        // Google button
        Spacer(Modifier.height(28.dp))
        val googleSrc = remember { MutableInteractionSource() }
        val googlePressed by googleSrc.collectIsPressedAsState()
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(Radius2xl))
                .background(Color.White, RoundedCornerShape(Radius2xl))
                .clickable(googleSrc, null) { if (!isLoading) googleSignIn() }
                .graphicsLayer(alpha = if (googlePressed || isLoading) 0.80f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google_g),
                    contentDescription = "Google",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("Continue with Google", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Divider
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            Spacer(Modifier.width(12.dp))
            Text("OR", color = MutedForeground, fontSize = 11.sp)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        }

        // Email field
        Spacer(Modifier.height(16.dp))
        GlassField(email, { email = it }, "you@email.com",
            { Icon(Icons.Default.Email, null, tint = MutedForeground, modifier = Modifier.size(16.dp)) },
            KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        GlassField(password, { password = it }, "Password",
            { Icon(Icons.Default.Lock, null, tint = MutedForeground, modifier = Modifier.size(16.dp)) },
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(8.dp))

        // Marketing checkbox
        Row(
            Modifier.fillMaxWidth().clickable { marketingConsent = !marketingConsent }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(7.dp))
                    .background(if (marketingConsent) Primary else Color.Transparent)
                    .border(1.5.dp, if (marketingConsent) Primary else Color.White.copy(alpha = 0.20f), RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (marketingConsent) Icon(Icons.Default.Check, null, tint = OnPrimary, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Send me news & marketing from Musikk.", color = MutedForeground, fontSize = 12.sp)
        }

        // CTA button
        Spacer(Modifier.height(16.dp))
        val ctaSrc = remember { MutableInteractionSource() }
        val ctaPressed by ctaSrc.collectIsPressedAsState()
        Box(
            Modifier.fillMaxWidth().height(52.dp)
                .shadow(12.dp, RoundedCornerShape(Radius2xl), ambientColor = Primary, spotColor = Primary)
                .clip(RoundedCornerShape(Radius2xl)).background(Primary)
                .clickable(ctaSrc, null) { if (!isLoading) emailAuth() }
                .graphicsLayer(alpha = if (ctaPressed || isLoading) 0.85f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Create free account \u2192", color = OnPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Legal
        Spacer(Modifier.height(24.dp))
        Text(
            "By continuing, I Agree to the Terms of Service and Privacy Policy.",
            color = MutedForeground.copy(alpha = 0.80f), fontSize = 11.sp,
            lineHeight = (11 * 1.6).sp, textAlign = TextAlign.Center,
        )
    }
}

/** Create or update the Firestore user document after auth */
private fun ensureUserDocument(auth: FirebaseAuth) {
    val user = auth.currentUser ?: return
    val firestore = FirebaseFirestore.getInstance()
    val uid = user.uid
    val email = user.email ?: ""

    val data = hashMapOf<String, Any>(
        "uid" to uid,
        "email" to email,
        "displayName" to (user.displayName ?: email.split("@").first()),
        "photoUrl" to (user.photoUrl?.toString() ?: ""),
        "marketingConsent" to true,
        "freeCreditsMinutes" to 20,
        "totalCreditsMinutes" to 20,
        "subscriptionTier" to "Free",
        "processingEngine" to "Ultra BS-RoFormer",
        "creditsResetDate" to (System.currentTimeMillis() + 30L * 24 * 3600 * 1000),
        "createdAt" to System.currentTimeMillis(),
        "lastLoginAt" to System.currentTimeMillis(),
    )

    firestore.collection("users").document(uid)
        .set(data)
        .addOnSuccessListener { /* User document created */ }
        .addOnFailureListener { /* Will retry on next login */ }
}
