package com.storepilot.backend.booking

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.seller.SellerPlan
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class BookingAnalyticsServiceTest {
    private val bookingRepository = mockk<BookingRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val currentActor = mockk<CurrentActor>()
    private val platformConfigService = mockk<PlatformConfigService>()

    private val service = BookingAnalyticsService(bookingRepository, storeRepository, currentActor, platformConfigService)

    private val storeId: UUID = UUID.randomUUID()
    private val sellerId: UUID = UUID.randomUUID()
    private lateinit var seller: Seller
    private lateinit var store: Store

    private fun platformSettings(proPlanEnabled: Boolean) = PlatformSettings(
        name = "StorePilot", tagline = "tagline", countryName = "Australia", countryCode = "AU",
        currencyCode = "AUD", currencySymbol = "$", currencyLocale = "en-AU", platformFeePercent = BigDecimal("3.5"),
        flatShippingFee = 1000, proMonthlyPriceCents = 2900, defaultCodEnabled = true, defaultOnlinePaymentEnabled = false,
        defaultBankTransferEnabled = true, proPlanEnabled = proPlanEnabled, supportEmail = "hello@storepilot.au",
        companyLocation = "Sydney, Australia", timezone = "Australia/Sydney", returnWindowDays = 14,
    )

    @BeforeEach
    fun setUp() {
        seller = mockk()
        every { seller.id } returns sellerId
        every { seller.plan } returns SellerPlan.PRO

        store = mockk()
        every { store.id } returns storeId
        every { store.seller } returns seller

        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { currentActor.requireSeller() } returns seller
        every { platformConfigService.current() } returns platformSettings(proPlanEnabled = true)
    }

    private fun booking(
        serviceName: String,
        total: Int,
        status: BookingStatus,
        paymentStatus: PaymentStatus,
        buyer: Buyer? = null,
    ): Booking =
        Booking(
            bookingNumber = "BK-AU-${UUID.randomUUID()}",
            store = store,
            service = mockk(),
            serviceName = serviceName,
            servicePrice = total,
            serviceDurationMinutes = 30,
            scheduledStart = Instant.now(),
            scheduledEnd = Instant.now().plusSeconds(1800),
            platformFee = 0,
            total = total,
            status = status,
            paymentMethod = PaymentMethod.STRIPE,
            paymentStatus = paymentStatus,
            buyerName = "Test Buyer",
            buyerPhone = "+61400000000",
            buyerEmail = "buyer@example.com",
            buyer = buyer,
        )

    @Test
    fun `getAnalytics rejects a non-Pro seller`() {
        every { seller.plan } returns SellerPlan.FREE
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns emptyList()
        assertThrows(ForbiddenException::class.java) { service.getAnalytics(storeId) }
    }

    @Test
    fun `getAnalytics allows a non-Pro seller when the deployment has no Pro tier concept`() {
        every { seller.plan } returns SellerPlan.FREE
        every { platformConfigService.current() } returns platformSettings(proPlanEnabled = false)
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns emptyList()
        service.getAnalytics(storeId)
    }

    @Test
    fun `getAnalytics rejects a seller who doesn't own the store`() {
        val otherSeller: Seller = mockk()
        every { otherSeller.id } returns UUID.randomUUID()
        every { currentActor.requireSeller() } returns otherSeller
        assertThrows(ForbiddenException::class.java) { service.getAnalytics(storeId) }
    }

    @Test
    fun `getAnalytics computes revenue only from completed and paid bookings`() {
        val bookings = listOf(
            booking("Haircut", 5000, BookingStatus.COMPLETED, PaymentStatus.PAID),
            booking("Haircut", 5000, BookingStatus.COMPLETED, PaymentStatus.UNPAID),
            booking("Haircut", 5000, BookingStatus.PENDING, PaymentStatus.PAID),
        )
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns bookings

        val result = service.getAnalytics(storeId)

        assertEquals(5000, result.totalRevenue)
        assertEquals(3, result.totalBookings)
        assertEquals(2, result.completedBookings)
    }

    @Test
    fun `getAnalytics computes the no-show rate as a percent`() {
        val bookings = listOf(
            booking("Haircut", 5000, BookingStatus.COMPLETED, PaymentStatus.PAID),
            booking("Haircut", 5000, BookingStatus.NO_SHOW, PaymentStatus.UNPAID),
            booking("Haircut", 5000, BookingStatus.NO_SHOW, PaymentStatus.UNPAID),
            booking("Haircut", 5000, BookingStatus.CANCELLED, PaymentStatus.UNPAID),
        )
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns bookings

        val result = service.getAnalytics(storeId)

        assertEquals(2, result.noShowBookings)
        assertEquals(1, result.cancelledBookings)
        assertEquals(50.0, result.noShowRate)
    }

    @Test
    fun `getAnalytics ranks top services by revenue, highest first`() {
        val bookings = listOf(
            booking("Haircut", 3000, BookingStatus.COMPLETED, PaymentStatus.PAID),
            booking("Haircut", 3000, BookingStatus.COMPLETED, PaymentStatus.PAID),
            booking("Massage", 8000, BookingStatus.COMPLETED, PaymentStatus.PAID),
        )
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns bookings

        val result = service.getAnalytics(storeId)

        assertEquals(2, result.topServices.size)
        assertEquals("Massage", result.topServices[0].serviceName)
        assertEquals(8000, result.topServices[0].revenue)
        assertEquals("Haircut", result.topServices[1].serviceName)
        assertEquals(6000, result.topServices[1].revenue)
        assertEquals(2, result.topServices[1].bookingCount)
    }

    @Test
    fun `getAnalytics computes repeat buyer rate excluding guest bookings`() {
        val repeatBuyer: Buyer = mockk()
        every { repeatBuyer.id } returns UUID.randomUUID()
        val oneTimeBuyer: Buyer = mockk()
        every { oneTimeBuyer.id } returns UUID.randomUUID()

        val bookings = listOf(
            booking("Haircut", 3000, BookingStatus.COMPLETED, PaymentStatus.PAID, buyer = repeatBuyer),
            booking("Haircut", 3000, BookingStatus.COMPLETED, PaymentStatus.PAID, buyer = repeatBuyer),
            booking("Haircut", 3000, BookingStatus.COMPLETED, PaymentStatus.PAID, buyer = oneTimeBuyer),
            booking("Haircut", 3000, BookingStatus.COMPLETED, PaymentStatus.PAID, buyer = null),
        )
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns bookings

        val result = service.getAnalytics(storeId)

        // 1 of 2 real buyers (repeatBuyer) has more than one booking — the guest booking (buyer = null) is excluded entirely.
        assertEquals(50.0, result.repeatBuyerRate)
    }

    @Test
    fun `getAnalytics returns all-zero stats for a store with no bookings`() {
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns emptyList()

        val result = service.getAnalytics(storeId)

        assertEquals(0, result.totalBookings)
        assertEquals(0.0, result.noShowRate)
        assertEquals(0.0, result.repeatBuyerRate)
        assertEquals(0, result.totalRevenue)
        assertEquals(emptyList<ServiceAnalytics>(), result.topServices)
    }
}
