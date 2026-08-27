package com.storepilot.backend.messaging

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MessageRepository : JpaRepository<Message, UUID> {
    /** Unpaged — internal cross-service use (BuyerExportService's full data-export bundle). GET /api/conversations/{id}/messages uses the paged overload below. */
    fun findByConversationIdOrderByCreatedAtAsc(conversationId: UUID): List<Message>

    fun findByConversationIdOrderByCreatedAtAsc(conversationId: UUID, pageable: Pageable): Page<Message>
}
