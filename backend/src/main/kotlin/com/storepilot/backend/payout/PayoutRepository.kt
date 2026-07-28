package com.storepilot.backend.payout

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PayoutRepository : JpaRepository<Payout, UUID> {
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<Payout>
}
