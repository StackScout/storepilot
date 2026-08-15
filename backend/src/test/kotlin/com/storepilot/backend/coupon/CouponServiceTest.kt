package com.storepilot.backend.coupon

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CouponServiceTest {
    private val couponRepository = mockk<CouponRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val currentActor = mockk<CurrentActor>()

    private val service = CouponService(couponRepository, storeRepository, currentActor)

    private val storeId: UUID = UUID.randomUUID()
    private val otherStoreId: UUID = UUID.randomUUID()

    private fun coupon(
        code: String = "SAVE10",
        storeScoped: Boolean = false,
        discountType: DiscountType = DiscountType.PERCENT,
        discountValue: Int = 10,
        appliesToOrders: Boolean = true,
        appliesToBookings: Boolean = true,
        maxUses: Int? = null,
        usedCount: Int = 0,
        minSubtotal: Int = 0,
        expiresAt: Instant? = null,
        active: Boolean = true,
    ): Coupon {
        val store = if (storeScoped) mockk<Store> { every { id } returns storeId } else null
        return Coupon(
            code = code,
            store = store,
            discountType = discountType,
            discountValue = discountValue,
            appliesToOrders = appliesToOrders,
            appliesToBookings = appliesToBookings,
            maxUses = maxUses,
            usedCount = usedCount,
            minSubtotal = minSubtotal,
            expiresAt = expiresAt,
            active = active,
        ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
    }

    @Test
    fun `resolve computes a percent discount, HALF_UP rounded`() {
        every { couponRepository.findByCode("SAVE10") } returns coupon(discountType = DiscountType.PERCENT, discountValue = 15)
        val resolution = service.resolve("save10", storeId, CouponKind.ORDER, 1000)
        assertEquals(150, resolution.discountAmount)
    }

    @Test
    fun `resolve computes a fixed discount`() {
        every { couponRepository.findByCode("FLAT500") } returns coupon(code = "FLAT500", discountType = DiscountType.FIXED, discountValue = 500)
        val resolution = service.resolve("FLAT500", storeId, CouponKind.ORDER, 1000)
        assertEquals(500, resolution.discountAmount)
    }

    @Test
    fun `resolve caps a fixed discount at the checkout amount`() {
        every { couponRepository.findByCode("FLAT500") } returns coupon(code = "FLAT500", discountType = DiscountType.FIXED, discountValue = 500)
        val resolution = service.resolve("FLAT500", storeId, CouponKind.ORDER, 300)
        assertEquals(300, resolution.discountAmount)
    }

    @Test
    fun `resolve rejects an unknown code`() {
        every { couponRepository.findByCode("NOPE") } returns null
        assertThrows(ConflictException::class.java) { service.resolve("nope", storeId, CouponKind.ORDER, 1000) }
    }

    @Test
    fun `resolve rejects an inactive coupon`() {
        every { couponRepository.findByCode("SAVE10") } returns coupon(active = false)
        assertThrows(ConflictException::class.java) { service.resolve("SAVE10", storeId, CouponKind.ORDER, 1000) }
    }

    @Test
    fun `resolve rejects an expired coupon`() {
        every { couponRepository.findByCode("SAVE10") } returns coupon(expiresAt = Instant.now().minusSeconds(60))
        assertThrows(ConflictException::class.java) { service.resolve("SAVE10", storeId, CouponKind.ORDER, 1000) }
    }

    @Test
    fun `resolve rejects a coupon that has reached its usage limit`() {
        every { couponRepository.findByCode("SAVE10") } returns coupon(maxUses = 5, usedCount = 5)
        assertThrows(ConflictException::class.java) { service.resolve("SAVE10", storeId, CouponKind.ORDER, 1000) }
    }

    @Test
    fun `resolve rejects a store-scoped coupon used against a different store`() {
        every { couponRepository.findByCode("SAVE10") } returns coupon(storeScoped = true)
        assertThrows(ConflictException::class.java) { service.resolve("SAVE10", otherStoreId, CouponKind.ORDER, 1000) }
    }

    @Test
    fun `resolve allows a platform-wide coupon against any store`() {
        every { couponRepository.findByCode("SAVE10") } returns coupon(storeScoped = false)
        val resolution = service.resolve("SAVE10", otherStoreId, CouponKind.ORDER, 1000)
        assertEquals(100, resolution.discountAmount)
    }

    @Test
    fun `resolve rejects a coupon that doesn't apply to bookings`() {
        every { couponRepository.findByCode("SAVE10") } returns coupon(appliesToBookings = false)
        assertThrows(ConflictException::class.java) { service.resolve("SAVE10", storeId, CouponKind.BOOKING, 1000) }
    }

    @Test
    fun `resolve rejects a coupon that doesn't apply to orders`() {
        every { couponRepository.findByCode("SAVE10") } returns coupon(appliesToOrders = false)
        assertThrows(ConflictException::class.java) { service.resolve("SAVE10", storeId, CouponKind.ORDER, 1000) }
    }

    @Test
    fun `resolve rejects an amount below the coupon's minimum spend`() {
        every { couponRepository.findByCode("SAVE10") } returns coupon(minSubtotal = 2000)
        assertThrows(ConflictException::class.java) { service.resolve("SAVE10", storeId, CouponKind.ORDER, 1000) }
    }

    @Test
    fun `resolve normalizes the code to uppercase before lookup`() {
        every { couponRepository.findByCode("SAVE10") } returns coupon()
        service.resolve("  save10  ", storeId, CouponKind.ORDER, 1000)
    }

    @Test
    fun `preview returns valid=false with a message instead of throwing`() {
        every { couponRepository.findByCode("NOPE") } returns null
        val result = service.preview(CouponPreviewInput(code = "nope", storeId = storeId, kind = "order", amount = 1000))
        assertEquals(false, result.valid)
        assertEquals(0, result.discountAmount)
        assertEquals("Invalid coupon code", result.message)
    }

    @Test
    fun `preview returns valid=true with the computed discount`() {
        every { couponRepository.findByCode("SAVE10") } returns coupon()
        val result = service.preview(CouponPreviewInput(code = "SAVE10", storeId = storeId, kind = "order", amount = 1000))
        assertEquals(true, result.valid)
        assertEquals(100, result.discountAmount)
    }
}
