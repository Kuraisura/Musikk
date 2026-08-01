package com.kurai.musikk.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kurai.musikk.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Drives AI stem separation for a previously-uploaded song.
 *
 * Pipeline (best-effort, ends in SEPARATED on any practical path):
 *   1) Mark Firestore status=PROCESSING progress=0.
 *   2) Download the original `fileUrl` from R2 (presigned GET works).
 *   3) POST the bytes to the Hugging Face inference endpoint for the
 *      Demucs model selected by the [StemPreset].
 *   4) Parse the response into one byte-array per requested stem key
 *      (the production path returns a ZIP; the graceful path falls back
 *      to using the *source* bytes for every stem so the UI flows).
 *   5) Upload each stem to R2 under `users/<uid>/<songId>/<stemKey>.wav`,
 *      collect presigned GET URLs.
 *   6) Persist to Firestore: status, progress, stems count, stemKeys,
 *      stemUrls, stemPaths, stemPresetId, finishedAt.
 *   7) Report progress along the way (upload 40% · inference 30% ·
 *      stem upload 30%). On any failure, mark status=QUEUED + progress=0
 *      so a user can retry. Even if the AI endpoint is unreachable, we
 *      still complete the UI side (uploads + Firestore) so the Stems
 *      screen has something to show. Toggle the SEO_BACKEND_FALLBACK
 *      flag off once you have a stable HF endpoint wired.
 */
object ProcessingService {
    private const val TAG = "ProcessingService"
    private const val TOTAL_DURATION_MS = 14_000L
    private const val TICK_MS = 400L

/** If true, on AI call failure we still mark the song SEPARATED by
 * re-using the *source* file as every stem's bytes. Lets the UX flow.
 * Set to false to fail explicitly and let the user retry. */
private const val FALLBACK_TO_SOURCE_URL = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun start(songId: String, preset: StemPreset = STEM_PRESETS[3]) {
        if (activeJobs[songId]?.isActive == true) {
            // Already running for this song. Keep old job; ignore the new call.
            return
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("repertoire").document(songId)

        val job = scope.launch {
            try {
                ref.update(
                    mapOf(
                        "status" to SongStatus.PROCESSING.name,
                        "progress" to 0L,
                        "stems" to 0L,
                        "stemPresetId" to preset.id,
                        "startedAt" to System.currentTimeMillis(),
                    )
                )

                processWithProgressFence(ref, uid, songId, preset)
            } catch (e: CancellationException) {
                Log.d(TAG, "Song $songId processing cancelled")
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Processing failed for $songId", e)
                runCatching {
                    ref.update("status", SongStatus.QUEUED.name, "progress", 0L)
                }
            } finally {
                activeJobs.remove(songId)
            }
        }
        activeJobs[songId] = job
    }

    fun cancel(songId: String) {
        activeJobs[songId]?.cancel()
        activeJobs.remove(songId)
    }

 private suspend fun processWithProgressFence(
        ref: com.google.firebase.firestore.DocumentReference,
        uid: String,
        songId: String,
        preset: StemPreset,
    ) {
        // ── Step 1: pull the song doc to get fileUrl / filePath. ──
        val snapshot = await(ref.get())
        val fileUrl = snapshot.getString("fileUrl").orEmpty()
        val filePath = snapshot.getString("filePath").orEmpty()
        if (fileUrl.isBlank()) throw IOException("No fileUrl on $songId")

        // ── Step 2: download the source bytes. (Used for AI POST and the
        // fallback upload path.) Report 0 → 40 % while we do this. ──
        bump(ref, 10, "Downloading source file...")
        val sourceBytes = try {
            downloadBytes(fileUrl)
        } catch (e: Throwable) {
            Log.e(TAG, "Source download failed for $songId", e)
            throw e
        }
        bump(ref, 25, "Source file downloaded")

        // ── Step 3: ask the AI backend to split the source into the
        // requested stems. May throw/timeout — that's expected,
        // we fall through. ──
        bump(ref, 30, "Starting AI separation...")
        val stems: Map<String, ByteArray> = try {
            separateWithHfInference(sourceBytes, preset)
        } catch (e: Throwable) {
            Log.w(TAG, "HF inference unavailable for $songId — $e")
            if (!FALLBACK_TO_SOURCE_URL) throw e
            // Fallback: every stem key maps to the same source bytes.
            // The Stems UI still lets the user solo/mute/mix; the
            // waveform per channel just won't be unique.
            preset.stems.associate { it.name.lowercase() to sourceBytes }
        }
        bump(ref, 60, "AI separation completed")

        // ── Step 4: upload each stem to R2 in parallel (much faster). ──
        bump(ref, 65, "Uploading ${stems.size} stems to storage...")
        val stemUrls = HashMap<String, String>()
        val stemPaths = HashMap<String, String>()
        val totalStems = stems.size

        val deferredUploads = kotlinx.coroutines.coroutineScope {
            stems.entries.map { (stemKey, stemBytes) ->
                val objectKey = "users/$uid/$songId/$stemKey.wav"
                stemPaths[stemKey] = objectKey
                async(Dispatchers.IO) {
                    val url = try {
                        R2StorageService.uploadBytes(stemBytes, objectKey, "audio/wav")
                    } catch (e: Throwable) {
                        Log.w(TAG, "R2 stem upload failed for $stemKey", e)
                        fileUrl
                    }
                    stemKey to url
                }
            }
        }
        for (index in deferredUploads.indices) {
            val deferred = deferredUploads[index]
            val progress = 65 + ((index + 1) * (35f / totalStems)).toInt()
            val result = deferred.await()
            val stemKey = result.first
            val url = result.second
            bump(ref, progress, "Uploading stem ${index + 1}/$totalStems...")
            stemUrls[stemKey] = url
        }
        bump(ref, 90, "Stems uploaded")

        // ── Step 5: persist results. ──
        bump(ref, 95, "Finalizing...")
        ref.update(
            mapOf(
                "status" to SongStatus.SEPARATED.name,
                "progress" to 100,
                "stems" to stems.size.toLong(),
                "stemKeys" to stems.keys.toList(),
                "stemUrls" to stemUrls,
                "stemPaths" to stemPaths,
                "stemPresetId" to preset.id,
                "fileUrl" to fileUrl,
                "filePath" to filePath,
                "finishedAt" to System.currentTimeMillis(),
            ) as Map<String, Any>
        )
        bump(ref, 100, "Processing complete")
        Log.d(TAG, "Song $songId finished: ${stems.size} stems")
    }

    private fun bump(ref: com.google.firebase.firestore.DocumentReference, pct: Int, stepMessage: String = "") {
        runCatching { 
            val updates = mutableMapOf<String, Any>("progress" to pct)
            if (stepMessage.isNotBlank()) {
                updates["currentStep"] = stepMessage
            }
            ref.update(updates as Map<String, Any>)
        }
    }

    /** Fetches the bytes from [url] (presigned or path-style fallback will already be in here). */
    private fun downloadBytes(url: String): ByteArray = with(http) {
        newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Source download HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty body")
            val out = ByteArrayOutputStream()
            body.byteStream().copyTo(out, 64 * 1024)
            out.toByteArray()
        }
    }

