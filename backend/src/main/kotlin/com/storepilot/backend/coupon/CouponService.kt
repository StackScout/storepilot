package com.storepilot.backend.coupon

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.toPageResponse
import com.storepilot.backend.common.wireValueOf
import com.storepilot.backend.store.StoreAccessService
import com.storepilot.backend.store.StoreRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/** Hard cap regardless of what a caller requests via `size` — same convention as ProductService/StoreService's own MAX_PAGE_SIZE. */
private const val MAX_PAGE_SIZE = 100

data class CouponResolution(val couponId: UUID, val code: String, val discountAmount: Int)

/**
 * Owns coupon CRUD (dual-scoped: store-specific via the seller-facing
 * methods, platform-wide via the admin-facing ones — see Coupon's doc
 * comment) plus the shared validate-and-compute-discount logic used by both
 * the public preview endpoint and the real order/booking checkout paths.
 * Codes are always stored/looked-up uppercased so "save10" and "SAVE10"
 * collide as the same coupon — same normalize-on-write principle as
 * OrderService's order-number generation, just applied to a user-entered
 * value instead of a generated one.
 */
@Service
@Transactional(readOnly = true)
class CouponService(
    private val couponRepository: CouponRepository,
    private val storeRepository: StoreRepository,
    private val currentActor: CurrentActor,
    private val storeAccessService: StoreAccessService,
) {
    // --- Seller-scoped (store-specific coupons) ---

    /** Unpaged — internal cross-service use (e.g. SellerExportService's full data-export bundle). GET /api/stores/{storeId}/coupons uses the paged overload below. */
    fun listForStore(storeId: UUID): List<CouponResponse> {
        requireOwnedStore(storeId)
        return couponRepository.findByStoreIdOrderByCreatedAtDesc(storeId).map { it.toResponse() }
    }

    /** Paged sibling of the above — GET /api/stores/{storeId}/coupons itself. */
    fun listForStore(storeId: UUID, page: Int, size: Int): PageResponse<CouponResponse> {
        requireOwnedStore(storeId)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return couponRepository.findByStoreIdOrderByCreatedAtDesc(storeId, pageable).toPageResponse { it.toResponse() }
    }

    @Transactional
    fun createForStore(storeId: UUID, input: CouponInput): CouponResponse {
        val store = requireOwnedStore(storeId)
        val coupon = Coupon(
            code = normalizeCode(input.code),
            store = store,
            discountType = wireValueOf<DiscountType>(input.discountType),
            discountValue = input.discountValue,
            appliesToOrders = input.appliesToOrders,
            appliesToBookings = input.appliesToBookings,
            maxUses = input.maxUses,
            minSubtotal = input.minSubtotal,
            expiresAt = input.expiresAt,
            active = input.active,
        )
        return save(coupon).toResponse()
    }

    @Transactional
    fun updateForStore(id: UUID, input: CouponInput): CouponResponse {
        val coupon = requireCoupon(id)
        val storeId = coupon.store?.id ?: throw NotFoundException("Coupon $id not found")
        requireOwnedStore(storeId)
        applyInput(coupon, input)
        return save(coupon).toResponse()
    }

    @Transactional
    fun deleteForStore(id: UUID) {
        val coupon = requireCoupon(id)
        val storeId = coupon.store?.id ?: throw NotFoundException("Coupon $id not found")
        requireOwnedStore(storeId)
        couponRepository.delete(coupon)
    }

    // --- Admin-scoped (platform-wide coupons) ---

    fun listPlatformWide(page: Int, size: Int): PageResponse<CouponResponse> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return couponRepository.findByStoreIdIsNullOrderByCreatedAtDesc(pageable).toPageResponse { it.toResponse() }
    }

    @Transactional
    fun createPlatformWide(input: CouponInput): CouponResponse {
        currentActor.requireAdmin()
        val coupon = Coupon(
            code = normalizeCode(input.code),
            store = null,
            discountType = wireValueOf<DiscountType>(input.discountType),
            discountValue = input.discountValue,
            appliesToOrders = input.appliesToOrders,
            appliesToBookings = input.appliesToBookings,
            maxUses = input.maxUses,
            minSubtotal = input.minSubtotal,
            expiresAt = input.expiresAt,
            active = input.active,
        )
        return save(coupon).toResponse()
    }

    @Transactional
    fun updatePlatformWide(id: UUID, input: CouponInput): CouponResponse {
        currentActor.requireAdmin()
        val coupon = requireCoupon(id)
        if (coupon.store != null) throw NotFoundException("Coupon $id not found")
        applyInput(coupon, input)
        return save(coupon).toResponse()
    }

    @Transactional
    fun deletePlatformWide(id: UUID) {
        currentActor.requireAdmin()
        val coupon = requireCoupon(id)
        if (coupon.store != null) throw NotFoundException("Coupon $id not found")
        couponRepository.delete(coupon)
    }

    // --- Validate + apply (used by the public preview endpoint and by OrderService/BookingService at checkout) ---

    /** POST /api/coupons/preview — dry run, never records a use. */
    fun preview(input: CouponPreviewInput): CouponPreviewResponse =
        try {
            val resolution = resolve(input.code, input.storeId, wireValueOf<CouponKind>(input.kind), input.amount)
            CouponPreviewResponse(valid = true, discountAmount = resolution.discountAmount, message = null)
        } catch (e: ConflictException) {
            CouponPreviewResponse(valid = false, discountAmount = 0, message = e.message)
        }

    /**
     * Validates [code] against [storeId]/[kind]/[amount] and computes the
     * discount, without recording a use — callers that actually complete a
     * checkout must follow a successful resolve() with recordUse() inside
     * the same transaction as the order/booking write. Read-only; safe to
     * call from inside an already-open writable transaction (OrderService/
     * BookingService's createOrder/createBooking), since propagation joins
     * the existing transaction rather than starting a new read-only one.
     */
    fun resolve(code: String, storeId: UUID, kind: CouponKind, amount: Int): CouponResolution {
        val coupon = couponRepository.findByCode(normalizeCode(code)) ?: throw ConflictException("Invalid coupon code")
        if (!coupon.active) throw ConflictException("Invalid coupon code")
        val expiresAt = coupon.expiresAt
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) throw ConflictException("This coupon has expired")
        val maxUses = coupon.maxUses
        if (maxUses != null && coupon.usedCount >= maxUses) throw ConflictException("This coupon has reached its usage limit")
        val couponStoreId = coupon.store?.id
        if (couponStoreId != null && couponStoreId != storeId) throw ConflictException("This coupon isn't valid for this store")
        val appliesToKind = if (kind == CouponKind.ORDER) coupon.appliesToOrders else coupon.appliesToBookings
        if (!appliesToKind) {
            throw ConflictException("This coupon can't be used for ${if (kind == CouponKind.ORDER) "product orders" else "bookings"}")
        }
        if (amount < coupon.minSubtotal) throw ConflictException("This coupon needs a minimum spend to apply")

        val discount = when (coupon.discountType) {
            DiscountType.PERCENT -> (BigDecimal(amount) * BigDecimal(coupon.discountValue))
                .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
                .toInt()
            DiscountType.FIXED -> coupon.discountValue
        }.coerceIn(0, amount)

        return CouponResolution(couponId = requireNotNull(coupon.id), code = coupon.code, discountAmount = discount)
    }

    /** Must be called inside the same transaction as the order/booking write it discounted — see resolve()'s doc comment. */
    @Transactional
    fun recordUse(couponId: UUID) {
        val coupon = couponRepository.findById(couponId).orElseThrow { NotFoundException("Coupon $couponId not found") }
        coupon.usedCount += 1
        couponRepository.save(coupon)
    }

    // --- Shared helpers ---

    private fun applyInput(coupon: Coupon, input: CouponInput) {
        coupon.code = normalizeCode(input.code)
        coupon.discountType = wireValueOf<DiscountType>(input.discountType)
        coupon.discountValue = input.discountValue
        coupon.appliesToOrders = input.appliesToOrders
        coupon.appliesToBookings = input.appliesToBookings
        coupon.maxUses = input.maxUses
        coupon.minSubtotal = input.minSubtotal
        coupon.expiresAt = input.expiresAt
        coupon.active = input.active
    }

    private fun save(coupon: Coupon): Coupon =
        try {
            couponRepository.save(coupon)
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            throw ConflictException("Coupon code \"${coupon.code}\" is already in use")
        }

    private fun normalizeCode(code: String): String = code.trim().uppercase()

    private fun requireCoupon(id: UUID): Coupon = couponRepository.findById(id).orElseThrow { NotFoundException("Coupon $id not found") }

    private fun requireOwnedStore(storeId: UUID) = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        .let { storeAccessService.requireOperationalAccess(it) }
}
