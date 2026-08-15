package com.storepilot.backend.buyer

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AddressRepository : JpaRepository<Address, UUID> {
    /** Default first (for prefilling checkout), then oldest-first among the rest. */
    fun findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyerId: UUID): List<Address>
}
