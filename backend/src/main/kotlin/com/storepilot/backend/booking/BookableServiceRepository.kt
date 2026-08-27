package com.storepilot.backend.booking

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BookableServiceRepository : JpaRepository<BookableService, UUID> {
    fun findByStoreIdOrderByUpdatedAtDesc(storeId: UUID): List<BookableService>

    /** Public/non-owner view of a store's services — see BookableServiceService.listByStore. */
    fun findByStoreIdAndStatusNotOrderByUpdatedAtDesc(storeId: UUID, status: ServiceStatus): List<BookableService>

    fun findByStoreIdAndSlug(storeId: UUID, slug: String): BookableService?

    /** Used by store-page-content to decide whether the Services section has anything to show — see docs/features/bookings.md's derived-3-mode-UI rule. */
    fun existsByStoreIdAndStatus(storeId: UUID, status: ServiceStatus): Boolean

    /** Guards CategoryController's delete — see its doc comment. */
    fun existsByCategory(category: String): Boolean
}
