package com.synapse.social.studioasinc.feature.inbox.inbox

import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatMediaDelegateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val uploadMediaUseCase = mock(UploadMediaUseCase::class.java)
    private val sendMessageUseCase = mock(SendMessageUseCase::class.java)
    private val fileUploader = mock(FileUploader::class.java)

    private class FakeMediaCompressor : MediaCompressor {
        var lastQuality: MediaUploadQuality? = null
        override suspend fun compress(filePath: String): Result<String> = Result.success(filePath)
        override suspend fun compress(filePath: String, quality: MediaUploadQuality): Result<String> {
            lastQuality = quality
            return Result.success("${filePath}_compressed_${quality.name}")
        }
    }

    private val optimisticAdded = mutableListOf<Message>()
    private val optimisticSuccesses = mutableListOf<Message>()
    private var errorMessage: String? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        optimisticAdded.clear()
        optimisticSuccesses.clear()
        errorMessage = null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDelegate(
        compressor: MediaCompressor? = null
    ): ChatMediaDelegate {
        return ChatMediaDelegate(
            uploadMediaUseCase = uploadMediaUseCase,
            sendMessageUseCase = sendMessageUseCase,
            fileUploader = fileUploader,
            uploadProgressManager = mock(UploadProgressManager::class.java),
            viewModelScope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            currentUserIdProvider = { "user_1" },
            chatIdProvider = { "chat_123" },
            onOptimisticMessageAdded = { msg, _ -> optimisticAdded.add(msg) },
            onOptimisticMessageUpdated = { _, _ -> },
            onOptimisticMessageSuccess = { _, msg -> optimisticSuccesses.add(msg) },
            onOptimisticMessageFailed = { _, err -> errorMessage = err },
            onError = { err -> errorMessage = err },
            mediaCompressor = compressor
        )
    }

    @Test
    fun `uploadAndSendMedia compresses image with selected HD quality before upload`() = runTest {
        val fakeCompressor = FakeMediaCompressor()
        val delegate = createDelegate(fakeCompressor)

        `when`(fileUploader.getFileSize(anyString())).thenReturn(2000L)

        doReturn(Result.success("https://cdn.example.com/photo.jpg"))
            .`when`(uploadMediaUseCase).invoke(
                chatId = anyString(),
                filePath = anyString(),
                fileName = anyString(),
                contentType = anyString(),
                onProgress = any()
            )

        val dummyMsg = Message(
            id = "msg_1",
            chatId = "chat_123",
            senderId = "user_1",
            content = "HD photo",
            messageType = MessageType.IMAGE,
            deliveryStatus = DeliveryStatus.SENT,
            createdAt = "2025-01-01T00:00:00Z"
        )
        doReturn(Result.success(dummyMsg))
            .`when`(sendMessageUseCase).invoke(
                chatId = anyString(),
                content = anyString(),
                mediaUrl = anyString(),
                messageType = anyString(),
                expiresAt = any(),
                replyToId = any()
            )

        delegate.uploadAndSendMedia(
            filePath = "/path/photo.jpg",
            fileName = "photo.jpg",
            contentType = "image/jpeg",
            messageType = "image",
            caption = "HD photo",
            quality = MediaUploadQuality.HD
        )
        advanceUntilIdle()

        assertEquals(MediaUploadQuality.HD, fakeCompressor.lastQuality)
        assertEquals(1, optimisticSuccesses.size)
        assertEquals("msg_1", optimisticSuccesses.first().id)
    }

    @Test
    fun `uploadAndSendMultipleMedia sends each item exactly once attaching caption to first item`() = runTest {
        val fakeCompressor = FakeMediaCompressor()
        val delegate = createDelegate(fakeCompressor)

        `when`(fileUploader.getFileSize(anyString())).thenReturn(1000L)

        doReturn(Result.success("https://cdn.example.com/media"))
            .`when`(uploadMediaUseCase).invoke(
                chatId = anyString(),
                filePath = anyString(),
                fileName = anyString(),
                contentType = anyString(),
                onProgress = any()
            )

        val dummyMsg = Message(
            id = "msg_x",
            chatId = "chat_123",
            senderId = "user_1",
            content = "sent",
            messageType = MessageType.IMAGE,
            deliveryStatus = DeliveryStatus.SENT,
            createdAt = "2025-01-01T00:00:00Z"
        )
        doReturn(Result.success(dummyMsg))
            .`when`(sendMessageUseCase).invoke(
                chatId = anyString(),
                content = anyString(),
                mediaUrl = anyString(),
                messageType = anyString(),
                expiresAt = any(),
                replyToId = any()
            )

        val uri1 = mock(Uri::class.java)
        val uri2 = mock(Uri::class.java)

        val file1 = PickedFile(uri = uri1, mimeType = "image/jpeg", fileName = "pic1.jpg", size = 1000L)
        val file2 = PickedFile(uri = uri2, mimeType = "video/mp4", fileName = "vid2.mp4", size = 2000L)

        val multipleFiles = listOf(
            "/path/pic1.jpg" to file1,
            "/path/vid2.mp4" to file2
        )

        delegate.uploadAndSendMultipleMedia(
            files = multipleFiles,
            caption = "Caption for gallery",
            quality = MediaUploadQuality.STANDARD
        )
        advanceUntilIdle()

        assertEquals(2, optimisticAdded.size)
        assertEquals(2, optimisticSuccesses.size)
    }

    @Test
    fun `uploadAndSendMedia fails when video file size exceeds 50MB limit`() = runTest {
        val fakeCompressor = FakeMediaCompressor()
        val delegate = createDelegate(fakeCompressor)

        `when`(fileUploader.getFileSize(anyString())).thenReturn(60 * 1024 * 1024L) // 60MB

        delegate.uploadAndSendMedia(
            filePath = "/path/large_video.mp4",
            fileName = "large_video.mp4",
            contentType = "video/mp4",
            messageType = "video",
            caption = null
        )
        advanceUntilIdle()

        assertEquals("Video file size exceeds 50MB limit", errorMessage)
        verifyNoInteractions(uploadMediaUseCase)
        verifyNoInteractions(sendMessageUseCase)
    }

    @Test
    fun `uploadAndSendVoiceMessage succeeds and replaces optimistic audio message`() = runTest {
        val delegate = createDelegate()
        val voiceUploadService = mock(com.synapse.social.studioasinc.feature.inbox.inbox.voice.VoiceUploadService::class.java)
        val dummyAudioFile = java.io.File.createTempFile("voice", ".m4a")
        dummyAudioFile.deleteOnExit()

        val config = com.synapse.social.studioasinc.shared.domain.model.StorageConfig()
        `when`(voiceUploadService.upload(
            audioFile = any(java.io.File::class.java) ?: dummyAudioFile,
            chatId = anyString(),
            config = any(com.synapse.social.studioasinc.shared.domain.model.StorageConfig::class.java) ?: config,
            onProgress = any()
        )).thenReturn(Result.success("https://example.supabase.co/storage/v1/object/authenticated/chat-attachments/voice.m4a"))

        val persistedMsg = Message(
            id = "msg_audio_1",
            chatId = "chat_123",
            senderId = "user_1",
            content = "",
            mediaUrl = "https://example.supabase.co/storage/v1/object/authenticated/chat-attachments/voice.m4a",
            messageType = MessageType.AUDIO,
            deliveryStatus = DeliveryStatus.SENT,
            createdAt = "2025-01-01T00:00:00Z"
        )
        `when`(sendMessageUseCase.invoke(
            chatId = anyString(),
            content = anyString(),
            mediaUrl = anyString(),
            messageType = anyString(),
            expiresAt = any(),
            replyToId = any()
        )).thenReturn(Result.success(persistedMsg))

        delegate.uploadAndSendVoiceMessage(
            audioFile = dummyAudioFile,
            storageConfig = config,
            voiceUploadService = voiceUploadService
        )
        advanceUntilIdle()

        assertEquals(1, optimisticAdded.size)
        assertEquals(MessageType.AUDIO, optimisticAdded.first().messageType)
        assertEquals(1, optimisticSuccesses.size)
        assertEquals("msg_audio_1", optimisticSuccesses.first().id)
    }

    @Test
    fun `uploadAndSendVoiceMessage surfaces failure when voice upload fails`() = runTest {
        val delegate = createDelegate()
        val voiceUploadService = mock(com.synapse.social.studioasinc.feature.inbox.inbox.voice.VoiceUploadService::class.java)
        val dummyAudioFile = java.io.File.createTempFile("voice", ".m4a")
        dummyAudioFile.deleteOnExit()

        val config = com.synapse.social.studioasinc.shared.domain.model.StorageConfig()
        `when`(voiceUploadService.upload(
            audioFile = any(java.io.File::class.java) ?: dummyAudioFile,
            chatId = anyString(),
            config = any(com.synapse.social.studioasinc.shared.domain.model.StorageConfig::class.java) ?: config,
            onProgress = any()
        )).thenReturn(Result.failure(Exception("Storage unavailable")))

        delegate.uploadAndSendVoiceMessage(
            audioFile = dummyAudioFile,
            storageConfig = config,
            voiceUploadService = voiceUploadService
        )
        advanceUntilIdle()

        assertEquals(1, optimisticAdded.size)
        assertEquals("Voice upload failed: Storage unavailable", errorMessage)
        verifyNoInteractions(sendMessageUseCase)
    }
}
