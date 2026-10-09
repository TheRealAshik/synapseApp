package com.synapse.social.studioasinc.shared.data.datasource

import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ChatMessageDataSourceTest {

    @Test
    fun testMarkMessagesAsDelivered_throwsNotAuthenticatedExceptionWhenUserNotLoggedIn() = runTest {
        val dummyClient = createSupabaseClient(
            supabaseUrl = "https://placeholder.supabase.co",
            supabaseKey = "placeholder-anon-key"
        ) {}

        val dataSource = ChatMessageDataSource(dummyClient)

        assertFailsWith<NotAuthenticatedException> {
            dataSource.markMessagesAsDelivered("chat_123")
        }
    }
}
