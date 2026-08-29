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
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class OrderDeliveryReminderJobTest {
    private val orderRepository = mockk<OrderRepository>()
    private val orderNotifier = mockk<OrderNotifier>(relaxed = true)

    private val job = OrderDeliveryReminderJob(orderRepository, orderNotifier)

    private lateinit var store: Store

    @BeforeEach
    fun setUp() {
        val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
        store = Store(
            seller = seller, slug = "store", name = "Handicrafts Store", tagline = "tagline", description = "description",
            category = "handicrafts", address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000", verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }
    }

    /** deliveryTimeHours = 120 -> deadline = shippedAt + 120h. */
    private fun order(shippedAt: Instant?) = Order(
        orderNumber = "AU-1001", store = store, subtotal = 1000, deliveryMethod = DeliveryMethod.SHIPPING,
        shippingFee = 0, platformFee = 35, total = 1000, paymentMethod = PaymentMethod.COD, paymentStatus = PaymentStatus.UNPAID,
        shipping = ShippingDetails(), buyerEmail = "buyer@example.com", fulfillmentTimeHours = 48, deliveryTimeHours = 120, shippedAt = shippedAt,
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    @Test
    fun `reminds once past the shipped-plus-delivery-window deadline`() {
        val o = order(shippedAt = Instant.now().minus(121, ChronoUnit.HOURS))
        every { orderRepository.findCandidatesForDeliveryReminder() } returns listOf(o)
        every { orderRepository.saveAll(any<List<Order>>()) } returns listOf(o)

        job.run()

        verify { orderNotifier.deliveryDueReminder(o) }
        assertNotNull(o.deliveryReminderSentAt)
        verify { orderRepository.saveAll(listOf(o)) }
    }

    @Test
    fun `does not remind while still inside the delivery window`() {
        val o = order(shippedAt = Instant.now().minus(10, ChronoUnit.HOURS))
        every { orderRepository.findCandidatesForDeliveryReminder() } returns listOf(o)

        job.run()

        verify(exactly = 0) { orderNotifier.deliveryDueReminder(any()) }
        verify(exactly = 0) { orderRepository.saveAll(any<List<Order>>()) }
    }

    @Test
    fun `skips an order that was never shipped`() {
        val o = order(shippedAt = null)
        every { orderRepository.findCandidatesForDeliveryReminder() } returns listOf(o)

        job.run()

        verify(exactly = 0) { orderNotifier.deliveryDueReminder(any()) }
        verify(exactly = 0) { orderRepository.saveAll(any<List<Order>>()) }
    }
}
