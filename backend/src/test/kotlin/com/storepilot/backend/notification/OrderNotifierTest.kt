package com.storepilot.backend.notification

import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.ShippingDetails
import com.storepilot.backend.order.DeliveryMethod
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.returns.ReturnReasonCategory
import com.storepilot.backend.returns.ReturnRequest
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.SellerType
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreSettings
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class OrderNotifierTest {
    private val emailService = mockk<EmailService>(relaxed = true)
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val notificationProperties = NotificationProperties(frontendBaseUrl = "https://storepilot.au")
    private val platformConfigService = mockk<PlatformConfigService>()
    private val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
    private val pushTokenRepository = mockk<PushTokenRepository>()

    private val notifier = OrderNotifier(emailService, storeSettingsRepository, notificationProperties, platformConfigService, pushNotificationService, pushTokenRepository)

    private lateinit var seller: Seller
    private lateinit var store: Store

    @BeforeEach
    fun setUp() {
        seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
        store = Store(
            seller = seller, slug = "store", name = "Handicrafts Store", tagline = "tagline", description = "description",
            category = StoreCategory.HANDICRAFTS, address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000", verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }
        every { platformConfigService.current() } returns PlatformSettings(
            name = "StorePilot", tagline = "tagline", countryName = "Australia", countryCode = "AU",
            currencyCode = "AUD", currencySymbol = "$", currencyLocale = "en-AU", platformFeePercent = BigDecimal("3.5"),
            flatShippingFee = 1000, proMonthlyPriceCents = 2900, defaultCodEnabled = true, defaultOnlinePaymentEnabled = false,
            defaultBankTransferEnabled = true, supportEmail = "hello@storepilot.au", companyLocation = "Sydney, Australia",
            timezone = "Australia/Sydney", returnWindowDays = 14,
        )
        every { pushTokenRepository.findBySellerId(any()) } returns emptyList()
    }

    private fun order(
        sellerAbn: String? = null,
        gstAmount: Int? = null,
        trackingNumber: String? = null,
        courierServiceName: String? = null,
    ) = Order(
        orderNumber = "AU-1001", store = store, subtotal = 1000, deliveryMethod = DeliveryMethod.SHIPPING,
        shippingFee = 0, platformFee = 35, total = 1000, paymentMethod = PaymentMethod.STRIPE, paymentStatus = PaymentStatus.PAID,
        shipping = ShippingDetails(), buyerEmail = "buyer@example.com", sellerAbn = sellerAbn, gstAmount = gstAmount,
        trackingNumber = trackingNumber, courierServiceName = courierServiceName, fulfillmentTimeHours = 48, deliveryTimeHours = 120,
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    private fun storeSettings() = StoreSettings(
        store = store, contactEmail = "contact@store.example.com", contactPhone = "+61400000001",
        bankAccountName = "Store Account", bankAccountNumber = "123456789", bankName = "Test Bank",
        transactionFeePercent = BigDecimal("3.5"), sellerType = SellerType.INDIVIDUAL,
    )

    @Test
    fun `sellerOrderPlaced sends a push to every registered device`() {
        val o = order()
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(
            PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() },
            PushToken(seller = seller, token = "token-2", platform = "android").apply { id = UUID.randomUUID() },
        )

        notifier.sellerOrderPlaced(o)

        verify {
            pushNotificationService.send(
                listOf("token-1", "token-2"),
                match { it.contains(o.orderNumber) },
                any(),
                mapOf("type" to "order", "id" to o.id.toString()),
            )
        }
    }

    @Test
    fun `sellerOrderPlaced does nothing when the seller has no registered devices`() {
        val o = order()

        notifier.sellerOrderPlaced(o)

        verify(exactly = 0) { pushNotificationService.send(any(), any(), any(), any()) }
    }

    @Test
    fun `sellerOrderPlaced swallows a push failure`() {
        val o = order()
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })
        every { pushNotificationService.send(any(), any(), any(), any()) } throws RuntimeException("Expo is down")

        notifier.sellerOrderPlaced(o)
    }

    @Test
    fun `fulfillmentDueSoon, fulfillmentOverdue, and deliveryDueReminder each send a distinct push`() {
        val o = order()
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })

        notifier.fulfillmentDueSoon(o)
        notifier.fulfillmentOverdue(o)
        notifier.deliveryDueReminder(o)

        verify { pushNotificationService.send(any(), match { it.startsWith("Ship soon") }, any(), any()) }
        verify { pushNotificationService.send(any(), match { it.startsWith("Overdue") }, any(), any()) }
        verify { pushNotificationService.send(any(), match { it.startsWith("Check delivery") }, any(), any()) }
    }

    @Test
    fun `orderConfirmed sends a plain confirmation when the order has no GST snapshot`() {
        val o = order()
        val subjectSlot = slot<String>()
        val bodySlot = slot<String>()

        notifier.orderConfirmed(o)

        verify { emailService.send(to = "buyer@example.com", subject = capture(subjectSlot), body = capture(bodySlot), attachment = null) }
        assertTrue(subjectSlot.captured.contains("confirmed"))
        assertFalse(bodySlot.captured.contains("TAX INVOICE"))
        assertFalse(bodySlot.captured.contains("Seller ABN"))
    }

    @Test
    fun `orderConfirmed renders a tax invoice when the order has an ABN and GST snapshot`() {
        val o = order(sellerAbn = "12345678901", gstAmount = 91)
        val subjectSlot = slot<String>()
        val bodySlot = slot<String>()

        notifier.orderConfirmed(o)

        verify { emailService.send(to = "buyer@example.com", subject = capture(subjectSlot), body = capture(bodySlot), attachment = null) }
        assertTrue(subjectSlot.captured.contains("Tax invoice"))
        assertTrue(bodySlot.captured.contains("TAX INVOICE"))
        assertTrue(bodySlot.captured.contains("Seller ABN: 12345678901"))
        assertTrue(bodySlot.captured.contains("Includes GST: AUD 0.91"))
    }

    @Test
    fun `receiptUploaded emails the store's contact address and pushes the seller`() {
        val o = order()
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings())
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })

        notifier.receiptUploaded(o)

        verify { emailService.send(to = "contact@store.example.com", subject = any(), body = any(), attachment = null) }
        verify { pushNotificationService.send(any(), match { it.contains("Receipt uploaded") }, any(), any()) }
    }

    @Test
    fun `receiptUploaded skips notifying when the store has no settings row`() {
        val o = order()
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.empty()

        notifier.receiptUploaded(o)

        verify(exactly = 0) { emailService.send(any(), any(), any(), any()) }
    }

    @Test
    fun `bankTransferVerified sends an approval email when approved`() {
        val o = order()
        val bodySlot = slot<String>()

        notifier.bankTransferVerified(o, approved = true, note = null)

        verify { emailService.send(to = "buyer@example.com", subject = match { it.contains("confirmed") }, body = capture(bodySlot), attachment = null) }
        assertTrue(bodySlot.captured.contains("confirmed"))
    }

    @Test
    fun `bankTransferVerified includes the rejection note when declined`() {
        val o = order()
        val bodySlot = slot<String>()

        notifier.bankTransferVerified(o, approved = false, note = "Amount doesn't match")

        verify { emailService.send(to = "buyer@example.com", subject = match { it.contains("rejected") }, body = capture(bodySlot), attachment = null) }
        assertTrue(bodySlot.captured.contains("Amount doesn't match"))
    }

    @Test
    fun `orderShipped attaches the courier receipt file when present`() {
        val o = order(trackingNumber = "TRACK123", courierServiceName = "AusPost")
        val file = MockMultipartFile("receipt", "receipt.pdf", "application/pdf", "content".toByteArray())
        val attachmentSlot = slot<EmailAttachment>()

        notifier.orderShipped(o, file)

        verify { emailService.send(to = "buyer@example.com", subject = any(), body = any(), attachment = capture(attachmentSlot)) }
        assertEquals("receipt.pdf", attachmentSlot.captured.filename)
        assertEquals("application/pdf", attachmentSlot.captured.contentType)
    }

    @Test
    fun `orderShipped sends no attachment when the courier receipt file is null`() {
        val o = order(trackingNumber = "TRACK123", courierServiceName = "AusPost")

        notifier.orderShipped(o, null)

        verify { emailService.send(to = "buyer@example.com", subject = any(), body = any(), attachment = null) }
    }

    @Test
    fun `orderShipped sends no attachment when the courier receipt file is empty`() {
        val o = order(trackingNumber = "TRACK123", courierServiceName = "AusPost")
        val emptyFile = MockMultipartFile("receipt", "receipt.pdf", "application/pdf", ByteArray(0))

        notifier.orderShipped(o, emptyFile)

        verify { emailService.send(to = "buyer@example.com", subject = any(), body = any(), attachment = null) }
    }

    @Test
    fun `returnRequested emails the store and pushes the seller`() {
        val o = order()
        val request = ReturnRequest(order = o, reasonCategory = ReturnReasonCategory.DEFECTIVE, reasonNote = "Box was crushed").apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings())
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })
        val bodySlot = slot<String>()

        notifier.returnRequested(o, request)

        verify { emailService.send(to = "contact@store.example.com", subject = any(), body = capture(bodySlot), attachment = null) }
        assertTrue(bodySlot.captured.contains("Box was crushed"))
    }

    @Test
    fun `returnRequested skips notifying when the store has no settings row`() {
        val o = order()
        val request = ReturnRequest(order = o, reasonCategory = ReturnReasonCategory.DEFECTIVE).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.empty()

        notifier.returnRequested(o, request)

        verify(exactly = 0) { emailService.send(any(), any(), any(), any()) }
    }

    @Test
    fun `returnDecided emails an approval or decline with an optional note`() {
        val o = order()
        val declineBodySlot = slot<String>()

        notifier.returnDecided(o, approved = true, note = null)
        notifier.returnDecided(o, approved = false, note = "Item wasn't damaged")

        verify { emailService.send(to = "buyer@example.com", subject = match { it.contains("approved") }, body = any(), attachment = null) }
        verify { emailService.send(to = "buyer@example.com", subject = match { it.contains("declined") }, body = capture(declineBodySlot), attachment = null) }
        assertTrue(declineBodySlot.captured.contains("Item wasn't damaged"))
    }

    @Test
    fun `returnRefunded includes the refund reference when present`() {
        val o = order()
        val bodySlot = slot<String>()

        notifier.returnRefunded(o, refundReference = "re_12345")

        verify { emailService.send(to = "buyer@example.com", subject = any(), body = capture(bodySlot), attachment = null) }
        assertTrue(bodySlot.captured.contains("re_12345"))
    }

    @Test
    fun `receiptReminder emails the buyer with the order URL`() {
        val o = order()
        val bodySlot = slot<String>()

        notifier.receiptReminder(o)

        verify { emailService.send(to = "buyer@example.com", subject = any(), body = capture(bodySlot), attachment = null) }
        assertTrue(bodySlot.captured.contains("https://storepilot.au/orders/${o.id}"))
    }

    @Test
    fun `an email send failure never propagates`() {
        val o = order()
        every { emailService.send(any(), any(), any(), any()) } throws RuntimeException("SES is down")

        notifier.receiptReminder(o)
    }
}
