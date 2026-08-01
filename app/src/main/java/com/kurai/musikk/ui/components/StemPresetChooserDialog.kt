package com.kurai.musikk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kurai.musikk.data.STEM_PRESETS
import com.kurai.musikk.data.StemKey
import com.kurai.musikk.data.StemPreset
import com.kurai.musikk.ui.theme.*

/**
 * Modal chooser listing all 10 stem presets. The user picks one and
 * [onChosen] is invoked; [onDismiss] cancels.
 */
@Composable
fun StemPresetChooserDialog(
    onDismiss: () -> Unit,
    onChosen: (StemPreset) -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.dp
    val screenHeightDp = config.screenHeightDp.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = screenHeightDp * 0.85f)
                .padding(20.dp)
                .clip(RoundedCornerShape(RadiusDialog))
                .background(SystemDialogBg)
                .border(1.dp, BorderHairline, RoundedCornerShape(RadiusDialog))
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "AI Stem Separation",
                        color = SystemDialogText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Pick the preset to split this song into.",
                    color = SystemDialogBody,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    STEM_PRESETS.forEach { preset ->
                        PresetRow(
                            preset = preset,
                            isSelected = selectedId == preset.id,
                            onClick = { selectedId = preset.id },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DialogButton(text = "Cancel", onClick = onDismiss, isPrimary = false)
                    Spacer(Modifier.width(8.dp))
                    DialogButton(
                        text = if (selectedId == null) "Pick a preset" else "Process",
                        onClick = {
                            val picked = STEM_PRESETS.firstOrNull { it.id == selectedId }
                                ?: STEM_PRESETS[3]
                            onChosen(picked)
                        },
                        isPrimary = selectedId != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: StemPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(if (isSelected) Primary.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f))
            .border(
                width = 1.dp,
                color = if (isSelected) Primary else RingHairline,
                shape = RoundedCornerShape(RadiusLg),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.displayName,
                color = SystemDialogText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = preset.description,
                color = SystemDialogBody,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(preset.stems.size) { idx ->
                        StemChip(preset.stems[idx])
                    }
                }
            }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StemChip(stem: StemKey) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(RadiusFull))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, RingHairline, RoundedCornerShape(RadiusFull))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = stem.displayName,
            color = SystemDialogBody,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.04.em,
        )
    }
}

@Composable
private fun DialogButton(text: String, onClick: () -> Unit, isPrimary: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(RadiusFull))
            .background(if (isPrimary) Primary else Color.White.copy(alpha = 0.06f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isPrimary) OnPrimary else SystemDialogText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
