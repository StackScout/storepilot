package com.storepilot.backend.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WishlistItemRepository : JpaRepository<WishlistItem, UUID> {
    /** Unpaged — internal use only (e.g. BuyerAccountService's account-deletion sweep). GET /api/me/wishlist uses the paged overload below. */
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: UUID): List<WishlistItem>

    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: UUID, pageable: Pageable): Page<WishlistItem>

    fun findByBuyerIdAndProductId(buyerId: UUID, productId: UUID): WishlistItem?

    fun existsByBuyerIdAndProductId(buyerId: UUID, productId: UUID): Boolean
}
