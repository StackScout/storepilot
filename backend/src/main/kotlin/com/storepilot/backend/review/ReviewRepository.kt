package com.storepilot.backend.review

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReviewRepository : JpaRepository<Review, UUID> {
    fun findByProductIdOrderByCreatedAtDesc(productId: UUID, pageable: Pageable): Page<Review>

    fun findByStoreIdAndProductIdIsNullOrderByCreatedAtDesc(storeId: UUID, pageable: Pageable): Page<Review>

    /** Buyer data export — every review this buyer has written, product or store-level alike. */
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: UUID): List<Review>

    /** Seller data export — every review of this store, product or store-level alike (unlike findByStoreIdAndProductIdIsNullOrderByCreatedAtDesc, which is store-level reviews only). */
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<Review>

    fun existsByBuyerIdAndProductId(buyerId: UUID, productId: UUID): Boolean

    fun existsByBuyerIdAndStoreIdAndProductIdIsNull(buyerId: UUID, storeId: UUID): Boolean
}
