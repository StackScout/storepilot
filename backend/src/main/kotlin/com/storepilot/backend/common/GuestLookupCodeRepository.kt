package com.storepilot.backend.common

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GuestLookupCodeRepository : JpaRepository<GuestLookupCode, UUID> {
    fun findByTargetTypeAndTargetId(targetType: String, targetId: UUID): GuestLookupCode?
}
