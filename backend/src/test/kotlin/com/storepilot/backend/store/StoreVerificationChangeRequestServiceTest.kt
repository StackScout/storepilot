package com.storepilot.backend.store

import com.storepilot.backend.admin.Admin
import com.storepilot.backend.admin.AdminNotificationService
import com.storepilot.backend.admin.AuditAction
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.common.wireValueOf
import com.storepilot.backend.seller.Seller
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class StoreVerificationChangeRequestServiceTest {
    private val storeRepository = mockk<StoreRepository>()
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val changeRequestRepository = mockk<StoreVerificationChangeRequestRepository>()
    private val currentActor = mockk<CurrentActor>()
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)
    private val platformConfigService = mockk<PlatformConfigService>()
    private val auditLogService = mockk<AuditLogService>(relaxed = true)
    private val adminNotificationService = mockk<AdminNotificationService>(relaxed = true)

    private val service = StoreVerificationChangeRequestService(
        storeRepository,
        storeSettingsRepository,
        changeRequestRepository,
        currentActor,
        fileStorageService,
        platformConfigService,
        auditLogService,
        adminNotificationService,
    )

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller")
    private val storeId: UUID = UUID.randomUUID()
    private lateinit var store: Store
    private lateinit var settings: StoreSettings

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller,
            slug = "test-store",
            name = "Test Store",
            tagline = "tagline",
            description = "description",
            category = StoreCategory.HANDICRAFTS,
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = storeId }
        settings = StoreSettings(
            store = store,
            contactEmail = "store@example.com",
            contactPhone = "+61400000001",
            bankAccountName = "Test",
            bankAccountNumber = "123",
            bankName = "Test Bank",
            transactionFeePercent = BigDecimal("2.0"),
            sellerType = SellerType.INDIVIDUAL,
            driverLicenceNumber = "11111111",
        )
        every { currentActor.requireSeller() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(settings)
        every { platformConfigService.current() } returns mockk<PlatformSettings> { every { countryCode } returns "AU" }
    }

    @Test
    fun `submit rejects a store that isn't ACTIVE yet`() {
        store.verificationStatus = StoreVerificationStatus.PENDING
        every { changeRequestRepository.findByStoreIdAndStatus(storeId, StoreVerificationChangeRequestStatus.PENDING) } returns null

        assertThrows(ConflictException::class.java) {
            service.submit(storeId, VerificationChangeRequestInput(driverLicenceNumber = "99999999"), null, null, null, null)
        }
    }

    @Test
    fun `submit rejects a second request while one is already pending`() {
        every {
            changeRequestRepository.findByStoreIdAndStatus(storeId, StoreVerificationChangeRequestStatus.PENDING)
        } returns StoreVerificationChangeRequest(store = store)

        assertThrows(ConflictException::class.java) {
            service.submit(storeId, VerificationChangeRequestInput(driverLicenceNumber = "99999999"), null, null, null, null)
        }
    }

    @Test
    fun `submit rejects an empty submission with no field or file changed`() {
        every { changeRequestRepository.findByStoreIdAndStatus(storeId, StoreVerificationChangeRequestStatus.PENDING) } returns null

        assertThrows(IllegalArgumentException::class.java) {
            service.submit(storeId, VerificationChangeRequestInput(), null, null, null, null)
        }
    }

    @Test
    fun `submit validates the proposed value merged against the store's current settings, not just what was sent`() {
        // Store's existing sellerType is already BUSINESS but has no ABN on
        // file — submitting only a driver's licence change must still fail
        // validation because the merged view is missing a required ABN.
        settings.sellerType = SellerType.BUSINESS
        settings.abn = null
        every { changeRequestRepository.findByStoreIdAndStatus(storeId, StoreVerificationChangeRequestStatus.PENDING) } returns null

        assertThrows(IllegalArgumentException::class.java) {
            service.submit(storeId, VerificationChangeRequestInput(driverLicenceNumber = "22222222"), null, null, null, null)
        }
    }

    @Test
    fun `submit persists the request, audit-logs it, and notifies admins — without touching live settings`() {
        every { changeRequestRepository.findByStoreIdAndStatus(storeId, StoreVerificationChangeRequestStatus.PENDING) } returns null
        val savedSlot = io.mockk.slot<StoreVerificationChangeRequest>()
        every { changeRequestRepository.save(capture(savedSlot)) } answers {
            savedSlot.captured.apply {
                id = UUID.randomUUID()
                createdAt = java.time.Instant.now()
            }
        }

        val response = service.submit(storeId, VerificationChangeRequestInput(driverLicenceNumber = "99999999"), null, null, null, null)

        assertEquals("99999999", response.driverLicenceNumber)
        assertEquals("11111111", response.currentDriverLicenceNumber, "current* reflects live settings, unchanged by submit")
        assertEquals("11111111", settings.driverLicenceNumber, "submit never mutates the real StoreSettings row")
        verify { auditLogService.recordAsSeller(seller, AuditAction.STORE_VERIFICATION_CHANGE_REQUESTED, "store", storeId.toString(), any()) }
        verify { adminNotificationService.notifyVerificationChangeRequested(store) }
    }

    @Test
    fun `a non-owning seller cannot submit a request for someone else's store`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) {
            service.submit(storeId, VerificationChangeRequestInput(driverLicenceNumber = "99999999"), null, null, null, null)
        }
    }

    @Test
    fun `adminApprove applies the proposed fields onto live settings and closes the request`() {
        val admin = Admin(cognitoSub = "admin-sub", email = "admin@example.com", name = "Admin")
        every { currentActor.requireAdmin() } returns admin
        val requestId = UUID.randomUUID()
        val request = StoreVerificationChangeRequest(store = store, driverLicenceNumber = "99999999")
        every { changeRequestRepository.findById(requestId) } returns Optional.of(request)
        every { changeRequestRepository.save(any()) } answers { firstArg() }
        every { storeSettingsRepository.save(any()) } answers { firstArg() }

        service.adminApprove(requestId)

        assertEquals("99999999", settings.driverLicenceNumber)
        assertEquals(StoreVerificationChangeRequestStatus.APPROVED, request.status)
        assertEquals("admin@example.com", request.reviewedByEmail)
        verify { auditLogService.record(AuditAction.STORE_VERIFICATION_CHANGE_APPROVED, "store", storeId.toString(), any()) }
    }

    @Test
    fun `adminApprove rejects a request that was already reviewed`() {
        val requestId = UUID.randomUUID()
        val alreadyApproved = StoreVerificationChangeRequest(store = store, status = StoreVerificationChangeRequestStatus.APPROVED)
        every { changeRequestRepository.findById(requestId) } returns Optional.of(alreadyApproved)

        assertThrows(ConflictException::class.java) { service.adminApprove(requestId) }
    }

    @Test
    fun `adminReject requires a non-blank reason and leaves live settings untouched`() {
        val admin = Admin(cognitoSub = "admin-sub", email = "admin@example.com", name = "Admin")
        every { currentActor.requireAdmin() } returns admin
        val requestId = UUID.randomUUID()
        val request = StoreVerificationChangeRequest(store = store, driverLicenceNumber = "99999999")
            .apply { id = requestId; createdAt = java.time.Instant.now() }
        every { changeRequestRepository.findById(requestId) } returns Optional.of(request)
        every { changeRequestRepository.save(any()) } answers { firstArg() }

        assertThrows(IllegalArgumentException::class.java) {
            service.adminReject(requestId, VerificationChangeRequestReviewInput(rejectionReason = "  "))
        }

        val response = service.adminReject(requestId, VerificationChangeRequestReviewInput(rejectionReason = "Not verifiable"))

        assertEquals(StoreVerificationChangeRequestStatus.REJECTED, response.status.let { wireValueOf<StoreVerificationChangeRequestStatus>(it) })
        assertEquals("11111111", settings.driverLicenceNumber, "reject must never mutate live StoreSettings")
        verify { auditLogService.record(AuditAction.STORE_VERIFICATION_CHANGE_REJECTED, "store", storeId.toString(), any()) }
    }

    @Test
    fun `current returns null when there is no pending request`() {
        every { changeRequestRepository.findByStoreIdAndStatus(storeId, StoreVerificationChangeRequestStatus.PENDING) } returns null

        assertEquals(null, service.current(storeId))
    }
}
