package com.synapse.social.studioasinc.feature.inbox.inbox

import com.synapse.social.studioasinc.core.util.UploadProgressManager
import com.synapse.social.studioasinc.feature.shared.components.picker.PickedFile
import com.synapse.social.studioasinc.shared.domain.model.chat.DeliveryStatus
import com.synapse.social.studioasinc.shared.domain.model.chat.Message
import com.synapse.social.studioasinc.shared.domain.model.chat.MessageType
import com.synapse.social.studioasinc.shared.domain.model.settings.MediaUploadQuality
import com.synapse.social.studioasinc.shared.domain.repository.FileUploader
import com.synapse.social.studioasinc.shared.domain.service.MediaCompressor
import com.synapse.social.studioasinc.shared.domain.usecase.chat.SendMessageUseCase
import com.synapse.social.studioasinc.shared.domain.usecase.chat.UploadMediaUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class ChatMediaDelegate(
    private val uploadMediaUseCase: UploadMediaUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val fileUploader: FileUploader,
    private val uploadProgressManager: UploadProgressManager,
    private val viewModelScope: CoroutineScope,
    private val currentUserIdProvider: () -> String?,
    private val chatIdProvider: () -> String?,
    private val onOptimisticMessageAdded: (Message, String) -> Unit,
    private val onOptimisticMessageUpdated: (String, String) -> Unit,
    private val onOptimisticMessageSuccess: (String, Message) -> Unit,
    private val onOptimisticMessageFailed: (String, String?) -> Unit,
    private val onError: (String?) -> Unit,
    private val mediaCompressor: MediaCompressor? = null
) {

    fun uploadAndSendVoiceMessage(
        audioFile: java.io.File,
        storageConfig: com.synapse.social.studioasinc.shared.domain.model.StorageConfig,
        voiceUploadService: com.synapse.social.studioasinc.feature.inbox.inbox.voice.VoiceUploadService,
        sendMessageUseCase: com.synapse.social.studioasinc.shared.domain.usecase.chat.SendMessageUseCase = this.sendMessageUseCase,
        currentUserIdProvider: () -> String? = this.currentUserIdProvider,
        chatIdProvider: () -> String? = this.chatIdProvider,
        viewModelScope: kotlinx.coroutines.CoroutineScope = this.viewModelScope
    ) {
        val chatId = chatIdProvider() ?: return
        val currentUserId = currentUserIdProvider() ?: return

        viewModelScope.launch {
            // Optimistic message
            val tempId = UUID.randomUUID().toString()
            val optimisticMessage = Message(
                id = tempId,
                chatId = chatId,
                senderId = currentUserId,
                content = "Sending voice message...",
                messageType = MessageType.AUDIO,
                deliveryStatus = DeliveryStatus.SENT,
                createdAt = Instant.now().toString()
            )
            onOptimisticMessageAdded(optimisticMessage, tempId)

            val uploadResult = voiceUploadService.upload(
                audioFile = audioFile,
                chatId = chatId,
                config = storageConfig
            )

            uploadResult.onSuccess { mediaUrl ->
                if (coroutineContext[Job]?.isActive == false) return@onSuccess
                sendMessageUseCase(
                    chatId = chatId,
                    content = "", // Voice messages have no text content
                    mediaUrl = mediaUrl,
                    messageType = MessageType.AUDIO.name.lowercase()
                ).onSuccess { actualMessage ->
                    onOptimisticMessageSuccess(tempId, actualMessage)
                }.onFailure { e ->
                    onOptimisticMessageFailed(tempId, "Failed to send: ${e.message}")
                }
            }.onFailure { e ->
                onOptimisticMessageFailed(tempId, "Voice upload failed: ${e.message}")
            }
        }
    }

    fun sendMediaMessage(
        mediaUrl: String,
        fileName: String,
        contentType: String,
        messageType: String,
        caption: String? = null
    ) {
        val chatId = chatIdProvider() ?: return
        viewModelScope.launch {
            val tempId = UUID.randomUUID().toString()
            val type = when (messageType) {
                "image" -> MessageType.IMAGE
                "video" -> MessageType.VIDEO
                "audio" -> MessageType.AUDIO
                else -> MessageType.FILE
            }
            val newMessage = Message(
                id = tempId,
                chatId = chatId,
                senderId = currentUserIdProvider() ?: "",
                content = "Sending...",
                messageType = type,
                deliveryStatus = DeliveryStatus.SENT,
                createdAt = Instant.now().toString()
            )
            onOptimisticMessageAdded(newMessage, tempId)
            sendMessageUseCase(
                chatId = chatId,
                content = if (!caption.isNullOrBlank()) caption else fileName,
                mediaUrl = mediaUrl,
                messageType = messageType
            ).onSuccess { actualMessage ->
                onOptimisticMessageSuccess(tempId, actualMessage)
            }.onFailure { e ->
                onOptimisticMessageFailed(tempId, "Failed to send: ${e.message}")
            }
        }
    }

    fun uploadAndSendMedia(
        filePath: String,
        fileName: String,
        contentType: String,
        messageType: String,
        caption: String? = null,
        quality: MediaUploadQuality = MediaUploadQuality.STANDARD
    ) {
        val chatId = chatIdProvider() ?: return

        viewModelScope.launch {
            var uploadPath = filePath
            if (messageType == "image" && mediaCompressor != null) {
                mediaCompressor.compress(filePath, quality).onSuccess { compressedPath ->
                    uploadPath = compressedPath
                }
            }

            val fileSize = fileUploader.getFileSize(uploadPath)
            val maxVideoSize = 50 * 1024 * 1024L // 50MB
            val maxImageSize = 10 * 1024 * 1024L // 10MB

            if (messageType == "video" && fileSize > maxVideoSize) {
                onError("Video file size exceeds 50MB limit")
                return@launch
            }
            if (messageType == "image" && fileSize > maxImageSize) {
                onError("Image file size exceeds 10MB limit")
                return@launch
            }

            // Optimistic update
            val tempId = UUID.randomUUID().toString()

            val type = when(messageType) {
                "image" -> MessageType.IMAGE
                "video" -> MessageType.VIDEO
                "audio" -> MessageType.AUDIO
                else -> MessageType.FILE
            }
            val newMessage = Message(
                id = tempId,
                chatId = chatId,
                senderId = currentUserIdProvider() ?: "",
                content = "Uploading...",
                messageType = type,
                deliveryStatus = DeliveryStatus.SENT,
                createdAt = Instant.now().toString()
            )
            onOptimisticMessageAdded(newMessage, tempId)

            uploadMediaUseCase(
                chatId = chatId,
                filePath = uploadPath,
                fileName = fileName,
                contentType = contentType,
                onProgress = { progress ->
                    uploadProgressManager.updateProgress(chatId, fileName, progress)
                    onOptimisticMessageUpdated(tempId, "Uploading... $progress%")
                }
            ).onSuccess { mediaUrl ->
                val finalContent = if (!caption.isNullOrBlank()) caption else if (messageType == "image" || messageType == "video") "Media message" else fileName
                sendMessageUseCase(
                    chatId = chatId,
                    content = finalContent,
                    mediaUrl = mediaUrl,
                    messageType = messageType
                ).onSuccess { actualMessage ->
                    onOptimisticMessageSuccess(tempId, actualMessage)
                    uploadProgressManager.dismissProgress(chatId, fileName)
                }.onFailure { e ->
                    uploadProgressManager.finishProgress(chatId, fileName, false, "Upload Failed")
                    onOptimisticMessageFailed(tempId, "Failed to send: ${e.message}")
                }
            }.onFailure { e ->
                uploadProgressManager.finishProgress(chatId, fileName, false, "Upload Failed")
                onOptimisticMessageFailed(tempId, "Upload failed: ${e.message}")
            }
        }
    }

    fun uploadAndSendMultipleMedia(
        files: List<Pair<String, PickedFile>>,
        caption: String? = null,
        quality: MediaUploadQuality = MediaUploadQuality.STANDARD
    ) {
        files.forEachIndexed { index, (filePath, pickedFile) ->
            val type = when {
                pickedFile.mimeType.startsWith("image/") -> "image"
                pickedFile.mimeType.startsWith("video/") -> "video"
                pickedFile.mimeType.startsWith("audio/") -> "audio"
                else -> "file"
            }
            val itemCaption = if (index == 0) caption else null
            uploadAndSendMedia(
                filePath = filePath,
                fileName = pickedFile.fileName,
                contentType = pickedFile.mimeType,
                messageType = type,
                caption = itemCaption,
                quality = quality
            )
        }
    }
}
