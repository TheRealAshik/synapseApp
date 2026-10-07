package com.synapse.social.studioasinc.shared.domain.model.chat

import com.synapse.social.studioasinc.shared.domain.model.ReactionType
import com.synapse.social.studioasinc.shared.domain.model.LinkPreview

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val messageType: MessageType,
    val mediaUrl: String? = null,
    val deliveryStatus: DeliveryStatus,
    val isDeleted: Boolean = false,
    val isEdited: Boolean = false,
    val replyToId: String? = null,
    val createdAt: String,
    val updatedAt: String? = null,
    val readBy: List<String> = emptyList(),
    val expiresAt: String? = null,
    val encryptionFailureReason: String? = null,
    val reactions: Map<ReactionType, Int> = emptyMap(),
    val userReaction: ReactionType? = null,
    val userReactions: Map<String, ReactionType> = emptyMap(),
    val linkPreview: LinkPreview? = null,
    val attachments: List<MessageAttachment> = emptyList(),
    val mediaGroupId: String? = null
) {
    fun isFromMe(currentUserId: String): Boolean = senderId == currentUserId

    fun withUserReactions(newUserReactions: Map<String, ReactionType>, currentUserId: String?): Message {
        val derivedCounts = newUserReactions.values.groupBy { it }.mapValues { it.value.size }
        val currentUserReaction = if (currentUserId != null) newUserReactions[currentUserId] else null
        return copy(
            reactions = derivedCounts,
            userReaction = currentUserReaction,
            userReactions = newUserReactions
        )
    }
}

enum class DeliveryStatus {
    SENT, DELIVERED, READ
}
