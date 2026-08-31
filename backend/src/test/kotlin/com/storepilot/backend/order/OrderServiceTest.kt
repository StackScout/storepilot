package com.storepilot.backend.order

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.GuestLookupOtpService
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.ShippingDetails
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.sse.SseHub
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.coupon.CouponKind
import com.storepilot.backend.coupon.CouponResolution
import com.storepilot.backend.coupon.CouponService
import com.storepilot.backend.notification.OrderNotifier
import com.storepilot.backend.product.Product
import com.storepilot.backend.product.ProductService
import com.storepilot.backend.product.ProductStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.seller.SellerPlan
import com.storepilot.backend.store.SellerType
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAccessService
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreSettings
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreStaffMemberRepository
import com.storepilot.backend.store.StoreVerificationStatus
import com.storepilot.backend.stripe.StripeService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class OrderServiceTest {
    private val orderRepository = mockk<OrderRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val productService = mockk<ProductService>()
    private val receiptStorageService = mockk<ReceiptStorageService>(relaxed = true)
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)
    private val orderNotifier = mockk<OrderNotifier>(relaxed = true)
    private val currentActor = mockk<CurrentActor>()
    private val platformConfigService = mockk<PlatformConfigService>()
    private val stripeService = mockk<StripeService>(relaxed = true)
    private val guestLookupOtpService = mockk<GuestLookupOtpService>(relaxed = true)
    private val sseHub = mockk<SseHub>(relaxed = true)
    private val couponService = mockk<CouponService>()
    private val storeStaffMemberRepository = mockk<StoreStaffMemberRepository>(relaxed = true)
    private val storeAccessService = StoreAccessService(currentActor, storeStaffMemberRepository)

    private val service = OrderService(
        orderRepository,
        storeRepository,
        storeSettingsRepository,
        productService,
        receiptStorageService,
        fileStorageService,
        orderNotifier,
        currentActor,
        platformConfigService,
        stripeService,
        guestLookupOtpService,
        sseHub,
        couponService,
        storeAccessService,
    )

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller", plan = SellerPlan.PRO).apply { id = UUID.randomUUID() }
    private val storeId: UUID = UUID.randomUUID()
    private lateinit var store: Store
    private lateinit var storeSettings: StoreSettings
    private lateinit var product: Product
    private val productId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller,
            slug = "handicrafts-store",
            name = "Handicrafts Store",
            tagline = "tagline",
            description = "description",
            category = "handicrafts",
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = storeId }

        storeSettings = StoreSettings(
            store = store,
            contactEmail = "store@example.com",
            contactPhone = "+61400000001",
            bankAccountName = "Handicrafts Store",
            bankAccountNumber = "12345678",
            bankName = "Test Bank",
            transactionFeePercent = BigDecimal("5.0"),
            sellerType = SellerType.INDIVIDUAL,
            pickupEnabled = true,
        )

        product = Product(
            store = store,
            name = "A product",
            slug = "a-product",
            description = "description",
            category = "handicrafts",
            price = 1000,
            stockQuantity = 10,
            trackStock = true,
            status = ProductStatus.ACTIVE,
        ).apply { id = productId }

        every { currentActor.requireSeller() } returns seller
        every { currentActor.buyerOrNull() } returns null
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(storeSettings)
        every { productService.findEntity(productId) } returns product
        every { productService.decrementStock(any()) } returns Unit
        every { productService.restoreStock(any()) } returns Unit
        every { platformConfigService.current() } returns platformSettings()
        every { orderRepository.save(any()) } answers {
            (firstArg() as Order).apply {
                if (id == null) id = UUID.randomUUID()
                if (createdAt == null) createdAt = Instant.now()
                updatedAt = Instant.now()
            }
        }
    }

    private fun platformSettings(
        countryCode: String = "AU",
        onlinePaymentEnabled: Boolean = false,
        codEnabled: Boolean = true,
        bankTransferEnabled: Boolean = true,
        proPlanEnabled: Boolean = true,
    ) = PlatformSettings(
        name = "StorePilot",
        tagline = "tagline",
        countryName = "Australia",
        countryCode = countryCode,
        currencyCode = "AUD",
        currencySymbol = "$",
        currencyLocale = "en-AU",
        platformFeePercent = BigDecimal("3.5"),
        flatShippingFee = 1000,
        proMonthlyPriceCents = 2900,
        defaultCodEnabled = codEnabled,
        defaultOnlinePaymentEnabled = onlinePaymentEnabled,
        defaultBankTransferEnabled = bankTransferEnabled,
        proPlanEnabled = proPlanEnabled,
        supportEmail = "hello@storepilot.au",
        companyLocation = "Sydney, Australia",
        timezone = "Australia/Sydney",
        returnWindowDays = 14,
    )

    private fun checkoutInput(
        quantity: Int = 1,
        paymentMethod: String = "cod",
        deliveryMethod: String = "shipping",
        addressLine1: String? = "1 Test St",
        city: String? = "Sydney",
        state: String? = "NSW",
        postalCode: String? = "2000",
        fullName: String = "Jane Buyer",
        phone: String = "0400000000",
        couponCode: String? = null,
    ) = CheckoutInput(
        storeId = storeId,
        items = listOf(CheckoutItemInput(productId = productId, quantity = quantity)),
        shipping = ShippingDetailsInput(
            fullName = fullName,
            phone = phone,
            addressLine1 = addressLine1,
            city = city,
            state = state,
            postalCode = postalCode,
        ),
        paymentMethod = paymentMethod,
        deliveryMethod = deliveryMethod,
        email = "buyer@example.com",
        couponCode = couponCode,
    )

    // ---- createOrder ----

    @Test
    fun `createOrder rejects insufficient stock when the product tracks stock`() {
        product.stockQuantity = 1
        val ex = assertThrows(ConflictException::class.java) { service.createOrder(checkoutInput(quantity = 5)) }
        assertTrue(ex.message!!.contains("only has"))
    }

    @Test
    fun `createOrder ignores stock when the product doesn't track it`() {
        product.trackStock = false
        product.stockQuantity = 0
        val result = service.createOrder(checkoutInput(quantity = 5))
        assertEquals(5000, result.subtotal)
    }

    @Test
    fun `createOrder throws when the product doesn't exist`() {
        every { productService.findEntity(productId) } returns null
        assertThrows(NotFoundException::class.java) { service.createOrder(checkoutInput()) }
    }

    @Test
    fun `createOrder computes subtotal, platform fee, and total`() {
        val result = service.createOrder(checkoutInput(quantity = 2, paymentMethod = "cod"))
        // subtotal = 2 * 1000 = 2000; platform fee = 5% of 2000 = 100; shipping = 1000 (flat); total = subtotal + shipping = 3000
        assertEquals(2000, result.subtotal)
        assertEquals(100, result.platformFee)
        assertEquals(1000, result.shippingFee)
        assertEquals(3000, result.total)
        assertEquals(0, result.discountAmount)
    }

    @Test
    fun `createOrder applies a coupon discount before computing fee and total`() {
        every { couponService.resolve("SAVE10", storeId, CouponKind.ORDER, 1000) } returns
            CouponResolution(couponId = UUID.randomUUID(), code = "SAVE10", discountAmount = 200)
        every { couponService.recordUse(any()) } returns Unit

        val result = service.createOrder(checkoutInput(couponCode = "SAVE10"))

        // subtotal 1000 - discount 200 = 800 discounted subtotal; fee = 5% of 800 = 40; total = 800 + 1000 shipping = 1800
        assertEquals(1000, result.subtotal)
        assertEquals(200, result.discountAmount)
        assertEquals(40, result.platformFee)
        assertEquals(1800, result.total)
        verify { couponService.recordUse(any()) }
    }

    @Test
    fun `createOrder rejects a blank recipient name`() {
        assertThrows(IllegalArgumentException::class.java) { service.createOrder(checkoutInput(fullName = "  ")) }
    }

    @Test
    fun `createOrder rejects a blank phone number`() {
        assertThrows(IllegalArgumentException::class.java) { service.createOrder(checkoutInput(phone = "")) }
    }

    @Test
    fun `createOrder requires an address for shipping delivery`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.createOrder(checkoutInput(deliveryMethod = "shipping", addressLine1 = null))
        }
    }

    @Test
    fun `createOrder skips the address requirement for pickup and zeroes shipping fee`() {
        val result = service.createOrder(
            checkoutInput(deliveryMethod = "pickup", addressLine1 = null, city = null, state = null, postalCode = null),
        )
        assertEquals(0, result.shippingFee)
        assertEquals("pickup", result.deliveryMethod)
    }

    @Test
    fun `createOrder rejects pickup when the store doesn't offer it`() {
        storeSettings.pickupEnabled = false
        assertThrows(ConflictException::class.java) {
            service.createOrder(checkoutInput(deliveryMethod = "pickup", addressLine1 = null, city = null, state = null, postalCode = null))
        }
    }

    @Test
    fun `createOrder rejects COD for a non-Pro seller`() {
        seller.plan = SellerPlan.FREE
        assertThrows(ConflictException::class.java) { service.createOrder(checkoutInput(paymentMethod = "cod")) }
    }

    @Test
    fun `createOrder rejects bank transfer for a non-Pro seller`() {
        seller.plan = SellerPlan.FREE
        assertThrows(ConflictException::class.java) { service.createOrder(checkoutInput(paymentMethod = "bank-transfer")) }
    }

    @Test
    fun `createOrder allows COD for a non-Pro seller when the deployment has no Pro tier concept`() {
        seller.plan = SellerPlan.FREE
        every { platformConfigService.current() } returns platformSettings(proPlanEnabled = false)
        val result = service.createOrder(checkoutInput(paymentMethod = "cod"))
        assertEquals("cod", result.paymentMethod)
    }

    @Test
    fun `createOrder rejects PayHere when the platform doesn't offer online payment`() {
        // onlinePaymentEnabled = false is the fixture default.
        assertThrows(ConflictException::class.java) { service.createOrder(checkoutInput(paymentMethod = "payhere")) }
    }

    @Test
    fun `createOrder rejects Stripe when the platform doesn't offer online payment`() {
        assertThrows(ConflictException::class.java) { service.createOrder(checkoutInput(paymentMethod = "stripe")) }
    }

    @Test
    fun `createOrder allows Stripe when the platform offers online payment`() {
        every { platformConfigService.current() } returns platformSettings(onlinePaymentEnabled = true)
        val result = service.createOrder(checkoutInput(paymentMethod = "stripe"))
        assertEquals("stripe", result.paymentMethod)
    }

    @Test
    fun `createOrder rejects COD when the platform doesn't offer it`() {
        every { platformConfigService.current() } returns platformSettings(codEnabled = false)
        assertThrows(ConflictException::class.java) { service.createOrder(checkoutInput(paymentMethod = "cod")) }
    }

    @Test
    fun `createOrder rejects bank transfer when the platform doesn't offer it`() {
        every { platformConfigService.current() } returns platformSettings(bankTransferEnabled = false)
        assertThrows(ConflictException::class.java) { service.createOrder(checkoutInput(paymentMethod = "bank-transfer")) }
    }

    @Test
    fun `createOrder snapshots GST amount and ABN when the seller is GST-registered`() {
        storeSettings.gstRegistered = true
        storeSettings.abn = "12345678901"
        val result = service.createOrder(checkoutInput(quantity = 2))
        // total = 3000 (see the plain computation test above); GST = total / 11, rounded
        assertEquals(BigDecimal(3000).divide(BigDecimal(11), 0, java.math.RoundingMode.HALF_UP).toInt(), result.gstAmount)
        assertEquals("12345678901", result.sellerAbn)
    }

    @Test
    fun `createOrder leaves GST fields null when the seller isn't GST-registered`() {
        storeSettings.gstRegistered = false
        val result = service.createOrder(checkoutInput())
        assertNull(result.gstAmount)
        assertNull(result.sellerAbn)
    }

    @Test
    fun `createOrder resolves fulfillment and delivery time from the product override, falling back to the store default`() {
        product.fulfillmentTimeHours = 12
        product.deliveryTimeHours = null
        storeSettings.defaultDeliveryTimeHours = 200

        val slot = slot<Order>()
        every { orderRepository.save(capture(slot)) } answers {
            slot.captured.apply { id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = Instant.now() }
        }

        service.createOrder(checkoutInput())

        assertEquals(12, slot.captured.fulfillmentTimeHours)
        assertEquals(200, slot.captured.deliveryTimeHours)
    }

    @Test
    fun `createOrder links the order to the current buyer when signed in`() {
        val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        every { currentActor.buyerOrNull() } returns buyer

        val result = service.createOrder(checkoutInput())

        assertEquals(buyer.id, result.buyerId)
    }

    @Test
    fun `createOrder decrements stock and notifies the buyer and seller`() {
        service.createOrder(checkoutInput(quantity = 3))

        verify { productService.decrementStock(listOf(productId to 3)) }
        verify { orderNotifier.orderConfirmed(any()) }
        verify { orderNotifier.sellerOrderPlaced(any()) }
    }

    // ---- updateStatus ----

    private fun pendingOrder(paymentMethod: PaymentMethod = PaymentMethod.COD, paymentStatus: PaymentStatus = PaymentStatus.UNPAID) = Order(
        orderNumber = "AU-20260101-1234",
        store = store,
        subtotal = 1000,
        shippingFee = 1000,
        platformFee = 50,
        total = 2000,
        status = OrderStatus.PENDING,
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus,
        shipping = ShippingDetails(fullName = "Jane Buyer", phone = "0400000000"),
        buyerEmail = "buyer@example.com",
        fulfillmentTimeHours = 48,
        deliveryTimeHours = 120,
    ).apply {
        id = UUID.randomUUID()
        createdAt = Instant.now()
        items.add(OrderItem(order = this, productId = productId, productName = "A product", productImageUrl = "", unitPrice = 1000, quantity = 1))
    }

    @Test
    fun `updateStatus rejects a seller who doesn't own the order`() {
        val order = pendingOrder()
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) {
            service.updateStatus(order.id!!, OrderStatusUpdateInput(status = "confirmed"), null)
        }
    }

    @Test
    fun `updateStatus rejects an illegal transition`() {
        val order = pendingOrder()
        order.status = OrderStatus.DELIVERED
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        assertThrows(ConflictException::class.java) {
            service.updateStatus(order.id!!, OrderStatusUpdateInput(status = "confirmed"), null)
        }
    }

    @Test
    fun `updateStatus allows a legal transition`() {
        val order = pendingOrder()
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        val result = service.updateStatus(order.id!!, OrderStatusUpdateInput(status = "confirmed"), null)

        assertEquals("confirmed", result.status)
    }

    @Test
    fun `updateStatus marks a COD order paid on delivery`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.COD)
        order.status = OrderStatus.SHIPPED
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        val result = service.updateStatus(order.id!!, OrderStatusUpdateInput(status = "delivered"), null)

        assertEquals("paid", result.paymentStatus)
    }

    @Test
    fun `updateStatus restores stock when an order is cancelled`() {
        val order = pendingOrder()
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        service.updateStatus(order.id!!, OrderStatusUpdateInput(status = "cancelled"), null)

        verify { productService.restoreStock(listOf(productId to 1)) }
    }

    @Test
    fun `updateStatus refunds via Stripe and marks the order refunded when cancelling a paid Stripe order`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.STRIPE, paymentStatus = PaymentStatus.PAID)
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        val result = service.updateStatus(order.id!!, OrderStatusUpdateInput(status = "cancelled"), null)

        verify { stripeService.refundPayment(order) }
        assertEquals("refunded", result.paymentStatus)
    }

    @Test
    fun `updateStatus doesn't call Stripe when cancelling a paid non-Stripe order`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.BANK_TRANSFER, paymentStatus = PaymentStatus.PAID)
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        val result = service.updateStatus(order.id!!, OrderStatusUpdateInput(status = "cancelled"), null)

        verify(exactly = 0) { stripeService.refundPayment(any()) }
        assertEquals("refunded", result.paymentStatus)
    }

    @Test
    fun `updateStatus requires a tracking number and courier name to mark shipped`() {
        val order = pendingOrder()
        order.status = OrderStatus.CONFIRMED
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        assertThrows(IllegalArgumentException::class.java) {
            service.updateStatus(order.id!!, OrderStatusUpdateInput(status = "shipped"), null)
        }
    }

    @Test
    fun `updateStatus records tracking details and shippedAt when marking shipped`() {
        val order = pendingOrder()
        order.status = OrderStatus.CONFIRMED
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        val result = service.updateStatus(
            order.id!!,
            OrderStatusUpdateInput(status = "shipped", trackingNumber = "TRACK123", courierServiceName = "Auspost"),
            null,
        )

        assertEquals("shipped", result.status)
        assertEquals("TRACK123", result.trackingNumber)
        assertEquals("Auspost", result.courierServiceName)
        verify { orderNotifier.orderShipped(any(), null) }
    }

    // ---- uploadReceipt ----

    @Test
    fun `uploadReceipt rejects a non-bank-transfer order`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.COD)
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)
        val file = org.springframework.mock.web.MockMultipartFile("file", "receipt.jpg", "image/jpeg", byteArrayOf(1))

        assertThrows(ConflictException::class.java) { service.uploadReceipt(order.id!!, file) }
    }

    @Test
    fun `uploadReceipt rejects an order that's already paid`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.BANK_TRANSFER, paymentStatus = PaymentStatus.PAID)
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)
        val file = org.springframework.mock.web.MockMultipartFile("file", "receipt.jpg", "image/jpeg", byteArrayOf(1))

        assertThrows(ConflictException::class.java) { service.uploadReceipt(order.id!!, file) }
    }

    @Test
    fun `uploadReceipt stores the receipt and notifies the seller`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.BANK_TRANSFER)
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)
        every { receiptStorageService.store(any()) } returns "receipts/abc.jpg"
        val file = org.springframework.mock.web.MockMultipartFile("file", "receipt.jpg", "image/jpeg", byteArrayOf(1))

        service.uploadReceipt(order.id!!, file)

        assertEquals("receipts/abc.jpg", order.receiptUrl)
        verify { orderNotifier.receiptUploaded(order) }
    }

    // ---- verifyBankTransfer ----

    @Test
    fun `verifyBankTransfer approves payment and confirms a pending order`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.BANK_TRANSFER)
        order.receiptUrl = "receipts/abc.jpg"
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        val result = service.verifyBankTransfer(order.id!!, VerifyBankTransferInput(approved = true))

        assertEquals("paid", result.paymentStatus)
        assertEquals("confirmed", result.status)
    }

    @Test
    fun `verifyBankTransfer rejection clears the receipt and leaves the order unpaid`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.BANK_TRANSFER)
        order.receiptUrl = "receipts/abc.jpg"
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        val result = service.verifyBankTransfer(order.id!!, VerifyBankTransferInput(approved = false, note = "Wrong amount"))

        assertNull(order.receiptUrl)
        assertEquals("unpaid", result.paymentStatus)
        assertEquals("pending", result.status)
    }

    @Test
    fun `verifyBankTransfer rejects a non-owning seller`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.BANK_TRANSFER)
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) {
            service.verifyBankTransfer(order.id!!, VerifyBankTransferInput(approved = true))
        }
    }

    // ---- cancelBankTransferOrder ----

    @Test
    fun `cancelBankTransferOrder rejects a non-bank-transfer order`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.COD)
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        assertThrows(ConflictException::class.java) { service.cancelBankTransferOrder(order.id!!) }
    }

    @Test
    fun `cancelBankTransferOrder rejects a non-pending order`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.BANK_TRANSFER)
        order.status = OrderStatus.CONFIRMED
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        assertThrows(ConflictException::class.java) { service.cancelBankTransferOrder(order.id!!) }
    }

    @Test
    fun `cancelBankTransferOrder rejects when a receipt is already on file`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.BANK_TRANSFER)
        order.receiptUrl = "receipts/abc.jpg"
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        assertThrows(ConflictException::class.java) { service.cancelBankTransferOrder(order.id!!) }
    }

    @Test
    fun `cancelBankTransferOrder succeeds when no receipt has ever been uploaded`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.BANK_TRANSFER)
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        val result = service.cancelBankTransferOrder(order.id!!)

        assertEquals("cancelled", result.status)
        assertTrue(result.timeline.last().note!!.contains("before a payment receipt"))
    }

    @Test
    fun `cancelBankTransferOrder is available again after a rejected receipt`() {
        val order = pendingOrder(paymentMethod = PaymentMethod.BANK_TRANSFER)
        order.timeline.add(OrderTimelineEntry(order = order, status = OrderStatus.PENDING, label = "Payment receipt rejected", timestamp = Instant.now()))
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)

        val result = service.cancelBankTransferOrder(order.id!!)

        assertEquals("cancelled", result.status)
        assertTrue(result.timeline.last().note!!.contains("receipt was rejected"))
    }

    // ---- guest lookup ----

    @Test
    fun `requestLookupCode is a silent no-op when the order number and phone don't match`() {
        every { orderRepository.findByOrderNumberIgnoreCase(any()) } returns null

        service.requestLookupCode("AU-20260101-9999", "0400000000")

        verify(exactly = 0) { guestLookupOtpService.requestCode(any(), any(), any(), any()) }
    }

    @Test
    fun `requestLookupCode sends a code when the order number and phone match`() {
        val order = pendingOrder()
        order.shipping.phone = "0400000000"
        every { orderRepository.findByOrderNumberIgnoreCase("AU-20260101-1234") } returns order

        service.requestLookupCode("AU-20260101-1234", "0400000000")

        verify { guestLookupOtpService.requestCode("order", order.id!!, order.buyerEmail, any()) }
    }

    @Test
    fun `verifyLookupCode throws when the order number and phone don't match`() {
        every { orderRepository.findByOrderNumberIgnoreCase(any()) } returns null

        assertThrows(NotFoundException::class.java) { service.verifyLookupCode("AU-20260101-9999", "0400000000", "123456") }
    }

    @Test
    fun `verifyLookupCode returns the order once the code is verified`() {
        val order = pendingOrder()
        order.shipping.phone = "0400000000"
        every { orderRepository.findByOrderNumberIgnoreCase("AU-20260101-1234") } returns order
        every { guestLookupOtpService.verifyCode("order", order.id!!, "123456") } returns Unit

        val result = service.verifyLookupCode("AU-20260101-1234", "0400000000", "123456")

        assertEquals(order.id, result.id)
    }

    // ---- listByStore / listStripeSettlementsByStore / getById ----

    @Test
    fun `listByStore rejects a seller who doesn't own the store`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) { service.listByStore(storeId, null, 0, 20) }
    }

    @Test
    fun `listByStore returns the store's orders`() {
        val order = pendingOrder()
        every { orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId, any()) } returns
            org.springframework.data.domain.PageImpl(listOf(order))

        val result = service.listByStore(storeId, null, 0, 20)

        assertEquals(1, result.content.size)
        assertFalse(result.content.isEmpty())
    }

    @Test
    fun `getById throws NotFoundException for a missing order`() {
        val id = UUID.randomUUID()
        every { orderRepository.findById(id) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.getById(id) }
    }
}
