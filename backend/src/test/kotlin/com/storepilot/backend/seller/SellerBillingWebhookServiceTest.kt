package com.storepilot.backend.seller

import com.storepilot.backend.stripe.StripeProperties
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.EventDataObjectDeserializer
import com.stripe.model.StripeObject
import com.stripe.model.Subscription
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

class SellerBillingWebhookServiceTest {
    private val stripeProperties = StripeProperties(billingWebhookSecret = "whsec_billing_test")
    private val sellerBillingService = mockk<SellerBillingService>(relaxed = true)

    private val service = SellerBillingWebhookService(stripeProperties, sellerBillingService)

    @BeforeEach
    fun setUp() {
        mockkStatic(Webhook::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun eventOf(eventType: String, payload: StripeObject?): Event {
        val deserializer = mockk<EventDataObjectDeserializer> {
            every { getObject() } returns Optional.ofNullable(payload)
            every { deserializeUnsafe() } returns payload
        }
        return mockk<Event> {
            every { type } returns eventType
            every { id } returns "evt_billing_1"
            every { dataObjectDeserializer } returns deserializer
        }
    }

    @Test
    fun `handleWebhookEvent ignores a forged signature`() {
        every { Webhook.constructEvent(any(), any(), "whsec_billing_test") } throws
            SignatureVerificationException("bad signature", "bad-sig")

        service.handleWebhookEvent("{}", "bad-sig")

        verify(exactly = 0) { sellerBillingService.handleCheckoutCompleted(any()) }
        verify(exactly = 0) { sellerBillingService.handleSubscriptionEvent(any()) }
    }

    @Test
    fun `handleWebhookEvent completes billing checkout for checkout_session_completed`() {
        val session = mockk<Session>()
        every { Webhook.constructEvent(any(), any(), any()) } returns eventOf("checkout.session.completed", session)

        service.handleWebhookEvent("{}", "sig")

        verify { sellerBillingService.handleCheckoutCompleted(session) }
    }

    @Test
    fun `handleWebhookEvent ignores checkout_session_completed when the payload isn't a Session`() {
        every { Webhook.constructEvent(any(), any(), any()) } returns eventOf("checkout.session.completed", mockk<Subscription>())

        service.handleWebhookEvent("{}", "sig")

        verify(exactly = 0) { sellerBillingService.handleCheckoutCompleted(any()) }
    }

    @Test
    fun `handleWebhookEvent syncs on customer_subscription_updated`() {
        val subscription = mockk<Subscription>()
        every { Webhook.constructEvent(any(), any(), any()) } returns eventOf("customer.subscription.updated", subscription)

        service.handleWebhookEvent("{}", "sig")

        verify { sellerBillingService.handleSubscriptionEvent(subscription) }
    }

    @Test
    fun `handleWebhookEvent syncs on customer_subscription_deleted`() {
        val subscription = mockk<Subscription>()
        every { Webhook.constructEvent(any(), any(), any()) } returns eventOf("customer.subscription.deleted", subscription)

        service.handleWebhookEvent("{}", "sig")

        verify { sellerBillingService.handleSubscriptionEvent(subscription) }
    }

    @Test
    fun `handleWebhookEvent ignores subscription events with no deserializable payload`() {
        every { Webhook.constructEvent(any(), any(), any()) } returns eventOf("customer.subscription.updated", null)

        service.handleWebhookEvent("{}", "sig")

        verify(exactly = 0) { sellerBillingService.handleSubscriptionEvent(any()) }
    }

    @Test
    fun `handleWebhookEvent ignores an unhandled event type`() {
        every { Webhook.constructEvent(any(), any(), any()) } returns eventOf("invoice.paid", null)

        service.handleWebhookEvent("{}", "sig")

        verify(exactly = 0) { sellerBillingService.handleCheckoutCompleted(any()) }
        verify(exactly = 0) { sellerBillingService.handleSubscriptionEvent(any()) }
    }
}
