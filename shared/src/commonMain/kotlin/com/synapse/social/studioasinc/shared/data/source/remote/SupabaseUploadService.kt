package com.synapse.social.studioasinc.shared.data.source.remote

import com.synapse.social.studioasinc.shared.domain.model.StorageConfig
import com.synapse.social.studioasinc.shared.domain.model.UploadError
import com.synapse.social.studioasinc.shared.domain.model.UploadResult
import com.synapse.social.studioasinc.shared.util.TimeProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.github.aakira.napier.Napier
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.coroutineScope

class SupabaseUploadService(private val supabase: SupabaseClient) : UploadService {
    override suspend fun upload(
        fileProvider: suspend (Long) -> ByteReadChannel,
        fileSize: Long,
        fileName: String,
        config: StorageConfig,
        bucketName: String?,
        onProgress: (Float) -> Unit
    ): UploadResult {
        val targetBucket = bucketName.orEmpty()
            .ifBlank { config.supabaseBucket }
            .ifBlank { com.synapse.social.studioasinc.shared.core.network.SupabaseClient.BUCKET_POST_MEDIA }

        Napier.d("Uploading to Supabase bucket: $targetBucket, file: $fileName", tag = "SupabaseUpload")

        try {
            val bucket = supabase.storage.from(targetBucket)
            val path = "${TimeProvider.nowMillis()}_$fileName"

            coroutineScope {
                val channel = fileProvider(0L)
                val bytes = channel.readRemaining().readBytes()
                bucket.upload(path, bytes) {
                    this.upsert = false
                }
                onProgress(1.0f)
            }

            val url = if (targetBucket == "chat-attachments") {
                bucket.authenticatedUrl(path)
            } else {
                bucket.publicUrl(path)
            }
            Napier.d("Supabase upload successful: $url", tag = "SupabaseUpload")
            return UploadResult(url = url, deleteUrl = null)
        } catch (e: Exception) {
            Napier.e("Supabase upload failed to bucket: $targetBucket", e, tag = "SupabaseUpload")
            val fallbackBucket = config.supabaseBucket
                .ifBlank { com.synapse.social.studioasinc.shared.core.network.SupabaseClient.BUCKET_POST_MEDIA }

            if (targetBucket != fallbackBucket) {
                try {
                    Napier.d("Retrying Supabase upload to fallback bucket: $fallbackBucket", tag = "SupabaseUpload")
                    val bucket = supabase.storage.from(fallbackBucket)
                    val path = "${TimeProvider.nowMillis()}_$fileName"

                    coroutineScope {
                        val channel = fileProvider(0L)
                        val bytes = channel.readRemaining().readBytes()
                        bucket.upload(path, bytes) {
                            this.upsert = false
                        }
                        onProgress(1.0f)
                    }

                    val url = bucket.publicUrl(path)
                    Napier.d("Supabase fallback upload successful: $url", tag = "SupabaseUpload")
                    return UploadResult(url = url, deleteUrl = null)
                } catch (fallbackEx: Exception) {
                    Napier.e("Supabase fallback upload failed to bucket: $fallbackBucket", fallbackEx, tag = "SupabaseUpload")
                }
            }

            throw UploadError.SupabaseError("Supabase upload failed: ${e.message ?: e.toString()}")
        }
    }
}
