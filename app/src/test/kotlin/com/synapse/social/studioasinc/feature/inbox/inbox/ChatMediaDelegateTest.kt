package com.synapse.social.studioasinc.feature.inbox.inbox

import android.net.Uri
import com.synapse.social.studioasinc.core.util.UploadProgressManager
import com.synapse.social.studioasinc.feature.shared.components.picker.PickedFile
import com.synapse.social.studioasinc.shared.domain.model.chat.AttachmentType
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
                mediaUrl = any(),
                messageType = anyString(),
                expiresAt = any(),
                replyToId = any(),
                attachments = any()
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
    fun `uploadAndSendMultipleMedia with single item delegates to single media message flow`() = runTest {
        val delegate = createDelegate()
        `when`(fileUploader.getFileSize(anyString())).thenReturn(1000L)

        doReturn(Result.success("https://cdn.example.com/single.jpg"))
            .`when`(uploadMediaUseCase).invoke(
                chatId = anyString(),
                filePath = anyString(),
                fileName = anyString(),
                contentType = anyString(),
                onProgress = any()
            )

        val dummyMsg = Message(
            id = "msg_single",
            chatId = "chat_123",
            senderId = "user_1",
            content = "Single photo",
            messageType = MessageType.IMAGE,
            deliveryStatus = DeliveryStatus.SENT,
            createdAt = "2025-01-01T00:00:00Z"
        )
        doReturn(Result.success(dummyMsg))
            .`when`(sendMessageUseCase).invoke(
                chatId = anyString(),
                content = anyString(),
                mediaUrl = any(),
                messageType = anyString(),
                expiresAt = any(),
                replyToId = any(),
                attachments = any()
            )

        val uri1 = mock(Uri::class.java)
        doReturn("content://media/single.jpg").`when`(uri1).toString()
        val file1 = PickedFile(uri = uri1, mimeType = "image/jpeg", fileName = "single.jpg", size = 1000L)

        delegate.uploadAndSendMultipleMedia(
            files = listOf("/path/single.jpg" to file1),
            caption = "Single photo"
        )
        advanceUntilIdle()

        assertEquals(1, optimisticAdded.size)
        assertEquals(MessageType.IMAGE, optimisticAdded.first().messageType)
        assertEquals(1, optimisticSuccesses.size)
    }

    @Test
    fun `uploadAndSendMultipleMedia with 2+ items creates unified MEDIA_GROUP message with attachments`() = runTest {
        val delegate = createDelegate()
        `when`(fileUploader.getFileSize(anyString())).thenReturn(1000L)

        doReturn(Result.success("https://cdn.example.com/media"))
            .`when`(uploadMediaUseCase).invoke(
                chatId = anyString(),
                filePath = anyString(),
                fileName = anyString(),
                contentType = anyString(),
                onProgress = any()
            )

        val dummyGroupMsg = Message(
            id = "msg_batch_1",
            chatId = "chat_123",
            senderId = "user_1",
            content = "Batch caption",
            messageType = MessageType.MEDIA_GROUP,
            deliveryStatus = DeliveryStatus.SENT,
            createdAt = "2025-01-01T00:00:00Z"
        )

        doReturn(Result.success(dummyGroupMsg))
            .`when`(sendMessageUseCase).invoke(
                chatId = anyString(),
                content = anyString(),
                mediaUrl = any(),
                messageType = anyString(),
                expiresAt = any(),
                replyToId = any(),
                attachments = any()
            )

        val uri1 = mock(Uri::class.java)
        doReturn("content://media/pic1.jpg").`when`(uri1).toString()
        val uri2 = mock(Uri::class.java)
        doReturn("content://media/vid2.mp4").`when`(uri2).toString()

        val file1 = PickedFile(uri = uri1, mimeType = "image/jpeg", fileName = "pic1.jpg", size = 1000L)
        val file2 = PickedFile(uri = uri2, mimeType = "video/mp4", fileName = "vid2.mp4", size = 2000L)

        delegate.uploadAndSendMultipleMedia(
            files = listOf(
                "/path/pic1.jpg" to file1,
                "/path/vid2.mp4" to file2
            ),
            caption = "Batch caption",
            quality = MediaUploadQuality.STANDARD
        )
        advanceUntilIdle()

        assertTrue(optimisticAdded.isNotEmpty())
        assertEquals(MessageType.MEDIA_GROUP, optimisticAdded.first().messageType)
        assertNotNull(optimisticAdded.first().mediaGroupId)
        assertEquals(2, optimisticAdded.first().attachments.size)

        assertEquals(1, optimisticSuccesses.size)
        assertEquals("msg_batch_1", optimisticSuccesses.first().id)
    }

    @Test
    fun `uploadAndSendMedia fails when video file size exceeds 50MB limit`() = runTest {
        val delegate = createDelegate()

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
}
