package com.synapse.social.studioasinc.shared.data.local.database
import com.synapse.social.studioasinc.shared.core.util.AppDispatchers

import com.synapse.social.studioasinc.shared.data.database.CachedMessage
import com.synapse.social.studioasinc.shared.data.database.StorageDatabase
import com.synapse.social.studioasinc.shared.domain.model.chat.DeliveryStatus
import com.synapse.social.studioasinc.shared.domain.model.chat.Message
import com.synapse.social.studioasinc.shared.domain.model.chat.MessageType
import com.synapse.social.studioasinc.shared.data.dto.chat.MessageAttachmentDto
import com.synapse.social.studioasinc.shared.data.mapper.ChatMapper
import com.synapse.social.studioasinc.shared.domain.model.chat.MessageAttachment
import com.synapse.social.studioasinc.shared.domain.model.ReactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class SqlDelightCachedMessageDao(
    private val db: StorageDatabase
) : CachedMessageDao {

    override suspend fun upsertAll(messages: List<Message>) {
        withContext(AppDispatchers.IO) {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                messages.forEach { message ->
                    db.cachedMessageQueries.upsertMessage(toCachedMessage(message, now))
                }
            }
        }
    }

    override suspend fun getMessages(chatId: String, limit: Int): List<Message> = withContext(AppDispatchers.IO) {
        db.cachedMessageQueries.selectByChatId(
            chatId = chatId,
            limit = limit.toLong()
        ).executeAsList().map { cached ->
            val domain = toDomainMessage(cached)
            try {
                val reactions = db.messageReactionQueries.selectByMessageId(domain.id).executeAsList()
                val summary = reactions
                    .groupBy { it.reaction_emoji }
                    .mapKeys { ReactionType.values().find { rt -> rt.emoji == it.key } ?: ReactionType.LIKE }
                    .mapValues { it.value.size }
                domain.copy(reactions = summary)
            } catch (e: Exception) {
                domain
            }
        }
    }

    override suspend fun upsert(message: Message) {
        withContext(AppDispatchers.IO) {
            val now = Clock.System.now().toEpochMilliseconds()
            db.cachedMessageQueries.upsertMessage(toCachedMessage(message, now))
        }
    }

    override suspend fun updateContent(id: String, content: String) {
        withContext(AppDispatchers.IO) {
            db.cachedMessageQueries.updateContent(
                content = content,
                id = id
            )
        }
    }

    override suspend fun markDeleted(id: String) {
        withContext(AppDispatchers.IO) {
            db.cachedMessageQueries.markDeleted(id)
        }
    }

    override suspend fun markDeleted(ids: List<String>) {
        withContext(AppDispatchers.IO) {
            db.transaction {
                ids.forEach { id ->
                    db.cachedMessageQueries.markDeleted(id)
                }
            }
        }
    }

    override suspend fun trimToLimit(chatId: String, limit: Int) {
        withContext(AppDispatchers.IO) {
            db.cachedMessageQueries.deleteOldestBeyondLimit(
                chatId = chatId,
                limit = limit.toLong()
            )
        }
    }

    override suspend fun deleteAll() {
        withContext(AppDispatchers.IO) {
            db.cachedMessageQueries.deleteAll()
        }
    }

    override suspend fun updateReactions(messageId: String, reactions: Map<ReactionType, Int>, userReaction: ReactionType?) {
    }

    override suspend fun markRead(chatId: String, userId: String) {
        withContext(AppDispatchers.IO) {
            val allMessages = db.cachedMessageQueries.selectByChatId(chatId, 500).executeAsList()
            db.transaction {
                allMessages.forEach { cached ->
                    val readBy = cached.read_by?.split(",")?.toMutableSet() ?: mutableSetOf()
                    if (userId !in readBy) {
                        readBy.add(userId)
                        db.cachedMessageQueries.updateReadBy(
                            read_by = readBy.joinToString(","),
                            id = cached.id
                        )
                    }
                }
            }
        }
    }

    private fun toCachedMessage(domain: Message, cachedAt: Long): CachedMessage {
        val mediaUrlValue = if (domain.attachments.isNotEmpty()) {
            try {
                val dtoList = domain.attachments.map { with(ChatMapper) { it.toDto() } }
                Json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(MessageAttachmentDto.serializer()),
                    dtoList
                )
            } catch (e: Exception) {
                domain.mediaUrl
            }
        } else {
            domain.mediaUrl
        }

        return CachedMessage(
            id = domain.id,
            chat_id = domain.chatId,
            sender_id = domain.senderId,
            content = domain.content,
            message_type = domain.messageType.name,
            media_url = mediaUrlValue,
            delivery_status = domain.deliveryStatus.name,
            read_by = domain.readBy.joinToString(separator = ",").takeIf { it.isNotEmpty() },
            created_at = domain.createdAt,
            expires_at = domain.expiresAt,
            reply_to_id = domain.replyToId,
            is_deleted = if (domain.isDeleted) 1L else 0L,
            is_edited = if (domain.isEdited) 1L else 0L,
            cached_at = cachedAt
        )
    }

    private fun toDomainMessage(cached: CachedMessage): Message {
        val messageType = try {
            MessageType.valueOf(cached.message_type)
        } catch (e: Exception) {
            MessageType.TEXT // fallback
        }

        val deliveryStatus = try {
            DeliveryStatus.valueOf(cached.delivery_status)
        } catch (e: Exception) {
            DeliveryStatus.SENT // fallback
        }

        var attachmentsList = emptyList<MessageAttachment>()
        var resolvedMediaUrl = cached.media_url

        if (cached.media_url?.trimStart()?.startsWith("[") == true) {
            try {
                val dtos = Json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(MessageAttachmentDto.serializer()),
                    cached.media_url
                )
                attachmentsList = dtos.map { with(ChatMapper) { it.toDomain() } }
                resolvedMediaUrl = attachmentsList.firstOrNull()?.url
            } catch (e: Exception) {
                com.synapse.social.studioasinc.shared.util.Logger.e("Error deserializing attachments for cached message ${cached.id}", throwable = e)
            }
        }

        val finalMessageType = if (attachmentsList.size >= 2 || messageType == MessageType.MEDIA_GROUP) MessageType.MEDIA_GROUP else messageType

        return Message(
            id = cached.id,
            chatId = cached.chat_id,
            senderId = cached.sender_id,
            content = cached.content,
            messageType = finalMessageType,
            mediaUrl = resolvedMediaUrl,
            deliveryStatus = deliveryStatus,
            isDeleted = cached.is_deleted == 1L,
            isEdited = cached.is_edited == 1L,
            replyToId = cached.reply_to_id,
            createdAt = cached.created_at,
            expiresAt = cached.expires_at,
            readBy = cached.read_by?.split(",") ?: emptyList(),
            reactions = emptyMap(),
            userReaction = null,
            attachments = attachmentsList,
            mediaGroupId = attachmentsList.firstOrNull()?.mediaGroupId
        )
    }
}
