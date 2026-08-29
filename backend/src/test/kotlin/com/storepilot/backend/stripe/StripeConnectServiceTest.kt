package com.storepilot.backend.stripe

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.notification.NotificationProperties
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.SellerType
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreSettings
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreVerificationStatus
import com.stripe.model.Account
import com.stripe.model.AccountLink
import com.stripe.net.OAuth
import com.stripe.net.RequestOptions
import com.stripe.param.AccountCreateParams
import com.stripe.param.AccountLinkCreateParams
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class StripeConnectServiceTest {
    private val storeRepository = mockk<StoreRepository>()
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val currentActor = mockk<CurrentActor>()
    private val notificationProperties = NotificationProperties(frontendBaseUrl = "http://localhost:3000")
    private val platformConfigService = mockk<PlatformConfigService>()
    private val stripeProperties = StripeProperties(connectClientId = "ca_test123")

    private val service = StripeConnectService(
        storeRepository,
        storeSettingsRepository,
        currentActor,
        notificationProperties,
        platformConfigService,
        stripeProperties,
    )

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
    private val storeId: UUID = UUID.randomUUID()
    private lateinit var store: Store

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller,
            slug = "store",
            name = "Store",
            tagline = "tagline",
            description = "description",
            category = "handicrafts",
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = storeId }
        every { currentActor.requireSeller() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { platformConfigService.current() } returns PlatformSettings(
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
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun settings(accountId: String? = null) = StoreSettings(
        store = store,
        contactEmail = "store@example.com",
        contactPhone = "+61400000001",
        bankAccountName = "Store",
        bankAccountNumber = "12345678",
        bankName = "Test Bank",
        transactionFeePercent = BigDecimal("5.0"),
        sellerType = SellerType.INDIVIDUAL,
        stripeAccountId = accountId,
    ).apply { id = storeId }

    // ---- startOnboarding ----

    @Test
    fun `startOnboarding rejects a country where Stripe isn't available yet`() {
        platformConfigService.current().countryCode = "LK"
        assertThrows(ConflictException::class.java) { service.startOnboarding(storeId) }
    }

    @Test
    fun `startOnboarding rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        assertThrows(ForbiddenException::class.java) { service.startOnboarding(storeId) }
    }

    @Test
    fun `startOnboarding throws when settings don't exist yet`() {
        every { storeSettingsRepository.findById(storeId) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.startOnboarding(storeId) }
    }

    @Test
    fun `startOnboarding creates a new Stripe account when none exists yet`() {
        val existing = settings(accountId = null)
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(existing)
        every { storeSettingsRepository.save(any()) } answers { firstArg() }

        mockkStatic(Account::class)
        mockkStatic(AccountLink::class)
        val fakeAccount = mockk<Account> { every { id } returns "acct_new123" }
        every { Account.create(any<AccountCreateParams>()) } returns fakeAccount
        val fakeLink = mockk<AccountLink> { every { url } returns "https://connect.stripe.com/setup/acct_new123" }
        every { AccountLink.create(any<AccountLinkCreateParams>()) } returns fakeLink

        val result = service.startOnboarding(storeId)

        assertEquals("https://connect.stripe.com/setup/acct_new123", result.onboardingUrl)
        assertEquals("acct_new123", existing.stripeAccountId)
        verify { storeSettingsRepository.save(existing) }
    }

    @Test
    fun `startOnboarding reuses an existing Stripe account without creating a new one`() {
        val existing = settings(accountId = "acct_existing")
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(existing)

        mockkStatic(Account::class)
        mockkStatic(AccountLink::class)
        val fakeLink = mockk<AccountLink> { every { url } returns "https://connect.stripe.com/setup/acct_existing" }
        every { AccountLink.create(any<AccountLinkCreateParams>()) } returns fakeLink

        val result = service.startOnboarding(storeId)

        assertEquals("https://connect.stripe.com/setup/acct_existing", result.onboardingUrl)
        verify(exactly = 0) { Account.create(any<AccountCreateParams>()) }
        verify(exactly = 0) { storeSettingsRepository.save(any()) }
    }

    // ---- refreshAccountStatus ----

    @Test
    fun `refreshAccountStatus rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        assertThrows(ForbiddenException::class.java) { service.refreshAccountStatus(storeId) }
    }

    @Test
    fun `refreshAccountStatus throws when no Stripe account is connected yet`() {
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(settings(accountId = null))
        assertThrows(ConflictException::class.java) { service.refreshAccountStatus(storeId) }
    }

    @Test
    fun `refreshAccountStatus retrieves and syncs the live account status`() {
        val existing = settings(accountId = "acct_existing")
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(existing)
        every { storeSettingsRepository.findByStripeAccountId("acct_existing") } returns existing
        every { storeSettingsRepository.save(any()) } answers { firstArg() }

        mockkStatic(Account::class)
        val fakeAccount = mockk<Account> {
            every { id } returns "acct_existing"
            every { chargesEnabled } returns true
            every { payoutsEnabled } returns false
        }
        every { Account.retrieve("acct_existing") } returns fakeAccount

        service.refreshAccountStatus(storeId)

        assertTrue(existing.stripeChargesEnabled)
        assertFalse(existing.stripePayoutsEnabled)
    }

    // ---- syncAccountStatus ----

    @Test
    fun `syncAccountStatus ignores an account not linked to any store`() {
        val fakeAccount = mockk<Account> { every { id } returns "acct_orphan" }
        every { storeSettingsRepository.findByStripeAccountId("acct_orphan") } returns null

        service.syncAccountStatus(fakeAccount)

        verify(exactly = 0) { storeSettingsRepository.save(any()) }
    }

    @Test
    fun `syncAccountStatus treats null flags as false`() {
        val existing = settings(accountId = "acct_1").apply { stripeChargesEnabled = true; stripePayoutsEnabled = true }
        every { storeSettingsRepository.findByStripeAccountId("acct_1") } returns existing
        every { storeSettingsRepository.save(any()) } answers { firstArg() }
        val fakeAccount = mockk<Account> {
            every { id } returns "acct_1"
            every { chargesEnabled } returns null
            every { payoutsEnabled } returns null
        }

        service.syncAccountStatus(fakeAccount)

        assertFalse(existing.stripeChargesEnabled)
        assertFalse(existing.stripePayoutsEnabled)
    }

    // ---- deauthorize ----

    @Test
    fun `deauthorize calls Stripe's OAuth deauthorize with the platform's connect client id`() {
        mockkStatic(OAuth::class)
        every { OAuth.deauthorize(any<Map<String, Any>>(), any<RequestOptions>()) } returns mockk(relaxed = true)

        service.deauthorize("acct_to_remove")

        verify {
            OAuth.deauthorize(
                match<Map<String, Any>> { it["client_id"] == "ca_test123" && it["stripe_user_id"] == "acct_to_remove" },
                any<RequestOptions>(),
            )
        }
    }
}
