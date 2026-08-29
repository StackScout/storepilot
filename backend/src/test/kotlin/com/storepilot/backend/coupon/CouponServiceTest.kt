package com.storepilot.backend.coupon

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
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

    // ---- seller-scoped CRUD ----

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
    private lateinit var store: Store

    @BeforeEach
    fun setUpStore() {
        store = Store(
            seller = seller, slug = "store", name = "Store", tagline = "tagline", description = "description",
            category = "handicrafts", address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000", verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = storeId }
        every { currentActor.requireSeller() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
    }

    private fun couponInput(code: String = "NEW20") = CouponInput(
        code = code,
        discountType = "percent",
        discountValue = 20,
        appliesToOrders = true,
        appliesToBookings = true,
    )

    @Test
    fun `createForStore rejects a seller who doesn't own the store`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) { service.createForStore(storeId, couponInput()) }
    }

    @Test
    fun `createForStore normalizes the code to uppercase and scopes it to the store`() {
        val slot = io.mockk.slot<Coupon>()
        every { couponRepository.save(capture(slot)) } answers { slot.captured.apply { id = UUID.randomUUID(); createdAt = Instant.now() } }

        val result = service.createForStore(storeId, couponInput(code = "  new20  "))

        assertEquals("NEW20", result.code)
        assertEquals(storeId, slot.captured.store?.id)
    }

    @Test
    fun `createForStore surfaces a duplicate code as a conflict`() {
        every { couponRepository.save(any()) } throws org.springframework.dao.DataIntegrityViolationException("duplicate key")

        assertThrows(ConflictException::class.java) { service.createForStore(storeId, couponInput()) }
    }

    @Test
    fun `updateForStore rejects a coupon belonging to another store`() {
        val otherStore = mockk<Store> { every { id } returns otherStoreId }
        val existing = coupon(storeScoped = true).apply { this.store = otherStore }
        every { couponRepository.findById(existing.id!!) } returns Optional.of(existing)
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        every { storeRepository.findById(otherStoreId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.updateForStore(existing.id!!, couponInput()) }
    }

    @Test
    fun `updateForStore applies every input field`() {
        val existing = coupon(storeScoped = true).apply { this.store = store }
        every { couponRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { couponRepository.save(any()) } answers { firstArg() }

        val result = service.updateForStore(existing.id!!, couponInput(code = "UPDATED"))

        assertEquals("UPDATED", result.code)
        assertEquals(20, result.discountValue)
    }

    @Test
    fun `deleteForStore removes the owner's own coupon`() {
        val existing = coupon(storeScoped = true).apply { this.store = store }
        every { couponRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { couponRepository.delete(existing) } returns Unit

        service.deleteForStore(existing.id!!)

        verify { couponRepository.delete(existing) }
    }

    @Test
    fun `listForStore rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) { service.listForStore(storeId) }
    }

    // ---- admin-scoped (platform-wide) ----

    @Test
    fun `createPlatformWide requires an admin`() {
        every { currentActor.requireAdmin() } returns mockk()
        every { couponRepository.save(any()) } answers {
            (firstArg() as Coupon).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        }

        val result = service.createPlatformWide(couponInput())

        assertEquals(null, result.storeId)
        verify { currentActor.requireAdmin() }
    }

    @Test
    fun `updatePlatformWide rejects a store-scoped coupon`() {
        every { currentActor.requireAdmin() } returns mockk()
        val storeScoped = coupon(storeScoped = true).apply { this.store = store }
        every { couponRepository.findById(storeScoped.id!!) } returns Optional.of(storeScoped)

        assertThrows(NotFoundException::class.java) { service.updatePlatformWide(storeScoped.id!!, couponInput()) }
    }

    @Test
    fun `updatePlatformWide updates a genuinely platform-wide coupon`() {
        every { currentActor.requireAdmin() } returns mockk()
        val platformWide = coupon(storeScoped = false)
        every { couponRepository.findById(platformWide.id!!) } returns Optional.of(platformWide)
        every { couponRepository.save(any()) } answers { firstArg() }

        val result = service.updatePlatformWide(platformWide.id!!, couponInput(code = "PLATFORM20"))

        assertEquals("PLATFORM20", result.code)
    }

    @Test
    fun `deletePlatformWide rejects a store-scoped coupon`() {
        every { currentActor.requireAdmin() } returns mockk()
        val storeScoped = coupon(storeScoped = true).apply { this.store = store }
        every { couponRepository.findById(storeScoped.id!!) } returns Optional.of(storeScoped)

        assertThrows(NotFoundException::class.java) { service.deletePlatformWide(storeScoped.id!!) }
    }

    @Test
    fun `listPlatformWide returns only coupons with no owning store`() {
        every { couponRepository.findByStoreIdIsNullOrderByCreatedAtDesc(any()) } returns
            PageImpl(listOf(coupon(storeScoped = false)))
        assertEquals(1, service.listPlatformWide(0, 20).content.size)
    }

    // ---- recordUse ----

    @Test
    fun `recordUse increments the coupon's used count`() {
        val existing = coupon(usedCount = 2)
        every { couponRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { couponRepository.save(existing) } returns existing

        service.recordUse(existing.id!!)

        assertEquals(3, existing.usedCount)
    }

    @Test
    fun `recordUse throws for a missing coupon`() {
        val id = UUID.randomUUID()
        every { couponRepository.findById(id) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.recordUse(id) }
    }
}
