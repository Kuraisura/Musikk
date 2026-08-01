package com.kurai.musikk.ui.screens.stems

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kurai.musikk.audioseparator.LocalStemSplitViewModel
import com.kurai.musikk.audioseparator.PresetHolder
import com.kurai.musikk.data.STEM_PRESETS
import com.kurai.musikk.data.SelectedSong
import com.kurai.musikk.data.Song
import com.kurai.musikk.data.StemAudioManager
import com.kurai.musikk.data.StemPreset
import com.kurai.musikk.ui.components.MusikkCard
import com.kurai.musikk.ui.components.ScreenHeader
import com.kurai.musikk.ui.screens.stems.TrackStrip
import com.kurai.musikk.ui.theme.*

@Composable
fun StemsScreen(
    onNavigateToRepertoire: () -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val viewModel = remember { LocalStemSplitViewModel(appContext) }
    val state by viewModel.state.collectAsState()
    val selectedSong by SelectedSong.current.collectAsState()
    val stemAudioManager = remember { StemAudioManager(context) }

    val isPlaying by stemAudioManager.isPlayingFlow.collectAsState()
    val progressPercent by stemAudioManager.progressFlow.collectAsState()
    val durationSecondsState by stemAudioManager.durationSecondsFlow.collectAsState()
    val duration = remember(durationSecondsState) { formatTime(durationSecondsState * 1000) }
    val currentPosition by derivedStateOf { formatTime((progressPercent / 100f * durationSecondsState * 1000).toInt()) }

    var isLoading by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val isInitialized by stemAudioManager.isPreparedFlow.collectAsState()

    LaunchedEffect(selectedSong) {
        Log.d("StemsScreen", "Selected song changed: ${selectedSong?.id}, stemUrls: ${selectedSong?.stemUrls}")
    }

    val stemCount = remember { mutableStateOf(0) }
    val volumes = remember { mutableStateListOf<Int>() }
    val mutes = remember { mutableStateListOf<Boolean>() }
    val solos = remember { mutableStateListOf<Boolean>() }
    val stemNames = remember { mutableStateListOf<String>() }
    val stemRoles = remember { mutableStateListOf<String>() }

    var showEffectsPanel by remember { mutableStateOf(false) }
    var lastPlayClickTime by remember { mutableStateOf(0L) }

    val currentSongId = selectedSong?.id
    val currentStems = state.stems
    DisposableEffect(currentSongId, currentStems) {
        val stemUrlsToUse = selectedSong?.stemUrls?.takeIf { it.isNotEmpty() } ?: currentStems
        val songToUse = selectedSong?.takeIf { it.stemUrls.isNotEmpty() }

        Log.d("StemsScreen", "Re-init: stemUrlsToUse=$stemUrlsToUse, songToUse=${songToUse?.id}, currentSongId=$currentSongId, stemKeys=${selectedSong?.stemKeys}")

        if (stemUrlsToUse != null && stemUrlsToUse.isNotEmpty()) {
            isLoading = true
            try {
                val preset = if (songToUse != null && songToUse.stemPresetId.isNotEmpty()) {
                    STEM_PRESETS.find { it.id == songToUse.stemPresetId } ?: PresetHolder.current
                } else {
                    PresetHolder.current
                }

                volumes.clear()
                mutes.clear()
                solos.clear()
                stemNames.clear()
                stemRoles.clear()

                val stemKeys = if (songToUse != null && songToUse.stemKeys.isNotEmpty()) {
                    songToUse.stemKeys
                } else {
                    preset.stems.map { it.name.lowercase() }
                }

                stemKeys.forEachIndexed { index, key ->
                    val stemKey = com.kurai.musikk.data.StemKey.values().find {
                        it.name.lowercase() == key.lowercase()
                    } ?: com.kurai.musikk.data.StemKey.values().getOrNull(index) ?: com.kurai.musikk.data.StemKey.OTHER

                    volumes.add(100) // Set to 100% by default
                    mutes.add(false)
                    solos.add(false)
                    stemNames.add(stemKey.displayName)
                    stemRoles.add(stemKey.name)
                }

                stemCount.value = stemKeys.size

                val fullMixUrl = stemUrlsToUse["full"] ?: stemUrlsToUse["mix"] ?: stemUrlsToUse["full_mix"] ?: ""
                val normalizedStemUrls = stemUrlsToUse.mapKeys { (key, _) -> key.lowercase() }

                // Ensure URL order exactly matches stemKeys so labels and audio never swap
                val orderedStemUrls = linkedMapOf<String, String>()
                stemKeys.forEach { key ->
                    val url = normalizedStemUrls[key.lowercase()] ?: normalizedStemUrls.values.firstOrNull { it.isNotBlank() } ?: ""
                    orderedStemUrls[key.lowercase()] = url
                }

                Log.d("StemsScreen", "All stem URLs: $normalizedStemUrls")
                Log.d("StemsScreen", "Volumes: $volumes")
                Log.d("StemsScreen", "Mutes: $mutes")
                Log.d("StemsScreen", "Solos: $solos")
                Log.d("StemsScreen", "Initializing audio with fullMixUrl: $fullMixUrl, stemUrls: $normalizedStemUrls")

                stemAudioManager.initialize(
                    fullMixUrl = fullMixUrl,
                    stemUrls = orderedStemUrls,
                    initialVolumes = volumes.toList()
                )

                stemAudioManager.onPrepared = {
                    isLoading = false
                }

                stemAudioManager.onCompletion = {
                }

                stemAudioManager.onError = { msg ->
                    isLoading = false
                    showError = true
                    errorMessage = msg
                }

                isLoading = false
            } catch (e: Exception) {
                isLoading = false
                showError = true
                errorMessage = e.message ?: "Failed to initialize audio"
            }
        } else {
            Log.d("StemsScreen", "No stem URLs available to initialize")
        }

        onDispose {
            stemAudioManager.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding(),
    ) {
        ScreenHeader(
            title = "Stems",
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { showEffectsPanel = !showEffectsPanel },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Foreground
                        )
                    }
                    IconButton(
                        onClick = onNavigateToRepertoire,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Foreground
                        )
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        color = Primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Initializing audio...",
                        color = MutedForeground,
                        fontSize = 14.sp
                    )
                }
            } else if (showError) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = Destructive,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Error",
                        color = Foreground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MutedForeground,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { showError = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = OnPrimary
                        )
                    ) {
                        Text("Retry")
                    }
                }
            } else if (stemCount.value == 0 && state.stems == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "No stems",
                        tint = MutedForeground,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No stems available",
                        color = Foreground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Please process a song first",
                        color = MutedForeground,
                        fontSize = 14.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(80.dp)
                            )
                        } else if (showError) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Destructive.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = Destructive,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        } else {
                            PlayArcButton(
                                isPlaying = isPlaying,
                                progressPercent = progressPercent,
                                onToggle = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastPlayClickTime < 800) return@PlayArcButton // debounce rapid clicks
                                    lastPlayClickTime = now
                                    Log.d("StemsScreen", "Play button clicked, isInitialized: $isInitialized, areAllStemsPrepared: ${stemAudioManager.areAllStemsPrepared()}, isPlaying: $isPlaying")
                                    if (isInitialized && stemAudioManager.areAllStemsPrepared()) {
                                        Log.d("StemsScreen", "Calling ${if (isPlaying) "pause" else "play"}")
                                        if (isPlaying) {
                                            stemAudioManager.pause()
                                        } else {
                                            stemAudioManager.play()
                                        }
                                    } else {
                                        Log.d("StemsScreen", "NOT ready! isInitialized: $isInitialized, areAllStemsPrepared: ${stemAudioManager.areAllStemsPrepared()}, stemCount: ${stemCount.value}")
                                    }
                                },
                                size = 80.dp,
                                arcColor = if (isInitialized && stemAudioManager.areAllStemsPrepared()) Primary else MutedForeground,
                                onColor = if (isInitialized && stemAudioManager.areAllStemsPrepared()) OnPrimary else MutedForeground
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        if (!isInitialized && !isLoading && !showError) {
                            Text(
                                text = if (stemCount.value == 0) "No stems loaded" else "Initializing audio...",
                                color = MutedForeground,
                                fontSize = 12.sp
                            )
                        }

                        // Show loading indicator if stems are still preparing
                        if (!stemAudioManager.areAllStemsPrepared() && stemCount.value > 0 && !isLoading) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Waiting for stems to load...",
                                    color = MutedForeground,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    color = Primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = currentPosition,
                                color = Foreground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = " / ",
                                color = MutedForeground,
                                fontSize = 14.sp
                            )
                            Text(
                                text = duration,
                                color = MutedForeground,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    stemAudioManager.seekToPercent(0f)
                                    stemAudioManager.play()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = "Restart",
                                    tint = MutedForeground
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            IconButton(
                                onClick = {
                                    val newPos = (progressPercent - 5f).coerceAtLeast(0f)
                                    stemAudioManager.seekToPercent(newPos)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 5s",
                                    tint = MutedForeground
                                )
                            }

                            Spacer(Modifier.width(24.dp))

                            IconButton(
                                onClick = {
                                    val newPos = (progressPercent + 5f).coerceAtMost(100f)
                                    stemAudioManager.seekToPercent(newPos)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 5s",
                                    tint = MutedForeground
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            IconButton(
                                onClick = {
                                    stemAudioManager.stop()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = MutedForeground
                                )
                            }
                        }
                    }

                    if (stemCount.value > 0) {
                        StackedTimeline(
                            seeds = List(stemCount.value) { it },
                            channelColors = List(stemCount.value) { index ->
                                channelColor(index)
                            },
                            volumes = volumes,
                            mutes = mutes,
                            audible = List(stemCount.value) { true },
                            progressPercent = progressPercent,
                            onSeek = { percent ->
                                stemAudioManager.seekToPercent(percent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            height = 120.dp
                        )

                        Spacer(Modifier.height(16.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (i in 0 until stemCount.value) {
                            TrackStrip(
                                channelIndex = i,
                                name = stemNames[i],
                                role = stemRoles[i],
                                volume = volumes[i],
                                onVolumeChange = { newVolume ->
                                    volumes[i] = newVolume
                                    stemAudioManager.setStemVolume(i, newVolume)
                                },
                                isSolo = solos[i],
                                onSoloToggle = {
                                    solos[i] = !solos[i]
                                    stemAudioManager.setStemSolo(i, solos[i])
                                },
                                isMute = mutes[i],
                                onMuteToggle = {
                                    mutes[i] = !mutes[i]
                                    stemAudioManager.toggleStemMute(i)
                                },
                                isAudible = true,
                                progress = progressPercent
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceElevated)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Master Volume",
                                    tint = Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Master",
                                    color = Foreground,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = { showEffectsPanel = !showEffectsPanel },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showEffectsPanel) Primary else SurfaceElevated,
                                contentColor = if (showEffectsPanel) OnPrimary else Foreground
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Effects",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Effects")
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceElevated)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val presetName = selectedSong?.let { song ->
                                if (song.stemPresetId.isNotEmpty()) {
                                    STEM_PRESETS.find { it.id == song.stemPresetId }?.displayName
                                } else {
                                    null
                                }
                            } ?: PresetHolder.current.displayName

                            Text(
                                text = presetName,
                                color = MutedForeground,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    if (showEffectsPanel) {
                        MusikkCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            padding = PaddingValues(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Audio Effects",
                                    color = Foreground,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Speed", color = Foreground, fontSize = 14.sp)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val current = stemAudioManager.getSpeed()
                                                stemAudioManager.setPlaybackSpeed((current - 0.1f).coerceAtLeast(0.5f))
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Slower", tint = Foreground)
                                        }
                                        Text(
                                            text = "${"%.1f".format(stemAudioManager.getSpeed())}x",
                                            color = Foreground,
                                            fontSize = 14.sp
                                        )
                                        IconButton(
                                            onClick = {
                                                val current = stemAudioManager.getSpeed()
                                                stemAudioManager.setPlaybackSpeed((current + 0.1f).coerceAtMost(2.0f))
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Faster", tint = Foreground)
                                        }
                                    }
                                }

                                Divider(color = BorderHairline)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Pitch", color = Foreground, fontSize = 14.sp)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val current = stemAudioManager.getPitchShift()
                                                stemAudioManager.setPitchShift((current - 1).coerceAtLeast(-12))
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Lower", tint = Foreground)
                                        }
                                        Text(
                                            text = "${stemAudioManager.getPitchShift()}",
                                            color = Foreground,
                                            fontSize = 14.sp
                                        )
                                        IconButton(
                                            onClick = {
                                                val current = stemAudioManager.getPitchShift()
                                                stemAudioManager.setPitchShift((current + 1).coerceAtMost(12))
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Higher", tint = Foreground)
                                        }
                                    }
                                }

                                Divider(color = BorderHairline)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Noise Reduction", color = Foreground, fontSize = 14.sp)
                                    Switch(
                                        checked = stemAudioManager.isDeepFilterEnabled(),
                                        onCheckedChange = { stemAudioManager.setDeepFilterEnabled(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Primary,
                                            checkedTrackColor = Primary.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                Text(
                                    text = "Cuts wind, AC hum, and ambient room rumble from below 2 kHz. Useful for outdoor or noisy recordings.",
                                    color = MutedForeground,
                                    fontSize = 11.sp,
                                )

                                Button(
                                    onClick = { showEffectsPanel = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Primary,
                                        contentColor = OnPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Int): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
