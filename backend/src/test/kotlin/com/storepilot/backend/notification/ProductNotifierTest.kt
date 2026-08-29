package com.storepilot.backend.notification

import com.storepilot.backend.product.Product
import com.storepilot.backend.product.ProductStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.SellerType
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreSettings
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class ProductNotifierTest {
    private val emailService = mockk<EmailService>(relaxed = true)
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val notificationProperties = NotificationProperties(frontendBaseUrl = "https://storepilot.au")
    private val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
    private val pushTokenRepository = mockk<PushTokenRepository>()

    private val notifier = ProductNotifier(emailService, storeSettingsRepository, notificationProperties, pushNotificationService, pushTokenRepository)

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
        every { pushTokenRepository.findBySellerId(any()) } returns emptyList()
    }

    private fun product() = Product(
        store = store, name = "Handmade Vase", slug = "handmade-vase", description = "description", category = "handicrafts",
        price = 5000, stockQuantity = 3, status = ProductStatus.ACTIVE,
    ).apply { id = UUID.randomUUID() }

    private fun storeSettings() = StoreSettings(
        store = store, contactEmail = "contact@store.example.com", contactPhone = "+61400000001",
        bankAccountName = "Store Account", bankAccountNumber = "123456789", bankName = "Test Bank",
        transactionFeePercent = BigDecimal("3.5"), sellerType = SellerType.INDIVIDUAL,
    )

    @Test
    fun `lowStockAlert emails the store contact and pushes the seller`() {
        val p = product()
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings())
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })
        val bodySlot = slot<String>()

        notifier.lowStockAlert(p)

        verify { emailService.send(to = "contact@store.example.com", subject = match { it.contains(p.name) }, body = capture(bodySlot)) }
        assertTrue(bodySlot.captured.contains("3 left"))
        verify { pushNotificationService.send(listOf("token-1"), match { it.contains(p.name) }, any(), mapOf("type" to "product", "id" to p.id.toString())) }
    }

    @Test
    fun `lowStockAlert skips notifying when the store has no settings row`() {
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.empty()

        notifier.lowStockAlert(product())

        verify(exactly = 0) { emailService.send(any(), any(), any()) }
        verify(exactly = 0) { pushNotificationService.send(any(), any(), any(), any()) }
    }

    @Test
    fun `lowStockAlert swallows an email failure and still attempts the push`() {
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings())
        every { emailService.send(any(), any(), any()) } throws RuntimeException("SES is down")
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })

        notifier.lowStockAlert(product())

        verify { pushNotificationService.send(any(), any(), any(), any()) }
    }

    @Test
    fun `lowStockAlert swallows a push failure`() {
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings())
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })
        every { pushNotificationService.send(any(), any(), any(), any()) } throws RuntimeException("Expo is down")

        notifier.lowStockAlert(product())
    }
}
