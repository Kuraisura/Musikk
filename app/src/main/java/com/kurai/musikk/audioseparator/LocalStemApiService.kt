package com.kurai.musikk.audioseparator

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

data class SplitResponse(
    val status: String,
    val original_filename: String,
    val job_id: String,
    val preset_id: String,
    val model_used: String,
    val stems: List<String>,
    val download_base_url: String,
)

data class ProgressResponse(
    val status: String,
    val progress: Int = 0,
    val step: String = "",
    val stems: List<String>? = null,
    val produced_files: Map<String, String>? = null,
)

interface LocalStemApiService {

    @Multipart
    @POST("split")
    suspend fun splitAudio(
        @Part file: MultipartBody.Part,
        @Part("preset_id") presetId: okhttp3.RequestBody,
    ): Response<SplitResponse>

    @GET("progress/{job_id}")
    suspend fun getProgress(
        @Path("job_id") jobId: String,
    ): Response<ProgressResponse>
}