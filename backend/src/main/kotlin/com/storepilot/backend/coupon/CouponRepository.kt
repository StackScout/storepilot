package com.storepilot.backend.coupon

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CouponRepository : JpaRepository<Coupon, UUID> {
    /** [code] must already be uppercased by the caller — see CouponService's normalize-on-write/read convention. */
    fun findByCode(code: String): Coupon?

    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<Coupon>

    fun findByStoreIdIsNullOrderByCreatedAtDesc(): List<Coupon>
}
