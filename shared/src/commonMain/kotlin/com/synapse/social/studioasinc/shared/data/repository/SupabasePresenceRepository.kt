package com.synapse.social.studioasinc.shared.data.repository

import com.synapse.social.studioasinc.shared.core.network.SupabaseClient
import com.synapse.social.studioasinc.shared.core.util.AppDispatchers
import com.synapse.social.studioasinc.shared.domain.repository.PresenceRepository
import io.github.aakira.napier.Napier
import io.github.jan.supabase.SupabaseClient as SupabaseClientLib
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabasePresenceRepository(
    private val client: SupabaseClientLib = SupabaseClient.client
) : PresenceRepository {
    
    private var heartbeatJob: Job? = null
    private var activeChatId: String? = null
    private val presenceChannel by lazy { client.realtime.channel("presence") }
    
    override suspend fun updatePresence(isOnline: Boolean, currentChatId: String?): Result<Unit> = runCatching {
        val session = client.auth.currentSessionOrNull() ?: run {
            Napier.w("Cannot update presence: No active session")
            return Result.failure(Exception("Not authenticated"))
        }
        val userId = session.user?.id?.toString() ?: run {
            Napier.w("Cannot update presence: No user ID in session")
            return Result.failure(Exception("No user ID"))
        }
        
        if (!isOnline) {
            activeChatId = null
        } else if (currentChatId != null) {
            activeChatId = currentChatId
        }
        val chatIdToSave = if (isOnline) (currentChatId ?: activeChatId) else null

        withContext(AppDispatchers.IO) {
            try {
                val now = Clock.System.now().toString()
                val statusStr = if (isOnline) "online" else "offline"
                client.postgrest.from("user_presence").upsert(
                    buildJsonObject {
                        put("user_id", userId)
                        put("status", statusStr)
                        put("last_seen", now)
                        put("updated_at", now)
                        put("current_chat_id", chatIdToSave)
                    }
                )
                Napier.d("✅ Presence updated: userId=$userId, isOnline=$isOnline, status=$statusStr, currentChatId=$chatIdToSave")
            } catch (e: Exception) {
                Napier.e("❌ Failed to update presence: ${e.message}", e)
                throw e
            }
        }
    }
    
    override suspend fun startPresenceTracking() {
        val session = client.auth.currentSessionOrNull() ?: run {
            Napier.w("⚠️ Cannot start presence tracking: No active session")
            return
        }
        val userId = session.user?.id?.toString() ?: run {
            Napier.w("⚠️ Cannot start presence tracking: No user ID")
            return
        }
        
        Napier.d("🟢 Starting presence tracking for user: $userId")
        
        // Initial presence update
        updatePresence(true).onSuccess {
            Napier.d("✅ Initial presence set to online")
        }.onFailure { 
            Napier.e("❌ Failed initial presence update", it)
            return // Don't start heartbeat if initial update fails
        }
        
        // Subscribe to realtime channel
        try {
            presenceChannel.subscribe(blockUntilSubscribed = true)
            presenceChannel.track(buildJsonObject {
                put("user_id", userId)
                put("online", true)
            })
            Napier.d("✅ Subscribed to presence channel")
        } catch (e: Exception) {
            Napier.e("❌ Failed to subscribe to presence channel", e)
        }
        
        // Start heartbeat
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(AppDispatchers.IO).launch {
            while (isActive) {
                delay(30_000) // 30s heartbeat
                updatePresence(true).onSuccess {
                    Napier.d("💓 Heartbeat: Presence updated")
                }.onFailure { 
                    Napier.e("❌ Heartbeat update failed", it)
                }
            }
        }
        Napier.d("✅ Presence heartbeat started (30s interval)")
    }
    
    override suspend fun stopPresenceTracking() {
        Napier.d("Stopping presence tracking")
        heartbeatJob?.cancel()
        activeChatId = null
        updatePresence(false).onFailure {
            Napier.e("Failed to update presence on stop", it)
        }
        try {
            presenceChannel.unsubscribe()
        } catch (e: Exception) {
            Napier.e("Failed to unsubscribe from presence channel", e)
        }
    }
    
    override fun observeUserPresence(userId: String): Flow<Boolean> {
        return kotlinx.coroutines.flow.flow {
            while (true) {
                val response = try {
                    client.postgrest.from("user_presence")
                        .select {
                            filter {
                                eq("user_id", userId)
                            }
                        }
                        .decodeSingleOrNull<UserPresenceDto>()
                } catch (e: Exception) {
                    Napier.e("❌ Error fetching presence for user $userId: ${e.message}", e)
                    throw e
                }

                val isActive = if (response == null) {
                    false
                } else {
                    val isStatusOnline = response.status == null || response.status.lowercase() == "online"
                    isStatusOnline && isWithinActiveWindow(response.lastSeen, windowMinutes = 2)
                }
                emit(isActive)
                delay(10_000) // Poll every 10 seconds
            }
        }
    }
    
    override suspend fun isUserInChat(userId: String, chatId: String): Boolean {
        val response = try {
            client.postgrest.from("user_presence")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<UserPresenceDto>()
        } catch (e: Exception) {
            Napier.e("❌ Error checking if user $userId is in chat $chatId: ${e.message}", e)
            throw e
        }

        if (response == null) return false

        val isStatusOnline = response.status == null || response.status.lowercase() == "online"
        val isOnline = isStatusOnline && isWithinActiveWindow(response.lastSeen, windowMinutes = 2)
        return response.currentChatId == chatId && isOnline
    }
    
    fun isWithinActiveWindow(lastSeen: String?, windowMinutes: Long = 5): Boolean {
        if (lastSeen == null) return false
        return try {
            val lastSeenInstant = kotlinx.datetime.Instant.parse(lastSeen)
            val now = Clock.System.now()
            val diff = now - lastSeenInstant
            diff.inWholeMinutes < windowMinutes
        } catch (e: Exception) {
            false
        }
    }
}

@Serializable
data class UserPresenceDto(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("current_chat_id") val currentChatId: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
