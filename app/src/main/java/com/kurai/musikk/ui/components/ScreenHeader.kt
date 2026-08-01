package com.kurai.musikk.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kurai.musikk.ui.theme.MutedForeground
import com.kurai.musikk.ui.theme.Foreground

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = title,
                color = Foreground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 23.sp,
                letterSpacing = (-0.02).em,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = MutedForeground,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
