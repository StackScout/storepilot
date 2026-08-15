package com.storepilot.backend.buyer

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SavedSearchRepository : JpaRepository<SavedSearch, UUID> {
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: UUID): List<SavedSearch>
}
