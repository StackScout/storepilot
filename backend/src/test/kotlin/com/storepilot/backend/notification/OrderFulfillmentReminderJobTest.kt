package com.storepilot.backend.notification

import com.storepilot.backend.common.ShippingDetails
import com.storepilot.backend.order.DeliveryMethod
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class OrderFulfillmentReminderJobTest {
    private val orderRepository = mockk<OrderRepository>()
    private val orderNotifier = mockk<OrderNotifier>(relaxed = true)
    private val notificationProperties = NotificationProperties(fulfillmentDueSoonLeadHours = 6)

    private val job = OrderFulfillmentReminderJob(orderRepository, orderNotifier, notificationProperties)

    private lateinit var store: Store

    @BeforeEach
    fun setUp() {
        val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
        store = Store(
            seller = seller, slug = "store", name = "Handicrafts Store", tagline = "tagline", description = "description",
            category = StoreCategory.HANDICRAFTS, address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000", verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }
    }

    /** fulfillmentTimeHours = 48 -> deadline = createdAt + 48h; the "due soon" lead is 6h. */
    private fun order(createdAgoHours: Long) = Order(
        orderNumber = "AU-1001", store = store, subtotal = 1000, deliveryMethod = DeliveryMethod.SHIPPING,
        shippingFee = 0, platformFee = 35, total = 1000, paymentMethod = PaymentMethod.STRIPE, paymentStatus = PaymentStatus.PAID,
        shipping = ShippingDetails(), buyerEmail = "buyer@example.com", fulfillmentTimeHours = 48, deliveryTimeHours = 120,
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now().minus(createdAgoHours, ChronoUnit.HOURS) }

    @Test
    fun `sends a due-soon push once inside the lead window but not yet past the deadline`() {
        val o = order(createdAgoHours = 43)
        every { orderRepository.findCandidatesForFulfillmentReminder() } returns listOf(o)
        every { orderRepository.saveAll(any<List<Order>>()) } returns listOf(o)

        job.run()

        verify { orderNotifier.fulfillmentDueSoon(o) }
        verify(exactly = 0) { orderNotifier.fulfillmentOverdue(any()) }
        assertNotNull(o.fulfillmentReminderSentAt)
        assertNull(o.fulfillmentOverdueReminderSentAt)
    }

    @Test
    fun `sends an overdue push once past the deadline, not the due-soon push`() {
        val o = order(createdAgoHours = 49)
        every { orderRepository.findCandidatesForFulfillmentReminder() } returns listOf(o)
        every { orderRepository.saveAll(any<List<Order>>()) } returns listOf(o)

        job.run()

        verify { orderNotifier.fulfillmentOverdue(o) }
        verify(exactly = 0) { orderNotifier.fulfillmentDueSoon(any()) }
        assertNotNull(o.fulfillmentOverdueReminderSentAt)
        assertNull(o.fulfillmentReminderSentAt)
    }

    @Test
    fun `does not send a due-soon push while still outside the lead window`() {
        val o = order(createdAgoHours = 10)
        every { orderRepository.findCandidatesForFulfillmentReminder() } returns listOf(o)

        job.run()

        verify(exactly = 0) { orderNotifier.fulfillmentDueSoon(any()) }
        verify(exactly = 0) { orderNotifier.fulfillmentOverdue(any()) }
        verify(exactly = 0) { orderRepository.saveAll(any<List<Order>>()) }
    }

    @Test
    fun `sends neither push once both one-shot flags are already set`() {
        val o = order(createdAgoHours = 50).apply {
            fulfillmentReminderSentAt = Instant.now()
            fulfillmentOverdueReminderSentAt = Instant.now()
        }
        every { orderRepository.findCandidatesForFulfillmentReminder() } returns listOf(o)

        job.run()

        verify(exactly = 0) { orderNotifier.fulfillmentOverdue(any()) }
        verify(exactly = 0) { orderNotifier.fulfillmentDueSoon(any()) }
        verify(exactly = 0) { orderRepository.saveAll(any<List<Order>>()) }
    }
}
