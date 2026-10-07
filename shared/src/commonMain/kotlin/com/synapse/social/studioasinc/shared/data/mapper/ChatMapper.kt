package com.synapse.social.studioasinc.shared.data.mapper

import com.synapse.social.studioasinc.shared.data.dto.chat.ChatDto
import com.synapse.social.studioasinc.shared.data.dto.chat.MessageAttachmentDto
import com.synapse.social.studioasinc.shared.data.dto.chat.MessageDto
import com.synapse.social.studioasinc.shared.domain.model.chat.AttachmentType
import com.synapse.social.studioasinc.shared.domain.model.chat.ChatInfo
import com.synapse.social.studioasinc.shared.domain.model.chat.DeliveryStatus
import com.synapse.social.studioasinc.shared.domain.model.chat.Message
import com.synapse.social.studioasinc.shared.domain.model.chat.MessageAttachment
import com.synapse.social.studioasinc.shared.domain.model.chat.MessageType

object ChatMapper {
    fun ChatDto.toDomain(): ChatInfo = ChatInfo(
        id = id,
        name = name,
        description = description,
        avatarUrl = avatarUrl,
        isGroup = isGroup,
        createdBy = createdBy,
        onlyAdminsCanMessage = onlyAdminsCanMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        disappearingMode = try {
            disappearingMode?.let { com.synapse.social.studioasinc.shared.domain.model.chat.DisappearingMode.valueOf(it) }
                ?: com.synapse.social.studioasinc.shared.domain.model.chat.DisappearingMode.OFF
        } catch (e: Exception) {
            com.synapse.social.studioasinc.shared.domain.model.chat.DisappearingMode.OFF
        }
    )

    fun MessageAttachmentDto.toDomain(): MessageAttachment = MessageAttachment(
        url = url,
        type = when (type.lowercase()) {
            "video" -> AttachmentType.VIDEO
            "audio" -> AttachmentType.AUDIO
            "file" -> AttachmentType.FILE
            else -> AttachmentType.IMAGE
        },
        size = size,
        duration = duration,
        mediaGroupId = mediaGroupId
    )

    fun MessageAttachment.toDto(): MessageAttachmentDto = MessageAttachmentDto(
        url = url,
        type = type.name.lowercase(),
        size = size,
        duration = duration,
        mediaGroupId = mediaGroupId
    )

    fun MessageDto.toDomain(): Message {
        val domainAttachments = attachments?.map { it.toDomain() } ?: emptyList()
        val derivedGroupId = domainAttachments.firstOrNull()?.mediaGroupId
        val baseType = when (messageType) {
            "image" -> MessageType.IMAGE
            "video" -> MessageType.VIDEO
            "audio" -> MessageType.AUDIO
            "media_group" -> MessageType.MEDIA_GROUP
            else -> MessageType.TEXT
        }
        val finalType = if (domainAttachments.size >= 2 || baseType == MessageType.MEDIA_GROUP) MessageType.MEDIA_GROUP else baseType

        return Message(
            id = id ?: "",
            chatId = chatId,
            senderId = senderId,
            content = content,
            messageType = finalType,
            mediaUrl = mediaUrl ?: domainAttachments.firstOrNull()?.url,
            deliveryStatus = when {
                readBy?.isNotEmpty() == true -> DeliveryStatus.READ
                deliveryStatus == "delivered" -> DeliveryStatus.DELIVERED
                else -> DeliveryStatus.SENT
            },
            isDeleted = isDeleted,
            isEdited = isEdited,
            replyToId = replyToId,
            createdAt = createdAt ?: "",
            updatedAt = updatedAt,
            readBy = readBy ?: emptyList(),
            expiresAt = expiresAt,
            encryptionFailureReason = encryptionFailureReason,
            attachments = domainAttachments,
            mediaGroupId = derivedGroupId
        )
    }
}
