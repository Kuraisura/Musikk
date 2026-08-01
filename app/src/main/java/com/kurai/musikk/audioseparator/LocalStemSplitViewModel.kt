package com.kurai.musikk.audioseparator

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurai.musikk.BuildConfig
import com.kurai.musikk.data.STEM_PRESETS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import com.kurai.musikk.data.STEM_PRESETS
import com.kurai.musikk.data.StemPreset

object PresetHolder {
    var current: StemPreset = STEM_PRESETS[3]
}

data class SeparationProgress(
    val processing: Boolean = false,
    val progress: Float = 0f,
    val currentStep: String = "",
    val stems: Map<String, String>? = null,
    val jobId: String = "",
    val error: String? = null
) {
    val isDone: Boolean get() = !processing && stems != null && error == null
}

class LocalStemSplitViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "LocalStemSplitViewModel"
    }

    private val _state = MutableStateFlow(SeparationProgress())
    val state: StateFlow<SeparationProgress> = _state.asStateFlow()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(
            okhttp3.ConnectionPool(
                5,
                5, java.util.concurrent.TimeUnit.MINUTES
            )
        )
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .addInterceptor { chain ->
            chain.proceed(chain.request())
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.LOCAL_STEM_SERVER_URL + "/")
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(LocalStemApiService::class.java)

    // Preset-based progress animation speeds (steps per second)
    private fun getProgressStepsPerSecond(preset: StemPreset): Float {
        return when (preset.id) {
            "vocals_only" -> 4.0f      // Fastest - 1 stem
            "instrumental" -> 3.5f     // Fast
            "karaoke_2" -> 3.0f        // Fast
            "standard_4" -> 2.5f       // Medium
            "band_5" -> 2.2f           // Medium
            "band_plus_6" -> 2.0f     // Medium
            "orchestral_7" -> 1.8f     // Slower
            "electronic_8" -> 1.6f     // Slower
            "rhythm_9" -> 1.4f         // Slow
            "studio_10" -> 1.2f        // Slowest - 10 stems
            else -> 2.0f
        }
    }

    private fun getSimulatedStep(progress: Float, preset: StemPreset): String {
        val pct = (progress * 100).toInt()
        return when {
            pct < 10 -> "Initializing..."
            pct < 25 -> "Analyzing audio..."
            pct < 40 -> "Extracting stems..."
            pct < 60 -> "Processing ${preset.stems.size} stems..."
            pct < 80 -> "Finalizing separation..."
            pct < 90 -> "Uploading results..."
            else -> "Almost done..."
        }
    }

    fun uploadAudioFromUrl(sourceUrl: String) {
        viewModelScope.launch {
            try {
                _state.value = SeparationProgress(
                    processing = true,
                    progress = 0f,
                    currentStep = "Starting processing...",
                )

                val sourceBytes = downloadBytes(sourceUrl)
                val fileName = sourceUrl.substringAfterLast("/").substringBefore("?").ifBlank { "audio.m4a" }

                _state.value = SeparationProgress(
                    processing = true,
                    progress = 0.1f,
                    currentStep = "Uploading to PC server...",
                )

                val presetIdBody = PresetHolder.current.id
                    .toRequestBody("text/plain".toMediaType())

                val requestBody = sourceBytes.toRequestBody(
                    guessContentType(fileName).toMediaTypeOrNull()
                )
                val part = MultipartBody.Part.createFormData("file", fileName, requestBody)

                val response = withContext(Dispatchers.IO) {
                    api.splitAudio(part, presetIdBody)
                }

                if (!response.isSuccessful) {
                    val errBody = response.errorBody()?.string() ?: "Unknown error"
                    _state.value = SeparationProgress(
                        processing = false,
                        error = "Server returned ${response.code()}: $errBody"
                    )
                    return@launch
                }

                val body = response.body() ?: run {
                    _state.value = SeparationProgress(
                        processing = false,
                        error = "Empty response from server"
                    )
                    return@launch
                }

                val jobId = body.job_id
                val host = BuildConfig.LOCAL_STEM_SERVER_URL
                    .replace("http://", "")
                    .replace("https://", "")

                _state.value = SeparationProgress(
                    processing = true,
                    progress = 0.15f,
                    currentStep = "Separation started, waiting for results...",
                    jobId = jobId,
                )

                // Poll for progress
                pollProgress(jobId, host)

            } catch (e: Exception) {
                _state.value = SeparationProgress(
                    processing = false,
                    error = e.message ?: "Upload failed"
                )
            }
        }
    }

    private suspend fun pollProgress(jobId: String, host: String) {
        var attempts = 0
        val maxAttempts = 300          // 5 min max, not 10 min
        val pollInterval = 1000L
        val staleThreshold = 15         // 15 sec of no real server progress = stale
        val preset = PresetHolder.current
        val stepsPerSecond = getProgressStepsPerSecond(preset)
        var simulatedProgress = 0f
        var lastUpdateTime = System.currentTimeMillis()
        var consecutiveStale = 0
        var lastRealProgress = 0f

        while (currentCoroutineContext().isActive && attempts < maxAttempts) {
            delay(pollInterval)
            attempts++

            val currentTime = System.currentTimeMillis()
            val elapsedSeconds = (currentTime - lastUpdateTime) / 1000f
            simulatedProgress += stepsPerSecond * elapsedSeconds
            simulatedProgress = simulatedProgress.coerceAtMost(99f)
            lastUpdateTime = currentTime

            try {
                val progressResponse = withContext(Dispatchers.IO) {
                    api.getProgress(jobId)
                }

                if (!progressResponse.isSuccessful) {
                    consecutiveStale++
                    // After too many stale responses, fail fast (idle server)
                    if (consecutiveStale > staleThreshold) {
                        _state.value = SeparationProgress(
                            processing = false,
                            error = "Server stopped responding (idle/stale). Please retry.",
                            jobId = jobId,
                        )
                        return
                    }
                    // Brief simulated update, then continue
                    _state.value = SeparationProgress(
                        processing = true,
                        progress = simulatedProgress / 100f,
                        currentStep = "Waiting for server... ($consecutiveStale)",
                        jobId = jobId,
                    )
                    continue
                }

                // Successful response — reset stale counter
                consecutiveStale = 0
                val prog = progressResponse.body() ?: run {
                    // Empty body but HTTP 200; treat as stale
                    consecutiveStale++
                    continue
                }
                
when (prog.status) {
    "completed" -> {
        val stemKeys = preset.stems.map { it.name.lowercase() }
        val baseUrl = "http://$host/download/$jobId"
        
        Log.d(TAG, "Progress completed: produced_files=${prog.produced_files}, stems=${prog.stems}")

        // Try to get stem URLs from multiple possible sources
        val stemMap = if (!prog.produced_files.isNullOrEmpty()) {
            // Use produced_files if available (maps stem names to filenames)
            // Construct full URLs for each stem
            prog.produced_files.map { (key, filename) ->
                val stemKey = key.lowercase()
                stemKey to "$baseUrl/$filename"
            }.toMap()
        } else if (!prog.stems.isNullOrEmpty()) {
            // Fallback to stems list with constructed URLs
            prog.stems.mapIndexed { index, filename ->
                val stemKey = stemKeys.getOrNull(index) ?: "stem_$index"
                stemKey to "$baseUrl/$filename"
            }.toMap()
        } else {
            emptyMap()
        }
        
        Log.d(TAG, "Constructed stemMap: $stemMap")

        _state.value = SeparationProgress(
                            processing = false,
                            progress = 1f,
                            currentStep = "Separation complete",
                            stems = stemMap,
                            jobId = jobId,
                        )
                        return
                    }
                    "error" -> {
                        _state.value = SeparationProgress(
                            processing = false,
                            error = "Server error: ${prog.step}"
                        )
                        return
                    }
                    "processing" -> {
                        val actualProgress = (prog.progress ?: 0) / 100f
                        val displayProgress = if (actualProgress > 0) actualProgress else simulatedProgress / 100f
                        val displayStep = if (prog.step.isNotBlank()) prog.step else getSimulatedStep(simulatedProgress, preset)

                        if (actualProgress > 0 && actualProgress == lastRealProgress && actualProgress < 0.99f) {
                            consecutiveStale++
                        } else {
                            lastRealProgress = actualProgress
                            consecutiveStale = 0
                        }
                        
                        _state.value = SeparationProgress(
                            processing = true,
                            progress = displayProgress.coerceAtMost(0.99f),
                            currentStep = displayStep,
                            jobId = jobId,
                        )
                    }
                }
            } catch (e: Exception) {
                // Network error during poll — use simulated progress
                _state.value = SeparationProgress(
                    processing = true,
                    progress = (simulatedProgress / 100f).coerceAtMost(0.99f),
                    currentStep = getSimulatedStep(simulatedProgress, preset),
                    jobId = jobId,
                )
            }
        }

        // Timeout
        _state.value = SeparationProgress(
            processing = false,
            error = "Processing timed out after ${maxAttempts}s"
        )
    }

    fun reset() {
        _state.value = SeparationProgress()
    }

    private suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        okHttp.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("Empty response")
            val baos = ByteArrayOutputStream()
            body.byteStream().use { input ->
                input.copyTo(baos, 64 * 1024)
            }
            baos.toByteArray()
        }
    }

    private fun guessContentType(name: String): String {
        return when {
            name.endsWith(".mp3", true) -> "audio/mpeg"
            name.endsWith(".wav", true) -> "audio/wav"
            name.endsWith(".flac", true) -> "audio/flac"
            name.endsWith(".m4a", true) -> "audio/mp4"
            name.endsWith(".ogg", true) -> "audio/ogg"
            name.endsWith(".aac", true) -> "audio/aac"
            else -> "application/octet-stream"
        }
    }
}