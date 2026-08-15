package com.storepilot.backend.messaging

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MessageRepository : JpaRepository<Message, UUID> {
    fun findByConversationIdOrderByCreatedAtAsc(conversationId: UUID): List<Message>
}
