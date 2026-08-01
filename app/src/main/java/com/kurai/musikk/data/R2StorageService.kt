package com.kurai.musikk.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kurai.musikk.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Formatter
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object R2StorageService {
    private const val TAG = "R2StorageService"
    private const val SERVICE = "s3"
    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val HASH_ALGORITHM = "SHA-256"
    private const val REQUEST_TYPE = "aws4_request"

    private val client = OkHttpClient()

    private val endpoint = BuildConfig.R2_ENDPOINT
    private val accessKey = BuildConfig.R2_ACCESS_KEY
    private val secretKey = BuildConfig.R2_SECRET_KEY
    private val bucket = BuildConfig.R2_BUCKET

    private val region = "auto"

    /**
     * Generates a time-limited S3-compatible presigned GET URL for [objectKey].
     *
     * R2's S3 endpoint does NOT serve plain GETs at the path-style URL
     * `https://<accountId>.r2.cloudflarestorage.com/<bucket>/<key>` (returns 404
     * NoSuchKey). Private objects must be read via a signed URL.
     */
    fun presignedGetUrl(objectKey: String, expiresInSeconds: Long = 7 * 24 * 3600L): String {
        val now = System.currentTimeMillis() / 1000L
        val amzDate = formatAmzDate(now)
        val dateStamp = amzDate.substring(0, 8)
        val credentialScope = "$dateStamp/$region/$SERVICE/$REQUEST_TYPE"
        val credential =Uri.encode("$accessKey/$credentialScope")

        val host = getHost(endpoint)
        val canonicalUri = "/$bucket/${encodePath(objectKey)}"
        val signedHeaders = "host"
        val canonicalHeaders = "host:$host\n"

        val expires = expiresInSeconds.coerceAtLeast(1L)
        val canonicalQuery = listOf(
            "X-Amz-Algorithm" to ALGORITHM,
            "X-Amz-Credential" to "$accessKey/$credentialScope",
            "X-Amz-Date" to amzDate,
            "X-Amz-Expires" to expires.toString(),
            "X-Amz-SignedHeaders" to signedHeaders,
        ).joinToString("&") { (k, v) ->
            Uri.encode(k) + "=" + Uri.encode(v)
        }

        val canonicalRequest = "GET\n" +
            "$canonicalUri\n" +
            "$canonicalQuery\n" +
            "$canonicalHeaders\n" +
            "$signedHeaders\n" +
            "UNSIGNED-PAYLOAD"

        val stringToSign = "$ALGORITHM\n$amzDate\n$credentialScope\n" +
            hashHex(canonicalRequest.toByteArray())

        val signingKey = getSignatureKey(secretKey, dateStamp, region, SERVICE)
        val signature = hmacSha256Hex(signingKey, stringToSign)

        // Build the final query string with the signature appended.
        val fullQuery = canonicalQuery + "&X-Amz-Signature=" + Uri.encode(signature)
        return "$endpoint$canonicalUri?$fullQuery"
    }

    suspend fun uploadFile(
        context: Context,
        uri: Uri,
        objectKey: String,
        contentType: String = "audio/mpeg"
    ): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Failed to open input stream for $uri")

        val bytes = readAllBytesCompat(inputStream)
        inputStream.close()

        val now = System.currentTimeMillis() / 1000L
        val amzDate = formatAmzDate(now)
        val dateStamp = amzDate.substring(0, 8)

        val canonicalUri = "/$bucket/${encodePath(objectKey)}"
        val httpUri = "/$bucket/$objectKey"
        val canonicalQueryString = ""
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val payloadHash = hashHex(bytes)

        val canonicalHeaders = "host:${getHost(endpoint)}\n" +
            "x-amz-content-sha256:$payloadHash\n" +
            "x-amz-date:$amzDate\n"

        val canonicalRequest = "PUT\n" +
            "$canonicalUri\n" +
            "$canonicalQueryString\n" +
            "$canonicalHeaders\n" +
            "$signedHeaders\n" +
            "$payloadHash"

        val credentialScope = "$dateStamp/$region/$SERVICE/$REQUEST_TYPE"
        val stringToSign = "$ALGORITHM\n$amzDate\n$credentialScope\n${hashHex(canonicalRequest.toByteArray())}"

        val signingKey = getSignatureKey(secretKey, dateStamp, region, SERVICE)
        val signature = hmacSha256Hex(signingKey, stringToSign)

        val authorizationHeader = "$ALGORITHM Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        Log.d(TAG, "CanonicalRequest:\n$canonicalRequest")
        Log.d(TAG, "StringToSign:\n$stringToSign")
        Log.d(TAG, "Signature: $signature")

        val mediaType = contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        val requestBody = bytes.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$endpoint$httpUri")
            .put(requestBody)
            .addHeader("Host", getHost(endpoint))
            .addHeader("x-amz-content-sha256", payloadHash)
            .addHeader("x-amz-date", amzDate)
            .addHeader("Authorization", authorizationHeader)
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                Log.e(TAG, "Upload failed: ${resp.code} - $errorBody\nCanonicalRequest was:\n$canonicalRequest\nStringToSign was:\n$stringToSign")
                throw IOException("R2 upload failed: ${resp.code} - $errorBody")
            }
        }
        // Return a GET-readable presigned URL — the raw path-style URL is
        // NOT publicly GETtable on R2 (404 NoSuchKey).
        presignedGetUrl(objectKey)
    }

    /**
     * Uploads an in-memory byte payload (typically an AI-generated stem) to R2
     * and returns either a presigned GET URL (if [presignResult] is true) or
     * the path-style URL that this object lives at.
     */
    suspend fun uploadBytes(
        bytes: ByteArray,
        objectKey: String,
        contentType: String = "audio/wav",
        presignResult: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000L
        val amzDate = formatAmzDate(now)
        val dateStamp = amzDate.substring(0, 8)

        val canonicalUri = "/$bucket/${encodePath(objectKey)}"
        val httpUri = "/$bucket/$objectKey"
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val payloadHash = hashHex(bytes)

        val canonicalHeaders = "host:${getHost(endpoint)}\n" +
            "x-amz-content-sha256:$payloadHash\n" +
            "x-amz-date:$amzDate\n"

        val canonicalRequest = "PUT\n" +
            "$canonicalUri\n" +
            "" + "\n" +
            "$canonicalHeaders\n" +
            "$signedHeaders\n" +
            "$payloadHash"

        val credentialScope = "$dateStamp/$region/$SERVICE/$REQUEST_TYPE"
        val stringToSign = "$ALGORITHM\n$amzDate\n$credentialScope\n${hashHex(canonicalRequest.toByteArray())}"

        val signingKey = getSignatureKey(secretKey, dateStamp, region, SERVICE)
        val signature = hmacSha256Hex(signingKey, stringToSign)

        val authorizationHeader = "$ALGORITHM Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val mediaType = contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        val requestBody = bytes.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$endpoint$httpUri")
            .put(requestBody)
            .addHeader("Host", getHost(endpoint))
            .addHeader("x-amz-content-sha256", payloadHash)
            .addHeader("x-amz-date", amzDate)
            .addHeader("Authorization", authorizationHeader)
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                Log.e(TAG, "Upload(bytes) failed: ${resp.code} - $errorBody")
                throw IOException("R2 upload(bytes) failed: ${resp.code} - $errorBody")
            }
        }
        if (presignResult) presignedGetUrl(objectKey) else "$endpoint$canonicalUri"
    }

    /**
     * Deletes an object from R2. Idempotent — logs and swallows errors so a
     * failing delete won't stop a multi-object cleanup.
     */
    suspend fun deleteObject(objectKey: String) = withContext(Dispatchers.IO) {
        if (objectKey.isBlank()) return@withContext
        try {
            val now = System.currentTimeMillis() / 1000L
            val amzDate = formatAmzDate(now)
            val dateStamp = amzDate.substring(0, 8)

            val canonicalUri = "/$bucket/${encodePath(objectKey)}"
            val httpUri = "/$bucket/$objectKey"
            val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
            val payloadHash = "UNSIGNED-PAYLOAD"

            val canonicalHeaders = "host:${getHost(endpoint)}\n" +
                "x-amz-content-sha256:$payloadHash\n" +
                "x-amz-date:$amzDate\n"

            val canonicalRequest = "DELETE\n" +
                "$canonicalUri\n" +
                "" + "\n" +
                "$canonicalHeaders\n" +
                "$signedHeaders\n" +
                "$payloadHash"

            val credentialScope = "$dateStamp/$region/$SERVICE/$REQUEST_TYPE"
            val stringToSign = "$ALGORITHM\n$amzDate\n$credentialScope\n${hashHex(canonicalRequest.toByteArray())}"

            val signingKey = getSignatureKey(secretKey, dateStamp, region, SERVICE)
            val signature = hmacSha256Hex(signingKey, stringToSign)

            val authorizationHeader = "$ALGORITHM Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

            // For DELETE we must send an empty body but still set x-amz-content-sha256.
            // Use a lightweight empty byte-array body via the toRequestBody extension.
            val emptyBody = ByteArray(0).toRequestBody(null)
            val request = Request.Builder()
                .url("$endpoint$httpUri")
                .delete(emptyBody)
                .addHeader("Host", getHost(endpoint))
                .addHeader("x-amz-content-sha256", payloadHash)
                .addHeader("x-amz-date", amzDate)
                .addHeader("Authorization", authorizationHeader)
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 404) {
                    Log.w(TAG, "R2 delete $objectKey returned ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "R2 delete $objectKey failed", e)
        }
    }

    private fun formatAmzDate(epochSeconds: Long): String {
        val date = java.util.Date(epochSeconds * 1000L)
        val fmt = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(date)
    }

    private fun getHost(url: String): String {
        return java.net.URL(url).host
    }

    private fun hashHex(data: ByteArray): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val hash = digest.digest(data)
        return hashToHex(hash)
    }

    private fun hashToHex(hash: ByteArray): String {
        val formatter = Formatter(Locale.US)
        for (b in hash) {
            formatter.format("%02x", b)
        }
        return formatter.toString()
    }

    private fun getSignatureKey(key: String, dateStamp: String, regionName: String, serviceName: String): ByteArray {
        val kDate = hmacSha256(("AWS4$key").toByteArray(), dateStamp)
        val kRegion = hmacSha256(kDate, regionName)
        val kService = hmacSha256(kRegion, serviceName)
        return hmacSha256(kService, REQUEST_TYPE)
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray())
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        val hash = hmacSha256(key, data)
        return hashToHex(hash)
    }

    private fun readAllBytesCompat(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun encodePath(key: String): String {
        return key.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8")
                .replace("+", "%20")
        }
    }
}
