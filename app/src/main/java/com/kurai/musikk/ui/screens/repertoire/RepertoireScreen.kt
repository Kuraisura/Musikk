package com.kurai.musikk.ui.screens.repertoire

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kurai.musikk.data.*
import com.kurai.musikk.audioseparator.LocalStemSplitViewModel
import com.kurai.musikk.audioseparator.PresetHolder
import com.kurai.musikk.ui.components.MusikkCard
import com.kurai.musikk.ui.components.Pill
import com.kurai.musikk.ui.components.PillTone
import com.kurai.musikk.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RepertoireScreen(
    onNavigateToStems: () -> Unit,
    onNavigateToPractice: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("All Songs") }
 var pendingProcessSong by remember { mutableStateOf<Song?>(null) }
 val songs = remember { mutableStateListOf<Song>() }
 val firestore = remember { FirebaseFirestore.getInstance() }
 val uid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
 val coroutineScope = rememberCoroutineScope()

 // Track the previous lastEditedAt per song so we can detect re-uploads
 // and cancel any in-flight AI processing for the same song id.
 val lastEditedBySong = remember { mutableStateMapOf<String, Long>() }
 val processingFileUrlBySong = remember { mutableStateMapOf<String, String>() }
 // Track which songs are currently being processed (to show loading spinner)
 val processingSongIds = remember { mutableStateSetOf<String>() }

    // Load songs from Firestore on startup
    LaunchedEffect(uid) {
        if (uid.isBlank()) return@LaunchedEffect
        firestore.collection("users").document(uid).collection("repertoire")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                songs.clear()
                snapshot.documents.forEach { doc ->
                    val rawStatus = doc.getString("status") ?: "QUEUED"
                    val status = runCatching { SongStatus.valueOf(rawStatus) }
                        .getOrDefault(SongStatus.QUEUED)
                    val fileUrl = doc.getString("fileUrl") ?: ""
                    val lastEdited = doc.getLong("lastEditedAt") ?: 0L
  val song = Song(
                id = doc.id,
                title = doc.getString("title") ?: "Unknown",
                artist = doc.getString("artist") ?: "Unknown Artist",
                coverUrl = doc.getString("coverUrl") ?: "",
                musicalKey = doc.getString("musicalKey") ?: "Unknown",
                bpm = (doc.getLong("bpm") ?: 120).toInt(),
                duration = doc.getString("duration") ?: "0:00",
                stems = (doc.getLong("stems") ?: 0).toInt(),
                status = status,
                progress = (doc.getLong("progress") ?: 0L).toInt(),
                fileUrl = fileUrl,
                filePath = doc.getString("filePath") ?: "",
                tags = emptySet(),
                stemKeys = (doc.get("stemKeys") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                stemUrls = (doc.get("stemUrls") as? Map<*, *>)
                    ?.mapNotNull { (k, v) -> val ks = k?.toString(); val vs = v?.toString(); if (ks != null && vs != null) ks to vs else null }
                    ?.toMap()
                ?: emptyMap(),
                stemPaths = (doc.get("stemPaths") as? Map<*, *>)
                    ?.mapNotNull { (k, v) -> val ks = k?.toString(); val vs = v?.toString(); if (ks != null && vs != null) ks to vs else null }
                    ?.toMap()
                ?: emptyMap(),
                stemPresetId = doc.getString("stemPresetId") ?: "",
                lastEditedAt = lastEdited,
                finishedAt = doc.getLong("finishedAt") ?: 0L,
                currentStep = doc.getString("currentStep") ?: "",
                startedAt = doc.getLong("startedAt") ?: 0L,
            )
                    songs.add(song)

                    // ── Last-edit propagation ──
                    // 1. If this is the currently-loaded song in Stems, push the
                    //    updated value into SelectedSong so the Stems screen
                    //    immediately sees the latest fileUrl / stem preset etc.
                    val sel = SelectedSong.current.value
                    if (sel != null && sel.id == song.id) {
                        SelectedSong.select(song)
                    }

                    // 2. If the user re-uploaded this song (fileUrl or
                    //    lastEditedAt increased) while processing was in flight,
                    //    cancel the in-flight job — the old result would be
                    //    against stale bytes.
                    val prevEdit = lastEditedBySong[song.id] ?: 0L
                    val prevFileUrl = processingFileUrlBySong[song.id]
                    val reUploaded = prevEdit != 0L &&
                        (lastEdited > prevEdit || (prevFileUrl != null && prevFileUrl != fileUrl))
                    if (reUploaded && song.status == SongStatus.PROCESSING) {
                        ProcessingService.cancel(song.id)
                    }
                    if (song.status == SongStatus.PROCESSING) {
                        processingFileUrlBySong[song.id] = fileUrl
                    } else {
                        processingFileUrlBySong.remove(song.id)
                    }
                    if (lastEdited > prevEdit) lastEditedBySong[song.id] = lastEdited
                }
            }
    }

    fun showToast(message: String, long: Boolean = false) {
        mainHandler.post {
            Toast.makeText(appContext, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    // File picker
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val fileName = getFileName(context, uri)
            val fileSize = getFileSize(context, uri)
            val ext = fileName.substringAfterLast('.', "").uppercase()

            if (!isValidFormat(fileName)) {
                showToast("Unsupported format: $ext")
                return@rememberLauncherForActivityResult
            }
            if (fileSize > 50 * 1024 * 1024) {
                showToast("File exceeds 50 MB limit")
                return@rememberLauncherForActivityResult
            }

            val fileNameTitle = fileName.substringBeforeLast('.').replace("_", " ").replace("-", " ").trim()
            val meta = extractMetadata(context, uri, fallbackTitle = fileNameTitle)

            val objectKey = "users/$uid/${System.currentTimeMillis()}_$fileName"
            val mimeType = mimeTypeForExtension(fileName)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val downloadUrl = R2StorageService.uploadFile(appContext, uri, objectKey, mimeType)

                    val songData = hashMapOf<String, Any>(
                        "title" to meta.title,
                        "artist" to meta.artist,
                        "coverUrl" to "",
                        "musicalKey" to "Unknown",
                        "bpm" to 120,
                        "duration" to meta.duration,
                        "stems" to 0,
                        "status" to "QUEUED",
                        "createdAt" to System.currentTimeMillis(),
                        "fileUrl" to downloadUrl,
                        "filePath" to objectKey,
                        "lastEditedAt" to System.currentTimeMillis(),
                        "stemKeys" to emptyList<String>(),
                        "stemUrls" to emptyMap<String, String>(),
                        "stemPaths" to emptyMap<String, String>(),
                    )

                    if (uid.isNotBlank()) {
                        firestore.collection("users").document(uid).collection("repertoire")
                            .add(songData)
                            .addOnSuccessListener {
                                showToast("${meta.title} added")
                            }
                            .addOnFailureListener { firestoreErr ->
                                Log.e("RepertoireUpload", "Firestore write failed", firestoreErr)
                                showToast("Failed to save: ${firestoreErr.message}", long = true)
                            }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e("RepertoireUpload", "Upload failed", e)
                    showToast("Upload failed: ${e.message ?: e::class.java.simpleName}", long = true)
                }
            }
        } catch (e: Throwable) {
            Log.e("RepertoireUpload", "Could not read file", e)
            showToast("Could not read file: ${e.message ?: e::class.java.simpleName}", long = true)
        }
    }

    // Filter + search logic
    val filteredSongs = songs.filter { song ->
        val matchesSearch = searchQuery.isBlank() ||
            song.title.contains(searchQuery, ignoreCase = true) ||
            song.artist.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (activeFilter) {
            "Practice List" -> SongTag.PRACTICE in song.tags
            "Favorites" -> SongTag.FAVORITES in song.tags
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Column(
        Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 20.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Repertoire", color = Foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Pill(text = "${songs.size} songs")
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).shadow(12.dp, RoundedCornerShape(Radius2xl), ambientColor = Primary, spotColor = Primary)
                .clip(RoundedCornerShape(Radius2xl)).background(Primary)
                .clickable { filePicker.launch(arrayOf("audio/*", "video/mp4", "video/quicktime")) },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, null, tint = OnPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Upload Song", color = OnPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Format pills
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            supportedFormats.forEach { fmt ->
                Pill(text = fmt)
            }
        }

        // Search
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(Radius2xl))
                .background(Surface.copy(alpha = 0.7f)).border(1.dp, InputRing, RoundedCornerShape(Radius2xl))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = MutedForeground, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Foreground, fontSize = 14.sp),
                    singleLine = true, cursorBrush = SolidColor(Primary),
                    decorationBox = { if (searchQuery.isEmpty()) Text("Search songs or artists", color = MutedForeground.copy(alpha = 0.7f), fontSize = 14.sp); it() },
                )
            }
        }

        // Filter pills
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All Songs", "Favorites", "Practice List").forEach { filter ->
                val active = filter == activeFilter
                Box(
                    Modifier.clip(RoundedCornerShape(RadiusFull))
                        .background(if (active) Primary else Color.White.copy(alpha = 0.04f))
                        .border(1.dp, if (active) Primary else Color.White.copy(alpha = 0.10f), RoundedCornerShape(RadiusFull))
                        .clickable { activeFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(filter, color = if (active) OnPrimary else MutedForeground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (songs.isEmpty()) {
            // Empty state
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.LibraryMusic, null, tint = MutedForeground.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("No songs yet", color = MutedForeground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text("Upload your first track to get started", color = MutedForeground.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        } else if (filteredSongs.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No songs match this filter", color = MutedForeground, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
 items(filteredSongs, key = { it.id }) { song ->
                val isProcessing = processingSongIds.contains(song.id)
                SongRow(
                    song = song,
                    isProcessing = isProcessing,
                    onLoadIntoStems = {
                        SelectedSong.select(song)
                        onNavigateToStems()
                    },
                    onProcess = {
                        pendingProcessSong = song
                    },
                    onUploadForLocalSplit = { selectedSong ->
                        pendingProcessSong = selectedSong
                    },
                    onToggleFavorite = {
                        val idx = songs.indexOf(song)
                        if (idx >= 0) {
                            val tags = song.tags.toMutableSet()
                            if (SongTag.FAVORITES in tags) tags.remove(SongTag.FAVORITES)
                            else tags.add(SongTag.FAVORITES)
                            songs[idx] = song.copy(tags = tags)
                        }
                    },
                    onDelete = {
                        if (uid.isBlank()) return@SongRow
                        ProcessingService.cancel(song.id)
                        val sel = SelectedSong.current.value
                        if (sel != null && sel.id == song.id) {
                            SelectedSong.clear()
                        }
                        coroutineScope.launch(Dispatchers.IO) {
                            if (song.filePath.isNotBlank()) R2StorageService.deleteObject(song.filePath)
                            song.stemPaths.values.forEach { path ->
                                R2StorageService.deleteObject(path)
                            }
                        }
                        firestore.collection("users").document(uid).collection("repertoire")
                            .document(song.id).delete()
                            .addOnFailureListener { e -> showToast("Delete failed: ${e.message}", long = true) }
                    },
                )
            }
            }
        }
    }

        // ── Stem-preset chooser ──
        val localVm = remember { LocalStemSplitViewModel(appContext) }
        val pending = pendingProcessSong
        
        if (pending != null) {
            com.kurai.musikk.ui.components.StemPresetChooserDialog(
                onDismiss = { 
                    pendingProcessSong = null
                    processingSongIds.remove(pending.id)
                },
                onChosen = { preset ->
                    PresetHolder.current = preset
                    showToast("Splitting into ${preset.stems.size} stems · ${preset.displayName}")
                    pendingProcessSong = null
                    processingSongIds.add(pending.id)
                    
                    // Update Firestore to mark as PROCESSING immediately
                    if (uid.isNotBlank()) {
                        firestore.collection("users").document(uid).collection("repertoire")
                            .document(pending.id)
                            .update(
                                mapOf(
                                    "status" to SongStatus.PROCESSING.name,
                                    "progress" to 0,
                                    "stemPresetId" to preset.id,
                                    "startedAt" to System.currentTimeMillis(),
                                )
                            )
                            .addOnFailureListener { e ->
                                showToast("Failed to start processing: ${e.message}", long = true)
                                processingSongIds.remove(pending.id)
                            }
                    }
                    
                    coroutineScope.launch {
                    // Update Firestore progress in real-time as localVm state changes
                    launch { 
                        localVm.state.collect { prog -> 
                            if (prog.processing) { 
                                val pct = (prog.progress * 100).toInt().coerceIn(0, 99)
                                runCatching { 
                                    firestore.collection("users").document(uid).collection("repertoire")
                                        .document(pending.id)
                                        .update( 
                                            mapOf( 
                                                "progress" to pct, 
                                                "currentStep" to prog.currentStep, 
                                                "status" to SongStatus.PROCESSING.name,
                                                "startedAt" to System.currentTimeMillis(),
                                            ) 
                                        ) 
                                }.onFailure { e ->
                                    Log.e("ProgressUpdate", "Failed to update progress", e)
                                }
                            } 
                        } 
                    }

                        localVm.uploadAudioFromUrl(pending.fileUrl)
                        val progress = localVm.state.first { it.isDone || it.error != null }
                        processingSongIds.remove(pending.id)
                        
                        if (progress.isDone && progress.stems != null) {
                            val stemKeys = preset.stems.map { it.name.lowercase() }
                            val updated = pending.copy(
                                stemKeys = stemKeys,
                                stemUrls = progress.stems,
                                stemPresetId = preset.id,
                                status = com.kurai.musikk.data.SongStatus.SEPARATED,
                                progress = 100,
                                stems = stemKeys.size,
                                finishedAt = System.currentTimeMillis(),
                            )
                            // Write final result to Firestore
                            runCatching {
                                firestore.collection("users").document(uid).collection("repertoire")
                                    .document(pending.id)
                                    .update(
                                        mapOf(
                                            "status" to SongStatus.SEPARATED.name,
                                            "progress" to 100,
                                            "stems" to stemKeys.size.toLong(),
                                            "stemKeys" to stemKeys,
                                            "stemUrls" to progress.stems,
                                            "stemPresetId" to preset.id,
                                            "finishedAt" to System.currentTimeMillis(),
                                        )
                                    )
 }
 Log.d("RepertoireScreen", "Setting SelectedSong with stemUrls: ${progress.stems}")
 SelectedSong.select(updated)
 onNavigateToStems()
                        } else if (progress.error != null) {
                            // Reset status to QUEUED on failure
                            if (uid.isNotBlank()) {
                                firestore.collection("users").document(uid).collection("repertoire")
                                    .document(pending.id)
                                    .update(
                                        mapOf(
                                            "status" to SongStatus.QUEUED.name,
                                            "progress" to 0,
                                        )
                                    )
                            }
                            showToast("Processing failed: ${progress.error}", long = true)
                        }
                    }
                },
            )
        }
}

@Composable
private fun SongRow(
    song: Song,
    isProcessing: Boolean = false,
    onLoadIntoStems: () -> Unit,
    onProcess: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onUploadForLocalSplit: (Song) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = (song.progress.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "songProgress",
    )

    MusikkCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(19.6.dp))
                        .background(SurfaceElevated).border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(19.6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = MutedForeground, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = Foreground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artist,
                        color = MutedForeground,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Last-edit indicator — recomposes every 30 s so the
                    // "edited 2m ago" caption stays fresh while the user is
                    // looking at Repertoire. Reflects the latest file changes
                    // the user made to the song (re-upload, reprocess, etc.).
                    if (song.lastEditedAt > 0L) {
                        var now by remember(song.lastEditedAt) { mutableLongStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(song.lastEditedAt) {
                            while (true) {
                                now = System.currentTimeMillis()
                                kotlinx.coroutines.delay(30_000L)
                            }
                        }
                        Text(
                            text = "Edited " + formatRelativeTime(now - song.lastEditedAt),
                            color = MutedForeground.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (song.musicalKey != "Unknown") Pill(text = song.musicalKey)
                        Pill(text = "${song.bpm} BPM")
                        Pill(text = song.duration)
                    }
                }
                Icon(
                    Icons.Default.MoreVert, "Menu",
                    tint = MutedForeground,
                    modifier = Modifier.size(28.dp).clickable { menuOpen = !menuOpen },
                )
            }

            // Status-specific action area
            Spacer(Modifier.height(10.dp))
            when (song.status) {
        SongStatus.QUEUED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(text = "Queued", tone = PillTone.MUTED)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(RadiusXl))
                        .background(Primary)
                        .clickable(enabled = !isProcessing) { 
                            if (!isProcessing) onProcess() 
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isProcessing) {
                            // Show a small loading spinner
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = OnPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = OnPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isProcessing) "Processing..." else "Process Song",
                            color = OnPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
            SongStatus.PROCESSING -> {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Pill(text = "Processing", tone = PillTone.DESTRUCTIVE)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${song.progress.coerceIn(0, 100)}%",
                            color = Foreground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    
                    // Show current step from Firestore
                    if (song.currentStep.isNotBlank()) {
                        Text(
                            text = song.currentStep,
                            color = MutedForeground,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        // Fallback messages based on progress
                        val fallbackStep = remember(song.progress) { 
                            when (song.progress) {
                                in 0..24 -> "Preparing..."
                                in 25..39 -> "Downloading..."
                                in 40..59 -> "Processing AI..."
                                in 60..89 -> "Uploading stems..."
                                else -> "Finalizing..."
                            }
                        }
                        Text(
                            text = fallbackStep,
                            color = MutedForeground,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(RadiusFull))
                            .background(Color.White.copy(alpha = 0.10f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(RadiusFull))
                                .background(Primary),
                        )
                    }
                }
            }
                SongStatus.SEPARATED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Pill(text = "${song.stems} stems ready", tone = PillTone.PRIMARY)
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(RadiusXl))
                                .background(Primary)
                                .clickable { onLoadIntoStems() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = OnPrimary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Load into Stems",
                                    color = OnPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            if (menuOpen) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(19.6.dp)).background(SurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(19.6.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MenuItem(Icons.Default.GraphicEq, "Load into Stems") { menuOpen = false; onLoadIntoStems() }
                    MenuItem(Icons.Default.FavoriteBorder, if (SongTag.FAVORITES in song.tags) "Remove from Favorites" else "Add to Favorites") { menuOpen = false; onToggleFavorite() }
                    MenuItem(Icons.Default.Delete, "Delete", isDestructive = true) { menuOpen = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isDestructive: Boolean = false, onClick: () -> Unit) {
    val color = if (isDestructive) Destructive else MutedForeground
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.5.dp))
            .clickable(enabled = true, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 12.sp)
    }
}

// ── Utilities ──

private fun isValidFormat(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in listOf("mp3", "wav", "flac", "m4a", "mp4", "mov", "wma")
}

private fun mimeTypeForExtension(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "wma" -> "audio/x-ms-wma"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        else -> "application/octet-stream"
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var name = "unknown"
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                val raw = cursor.getString(idx)
                if (!raw.isNullOrBlank()) name = raw
            }
        }
    }
    return name
}

