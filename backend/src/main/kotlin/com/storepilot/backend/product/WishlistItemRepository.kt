package com.storepilot.backend.product

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WishlistItemRepository : JpaRepository<WishlistItem, UUID> {
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: UUID): List<WishlistItem>

    fun findByBuyerIdAndProductId(buyerId: UUID, productId: UUID): WishlistItem?

    fun existsByBuyerIdAndProductId(buyerId: UUID, productId: UUID): Boolean
}
