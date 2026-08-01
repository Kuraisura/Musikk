package com.kurai.musikk.data

enum class SongStatus { SEPARATED, PROCESSING, QUEUED }
enum class SongTag { PRACTICE, FAVORITES, RECENT }

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val musicalKey: String,
    val bpm: Int,
    val duration: String,
    val stems: Int,
    val status: SongStatus,
    val progress: Int = 0,
    val fileUrl: String = "",
    val filePath: String = "",
    val tags: Set<SongTag> = emptySet(),
// ── AI separation ──
/** Stem keys produced by the last AI separation (e.g. ["vocals","drums","bass","other"]). */
    val stemKeys: List<String> = emptyList(),
/** Presigned GET URLs for each separated stem, keyed by StemKey.name. */
    val stemUrls: Map<String, String> = emptyMap(),
/** R2 object-key prefix for each separated stem (used to delete on song delete). */
    val stemPaths: Map<String, String> = emptyMap(),
/** Which StemPreset was last used. */
    val stemPresetId: String = "",
/** Epoch millis when the file was last (re)uploaded or stems last refreshed. */
    val lastEditedAt: Long = 0L,
/** Epoch millis when processing finished (if SEPARATED). */
    val finishedAt: Long = 0L,
/** Current processing step (e.g., "Downloading source file...", "Uploading stems..."). */
    val currentStep: String = "",
/** Epoch millis when processing started (if PROCESSING). */
    val startedAt: Long = 0L,
)
