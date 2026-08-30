package com.storepilot.backend.seller

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.stripe.StripeProperties
import com.stripe.model.Customer
import com.stripe.model.Subscription
import com.stripe.model.SubscriptionItem
import com.stripe.model.SubscriptionItemCollection
import com.stripe.model.checkout.Session
import com.stripe.param.CustomerCreateParams
import com.stripe.param.SubscriptionListParams
import com.stripe.param.checkout.SessionCreateParams
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class SellerBillingServiceTest {
    private val sellerRepository = mockk<SellerRepository>()
    private val currentActor = mockk<CurrentActor>()
    private val platformConfigService = mockk<PlatformConfigService>()
    private val stripeProperties = StripeProperties(
        billingSuccessUrlBase = "http://localhost:3000/dashboard/settings",
        billingCancelUrlBase = "http://localhost:3000/dashboard/settings",
    )

    private val service = SellerBillingService(sellerRepository, currentActor, platformConfigService, stripeProperties)

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }

    @BeforeEach
    fun setUp() {
        every { currentActor.requireSeller() } returns seller
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
            proPlanEnabled = true,
            supportEmail = "hello@storepilot.au",
            companyLocation = "Sydney, Australia",
            timezone = "Australia/Sydney",
            returnWindowDays = 14,
        )
        every { sellerRepository.save(any()) } answers { firstArg() }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun subscriptionMock(status: String, cancelAtPeriodEnd: Boolean? = false, periodEndEpoch: Long? = 1893456000L, id: String = "sub_123"): Subscription {
        val item = mockk<SubscriptionItem> { every { currentPeriodEnd } returns periodEndEpoch }
        val itemCollection = mockk<SubscriptionItemCollection> { every { data } returns listOf(item) }
        return mockk<Subscription> {
            every { this@mockk.id } returns id
            every { this@mockk.status } returns status
            every { this@mockk.cancelAtPeriodEnd } returns cancelAtPeriodEnd
            every { items } returns itemCollection
        }
    }

    // ---- currentPlan ----

    @Test
    fun `currentPlan reflects the seller's stored plan and live pricing`() {
        seller.plan = SellerPlan.PRO
        val result = service.currentPlan()
        assertEquals("pro", result.plan)
        assertEquals(2900, result.monthlyPriceCents)
        assertEquals("AUD", result.currencyCode)
    }

    // ---- startCheckout ----

    @Test
    fun `startCheckout rejects a seller already on Pro`() {
        seller.plan = SellerPlan.PRO
        assertThrows(ConflictException::class.java) { service.startCheckout() }
    }

    @Test
    fun `startCheckout creates a new Stripe customer when the seller has none yet`() {
        seller.plan = SellerPlan.FREE
        seller.stripeCustomerId = null

        mockkStatic(Customer::class)
        mockkStatic(Session::class)
        val fakeCustomer = mockk<Customer> { every { id } returns "cus_new123" }
        every { Customer.create(any<CustomerCreateParams>()) } returns fakeCustomer
        val fakeSession = mockk<Session> { every { url } returns "https://checkout.stripe.com/billing-session" }
        every { Session.create(any<SessionCreateParams>()) } returns fakeSession

        val result = service.startCheckout()

        assertEquals("https://checkout.stripe.com/billing-session", result.checkoutUrl)
        assertEquals("cus_new123", seller.stripeCustomerId)
    }

    @Test
    fun `startCheckout reuses an existing Stripe customer`() {
        seller.plan = SellerPlan.FREE
        seller.stripeCustomerId = "cus_existing"

        mockkStatic(Customer::class)
        mockkStatic(Session::class)
        val fakeSession = mockk<Session> { every { url } returns "https://checkout.stripe.com/billing-session" }
        every { Session.create(any<SessionCreateParams>()) } returns fakeSession

        service.startCheckout()

        verify(exactly = 0) { Customer.create(any<CustomerCreateParams>()) }
    }

    // ---- cancelAtPeriodEnd ----

    @Test
    fun `cancelAtPeriodEnd throws when there's no active subscription`() {
        seller.stripeSubscriptionId = null
        assertThrows(ConflictException::class.java) { service.cancelAtPeriodEnd() }
    }

    @Test
    fun `cancelAtPeriodEnd updates the subscription and syncs the seller's plan`() {
        seller.stripeSubscriptionId = "sub_123"
        mockkStatic(Subscription::class)
        val retrieved = mockk<Subscription>()
        val updated = subscriptionMock(status = "active", cancelAtPeriodEnd = true)
        every { Subscription.retrieve("sub_123") } returns retrieved
        every { retrieved.update(mapOf("cancel_at_period_end" to true)) } returns updated

        val result = service.cancelAtPeriodEnd()

        assertTrue(result.cancelAtPeriodEnd)
        assertEquals("pro", result.plan)
    }

    // ---- refreshFromStripe ----

    @Test
    fun `refreshFromStripe retrieves directly when a subscription id is already recorded`() {
        seller.stripeSubscriptionId = "sub_123"
        mockkStatic(Subscription::class)
        every { Subscription.retrieve("sub_123") } returns subscriptionMock(status = "active")

        val result = service.refreshFromStripe()

        assertEquals("pro", result.plan)
        verify(exactly = 0) { Subscription.list(any<SubscriptionListParams>()) }
    }

    @Test
    fun `refreshFromStripe falls back to listing the customer's subscriptions when none is recorded`() {
        seller.stripeSubscriptionId = null
        seller.stripeCustomerId = "cus_1"
        mockkStatic(Subscription::class)
        val collection = mockk<com.stripe.model.SubscriptionCollection> { every { data } returns listOf(subscriptionMock(status = "trialing")) }
        every { Subscription.list(any<SubscriptionListParams>()) } returns collection

        val result = service.refreshFromStripe()

        assertEquals("pro", result.plan)
    }

    @Test
    fun `refreshFromStripe is a no-op when the seller has neither a subscription nor a customer id`() {
        seller.stripeSubscriptionId = null
        seller.stripeCustomerId = null
        seller.plan = SellerPlan.FREE

        val result = service.refreshFromStripe()

        assertEquals("free", result.plan)
        verify(exactly = 0) { sellerRepository.save(any()) }
    }

    // ---- handleCheckoutCompleted ----

    @Test
    fun `handleCheckoutCompleted ignores a non-subscription-mode session`() {
        val session = mockk<Session> { every { mode } returns "payment" }
        service.handleCheckoutCompleted(session)
        verify(exactly = 0) { sellerRepository.findById(any()) }
    }

    @Test
    fun `handleCheckoutCompleted ignores a session with no matching seller`() {
        val id = UUID.randomUUID()
        val session = mockk<Session> {
            every { mode } returns "subscription"
            every { clientReferenceId } returns id.toString()
        }
        every { sellerRepository.findById(id) } returns java.util.Optional.empty()

        service.handleCheckoutCompleted(session)

        verify(exactly = 0) { sellerRepository.save(any()) }
    }

    @Test
    fun `handleCheckoutCompleted records the customer id even when no subscription id is present yet`() {
        val session = mockk<Session> {
            every { mode } returns "subscription"
            every { clientReferenceId } returns seller.id.toString()
            every { customer } returns "cus_1"
            every { subscription } returns null
        }
        every { sellerRepository.findById(seller.id!!) } returns java.util.Optional.of(seller)

        service.handleCheckoutCompleted(session)

        assertEquals("cus_1", seller.stripeCustomerId)
    }

    @Test
    fun `handleCheckoutCompleted syncs plan from the subscription when one is present`() {
        val session = mockk<Session> {
            every { mode } returns "subscription"
            every { clientReferenceId } returns seller.id.toString()
            every { customer } returns "cus_1"
            every { subscription } returns "sub_123"
        }
        every { sellerRepository.findById(seller.id!!) } returns java.util.Optional.of(seller)
        mockkStatic(Subscription::class)
        every { Subscription.retrieve("sub_123") } returns subscriptionMock(status = "active")

        service.handleCheckoutCompleted(session)

        assertEquals(SellerPlan.PRO, seller.plan)
    }

    // ---- handleSubscriptionEvent ----

    @Test
    fun `handleSubscriptionEvent ignores an unmatched subscription`() {
        val subscription = subscriptionMock(status = "canceled", id = "sub_orphan")
        every { sellerRepository.findByStripeSubscriptionId("sub_orphan") } returns null

        service.handleSubscriptionEvent(subscription)

        verify(exactly = 0) { sellerRepository.save(any()) }
    }

    @Test
    fun `handleSubscriptionEvent downgrades to free once a subscription is canceled`() {
        seller.plan = SellerPlan.PRO
        val subscription = subscriptionMock(status = "canceled")
        every { sellerRepository.findByStripeSubscriptionId("sub_123") } returns seller

        service.handleSubscriptionEvent(subscription)

        assertEquals(SellerPlan.FREE, seller.plan)
    }

    // ---- cancelAndDeleteCustomer ----

    @Test
    fun `cancelAndDeleteCustomer does nothing when the seller has no Stripe ids at all`() {
        seller.stripeSubscriptionId = null
        seller.stripeCustomerId = null
        mockkStatic(Subscription::class)
        mockkStatic(Customer::class)

        service.cancelAndDeleteCustomer(seller)

        verify(exactly = 0) { Subscription.retrieve(any<String>()) }
        verify(exactly = 0) { Customer.retrieve(any<String>()) }
    }

    @Test
    fun `cancelAndDeleteCustomer cancels the subscription and deletes the customer`() {
        seller.stripeSubscriptionId = "sub_123"
        seller.stripeCustomerId = "cus_1"
        mockkStatic(Subscription::class)
        mockkStatic(Customer::class)
        val subscription = mockk<Subscription>(relaxed = true)
        val customer = mockk<Customer>(relaxed = true)
        every { Subscription.retrieve("sub_123") } returns subscription
        every { Customer.retrieve("cus_1") } returns customer

        service.cancelAndDeleteCustomer(seller)

        verify { subscription.cancel() }
        verify { customer.delete() }
    }
}