private fun getFileSize(context: android.content.Context, uri: Uri): Long {
    var size = 0L
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst()) {
                size = cursor.getLong(idx)
            }
        }
    }
    return size
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60; val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private data class AudioMetadata(
    val title: String,
    val artist: String,
    val duration: String,
)

private fun extractMetadata(
    context: android.content.Context,
    uri: Uri,
    fallbackTitle: String,
): AudioMetadata {
    var title = fallbackTitle
    var artist = "Unknown Artist"
    var durationSec = 0

    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }?.let { title = it.trim() }
        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
            ?.takeIf { it.isNotBlank() }?.let { artist = it.trim() }
        val rawDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        durationSec = (rawDuration / 1000).toInt()
    } catch (_: Exception) {
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
    return AudioMetadata(title = title, artist = artist, duration = formatDuration(durationSec))
}

/** "just now" / "5m ago" / "2h ago" / "yesterday" / "Mar 4" */
private fun formatRelativeTime(deltaMs: Long): String {
    val d = deltaMs.coerceAtLeast(0L)
    val sec = d / 1000
    val min = sec / 60
    val hr = min / 60
    val day = hr / 24
    return when {
        sec < 30 -> "just now"
        min < 1 -> "${sec}s ago"
        min < 60 -> "${min}m ago"
        hr < 24 -> "${hr}h ago"
        day == 1L -> "yesterday"
        day < 7 -> "${day}d ago"
        else -> {
            val fmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
            fmt.format(java.util.Date(System.currentTimeMillis() - d))
        }
    }
}
