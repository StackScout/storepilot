package com.storepilot.backend.store

import com.storepilot.backend.admin.AdminNotificationService
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.security.CognitoIdentity
import com.storepilot.backend.common.security.CognitoProperties
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.payout.FeeCollectionRepository
import com.storepilot.backend.payout.FeeCollectionStatus
import com.storepilot.backend.payout.PayoutRepository
import com.storepilot.backend.payout.PayoutStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.seller.SellerPlan
import com.storepilot.backend.seller.SellerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupResponse
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class StoreServiceTest {
    private val storeRepository = mockk<StoreRepository>()
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val sellerRepository = mockk<SellerRepository>()
    private val currentActor = mockk<CurrentActor>()
    private val cognitoClient = mockk<CognitoIdentityProviderClient>(relaxed = true)
    private val cognitoProperties = CognitoProperties(userPoolId = "pool-1")
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)
    private val adminNotificationService = mockk<AdminNotificationService>(relaxed = true)
    private val platformConfigService = mockk<PlatformConfigService>()
    private val auditLogService = mockk<AuditLogService>(relaxed = true)
    private val orderRepository = mockk<OrderRepository>()
    private val followRepository = mockk<FollowRepository>()
    private val bookingRepository = mockk<BookingRepository>()
    private val payoutRepository = mockk<PayoutRepository>()
    private val feeCollectionRepository = mockk<FeeCollectionRepository>()

    private val service = StoreService(
        storeRepository,
        storeSettingsRepository,
        sellerRepository,
        currentActor,
        cognitoClient,
        cognitoProperties,
        fileStorageService,
        adminNotificationService,
        platformConfigService,
        auditLogService,
        orderRepository,
        followRepository,
        bookingRepository,
        payoutRepository,
        feeCollectionRepository,
    )

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller", plan = SellerPlan.PRO).apply { id = UUID.randomUUID() }
    private val storeId: UUID = UUID.randomUUID()
    private lateinit var store: Store

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller,
            slug = "handicrafts-store",
            name = "Handicrafts Store",
            tagline = "tagline",
            description = "description",
            category = StoreCategory.HANDICRAFTS,
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.PENDING,
        ).apply {
            id = storeId
            createdAt = java.time.Instant.now()
        }

        every { currentActor.requireSeller() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { storeRepository.save(any()) } answers {
            (firstArg() as Store).apply {
                if (id == null) id = UUID.randomUUID()
                if (createdAt == null) createdAt = java.time.Instant.now()
            }
        }
        every { platformConfigService.current() } returns australiaSettings()
    }

    private fun australiaSettings() = PlatformSettings(
        name = "StorePilot",
        tagline = "tagline",
        countryName = "Australia",
        countryCode = "AU",
        currencyCode = "AUD",
        currencySymbol = "$",
        currencyLocale = "en-AU",
        platformFeePercent = BigDecimal("3.5"),
        flatShippingFee = 1000,
        proMonthlyPriceCents = 2900,
        defaultCodEnabled = true,
        defaultOnlinePaymentEnabled = false,
        defaultBankTransferEnabled = true,
        supportEmail = "hello@storepilot.au",
        companyLocation = "Sydney, Australia",
        timezone = "Australia/Sydney",
        returnWindowDays = 14,
    )

    private fun storeSettings(status: StoreVerificationStatus = StoreVerificationStatus.PENDING) = StoreSettings(
        store = store.apply { verificationStatus = status },
        contactEmail = "store@example.com",
        contactPhone = "+61400000001",
        bankAccountName = "Handicrafts Store",
        bankAccountNumber = "12345678",
        bankName = "Test Bank",
        transactionFeePercent = BigDecimal("5.0"),
        sellerType = SellerType.INDIVIDUAL,
        driverLicenceNumber = "DL123456",
        codEnabled = true,
        onlinePaymentEnabled = false,
        bankTransferEnabled = true,
    ).apply { store.id?.let { id = it } }

    // ---- create (onboarding) ----

    @Test
    fun `create rejects an unauthenticated caller`() {
        every { currentActor.currentIdentityOrNull() } returns null
        assertThrows(ForbiddenException::class.java) { service.create(applicationInput()) }
    }

    @Test
    fun `create rejects an existing buyer account`() {
        every { currentActor.currentIdentityOrNull() } returns CognitoIdentity("sub-1", "user-1", "buyer@example.com", "Buyer")
        every { currentActor.isBuyer() } returns true

        assertThrows(ConflictException::class.java) { service.create(applicationInput()) }
    }

    @Test
    fun `create provisions a new seller, grants the Cognito seller group, and creates a store`() {
        every { currentActor.currentIdentityOrNull() } returns CognitoIdentity("new-sub", "new-user", "new@example.com", "New Seller")
        every { currentActor.isBuyer() } returns false
        every { sellerRepository.findByCognitoSub("new-sub") } returns null
        every { sellerRepository.save(any()) } answers { (firstArg() as Seller).apply { id = UUID.randomUUID() } }
        every { storeRepository.findBySellerId(any()) } returns null
        every { storeRepository.findBySlug(any()) } returns null
        every { cognitoClient.adminAddUserToGroup(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest>()) } returns
            AdminAddUserToGroupResponse.builder().build()

        val result = service.create(applicationInput(name = "Brand New Store"))

        assertEquals("Brand New Store", result.name)
        assertEquals("pending", result.verificationStatus)
        verify { cognitoClient.adminAddUserToGroup(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest>()) }
    }

    @Test
    fun `create reuses the existing seller row without re-granting the Cognito group`() {
        every { currentActor.currentIdentityOrNull() } returns CognitoIdentity(seller.cognitoSub!!, "existing-user", seller.email, seller.name)
        every { currentActor.isBuyer() } returns false
        every { sellerRepository.findByCognitoSub(seller.cognitoSub!!) } returns seller
        every { storeRepository.findBySellerId(seller.id!!) } returns null
        every { storeRepository.findBySlug(any()) } returns null

        service.create(applicationInput())

        verify(exactly = 0) { cognitoClient.adminAddUserToGroup(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest>()) }
    }

    @Test
    fun `create rejects a resubmission when the existing store is already active`() {
        every { currentActor.currentIdentityOrNull() } returns CognitoIdentity(seller.cognitoSub!!, "user", seller.email, seller.name)
        every { currentActor.isBuyer() } returns false
        every { sellerRepository.findByCognitoSub(seller.cognitoSub!!) } returns seller
        store.verificationStatus = StoreVerificationStatus.ACTIVE
        every { storeRepository.findBySellerId(seller.id!!) } returns store

        assertThrows(ConflictException::class.java) { service.create(applicationInput()) }
    }

    @Test
    fun `create treats a resubmission on a rejected store as an update, not a new store`() {
        every { currentActor.currentIdentityOrNull() } returns CognitoIdentity(seller.cognitoSub!!, "user", seller.email, seller.name)
        every { currentActor.isBuyer() } returns false
        every { sellerRepository.findByCognitoSub(seller.cognitoSub!!) } returns seller
        store.verificationStatus = StoreVerificationStatus.REJECTED
        every { storeRepository.findBySellerId(seller.id!!) } returns store

        val result = service.create(applicationInput(name = "Handicrafts Store"))

        assertEquals("pending", result.verificationStatus)
        verify(exactly = 0) { storeRepository.findBySlug(any()) }
    }

    private fun applicationInput(name: String = "Handicrafts Store") = StoreApplicationInput(
        name = name,
        category = "handicrafts",
        tagline = "tagline",
        description = "description",
        city = "Sydney",
        state = "NSW",
        whatsappNumber = "+61400000000",
    )

    // ---- updateSettingsAsSeller (upsert) ----

    @Test
    fun `updateSettingsAsSeller rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        every { storeSettingsRepository.findById(storeId) } returns Optional.empty()

        assertThrows(ForbiddenException::class.java) { service.updateSettingsAsSeller(storeId, StoreSettingsInput()) }
    }

    @Test
    fun `updateSettingsAsSeller creates settings on first submission with sane defaults`() {
        every { storeSettingsRepository.findById(storeId) } returns Optional.empty()
        every { storeSettingsRepository.save(any()) } answers { firstArg() }

        val result = service.updateSettingsAsSeller(
            storeId,
            StoreSettingsInput(contactEmail = "store@example.com", contactPhone = "0400000000", driverLicenceNumber = "DL999"),
        )

        assertTrue(result.codEnabled) // Pro seller + platform default true
        assertFalse(result.onlinePaymentEnabled) // platform default false
    }

    @Test
    fun `updateSettingsAsSeller forces COD off for a non-Pro seller even when creating fresh settings`() {
        seller.plan = SellerPlan.FREE
        every { storeSettingsRepository.findById(storeId) } returns Optional.empty()
        every { storeSettingsRepository.save(any()) } answers { firstArg() }

        val result = service.updateSettingsAsSeller(
            storeId,
            StoreSettingsInput(driverLicenceNumber = "DL999", bankTransferEnabled = true, onlinePaymentEnabled = true),
        )

        assertFalse(result.codEnabled)
        assertFalse(result.bankTransferEnabled)
    }

    @Test
    fun `updateSettingsAsSeller rejects turning off every payment method`() {
        every { storeSettingsRepository.findById(storeId) } returns Optional.empty()

        assertThrows(ConflictException::class.java) {
            service.updateSettingsAsSeller(
                storeId,
                StoreSettingsInput(codEnabled = false, onlinePaymentEnabled = false, bankTransferEnabled = false, driverLicenceNumber = "DL999"),
            )
        }
    }

    @Test
    fun `updateSettingsAsSeller requires a driver's licence number in Australia`() {
        every { storeSettingsRepository.findById(storeId) } returns Optional.empty()

        assertThrows(IllegalArgumentException::class.java) {
            service.updateSettingsAsSeller(storeId, StoreSettingsInput())
        }
    }

    @Test
    fun `updateSettingsAsSeller requires an ABN for a registered business`() {
        every { storeSettingsRepository.findById(storeId) } returns Optional.empty()

        assertThrows(IllegalArgumentException::class.java) {
            service.updateSettingsAsSeller(storeId, StoreSettingsInput(driverLicenceNumber = "DL999", sellerType = "business"))
        }
    }

    @Test
    fun `updateSettingsAsSeller freezes verification fields once the store is active`() {
        val existing = storeSettings(status = StoreVerificationStatus.ACTIVE)
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(existing)

        assertThrows(ConflictException::class.java) {
            service.updateSettingsAsSeller(storeId, StoreSettingsInput(driverLicenceNumber = "DL999999"))
        }
    }

    @Test
    fun `updateSettingsAsSeller allows non-verification edits once the store is active`() {
        val existing = storeSettings(status = StoreVerificationStatus.ACTIVE)
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(existing)
        every { storeSettingsRepository.save(any()) } answers { firstArg() }
        every { currentActor.sellerOrNull() } returns seller

        val result = service.updateSettingsAsSeller(storeId, StoreSettingsInput(contactPhone = "0499999999"))

        assertEquals("0499999999", result.contactPhone)
        verify { auditLogService.recordAsSeller(seller, com.storepilot.backend.admin.AuditAction.STORE_SETTINGS_UPDATED, "store", storeId.toString(), any()) }
    }

    @Test
    fun `updateSettingsAsSeller notifies admins when bank details change`() {
        val existing = storeSettings()
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(existing)
        every { storeSettingsRepository.save(any()) } answers { firstArg() }
        every { currentActor.sellerOrNull() } returns seller

        service.updateSettingsAsSeller(storeId, StoreSettingsInput(bankAccountNumber = "99999999"))

        verify { adminNotificationService.notifyBankDetailsChanged(any(), any(), any(), "99999999") }
    }

    @Test
    fun `updateSettingsAsSeller doesn't notify admins when bank details are unchanged`() {
        val existing = storeSettings()
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(existing)
        every { storeSettingsRepository.save(any()) } answers { firstArg() }
        every { currentActor.sellerOrNull() } returns seller

        service.updateSettingsAsSeller(storeId, StoreSettingsInput(contactPhone = "0499999999"))

        verify(exactly = 0) { adminNotificationService.notifyBankDetailsChanged(any(), any(), any(), any()) }
    }

    // ---- setVerificationStatus (admin) ----

    @Test
    fun `setVerificationStatus approves a store and records an audit entry`() {
        val result = service.setVerificationStatus(storeId, VerificationDecisionInput(status = "active"))

        assertEquals("active", result.verificationStatus)
        assertTrue(result.isVerified)
        verify { auditLogService.record(com.storepilot.backend.admin.AuditAction.STORE_APPROVED, "store", storeId.toString(), any()) }
    }

    @Test
    fun `setVerificationStatus rejects a store and stashes the rejection reason`() {
        val settings = storeSettings()
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(settings)
        every { storeSettingsRepository.save(any()) } answers { firstArg() }

        val result = service.setVerificationStatus(storeId, VerificationDecisionInput(status = "rejected", rejectionReason = "Blurry ID photo"))

        assertEquals("rejected", result.verificationStatus)
        assertFalse(result.isVerified)
        assertEquals("Blurry ID photo", settings.rejectionReason)
        verify { auditLogService.record(com.storepilot.backend.admin.AuditAction.STORE_REJECTED, "store", storeId.toString(), any()) }
    }

    // ---- closeStore ----

    @Test
    fun `closeStore rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) { service.closeStore(storeId) }
    }

    @Test
    fun `closeStore is idempotent when already closed`() {
        store.verificationStatus = StoreVerificationStatus.CLOSED

        val result = service.closeStore(storeId)

        assertEquals("closed", result.verificationStatus)
        verify(exactly = 0) { orderRepository.existsByStoreIdAndStatusIn(any(), any()) }
    }

    @Test
    fun `closeStore rejects when orders are still in progress`() {
        every { orderRepository.existsByStoreIdAndStatusIn(storeId, setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED)) } returns true

        assertThrows(ConflictException::class.java) { service.closeStore(storeId) }
    }

    @Test
    fun `closeStore rejects when bookings are still in progress`() {
        every { orderRepository.existsByStoreIdAndStatusIn(any(), any()) } returns false
        every { bookingRepository.existsByStoreIdAndStatusIn(storeId, setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED)) } returns true

        assertThrows(ConflictException::class.java) { service.closeStore(storeId) }
    }

    @Test
    fun `closeStore rejects when a platform fee is still outstanding`() {
        every { orderRepository.existsByStoreIdAndStatusIn(any(), any()) } returns false
        every { bookingRepository.existsByStoreIdAndStatusIn(any(), any()) } returns false
        every { feeCollectionRepository.existsByStoreIdAndStatus(storeId, FeeCollectionStatus.PENDING) } returns true

        assertThrows(ConflictException::class.java) { service.closeStore(storeId) }
    }

    @Test
    fun `closeStore rejects when a payout is still scheduled`() {
        every { orderRepository.existsByStoreIdAndStatusIn(any(), any()) } returns false
        every { bookingRepository.existsByStoreIdAndStatusIn(any(), any()) } returns false
        every { feeCollectionRepository.existsByStoreIdAndStatus(any(), any()) } returns false
        every { payoutRepository.existsByStoreIdAndStatus(storeId, PayoutStatus.SCHEDULED) } returns true

        assertThrows(ConflictException::class.java) { service.closeStore(storeId) }
    }

    @Test
    fun `closeStore succeeds when nothing is outstanding`() {
        every { orderRepository.existsByStoreIdAndStatusIn(any(), any()) } returns false
        every { bookingRepository.existsByStoreIdAndStatusIn(any(), any()) } returns false
        every { feeCollectionRepository.existsByStoreIdAndStatus(any(), any()) } returns false
        every { payoutRepository.existsByStoreIdAndStatus(any(), any()) } returns false

        val result = service.closeStore(storeId)

        assertEquals("closed", result.verificationStatus)
        assertFalse(result.isVerified)
        verify { auditLogService.recordAsSeller(seller, com.storepilot.backend.admin.AuditAction.STORE_CLOSED, "store", storeId.toString(), any()) }
    }

    // ---- follow / unfollow ----

    @Test
    fun `follow is idempotent when already following`() {
        val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        every { currentActor.requireBuyer() } returns buyer
        every { followRepository.existsByBuyerIdAndStoreId(buyer.id!!, storeId) } returns true

        service.follow(storeId)

        verify(exactly = 0) { followRepository.save(any()) }
    }

    @Test
    fun `follow saves a new follow and bumps the follower count`() {
        val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        every { currentActor.requireBuyer() } returns buyer
        every { followRepository.existsByBuyerIdAndStoreId(buyer.id!!, storeId) } returns false
        every { followRepository.save(any()) } answers { firstArg() }
        val before = store.followerCount

        service.follow(storeId)

        assertEquals(before + 1, store.followerCount)
        verify { followRepository.save(any()) }
    }

    @Test
    fun `unfollow is a no-op when not following`() {
        val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        every { currentActor.requireBuyer() } returns buyer
        every { followRepository.findByBuyerIdAndStoreId(buyer.id!!, storeId) } returns null

        service.unfollow(storeId)

        verify(exactly = 0) { followRepository.delete(any()) }
    }

    @Test
    fun `unfollow removes the follow and never drops the counter below zero`() {
        val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        val follow = Follow(buyer = buyer, store = store)
        store.followerCount = 0
        every { currentActor.requireBuyer() } returns buyer
        every { followRepository.findByBuyerIdAndStoreId(buyer.id!!, storeId) } returns follow
        every { followRepository.delete(follow) } returns Unit

        service.unfollow(storeId)

        assertEquals(0, store.followerCount)
    }

    // ---- basic reads ----

    @Test
    fun `getBySlug throws for a store that isn't active`() {
        every { storeRepository.findBySlug("handicrafts-store") } returns store.apply { verificationStatus = StoreVerificationStatus.PENDING }
        assertThrows(NotFoundException::class.java) { service.getBySlug("handicrafts-store") }
    }

    @Test
    fun `getBySlug returns an active store`() {
        store.verificationStatus = StoreVerificationStatus.ACTIVE
        every { storeRepository.findBySlug("handicrafts-store") } returns store
        val result = service.getBySlug("handicrafts-store")
        assertEquals(storeId, result.id)
    }

    @Test
    fun `getMyStore returns null when the seller hasn't onboarded yet`() {
        every { storeRepository.findBySellerId(seller.id!!) } returns null
        assertNull(service.getMyStore())
    }
}
