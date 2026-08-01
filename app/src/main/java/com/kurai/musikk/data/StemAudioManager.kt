package com.kurai.musikk.data

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class StemAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "StemAudioManager"
    }

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var fullMixPlayer: AudioPlayer? = null
    private var stemPlayers: MutableList<AudioPlayer> = mutableListOf()
    private var fullMixUrl: String = ""

    private val simpleProcessor = SimpleAudioProcessor(context)
    private val pureKotlinProcessorList = mutableListOf<PureKotlinAudioProcessor>()

    private val _isPlaying = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private var playing: Boolean
        get() = _isPlaying.value
        set(value) { _isPlaying.value = value }

    private var isPrepared = false
    private val _isPreparedState = MutableStateFlow(false)
    val isPreparedFlow: StateFlow<Boolean> = _isPreparedState.asStateFlow()

    private var durationSeconds = 0
    private val _durationSecondsState = MutableStateFlow(0)
    val durationSecondsFlow: StateFlow<Int> = _durationSecondsState.asStateFlow()

    private var currentPosition = 0f
    private val _progressState = MutableStateFlow(0f)
    val progressFlow: StateFlow<Float> = _progressState.asStateFlow()

    private val preparedStemCount = java.util.concurrent.atomic.AtomicInteger(0)

    private val volumes: MutableList<Float> = mutableListOf()
    private val mutes: MutableList<Boolean> = mutableListOf()
    private var soloIndex: Int? = null

    private var deepFilterEnabled = false
    private var speed = 1.0f
    private var pitchShift = 0

    var onPrepared: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onProgress: ((Float) -> Unit)? = null

    fun initialize(
        fullMixUrl: String,
        stemUrls: Map<String, String>,
        initialVolumes: List<Int> = listOf(80)
    ) {
        speed = 1.0f  // Force normal speed
        pitchShift = 0
        Log.d(TAG, "Initializing StemAudioManager with fullMixUrl: $fullMixUrl, stemUrls: $stemUrls")
        release()

        this.fullMixUrl = fullMixUrl
        isPrepared = false

        fullMixPlayer = AudioPlayer(context, handleAudioFocus = true).apply {
            onPrepared = {
                durationSeconds = this.durationSeconds()
                _durationSecondsState.value = durationSeconds
                Log.d(TAG, "fullMixPlayer prepared (duration=${durationSeconds}s)")
                if (stemPlayers.isEmpty()) {
                    isPrepared = true
                    _isPreparedState.value = true
                    mainHandler.post { onPrepared?.invoke() }
                }
            }
            onCompletion = {
                playing = false
                currentPosition = 0f
                _progressState.value = 0f
                onCompletion?.invoke()
            }
            onError = { msg ->
                Log.e(TAG, "fullMixPlayer error: $msg")
                mainHandler.post { onError?.invoke(msg) }
            }
        }

        stemPlayers.clear()
        volumes.clear()
        mutes.clear()
        preparedStemCount.set(0)

        stemUrls.values.forEach { url ->
            val player = AudioPlayer(context, handleAudioFocus = false).apply {
                onPrepared = {
                    Log.d(TAG, "stem player prepared with duration=${this.durationSeconds()}s")
                    if (preparedStemCount.incrementAndGet() == stemPlayers.size) {
                        val maxDur = stemPlayers.mapNotNull { it.durationSeconds().takeIf { d -> d > 0 } }.maxOrNull() ?: 0
                        if (maxDur > 0) {
                            durationSeconds = maxDur
                            _durationSecondsState.value = maxDur
                        }
                        isPrepared = true
                        _isPreparedState.value = true
                        Log.d(TAG, "All ${stemPlayers.size} stems prepared, publishing isPrepared=true")
                        mainHandler.post { onPrepared?.invoke() }
                    }
                }
                onCompletion = {
                    Log.d(TAG, "stem completed: ${this.sourceUrl.substringAfterLast('/')}")
                }
                onError = { msg ->
                    Log.e(TAG, "Stem player error: $msg for ${this.sourceUrl.substringAfterLast('/')}")
                    mainHandler.post { onError?.invoke(msg) }
                }
            }
            stemPlayers.add(player)
        }

        stemPlayers.forEach { _ ->
            pureKotlinProcessorList.add(PureKotlinAudioProcessor(context))
        }

        volumes.addAll(initialVolumes.map { it / 100f })
        while (volumes.size < stemPlayers.size) {
            volumes.add(0.8f)
        }
        while (mutes.size < stemPlayers.size) {
            mutes.add(false)
        }

        if (fullMixUrl.isNotBlank()) {
            fullMixPlayer?.load(fullMixUrl)
        }

        stemUrls.values.forEachIndexed { index, url ->
            if (url.isNotBlank()) {
                Log.d(TAG, "Loading stem $index from URL: $url")
                stemPlayers.getOrNull(index)?.load(url)
            } else {
                Log.w(TAG, "Stem $index has empty URL!")
            }
        }
        Log.d(TAG, "Total stems loaded: ${stemPlayers.size}")
    }

    fun load(fullMixUrl: String, stemUrls: Map<String, String>) {
        initialize(fullMixUrl, stemUrls)
    }

    fun play() {
        if (stemPlayers.isNotEmpty()) {
            val allReady = stemPlayers.all { it.isPrepared() }
            Log.d(TAG, "play: stemPlayers.size = ${stemPlayers.size}, allReady = $allReady")
            if (!allReady) {
                val readyCount = stemPlayers.count { it.isPrepared() }
                stemPlayers.forEachIndexed { index, player ->
                    Log.w(TAG, "Stem $index (${player.sourceUrl}) isPrepared = ${player.isPrepared()}")
                }
                Log.w(TAG, "play: only $readyCount/${stemPlayers.size} stems are prepared — refusing to start.")
                return
            }

            applySoloMute()
            stemPlayers.forEachIndexed { index, player ->
                val muted = mutes.getOrElse(index) { false }
                val vol = if (muted) 0f else volumes.getOrElse(index) { 0.8f }
                Log.d(TAG, "play: stem $index (${player.sourceUrl}): muted=$muted, vol=$vol")
                player.setVolume(vol)
            }

            val sharedPercent = currentPosition
            Log.d(TAG, "play: starting ${stemPlayers.size} stems at sharedPos=${sharedPercent}%")
            mainHandler.post {
                stemPlayers.forEach { it.seekToPercent(sharedPercent) }
                stemPlayers.forEach {
                    it.play()
                    Log.d(TAG, "play: ${it.sourceUrl} isPlaying=${it.isPlaying()}")
                }
            }

            isPrepared = true
        } else {
            fullMixPlayer?.takeIf { it.isPrepared() }?.play() ?: run {
                Log.w(TAG, "play: fullMixPlayer not prepared or absent")
                return
            }
            isPrepared = true
        }
        playing = true
        startProgressUpdates()
    }

    fun pause() {
        mainHandler.post {
            stemPlayers.forEach { it.pause() }
            fullMixPlayer?.pause()
        }
        playing = false
        stopProgressUpdates()
    }

    fun toggle() {
        if (playing) pause() else play()
    }

    fun stop() {
        mainHandler.post {
            stemPlayers.forEach {
                it.pause()
                it.seekToPercent(0f)
            }
            fullMixPlayer?.pause()
            fullMixPlayer?.seekToPercent(0f)
        }
        playing = false
        currentPosition = 0f
        _progressState.value = 0f
        stopProgressUpdates()
    }

    fun seekToPercent(percent: Float) {
        val clampedPercent = percent.coerceIn(0f, 100f)
        currentPosition = clampedPercent
        _progressState.value = clampedPercent
        mainHandler.post {
            fullMixPlayer?.seekToPercent(clampedPercent)
            stemPlayers.forEach { it.seekToPercent(clampedPercent) }
        }
    }

    fun setStemVolume(index: Int, volume: Int) {
        if (index < 0 || index >= volumes.size) return
        val volumeFloat = (volume / 100f).coerceIn(0f, 1f)
        volumes[index] = volumeFloat
        stemPlayers.getOrNull(index)?.setVolume(volumeFloat)
        if (soloIndex == index) {
            applySoloMute()
        }
    }

    fun setStemMute(index: Int, muted: Boolean) {
        if (index < 0 || index >= mutes.size) return
        mutes[index] = muted
        stemPlayers.getOrNull(index)?.setMute(muted)
    }

    fun toggleStemMute(index: Int) {
        if (index < 0 || index >= mutes.size) return
        mutes[index] = !mutes[index]
        stemPlayers.getOrNull(index)?.setMute(mutes[index])
    }

    fun setStemSolo(index: Int, solo: Boolean) {
        if (solo) {
            soloIndex = index
        } else if (soloIndex == index) {
            soloIndex = null
        }
        applySoloMute()
    }

    fun toggleStemSolo(index: Int) {
        if (soloIndex == index) {
            soloIndex = null
        } else {
            soloIndex = index
        }
        applySoloMute()
    }

    private fun applySoloMute() {
        val hasSolo = soloIndex != null
        stemPlayers.forEachIndexed { index, player ->
            val isSolo = soloIndex == index
            val isMuted = mutes.getOrElse(index) { false }
            val shouldBeMuted = isMuted || (hasSolo && !isSolo)
            player.setMute(shouldBeMuted)
        }
    }

    private fun shouldPlayStem(index: Int): Boolean {
        if (index < 0 || index >= mutes.size) return false
        val isSolo = soloIndex == index
        val hasSolo = soloIndex != null
        val isMuted = mutes.getOrElse(index) { false }
        return !isMuted && (!hasSolo || isSolo)
    }

    fun setDeepFilterEnabled(enabled: Boolean) {
        deepFilterEnabled = enabled
        fullMixPlayer?.setDeepFilterEnabled(enabled)
        stemPlayers.forEach { it.setDeepFilterEnabled(enabled) }
    }

    fun setPlaybackSpeed(newSpeed: Float) {
        speed = newSpeed.coerceIn(0.5f, 2.0f)
        fullMixPlayer?.setPlaybackSpeed(speed)
        stemPlayers.forEach { it.setPlaybackSpeed(speed) }
    }

    fun setPitchShift(newPitchShift: Int) {
        pitchShift = newPitchShift.coerceIn(-12, 12)
        fullMixPlayer?.setPitchShift(pitchShift)
        stemPlayers.forEach { it.setPitchShift(pitchShift) }
    }

    fun getStemCount(): Int = stemPlayers.size
    fun getStemVolume(index: Int): Int = if (index < volumes.size) (volumes[index] * 100).toInt() else 80
    fun getStemMute(index: Int): Boolean = mutes.getOrElse(index) { false }
    fun getStemSolo(index: Int): Boolean = soloIndex == index
    fun isDeepFilterEnabled(): Boolean = deepFilterEnabled
    fun getSpeed(): Float = speed
    fun getPitchShift(): Int = pitchShift

    fun areAllStemsPrepared(): Boolean {
        return if (stemPlayers.isEmpty()) {
            fullMixPlayer?.isPrepared() ?: false
        } else {
            stemPlayers.all { it.isPrepared() }
        }
    }

    fun applyPresetEffect(presetId: String) {
        when (presetId) {
            AudioPresets.VOCAL_ENHANCEMENT -> pureKotlinProcessorList.forEach { it.applyVocalEnhancement() }
            AudioPresets.BASS_BOOST -> pureKotlinProcessorList.forEach { it.applyBassBoost() }
            AudioPresets.TREBLE_BOOST -> pureKotlinProcessorList.forEach { it.applyTrebleBoost() }
            AudioPresets.NOISE_REDUCTION -> pureKotlinProcessorList.forEach { it.applyNoiseReduction() }
            AudioPresets.KARAOKE -> pureKotlinProcessorList.forEach { it.applyKaraoke() }
            AudioPresets.STUDIO -> pureKotlinProcessorList.forEach { it.applyStudio() }
        }
    }

    fun resetAllEffects() {
        pureKotlinProcessorList.forEach { it.resetAll() }
    }

    fun setEqGain(band: Int, gain: Float) {
        pureKotlinProcessorList.forEach { it.setEqGain(band, gain) }
    }

    fun setReverbEnabled(enabled: Boolean) {
        deepFilterEnabled = enabled
        pureKotlinProcessorList.forEach { it.setReverbEnabled(enabled) }
    }

    fun setReverbAmount(amount: Float) {
        pureKotlinProcessorList.forEach { it.setReverbAmount(amount) }
    }

    fun setDelayEnabled(enabled: Boolean) {
        pureKotlinProcessorList.forEach { it.setDelayEnabled(enabled) }
    }

    fun setDelayTime(timeMs: Int) {
        pureKotlinProcessorList.forEach { it.setDelayTime(timeMs) }
    }

    fun setChorusEnabled(enabled: Boolean) {
        pureKotlinProcessorList.forEach { it.setChorusEnabled(enabled) }
    }

    fun setCompressionEnabled(enabled: Boolean) {
        pureKotlinProcessorList.forEach { it.setCompressionEnabled(enabled) }
    }

    private fun startProgressUpdates() {
        CoroutineScope(Dispatchers.Main).launch {
            while (playing) {
                val pos = if (stemPlayers.isNotEmpty()) {
                    val leader = (stemPlayers.indices)
                        .firstOrNull { idx ->
                            stemPlayers[idx].isPrepared() &&
                            !mutes.getOrElse(idx) { false }
                        }
                        ?.let { stemPlayers[it] }
                        ?: stemPlayers.firstOrNull()
                    leader?.progressPercent() ?: 0f
                } else {
                    fullMixPlayer?.progressPercent() ?: 0f
                }
                currentPosition = pos
                _progressState.value = pos
                onProgress?.invoke(pos)
                kotlinx.coroutines.delay(100)
            }
        }
    }

    private fun stopProgressUpdates() {}

    fun release() {
        stop()
        fullMixPlayer?.release()
        stemPlayers.forEach { it.release() }
        stemPlayers.clear()
        pureKotlinProcessorList.forEach { it.release() }
        pureKotlinProcessorList.clear()
        volumes.clear()
        mutes.clear()
        soloIndex = null
        isPrepared = false
        playing = false
        currentPosition = 0f
        durationSeconds = 0
        preparedStemCount.set(0)
        _isPreparedState.value = false
        _durationSecondsState.value = 0
        _progressState.value = 0f
    }
}
