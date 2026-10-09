package com.synapse.social.studioasinc.data.repository

import com.synapse.social.studioasinc.domain.model.UserProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun testUserProfile_decodesRepresentativeSupabaseResponse() {
        val payload = """
            {
                "uid": "user_123",
                "username": "johndoe",
                "display_name": "John Doe",
                "email": "john@example.com",
                "bio": "Hello world",
                "avatar": "avatars/john.png",
                "verify": true,
                "is_private": false,
                "followers_count": 42,
                "following_count": 10,
                "posts_count": 5,
                "status": "ONLINE",
                "account_type": "user"
            }
        """.trimIndent()

        val profile = json.decodeFromString<UserProfile>(payload)

        assertEquals("user_123", profile.uid)
        assertEquals("johndoe", profile.username)
        assertEquals("John Doe", profile.displayName)
        assertEquals("avatars/john.png", profile.avatar)
        assertTrue(profile.verify)
        assertTrue(profile.isVerified)
        assertFalse(profile.isPrivate)
        assertEquals(42, profile.followersCount)
        assertEquals(10, profile.followingCount)
        assertEquals(5, profile.postsCount)
    }
}
