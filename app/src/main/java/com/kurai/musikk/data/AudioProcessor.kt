package com.kurai.musikk.data

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer

/**
 * Audio processor for handling FX and pitch/speed controls.
 * Uses Android's built-in audio effects and Media3 for processing.
 */
class AudioProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioProcessor"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var exoPlayer: ExoPlayer? = null
    private var currentSpeed: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var deepFilterEnabled: Boolean = false
    
    // Initialize with a MediaPlayer for basic effects
    fun initialize(mediaPlayer: MediaPlayer) {
        this.mediaPlayer = mediaPlayer
        this.exoPlayer = null
    }
    
    // Initialize with ExoPlayer for advanced processing
    fun initialize(exoPlayer: ExoPlayer) {
        this.exoPlayer = exoPlayer
        this.mediaPlayer = null
    }
    
    /**
     * Enable/disable ambient and wind noise filtering
     * For now, this just tracks the state since the actual implementation
     * requires more complex audio processing
     */
    fun setDeepFilterEnabled(enabled: Boolean) {
        deepFilterEnabled = enabled
        Log.d(TAG, "Deep filter set to: $enabled")
    }
    
    /**
     * Set playback speed (0.5x to 2.0x)
     */
    fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
        
        // Update MediaPlayer if available
        mediaPlayer?.let { player ->
            try {
                // MediaPlayer doesn't support playback speed directly
                Log.w(TAG, "MediaPlayer does not support playback speed changes - current: $currentSpeed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set playback speed", e)
            }
        }
        
        // Update ExoPlayer if available
        exoPlayer?.let { player ->
            try {
                val params = PlaybackParameters(currentSpeed, currentPitch)
                player.playbackParameters = params
                Log.d(TAG, "ExoPlayer speed set to: $currentSpeed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set ExoPlayer playback speed", e)
            }
        }
    }
    
    /**
     * Set pitch shift (-12 to +12 semitones)
     */
    fun setPitchShift(semitones: Int) {
        val pitch = 1.0f * Math.pow(2.0, semitones.toDouble() / 12.0).toFloat()
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        
        // Update ExoPlayer if available
        exoPlayer?.let { player ->
            try {
                val params = PlaybackParameters(currentSpeed, currentPitch)
                player.playbackParameters = params
                Log.d(TAG, "ExoPlayer pitch set to: $currentPitch (semitones: $semitones)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set ExoPlayer pitch shift", e)
            }
        }
        
        // MediaPlayer doesn't support pitch shifting
        mediaPlayer?.let {
            Log.w(TAG, "MediaPlayer does not support pitch shifting - current: $currentPitch")
        }
    }
    
    /**
     * Get current deep filter state
     */
    fun isDeepFilterEnabled(): Boolean = deepFilterEnabled
    
    /**
     * Get current speed
     */
    fun getCurrentSpeed(): Float = currentSpeed
    
    /**
     * Get current pitch
     */
    fun getCurrentPitch(): Float = currentPitch
    
    /**
     * Release all resources
     */
    fun release() {
        try {
            mediaPlayer = null
            exoPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release audio processor", e)
        }
    }
}