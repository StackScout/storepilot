package com.storepilot.backend.notification

import com.storepilot.backend.product.Product
import com.storepilot.backend.product.ProductRepository
import com.storepilot.backend.product.ProductStatus
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
import java.util.UUID

class LowStockAlertJobTest {
    private val productRepository = mockk<ProductRepository>()
    private val productNotifier = mockk<ProductNotifier>(relaxed = true)
    private val notificationProperties = NotificationProperties(lowStockThreshold = 5)

    private val job = LowStockAlertJob(productRepository, productNotifier, notificationProperties)

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

    private fun product() = Product(
        store = store, name = "Handmade Vase", slug = "handmade-vase", description = "description", category = "handicrafts",
        price = 5000, stockQuantity = 3, status = ProductStatus.ACTIVE,
    ).apply { id = UUID.randomUUID() }

    @Test
    fun `alerts and stamps every low-stock product returned by the repository`() {
        val p = product()
        every { productRepository.findLowStock(5) } returns listOf(p)
        every { productRepository.saveAll(any<List<Product>>()) } returns listOf(p)

        job.run()

        verify { productNotifier.lowStockAlert(p) }
        assertNotNull(p.lastLowStockAlertSentAt)
        verify { productRepository.saveAll(listOf(p)) }
    }

    @Test
    fun `does nothing when no product is low on stock`() {
        every { productRepository.findLowStock(5) } returns emptyList()

        job.run()

        verify(exactly = 0) { productNotifier.lowStockAlert(any()) }
        verify(exactly = 0) { productRepository.saveAll(any<List<Product>>()) }
    }
}
