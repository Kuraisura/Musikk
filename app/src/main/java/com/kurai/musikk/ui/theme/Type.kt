package com.kurai.musikk.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kurai.musikk.R

// Geist (UI & headings)
// Files: Geist-Regular.ttf, Geist-Medium.ttf, Geist-SemiBold.ttf
private val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

// Geist Mono (all numerics)
// Files: GeistMono-Regular.ttf, GeistMono-Medium.ttf, GeistMono-SemiBold.ttf
private val GeistMono = FontFamily(
    Font(R.font.geistmono_regular, FontWeight.Normal),
    Font(R.font.geistmono_medium, FontWeight.Medium),
    Font(R.font.geistmono_semibold, FontWeight.SemiBold),
)

// Fallback families when Geist files are not available
private val BodyFamily = Geist
private val MonoFamily = GeistMono

val MusikkTypography = Typography(
    // heroTitle
    titleLarge = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp, lineHeight = 31.sp, letterSpacing = (-0.02).em,
    ),
    // screenTitle
    titleMedium = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 23.sp, letterSpacing = (-0.02).em,
    ),
    // songTitle
    titleSmall = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 21.sp, letterSpacing = (-0.02).em,
    ),
    // bodyM
    bodyLarge = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.em,
    ),
    // rowTitle
    bodyMedium = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.em,
    ),
    // bodyS
    bodySmall = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 21.sp, letterSpacing = 0.em,
    ),
    // caption
    labelLarge = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.em,
    ),
    // captionS
    labelMedium = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.em,
    ),
    // micro
    labelSmall = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, lineHeight = 16.sp, letterSpacing = 0.02.em,
    ),
)

// ——— Custom mono text styles (not in default M3 Typography) ———
object MonoStyle {
    val display = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em,
        fontFeatureSettings = "tnum",
    )
    val title = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 20.sp, letterSpacing = 0.em,
        fontFeatureSettings = "tnum",
    )
    val body = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 18.sp, letterSpacing = 0.em,
        fontFeatureSettings = "tnum",
    )
    val small = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 14.sp, letterSpacing = 0.em,
        fontFeatureSettings = "tnum",
    )
}

// Brand lockup style — large letter-spacing
val BrandLockupStyle = TextStyle(
    fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp, lineHeight = 18.sp, letterSpacing = 0.22.em,
    textAlign = TextAlign.Center,
)
