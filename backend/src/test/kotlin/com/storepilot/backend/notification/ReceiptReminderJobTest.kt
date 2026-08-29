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
import java.util.UUID

class ReceiptReminderJobTest {
    private val orderRepository = mockk<OrderRepository>()
    private val orderNotifier = mockk<OrderNotifier>(relaxed = true)
    private val notificationProperties = NotificationProperties(firstReminderAfterHours = 6, reminderIntervalHours = 24)

    private val job = ReceiptReminderJob(orderRepository, orderNotifier, notificationProperties)

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

    private fun order() = Order(
        orderNumber = "AU-1001", store = store, subtotal = 1000, deliveryMethod = DeliveryMethod.SHIPPING,
        shippingFee = 0, platformFee = 35, total = 1000, paymentMethod = PaymentMethod.BANK_TRANSFER, paymentStatus = PaymentStatus.UNPAID,
        shipping = ShippingDetails(), buyerEmail = "buyer@example.com", fulfillmentTimeHours = 48, deliveryTimeHours = 120,
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    @Test
    fun `reminds and stamps every order due for a receipt reminder`() {
        val o = order()
        every { orderRepository.findDueForReceiptReminder(any(), any()) } returns listOf(o)
        every { orderRepository.saveAll(any<List<Order>>()) } returns listOf(o)

        job.run()

        verify { orderNotifier.receiptReminder(o) }
        assertNotNull(o.lastReminderSentAt)
        verify { orderRepository.saveAll(listOf(o)) }
    }

    @Test
    fun `does nothing when no order is due`() {
        every { orderRepository.findDueForReceiptReminder(any(), any()) } returns emptyList()

        job.run()

        verify(exactly = 0) { orderNotifier.receiptReminder(any()) }
        verify(exactly = 0) { orderRepository.saveAll(any<List<Order>>()) }
    }
}
