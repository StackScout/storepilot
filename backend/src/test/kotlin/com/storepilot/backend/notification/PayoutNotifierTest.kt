package com.storepilot.backend.notification

import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.payout.Payout
import com.storepilot.backend.payout.PayoutStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class PayoutNotifierTest {
    private val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
    private val pushTokenRepository = mockk<PushTokenRepository>()
    private val platformConfigService = mockk<PlatformConfigService>()
    private val sellerNotificationService = mockk<SellerNotificationService>(relaxed = true)

    private val notifier = PayoutNotifier(pushNotificationService, pushTokenRepository, platformConfigService, sellerNotificationService)

    private lateinit var seller: Seller
    private lateinit var store: Store

    @BeforeEach
    fun setUp() {
        seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
        store = Store(
            seller = seller, slug = "store", name = "Handicrafts Store", tagline = "tagline", description = "description",
            category = "handicrafts", address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000", verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }
        every { platformConfigService.current() } returns PlatformSettings(
            name = "StorePilot", tagline = "tagline", countryName = "Australia", countryCode = "AU",
            currencyCode = "AUD", currencySymbol = "$", currencyLocale = "en-AU", platformFeePercent = BigDecimal("3.5"),
            flatShippingFee = 1000, proMonthlyPriceCents = 2900, defaultCodEnabled = true, defaultOnlinePaymentEnabled = false,
            defaultBankTransferEnabled = true, proPlanEnabled = true, supportEmail = "hello@storepilot.au", companyLocation = "Sydney, Australia",
            timezone = "Australia/Sydney", returnWindowDays = 14,
        )
        every { pushTokenRepository.findBySellerId(any()) } returns emptyList()
    }

    private fun payout() = Payout(store = store, subtotal = 10100, platformFee = 100, net = 10000, status = PayoutStatus.PAID)
        .apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    @Test
    fun `payoutMarkedPaid pushes every registered device with the net amount`() {
        val p = payout()
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })

        notifier.payoutMarkedPaid(p)

        verify {
            pushNotificationService.send(
                listOf("token-1"),
                "Payout sent",
                match { it.contains("AUD 100.00") },
                mapOf("type" to "payout", "id" to p.id.toString()),
            )
        }
    }

    @Test
    fun `payoutMarkedPaid does nothing when the seller has no registered devices`() {
        notifier.payoutMarkedPaid(payout())
        verify(exactly = 0) { pushNotificationService.send(any(), any(), any(), any()) }
    }

    @Test
    fun `payoutMarkedPaid swallows a push failure`() {
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })
        every { pushNotificationService.send(any(), any(), any(), any()) } throws RuntimeException("Expo is down")

        notifier.payoutMarkedPaid(payout())
    }
}
