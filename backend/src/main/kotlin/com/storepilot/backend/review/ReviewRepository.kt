package com.storepilot.backend.review

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReviewRepository : JpaRepository<Review, UUID> {
    fun findByProductIdOrderByCreatedAtDesc(productId: UUID): List<Review>

    fun findByStoreIdAndProductIdIsNullOrderByCreatedAtDesc(storeId: UUID): List<Review>

    fun existsByBuyerIdAndProductId(buyerId: UUID, productId: UUID): Boolean

    fun existsByBuyerIdAndStoreIdAndProductIdIsNull(buyerId: UUID, storeId: UUID): Boolean
}
