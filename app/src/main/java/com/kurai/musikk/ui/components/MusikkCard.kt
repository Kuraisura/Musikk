package com.kurai.musikk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kurai.musikk.ui.theme.*

@Composable
fun MusikkCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Radius2xl)
    Surface(
        modifier = modifier
            .clip(shape)
            .border(1.dp, RingHairline, shape),
        shape = shape,
        color = Surface,
        content = {
            androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
                content()
            }
        },
    )
}
