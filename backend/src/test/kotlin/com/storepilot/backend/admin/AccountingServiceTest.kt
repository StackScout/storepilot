package com.storepilot.backend.admin

import com.storepilot.backend.order.OrderItemResponse
import com.storepilot.backend.order.OrderResponse
import com.storepilot.backend.order.OrderService
import com.storepilot.backend.order.ShippingDetailsResponse
import com.storepilot.backend.payout.FeeCollection
import com.storepilot.backend.payout.FeeCollectionRepository
import com.storepilot.backend.payout.FeeCollectionStatus
import com.storepilot.backend.payout.Payout
import com.storepilot.backend.payout.PayoutRepository
import com.storepilot.backend.payout.PayoutStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AccountingServiceTest {
    private val payoutRepository = mockk<PayoutRepository>()
    private val feeCollectionRepository = mockk<FeeCollectionRepository>()
    private val orderService = mockk<OrderService>()

    private val service = AccountingService(payoutRepository, feeCollectionRepository, orderService)

    private lateinit var store: Store

    @BeforeEach
    fun setUp() {
        val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
        store = Store(
            seller = seller, slug = "store", name = "Store", tagline = "tagline", description = "description",
            category = StoreCategory.HANDICRAFTS, address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000", verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }
    }

    private fun payout(status: PayoutStatus, net: Int) = Payout(store = store, subtotal = net + 10, platformFee = 10, net = net, status = status)
        .apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    private fun feeCollection(status: FeeCollectionStatus, platformFee: Int) = FeeCollection(store = store, subtotal = 1000, platformFee = platformFee, status = status)
        .apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    private fun orderResponse(total: Int, platformFee: Int) = OrderResponse(
        id = UUID.randomUUID(), orderNumber = "AU-1", storeId = store.id!!, storeName = store.name, storeSlug = store.slug,
        items = emptyList<OrderItemResponse>(), subtotal = total, deliveryMethod = "shipping", shippingFee = 0,
        platformFee = platformFee, total = total, couponCode = null, discountAmount = 0, sellerAbn = null, gstAmount = null,
        status = "delivered", paymentMethod = "stripe", paymentStatus = "paid", receiptUrl = null, trackingNumber = null,
        courierServiceName = null, courierReceiptUrl = null,
        shipping = ShippingDetailsResponse(fullName = null, phone = null, addressLine1 = null, city = null, state = null, postalCode = null),
        timeline = emptyList(), createdAt = Instant.now(), buyerEmail = "buyer@example.com", buyerId = null,
    )

    @Test
    fun `summary sums payouts, fee collections, and Stripe settlements by status`() {
        every { payoutRepository.findAll() } returns listOf(payout(PayoutStatus.SCHEDULED, 100), payout(PayoutStatus.SCHEDULED, 50), payout(PayoutStatus.PAID, 200))
        every { feeCollectionRepository.findAll() } returns listOf(feeCollection(FeeCollectionStatus.PENDING, 30), feeCollection(FeeCollectionStatus.COLLECTED, 40))
        every { orderService.adminListStripeSettlements() } returns listOf(orderResponse(1000, 35), orderResponse(2000, 70))

        val result = service.summary()

        assertEquals(150, result.payoutsScheduledTotal)
        assertEquals(200, result.payoutsPaidTotal)
        assertEquals(30, result.feeCollectionsPendingTotal)
        assertEquals(40, result.feeCollectionsCollectedTotal)
        assertEquals(3000, result.stripeSettledTotal)
        assertEquals(105, result.stripePlatformFeeTotal)
    }

    @Test
    fun `summary handles nothing to report`() {
        every { payoutRepository.findAll() } returns emptyList()
        every { feeCollectionRepository.findAll() } returns emptyList()
        every { orderService.adminListStripeSettlements() } returns emptyList()

        val result = service.summary()

        assertEquals(0, result.payoutsScheduledTotal)
        assertEquals(0, result.stripeSettledTotal)
    }
}
