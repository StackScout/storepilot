package com.storepilot.backend.messaging

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConversationRepository : JpaRepository<Conversation, UUID> {
    fun findByStoreIdAndBuyerId(storeId: UUID, buyerId: UUID): Conversation?

    fun findByBuyerIdOrderByLastMessageAtDesc(buyerId: UUID): List<Conversation>

    fun findByStoreIdOrderByLastMessageAtDesc(storeId: UUID): List<Conversation>
}
