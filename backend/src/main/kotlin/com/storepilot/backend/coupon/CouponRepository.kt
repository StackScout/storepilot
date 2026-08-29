package com.storepilot.backend.coupon

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CouponRepository : JpaRepository<Coupon, UUID> {
    /** [code] must already be uppercased by the caller — see CouponService's normalize-on-write/read convention. */
    fun findByCode(code: String): Coupon?

    /** Unpaged — internal cross-service use (e.g. SellerExportService's full data-export bundle). GET /api/stores/{storeId}/coupons uses the paged overload below. */
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<Coupon>

    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID, pageable: Pageable): Page<Coupon>

    fun findByStoreIdIsNullOrderByCreatedAtDesc(pageable: Pageable): Page<Coupon>
}
