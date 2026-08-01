package com.kurai.musikk.data

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer

/**
 * Simple audio processor using Android's built-in audio effects.
 * This provides basic EQ-based noise filtering and volume control.
 */
class SimpleAudioProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "SimpleAudioProcessor"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var virtualizer: Virtualizer? = null
    private var audioSessionId: Int = 0
    
    // Effect states
    private var volume: Float = 1.0f
    private var deepFilterEnabled: Boolean = false
    private var isMuted: Boolean = false
    
    /**
     * Initialize with a MediaPlayer (legacy path).
     */
    fun initialize(player: MediaPlayer) {
        this.mediaPlayer = player
        this.audioSessionId = player.audioSessionId
        setupAudioEffects()
    }

    /**
     * Initialize with an ExoPlayer. We just need its audio session id so the
     * Equalizer/Virtualizer can attach to the same output mix.
     */
    fun initialize(player: ExoPlayer) {
        this.mediaPlayer = null
        this.audioSessionId = player.audioSessionId
        setupAudioEffects()
    }
    
    /**
     * Setup audio effects
     */
    private fun setupAudioEffects() {
        try {
            // Setup equalizer for noise filtering
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                // Initialize with flat response
                for (band in 0 until numberOfBands) {
                    setBandLevel(band.toShort(), 0.toShort())
                }
            }
            
            // Setup virtualizer for spatial effects (deprecated but still works)
            try {
                virtualizer = Virtualizer(0, audioSessionId).apply {
                    enabled = false
                    setStrength(0.toShort())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Virtualizer not available", e)
            }
            
            Log.d(TAG, "Audio effects initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio effects", e)
        }
    }
    
    /**
     * Set volume (0.0 to 1.0). Volume is applied directly by ExoPlayer now
     * (see [AudioPlayer.setVolume]); this method only tracks the requested
     * value so callers can read it back via [getVolume].
     */
    fun setVolume(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        Log.d(TAG, "Volume set to: $volume")
    }
    
    /**
     * Set mute state. Mute is applied by ExoPlayer directly (see
     * [AudioPlayer.setMute]); here we just remember the flag.
     */
    fun setMute(muted: Boolean) {
        isMuted = muted
        Log.d(TAG, "Mute set to: $muted")
    }
    
    /**
     * Enable/disable deep filter (ambient & wind noise)
     */
    fun setDeepFilterEnabled(enabled: Boolean) {
        deepFilterEnabled = enabled
        if (enabled) {
            applyNoiseFilter()
        } else {
            resetEqualizer()
        }
        Log.d(TAG, "Deep filter set to: $enabled")
    }
    
    /**
     * Apply noise filtering using equalizer
     */
    private fun applyNoiseFilter() {
        equalizer?.let { eq ->
            try {
                // Reduce low frequencies to filter ambient/wind noise
                // These frequencies are typically where wind and ambient noise live
                val bands = eq.numberOfBands
                
                for (band in 0 until bands) {
                    val bandFreq = eq.getCenterFreq(band.toShort()) / 1000f // in kHz
                    val level: Short = when {
                        bandFreq < 0.5f -> -1800 // Strong reduction for very low frequencies (wind)
                        bandFreq < 1.0f -> -1200 // Medium reduction for low frequencies
                        bandFreq < 2.0f -> -600  // Light reduction for mid-low frequencies
                        else -> 0               // No change for higher frequencies
                    }
                    eq.setBandLevel(band.toShort(), level)
                }
                
                Log.d(TAG, "Noise filter applied with $bands bands")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply noise filter", e)
            }
        }
    }
    
    /**
     * Reset equalizer to flat response
     */
    private fun resetEqualizer() {
        equalizer?.let { eq ->
            try {
                for (band in 0 until eq.numberOfBands) {
                    eq.setBandLevel(band.toShort(), 0.toShort())
                }
                Log.d(TAG, "Equalizer reset to flat")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset equalizer", e)
            }
        }
    }
    
    /**
     * Get current state
     */
    fun getVolume(): Float = volume
    fun isDeepFilterEnabled(): Boolean = deepFilterEnabled
    fun isMuted(): Boolean = isMuted
    
    /**
     * Release all resources
     */
    fun release() {
        try {
            equalizer?.release()
            virtualizer?.release()
            mediaPlayer = null
            Log.d(TAG, "Audio effects released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release audio effects", e)
        }
    }
}