package com.storepilot.backend.seller

import com.storepilot.backend.admin.AuditAction
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.security.CognitoProperties
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.store.SellerType
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreSettings
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreVerificationStatus
import com.storepilot.backend.stripe.StripeConnectService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class SellerAccountServiceTest {
    private val currentActor = mockk<CurrentActor>()
    private val sellerRepository = mockk<SellerRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val sellerBillingService = mockk<SellerBillingService>(relaxed = true)
    private val stripeConnectService = mockk<StripeConnectService>(relaxed = true)
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)
    private val auditLogService = mockk<AuditLogService>(relaxed = true)
    private val cognitoClient = mockk<CognitoIdentityProviderClient>(relaxed = true)
    private val cognitoProperties = CognitoProperties(userPoolId = "pool-1")

    private val service = SellerAccountService(
        currentActor,
        sellerRepository,
        storeRepository,
        storeSettingsRepository,
        sellerBillingService,
        stripeConnectService,
        fileStorageService,
        auditLogService,
        cognitoClient,
        cognitoProperties,
    )

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }

    @BeforeEach
    fun setUp() {
        every { currentActor.requireSeller() } returns seller
        every { sellerRepository.save(any()) } answers { firstArg() }
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("seller-sub")
            .claim("username", "seller-sub")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt, emptyList())
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun store(status: StoreVerificationStatus) = Store(
        seller = seller,
        slug = "store",
        name = "Store",
        tagline = "tagline",
        description = "description",
        category = StoreCategory.HANDICRAFTS,
        address = StoreAddress(city = "Sydney", state = "NSW"),
        whatsappNumber = "+61400000000",
        verificationStatus = status,
    ).apply { id = UUID.randomUUID() }

    private fun settingsFor(store: Store, stripeAccountId: String? = null) = StoreSettings(
        store = store,
        contactEmail = "store@example.com",
        contactPhone = "+61400000001",
        bankAccountName = "Store",
        bankAccountNumber = "12345678",
        bankName = "Test Bank",
        transactionFeePercent = BigDecimal("5.0"),
        sellerType = SellerType.INDIVIDUAL,
        driverLicenceNumber = "DL123",
        stripeAccountId = stripeAccountId,
    ).apply { id = store.id }

    @Test
    fun `deleteCurrentSeller rejects a seller whose store isn't closed yet`() {
        every { storeRepository.findBySellerId(seller.id!!) } returns store(StoreVerificationStatus.ACTIVE)
        assertThrows(ConflictException::class.java) { service.deleteCurrentSeller() }
    }

    @Test
    fun `deleteCurrentSeller allows a seller with no store at all`() {
        every { storeRepository.findBySellerId(seller.id!!) } returns null

        service.deleteCurrentSeller()

        assertEquals("Deleted seller", seller.name)
        assertEquals(SellerPlan.FREE, seller.plan)
        verify { sellerBillingService.cancelAndDeleteCustomer(seller) }
        verify { cognitoClient.adminUserGlobalSignOut(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUserGlobalSignOutRequest>()) }
        verify { cognitoClient.adminDeleteUser(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest>()) }
    }

    @Test
    fun `deleteCurrentSeller anonymizes the seller's identity fields`() {
        every { storeRepository.findBySellerId(seller.id!!) } returns null

        service.deleteCurrentSeller()

        assertEquals("deleted-seller-${seller.id}@storepilot.invalid", seller.email)
        assertEquals("deleted-${seller.id}", seller.cognitoSub)
        assertNull(seller.stripeCustomerId)
    }

    @Test
    fun `deleteCurrentSeller deauthorizes a connected Stripe account when one exists`() {
        val closedStore = store(StoreVerificationStatus.CLOSED)
        every { storeRepository.findBySellerId(seller.id!!) } returns closedStore
        every { storeSettingsRepository.findById(closedStore.id!!) } returns Optional.of(settingsFor(closedStore, stripeAccountId = "acct_1"))
        every { storeSettingsRepository.save(any()) } answers { firstArg() }

        service.deleteCurrentSeller()

        verify { stripeConnectService.deauthorize("acct_1") }
    }

    @Test
    fun `deleteCurrentSeller continues even when Stripe deauthorization fails`() {
        val closedStore = store(StoreVerificationStatus.CLOSED)
        every { storeRepository.findBySellerId(seller.id!!) } returns closedStore
        every { storeSettingsRepository.findById(closedStore.id!!) } returns Optional.of(settingsFor(closedStore, stripeAccountId = "acct_1"))
        every { storeSettingsRepository.save(any()) } answers { firstArg() }
        every { stripeConnectService.deauthorize("acct_1") } throws RuntimeException("Stripe is down")

        service.deleteCurrentSeller()

        assertEquals("Deleted seller", seller.name)
    }

    @Test
    fun `deleteCurrentSeller redacts store settings and deletes their documents`() {
        val closedStore = store(StoreVerificationStatus.CLOSED)
        val settings = settingsFor(closedStore).apply { abnDocumentUrl = "docs/abn.jpg" }
        every { storeRepository.findBySellerId(seller.id!!) } returns closedStore
        every { storeSettingsRepository.findById(closedStore.id!!) } returns Optional.of(settings)
        every { storeSettingsRepository.save(any()) } answers { firstArg() }

        service.deleteCurrentSeller()

        assertEquals("[redacted]", settings.contactEmail)
        assertNull(settings.abn)
        assertNull(settings.abnDocumentUrl)
        verify { fileStorageService.delete("docs/abn.jpg") }
    }

    @Test
    fun `deleteCurrentSeller records an audit entry before anonymizing`() {
        every { storeRepository.findBySellerId(seller.id!!) } returns null

        service.deleteCurrentSeller()

        verify {
            auditLogService.recordAsSeller(
                seller,
                AuditAction.SELLER_ACCOUNT_DELETED,
                "seller",
                seller.id.toString(),
                match { it.contains("Seller") },
            )
        }
    }
}
