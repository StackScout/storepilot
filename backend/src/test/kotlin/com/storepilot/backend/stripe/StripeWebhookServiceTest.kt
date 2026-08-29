package com.storepilot.backend.stripe

import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Account
import com.stripe.model.Event
import com.stripe.model.EventDataObjectDeserializer
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class StripeWebhookServiceTest {
    private val stripeProperties = StripeProperties(webhookSecret = "whsec_test")
    private val stripeConnectService = mockk<StripeConnectService>(relaxed = true)
    private val stripeService = mockk<StripeService>(relaxed = true)

    private val service = StripeWebhookService(stripeProperties, stripeConnectService, stripeService)

    @BeforeEach
    fun setUp() {
        mockkStatic(Webhook::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun eventOf(eventType: String, payload: Any?): Event {
        val deserializer = mockk<EventDataObjectDeserializer> {
            every { getObject() } returns if (payload != null) Optional.of(payload as com.stripe.model.StripeObject) else Optional.empty()
            every { deserializeUnsafe() } returns (payload as? com.stripe.model.StripeObject)
        }
        return mockk<Event> {
            every { type } returns eventType
            every { id } returns "evt_1"
            every { dataObjectDeserializer } returns deserializer
        }
    }

    @Test
    fun `handleWebhookEvent ignores a forged signature`() {
        // A real instance, not a mock — mocking a Throwable subtype tends to spin
        // out filling in a synthetic stack trace and can exhaust the test heap.
        every { Webhook.constructEvent(any(), any(), "whsec_test") } throws SignatureVerificationException("bad signature", "bad-sig")

        service.handleWebhookEvent("{}", "bad-sig")

        verify(exactly = 0) { stripeConnectService.syncAccountStatus(any()) }
        verify(exactly = 0) { stripeService.handleCheckoutSessionCompleted(any()) }
    }

    @Test
    fun `handleWebhookEvent syncs account status for account_updated`() {
        val account = mockk<Account>()
        val event = eventOf("account.updated", account)
        every { Webhook.constructEvent(any(), any(), any()) } returns event

        service.handleWebhookEvent("{}", "sig")

        verify { stripeConnectService.syncAccountStatus(account) }
    }

    @Test
    fun `handleWebhookEvent ignores account_updated when the payload isn't an Account`() {
        val event = eventOf("account.updated", mockk<Session>())
        every { Webhook.constructEvent(any(), any(), any()) } returns event

        service.handleWebhookEvent("{}", "sig")

        verify(exactly = 0) { stripeConnectService.syncAccountStatus(any()) }
    }

    @Test
    fun `handleWebhookEvent completes checkout for checkout_session_completed`() {
        val session = mockk<Session>()
        val event = eventOf("checkout.session.completed", session)
        every { Webhook.constructEvent(any(), any(), any()) } returns event

        service.handleWebhookEvent("{}", "sig")

        verify { stripeService.handleCheckoutSessionCompleted(session) }
    }

    @Test
    fun `handleWebhookEvent ignores checkout_session_completed when the payload isn't a Session`() {
        val event = eventOf("checkout.session.completed", mockk<Account>())
        every { Webhook.constructEvent(any(), any(), any()) } returns event

        service.handleWebhookEvent("{}", "sig")

        verify(exactly = 0) { stripeService.handleCheckoutSessionCompleted(any()) }
    }

    @Test
    fun `handleWebhookEvent marks an expired session failed`() {
        val session = mockk<Session>()
        val event = eventOf("checkout.session.expired", session)
        every { Webhook.constructEvent(any(), any(), any()) } returns event

        service.handleWebhookEvent("{}", "sig")

        verify { stripeService.handleCheckoutSessionFailed(session, "Stripe payment session expired") }
    }

    @Test
    fun `handleWebhookEvent marks an async payment failure failed`() {
        val session = mockk<Session>()
        val event = eventOf("checkout.session.async_payment_failed", session)
        every { Webhook.constructEvent(any(), any(), any()) } returns event

        service.handleWebhookEvent("{}", "sig")

        verify { stripeService.handleCheckoutSessionFailed(session, "Stripe payment failed") }
    }

    @Test
    fun `handleWebhookEvent ignores an unhandled event type`() {
        val event = eventOf("customer.created", null)
        every { Webhook.constructEvent(any(), any(), any()) } returns event

        service.handleWebhookEvent("{}", "sig")

        verify(exactly = 0) { stripeConnectService.syncAccountStatus(any()) }
        verify(exactly = 0) { stripeService.handleCheckoutSessionCompleted(any()) }
        verify(exactly = 0) { stripeService.handleCheckoutSessionFailed(any(), any()) }
    }
}
