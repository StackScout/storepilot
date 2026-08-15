package com.storepilot.backend.coupon

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class CouponController(
    private val couponService: CouponService,
) {
    // --- Seller-scoped: store-specific coupons ---

    @GetMapping("/api/stores/{storeId}/coupons")
    fun listForStore(@PathVariable storeId: UUID): List<CouponResponse> = couponService.listForStore(storeId)

    @PostMapping("/api/stores/{storeId}/coupons")
    fun createForStore(@PathVariable storeId: UUID, @Valid @RequestBody input: CouponInput): ResponseEntity<CouponResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(couponService.createForStore(storeId, input))

    @PatchMapping("/api/coupons/{id}")
    fun updateForStore(@PathVariable id: UUID, @Valid @RequestBody input: CouponInput): CouponResponse =
        couponService.updateForStore(id, input)

    @DeleteMapping("/api/coupons/{id}")
    fun deleteForStore(@PathVariable id: UUID): ResponseEntity<Void> {
        couponService.deleteForStore(id)
        return ResponseEntity.noContent().build()
    }

    // --- Admin-scoped: platform-wide coupons (matched by SecurityConfig's /api/admin/** catch-all) ---

    @GetMapping("/api/admin/coupons")
    fun listPlatformWide(): List<CouponResponse> = couponService.listPlatformWide()

    @PostMapping("/api/admin/coupons")
    fun createPlatformWide(@Valid @RequestBody input: CouponInput): ResponseEntity<CouponResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(couponService.createPlatformWide(input))

    @PatchMapping("/api/admin/coupons/{id}")
    fun updatePlatformWide(@PathVariable id: UUID, @Valid @RequestBody input: CouponInput): CouponResponse =
        couponService.updatePlatformWide(id, input)

    @DeleteMapping("/api/admin/coupons/{id}")
    fun deletePlatformWide(@PathVariable id: UUID): ResponseEntity<Void> {
        couponService.deletePlatformWide(id)
        return ResponseEntity.noContent().build()
    }

    // --- Public: preview a coupon before checkout ---

    @PostMapping("/api/coupons/preview")
    fun preview(@Valid @RequestBody input: CouponPreviewInput): CouponPreviewResponse = couponService.preview(input)
}
