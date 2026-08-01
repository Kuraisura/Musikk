package com.kurai.musikk.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val RadiusSm   = 7.dp
val RadiusMd   = 10.5.dp
val RadiusLg   = 14.dp
val RadiusXl   = 19.6.dp
val Radius2xl  = 25.2.dp
val Radius3xl  = 33.6.dp
val RadiusDialog = 28.dp
val RadiusFull = 999.dp

val MusikkShapes = Shapes(
    extraSmall = RoundedCornerShape(RadiusSm),
    small      = RoundedCornerShape(RadiusLg),
    medium     = RoundedCornerShape(Radius2xl),
    large      = RoundedCornerShape(Radius3xl),
    extraLarge = RoundedCornerShape(RadiusFull),
)
