package com.kurai.musikk.ui.screens.practice

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurai.musikk.data.*
import com.kurai.musikk.ui.components.*
import com.kurai.musikk.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PracticeScreen() {
    var currentSong by remember { mutableStateOf(SelectedSong.current.value) }

    LaunchedEffect(Unit) {
        SelectedSong.current.collect { song -> currentSong = song }
    }

    if (currentSong == null) {
        EmptyPracticeScreen()
        return
    }

    val song = currentSong!!
    val songBpm = song.bpm

    PracticeContent(song = song, songBpm = songBpm)
}

@Composable
private fun EmptyPracticeScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(Background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MusikkIconTile(tileSize = 72)
        Spacer(Modifier.height(20.dp))
        Text("No song selected", color = Foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Load a song from the Repertoire to start practicing.", color = MutedForeground, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PracticeContent(song: Song, songBpm: Int) {
    val scrollState = rememberScrollState()

    // A/B Loop state
    var loopProgress by remember { mutableFloatStateOf(0f) }
    var loopA by remember { mutableFloatStateOf(24f) }
    var loopB by remember { mutableFloatStateOf(58f) }
    var isLooping by remember { mutableStateOf(false) }

    // Metronome state — defaults to song's BPM
    var bpm by remember { mutableIntStateOf(songBpm) }
    var metronomeOn by remember { mutableStateOf(false) }
    var currentBeat by remember { mutableIntStateOf(0) }
    var tapTimestamps by remember { mutableStateOf(listOf<Long>()) }

    // Chord state
    var chordIndex by remember { mutableIntStateOf(0) }

    // Transposer state
    var semitones by remember { mutableFloatStateOf(0f) }

    // Speed state
    var speed by remember { mutableFloatStateOf(1.0f) }

    // Session timer
    var sessionStart by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sessionElapsed by remember { mutableLongStateOf(0L) }

    // Animations
    LaunchedEffect(isLooping) {
        if (!isLooping) return@LaunchedEffect
        while (true) {
            delay(110)
            val p = loopProgress + 0.5f
            loopProgress = if (p >= loopB) loopA else p
        }
    }

    LaunchedEffect(metronomeOn) {
        if (!metronomeOn) return@LaunchedEffect
        while (true) {
            val interval = 60000L / bpm
            currentBeat = (currentBeat + 1) % 4
            delay(interval)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2200)
            chordIndex = (chordIndex + 1) % chordProgression.size
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            sessionElapsed = System.currentTimeMillis() - sessionStart
        }
    }

    Column(
        Modifier.fillMaxSize().background(Background).verticalScroll(scrollState)
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 20.dp),
    ) {
        // Header
        ScreenHeader(
            title = "Practice Toolkit",
            subtitle = "${song.title} \u00B7 ${song.musicalKey} \u00B7 ${song.bpm} BPM",
            trailing = { Pill(text = "Session ${formatSession(sessionElapsed)}", tone = PillTone.ACCENT) },
        )

        // A/B Loop Card
        Spacer(Modifier.height(12.dp))
        MusikkCard(padding = PaddingValues(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Repeat, null, tint = Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("A / B Loop", color = Foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Pill(text = "4 bars isolated", tone = PillTone.PRIMARY)
            }
            Spacer(Modifier.height(10.dp))
            Waveform(seed = 7, channelColor = Primary, barCount = 56, containerHeight = 80.dp, progress = loopProgress)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("A ${loopA.toInt()}% \u00B7 B ${loopB.toInt()}%", color = MutedForeground, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(RadiusFull)).background(Primary)
                        .clickable { isLooping = !isLooping }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(if (isLooping) "Looping" else "Play loop", color = OnPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 2x2 Grid: Metronome + Chord Reader
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Metronome
            MusikkCard(modifier = Modifier.weight(1f), padding = PaddingValues(14.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Metronome", color = Foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())

                    Spacer(Modifier.height(10.dp))
                    // Tap Tempo ring
                    Box(
                        Modifier.size(80.dp).clip(CircleShape)
                            .background(Primary.copy(alpha = 0.12f))
                            .border(1.dp, Primary.copy(alpha = 0.40f), CircleShape)
                            .clickable { /* Tap tempo */
                                val now = System.currentTimeMillis()
                                tapTimestamps = (tapTimestamps + now).takeLast(8)
                                if (tapTimestamps.size >= 2) {
                                    val gaps = tapTimestamps.zipWithNext { a, b -> b - a }.filter { it < 3000 }
                                    if (gaps.isNotEmpty()) {
                                        bpm = (60000.0 / gaps.average()).toInt().coerceIn(40, 240)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Timer, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Text("TAP", color = Primary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text("TEMPO", color = MutedForeground, fontSize = 9.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    // BPM stepper
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape).clickable { bpm = (bpm - 1).coerceAtLeast(40) }, contentAlignment = Alignment.Center) {
                            Text("\u2212", color = Foreground, fontSize = 18.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("$bpm", color = Foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text("BPM", color = MutedForeground, fontSize = 10.sp)
                        }
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape).clickable { bpm = (bpm + 1).coerceAtMost(240) }, contentAlignment = Alignment.Center) {
                            Text("+", color = Foreground, fontSize = 18.sp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    // Beat dots
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(4) { i ->
                            Box(
                                Modifier.size(6.dp).clip(CircleShape)
                                    .background(if (i == currentBeat) Primary else Color.White.copy(alpha = 0.15f))
                            )
                        }
                    }
                }
            }

            // Live Chord Reader
            MusikkCard(modifier = Modifier.weight(1f), padding = PaddingValues(14.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Live Chord Reader", color = Foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())

                    Spacer(Modifier.height(16.dp))
                    val chord = chordProgression.getOrElse(chordIndex) { "Cmaj7" }
                    val nextChord = chordProgression.getOrElse(chordIndex + 1) { "Cmaj7" }
                    Text(chord, color = Foreground, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("\u2192 $nextChord", color = MutedForeground, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    // Progress ticks
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        chordProgression.forEachIndexed { i, _ ->
                            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(RadiusFull))
                                .background(if (i == chordIndex) Accent else Color.White.copy(alpha = 0.12f)))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Synced to timeline \u00B7 bar ${chordIndex + 1}", color = MutedForeground, fontSize = 10.sp)
                }
            }
        }

        // Bottom row: Transposer + Speed
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Key Transposer
            MusikkCard(modifier = Modifier.weight(1f), padding = PaddingValues(14.dp)) {
                Text("Key Transposer", color = Foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                val st = semitones.toInt()
                val sign = if (st > 0) "+" else ""
                Text("$sign$st semitones", color = AccentText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Slider(
                    value = semitones, onValueChange = { semitones = it },
                    valueRange = -6f..6f, steps = 11,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Accent, activeTrackColor = Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("\u22126", color = MutedForeground, fontSize = 10.sp)
                    Text("0", color = MutedForeground, fontSize = 10.sp)
                    Text("+6", color = MutedForeground, fontSize = 10.sp)
                }
            }

            // Speed Controller
            MusikkCard(modifier = Modifier.weight(1f), padding = PaddingValues(14.dp)) {
                Text("Speed Controller", color = Foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("${"%.2f".format(speed)}x", color = Foreground, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("Original pitch preserved", color = MutedForeground, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Slider(
                    value = speed, onValueChange = { speed = it },
                    valueRange = 0.25f..1.5f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Primary, activeTrackColor = Primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0.25x", color = MutedForeground, fontSize = 10.sp)
                    Text("1.5x", color = MutedForeground, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

private fun formatSession(ms: Long): String {
    val h = ms / 3600000; val m = (ms % 3600000) / 60000; val s = (ms % 60000) / 1000
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