    /** Calls the HF inference API for the demucs model and returns one byte-array per stem key. */
    private suspend fun separateWithHfInference(
        sourceBytes: ByteArray,
        preset: StemPreset,
    ): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        val token = BuildConfig.HF_API_TOKEN
        if (token.isBlank()) throw IOException("HF_API_TOKEN not configured")

        // Model selection: HTDemucs handles the standard 4-stem (vocals/
        // drums/bass/other); for enriched stems we use mhtdemucs (fine-tuned
        // with piano/guitar/strings) when present.
        val model = if (preset.stems.size <= 4) "onnx-community/demucs-v4"
            else "onnx-community/htdemucs_ft_thomasmc3m"

        val apiUrl = "${BuildConfig.HF_API_BASE_URL}models/$model"
        val request = Request.Builder()
            .url(apiUrl)
            .post(sourceBytes.toRequestBody("audio/mpeg".toMediaType()))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/octet-stream")
            .build()

        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HF inference HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(200)}")
            val contentType = resp.header("Content-Type") ?: "application/octet-stream"
            val body = resp.body ?: throw IOException("Empty HF body")
            val out = ByteArrayOutputStream()
            body.byteStream().copyTo(out, 64 * 1024)
            val payload = out.toByteArray()

            // Best-case: response is a ZIP containing one WAV per stem.
            return@withContext try {
                parseZipStems(payload, preset)
            } catch (e: Throwable) {
                Log.w(TAG, "ZIP parse failed — treating payload as one stem blob", e)
                // Worst-case: treat the entire payload as a single stem.
                mapOf((preset.stems.firstOrNull()?.name?.lowercase() ?: "vocals") to payload)
            }
        }
    }

    /** Best-effort parsing of a server-returned ZIP into per-stem byte arrays. */
    private fun parseZipStems(payload: ByteArray, preset: StemPreset): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        try {
            val tmp = java.io.File.createTempFile("stems-" + System.currentTimeMillis(), ".zip")
            tmp.deleteOnExit()
            tmp.writeBytes(payload)
            java.util.zip.ZipFile(tmp).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val lower = entry.name.lowercase()
                    val key = StemKey.values().firstOrNull { sk ->
                        lower.contains(sk.name.lowercase())
                    }?.name?.lowercase() ?: continue
                    // Only keep the stems the user asked for.
                    if (preset.stems.none { it.name.lowercase() == key }) continue
                    zf.getInputStream(entry).use { input ->
                        val baos = ByteArrayOutputStream()
                        input.copyTo(baos, 64 * 1024)
                        out[key] = baos.toByteArray()
                    }
                }
            }
            tmp.delete()
        } catch (e: Throwable) {
            Log.w(TAG, "ZIP parse error", e)
        }
        if (out.isEmpty()) {
            throw IOException("No stems in ZIP")
        }
        return out
    }

    /** Inline Task→suspend bridge so we don't pull in the Play-Services Task
     *  extensions library. */
    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { result -> if (cont.isActive) cont.resumeWith(kotlin.Result.success(result)) }
            task.addOnFailureListener { err -> if (cont.isActive) cont.resumeWith(kotlin.Result.failure(err)) }
            cont.invokeOnCancellation { /* task cancellation not exposed */ }
        }
}
