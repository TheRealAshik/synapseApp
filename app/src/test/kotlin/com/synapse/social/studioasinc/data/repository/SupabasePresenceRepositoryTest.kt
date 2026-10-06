package com.synapse.social.studioasinc.data.repository

import com.synapse.social.studioasinc.shared.data.repository.SupabasePresenceRepository
import com.synapse.social.studioasinc.shared.data.repository.UserPresenceDto
import io.github.jan.supabase.createSupabaseClient
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabasePresenceRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val fakeClient = createSupabaseClient("https://example.supabase.co", "fake-key") {}

    @Test
    fun testUserPresenceDtoDeserializationWithCanonicalStatus() {
        val jsonString = """
            {
                "user_id": "user-123",
                "status": "online",
                "last_seen": "2026-10-06T12:00:00Z",
                "current_chat_id": "chat-456",
                "updated_at": "2026-10-06T12:00:00Z"
            }
        """.trimIndent()

        val dto = json.decodeFromString<UserPresenceDto>(jsonString)

        assertEquals("user-123", dto.userId)
        assertEquals("online", dto.status)
        assertEquals("2026-10-06T12:00:00Z", dto.lastSeen)
        assertEquals("chat-456", dto.currentChatId)
        assertEquals("2026-10-06T12:00:00Z", dto.updatedAt)
    }

    @Test
    fun testUserPresenceDtoDeserializationWithoutOptionalFields() {
        val jsonString = """
            {
                "status": "offline"
            }
        """.trimIndent()

        val dto = json.decodeFromString<UserPresenceDto>(jsonString)

        assertEquals("offline", dto.status)
        assertNull(dto.userId)
        assertNull(dto.lastSeen)
        assertNull(dto.currentChatId)
        assertNull(dto.updatedAt)
    }

    @Test
    fun testIsWithinActiveWindowWithRecentTimestamp() {
        val repository = SupabasePresenceRepository(fakeClient)
        val recentTimestamp = Clock.System.now().toString()

        assertTrue(repository.isWithinActiveWindow(recentTimestamp, windowMinutes = 2))
    }

    @Test
    fun testIsWithinActiveWindowWithStaleTimestamp() {
        val repository = SupabasePresenceRepository(fakeClient)
        val staleTimestamp = Clock.System.now().minus(5, DateTimeUnit.MINUTE).toString()

        assertFalse(repository.isWithinActiveWindow(staleTimestamp, windowMinutes = 2))
    }

    @Test
    fun testIsWithinActiveWindowWithNullOrInvalidTimestamp() {
        val repository = SupabasePresenceRepository(fakeClient)

        assertFalse(repository.isWithinActiveWindow(null, windowMinutes = 2))
        assertFalse(repository.isWithinActiveWindow("invalid-date", windowMinutes = 2))
    }
}
