package com.kurai.musikk.data

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kurai.musikk.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

    /**
     * Plays a single song whose bytes live at [sourceUrl] (R2, the local backend, etc.).
     *
     * Backed by Media3 ExoPlayer instead of raw android.media.MediaPlayer. ExoPlayer
     * exposes real-time, **independent** speed and pitch control via [PlaybackParameters]
     * (it uses the Sonic algorithm under the hood — high quality, artefact-free).
     * This replaces the previous no-op setPlaybackSpeed/setPitchShift that only logged,
     * and gives Moises/BandLab-style independent time/pitch stretching.
     *
     * The legacy download-to-cache behaviour is preserved (and is still required for
     * reliable seeking on long .m4a files served over plain HTTP from the PC backend).
     *
     * Audio focus is opt-in: pass [handleAudioFocus] = true only for the single,
     * top-level player (the full mix). Stem players must NOT request audio focus,
     * because Android grants focus to one player at a time and would silently
     * pause every other stem ExoPlayer, leaving only the first starter audible.
     */
    class AudioPlayer(
        private val context: Context,
        private val handleAudioFocus: Boolean = false,
    ) {

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var player: ExoPlayer? = null
    private var prepared = false
    private var playing = false
    // Duration captured at STATE_READY (on the main thread) so background
    // pollers don't need to touch exo.duration (ExoPlayer is thread-affine).
    @Volatile private var durationMs: Long = 0L
    @Volatile private var sessionId: Int = 0

    // Effects chains (kept for volume / EQ-based noise filter / deep filter).
    // These are driven off the ExoPlayer's audio session id, same way they used
    // the MediaPlayer's.
    private val audioEffects = AudioEffectsManager(context)
    private val simpleProcessor = SimpleAudioProcessor(context)

    var sourceUrl: String = ""
        private set

    var onPrepared: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onLoading: ((Boolean) -> Unit)? = null

    // Speed & pitch are independent. Speed is a multiplier (0.5..2.0); pitch is
    // expressed in semitones (-12..+12). ExoPlayer wants a pitch *ratio*, so we
    // convert semitones -> 2^(n/12).
    private var speed: Float = 1.0f
    private var pitchSemitones: Int = 0

    fun load(url: String) {
        Log.d(TAG, "load: Starting download for URL: $url")
        if (url.isBlank()) {
            postError("No audio URL")
            return
        }
        release()
        sourceUrl = url
        onLoading?.invoke(true)
        ioExecutor.execute {
            val local = try {
                downloadToCache(url)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $url", e)
                mainHandler.post {
                    onLoading?.invoke(false)
                    onError?.invoke("Download failed: ${e.message ?: e::class.java.simpleName}")
                }
                return@execute
            }
            mainHandler.post {
                onLoading?.invoke(false)
                prepareLocal(local)
            }
        }
    }

    private fun downloadToCache(url: String): File {
        val cacheDir = File(context.cacheDir, "audio").apply { mkdirs() }
        val safeName = url.hashCode().toString() + ".audio"
        val target = File(cacheDir, safeName)

        // Try the URL as-is; on failure, attempt to re-sign a legacy R2 path-style URL.
        var resp = fetch(url)
        if (resp == null) {
            val signed = reSign(url)
            if (signed != null) resp = fetch(signed)
        }
        if (resp == null) throw IOException("Download failed for $url")

        resp.use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty body")
            target.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        return target
    }

    private fun fetch(url: String): okhttp3.Response? {
        return try {
            val r = http.newCall(Request.Builder().url(url).build()).execute()
            if (r.isSuccessful) r else { r.close(); null }
        } catch (_: Exception) {
            null
        }
    }

    /** Re-signs a legacy unsigned R2 path-style URL into a presigned GET URL. */
    private fun reSign(url: String): String? {
        val prefix = BuildConfig.R2_ENDPOINT + "/" + BuildConfig.R2_BUCKET + "/"
        if (!url.startsWith(prefix)) return null
        val objectKey = url.removePrefix(prefix)
        if (objectKey.isBlank() || objectKey.contains("?")) return null
        return R2StorageService.presignedGetUrl(objectKey)
    }

    private fun prepareLocal(file: File) {
        Log.d(TAG, "Preparing local file: ${file.absolutePath}, exists: ${file.exists()}, length: ${file.length()}")
        try {
            val exo = ExoPlayer.Builder(context)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    /* handleAudioFocus = */ handleAudioFocus,
                )
                .build()

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            prepared = true
                            durationMs = exo.duration.takeIf { it > 0 } ?: 0L
                            sessionId = exo.audioSessionId
                            Log.d(TAG, "ExoPlayer READY (duration=${durationMs}ms, sessionId=$sessionId)")
                            mainHandler.post {
                                audioEffects.initializeWithSessionId(exo.audioSessionId)
                                simpleProcessor.initialize(exo)
                                applyPlaybackParameters()
                                onPrepared?.invoke()
                            }
                        }
                        Player.STATE_ENDED -> {
                            playing = false
                            Log.d(TAG, "ExoPlayer ENDED")
                            mainHandler.post { onCompletion?.invoke() }
                        }
                        Player.STATE_IDLE, Player.STATE_BUFFERING -> Unit
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.errorCodeName} ${error.message}")
                    playing = false
                    mainHandler.post { onError?.invoke("Playback error: ${error.errorCodeName}") }
                }
            })

            exo.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            exo.prepare()
            player = exo
            Log.d(TAG, "ExoPlayer prepare() called")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare ${file.absolutePath}", e)
            onError?.invoke(e.message ?: e::class.java.simpleName)
        }
    }

    private fun runOnMainIfNotThere(block: () -> Unit) {
        if (android.os.Looper.myLooper() === android.os.Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun play() {
        val exo = player ?: run { Log.w(TAG, "play: player is null"); return }
        if (!prepared) { Log.w(TAG, "play: not prepared yet"); return }
        if (playing) { Log.d(TAG, "play: already playing"); return }
        runOnMainIfNotThere {
            try {
                exo.play()
                playing = true
                Log.d(TAG, "play: started successfully (speed=$speed, pitch=$pitchSemitones st)")
            } catch (e: Exception) {
                Log.e(TAG, "play() failed", e)
                mainHandler.post { onError?.invoke("Play failed: ${e.message ?: e::class.java.simpleName}") }
            }
        }
    }

    /** ExoPlayer does not expose its internal MediaPlayer; downstream
     * effects that needed one now use the audio-session-id path instead. */
    fun getMediaPlayer(): android.media.MediaPlayer? = null

    /** Audio session id of the underlying ExoPlayer, captured at STATE_READY.
     * Use this to attach android.media.audiofx.* effects (Equalizer, etc.).
     * Safe to call from any thread. */
    fun audioSessionId(): Int = sessionId

    fun isPrepared(): Boolean = prepared

    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        simpleProcessor.setVolume(clampedVolume)
        audioEffects.setVolume(clampedVolume)
        // Reflect on ExoPlayer on the main thread (thread-affine).
        val exo = player ?: return
        runOnMainIfNotThere { exo.volume = clampedVolume }
    }

    fun setMute(muted: Boolean) {
        simpleProcessor.setMute(muted)
        audioEffects.setMute(muted)
        // Reflect immediately in ExoPlayer so we don't have to wait for the
        // effects chain to wake up.
        val vol = if (muted) 0f else simpleProcessor.getVolume()
        val exo = player ?: return
        runOnMainIfNotThere { exo.volume = vol }
    }

    fun setSolo(solo: Boolean) {
        audioEffects.setSolo(solo)
    }

    /**
     * Set playback speed independently of pitch. Range 0.5..2.0.
     * ExoPlayer + Sonic preserves pitch while scaling time.
     */
    fun setPlaybackSpeed(newSpeed: Float) {
        speed = newSpeed.coerceIn(0.5f, 2.0f)
        Log.d(TAG, "Playback speed set to: $speed")
        applyPlaybackParameters()
    }

    /**
     * Set pitch shift independently of speed, in semitones. Range -12..+12.
     * ExoPlayer + Sonic re-synthesises the audio to shift pitch without
     * changing duration.
     */
    fun setPitchShift(semitones: Int) {
        pitchSemitones = semitones.coerceIn(-12, 12)
        Log.d(TAG, "Pitch shift set to: $pitchSemitones semitones")
        applyPlaybackParameters()
    }

    private fun applyPlaybackParameters() {
        val exo = player ?: return
        val pitchRatio = Math.pow(2.0, pitchSemitones.toDouble() / 12.0).toFloat()
        runOnMainIfNotThere {
            try {
                exo.playbackParameters = PlaybackParameters(speed, pitchRatio)
            } catch (e: Exception) {
                Log.e(TAG, "applyPlaybackParameters failed", e)
            }
        }
    }

    fun setDeepFilterEnabled(enabled: Boolean) {
        simpleProcessor.setDeepFilterEnabled(enabled)
        audioEffects.setDeepFilterEnabled(enabled)
        Log.d(TAG, "Deep filter set to: $enabled")
    }

    fun isMuted(): Boolean = audioEffects.isMuted()
    fun isSolo(): Boolean = audioEffects.isSolo()
    fun isDeepFilterEnabled(): Boolean = audioEffects.isDeepFilterEnabled()
    fun getCurrentVolume(): Float = audioEffects.getVolume()
    fun getCurrentSpeed(): Float = speed
    fun getCurrentPitch(): Float =
        Math.pow(2.0, pitchSemitones.toDouble() / 12.0).toFloat()

    fun pause() {
        val exo = player ?: return
        if (!playing) return
        runOnMainIfNotThere {
            try {
                exo.pause()
                playing = false
            } catch (_: Exception) { }
        }
    }

    fun stop() {
        val exo = player ?: return
        runOnMainIfNotThere {
            try {
                exo.stop()
                // Re-prepare so a subsequent play() works.
                exo.prepare()
                playing = false
            } catch (_: Exception) { }
        }
    }

    fun toggle() {
        if (playing) pause() else play()
    }

    fun isPlaying(): Boolean = playing

    fun seekToPercent(percent: Float) {
        val exo = player ?: return
        if (!prepared) return
        val dur = durationMs
        if (dur > 0) {
            // ExoPlayer is thread-affine; hop to main to actually seek.
            mainHandler.post {
                try { exo.seekTo(((percent.coerceIn(0f, 100f) / 100f) * dur).toLong()) } catch (_: Exception) { }
            }
        }
    }

    /**
     * MUST be called on the main thread (ExoPlayer is thread-affine). Pollers
     * that use this should run on Dispatchers.Main.
     */
    fun getCurrentPosition(): Int {
        val exo = player ?: return 0
        if (!prepared) return 0
        return try { exo.currentPosition.toInt() } catch (_: Exception) { 0 }
    }

    /**
     * Safe to call from any thread — returns the duration captured at
     * STATE_READY, so no ExoPlayer access happens here.
     */
    fun getDuration(): Int = durationMs.toInt()

    /**
     * MUST be called on the main thread (ExoPlayer is thread-affine). Reads
     * exo.currentPosition; pollers should run on Dispatchers.Main.
     */
    fun progressPercent(): Float {
        val exo = player ?: return 0f
        if (!prepared) return 0f
        val dur = durationMs
        if (dur <= 0) return 0f
        return try {
            ((exo.currentPosition.toFloat() / dur) * 100f).coerceIn(0f, 100f)
        } catch (_: Exception) { 0f }
    }

    /**
     * Safe to call from any thread — uses the cached duration.
     */
    fun durationSeconds(): Int = (durationMs / 1000).toInt()

    fun release() {
        try {
            player?.release()
            audioEffects.release()
            simpleProcessor.release()
        } catch (_: Exception) { }
        player = null
        prepared = false
        playing = false
        durationMs = 0L
        sessionId = 0
        sourceUrl = ""
    }

    private fun postError(msg: String) {
        mainHandler.post { onError?.invoke(msg) }
    }

    companion object {
        private const val TAG = "AudioPlayer"
    }
}
