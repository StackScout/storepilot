package com.storepilot.backend.messaging

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConversationRepository : JpaRepository<Conversation, UUID> {
    fun findByStoreIdAndBuyerId(storeId: UUID, buyerId: UUID): Conversation?

    /** Unpaged — internal cross-service use (BuyerExportService's full data-export bundle). GET /api/me/conversations uses the paged overload below. */
    fun findByBuyerIdOrderByLastMessageAtDesc(buyerId: UUID): List<Conversation>

    fun findByBuyerIdOrderByLastMessageAtDesc(buyerId: UUID, pageable: Pageable): Page<Conversation>

    fun findByStoreIdOrderByLastMessageAtDesc(storeId: UUID, pageable: Pageable): Page<Conversation>
}
