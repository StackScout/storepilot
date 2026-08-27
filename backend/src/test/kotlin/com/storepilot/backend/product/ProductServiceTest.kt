package com.storepilot.backend.product

import com.storepilot.backend.common.CategoryRepository
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import java.util.Optional
import java.util.UUID

class ProductServiceTest {
    private val productRepository = mockk<ProductRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val currentActor = mockk<CurrentActor>()
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)
    private val wishlistItemRepository = mockk<WishlistItemRepository>()
    private val categoryRepository = mockk<CategoryRepository>()

    private val service = ProductService(productRepository, storeRepository, storeSettingsRepository, currentActor, fileStorageService, wishlistItemRepository, categoryRepository)

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
    private val storeId: UUID = UUID.randomUUID()
    private lateinit var store: Store
    private val image = MockMultipartFile("images", "photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))

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
        every { currentActor.requireSeller() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { storeSettingsRepository.findById(storeId) } returns Optional.empty()
        every { productRepository.findByStoreIdAndSlug(storeId, any()) } returns null
        every { categoryRepository.existsByWireValue(any()) } returns true
    }

    private fun formInput(category: String) = ProductFormInput(
        name = "A product",
        description = "A description long enough",
        category = category,
        price = 1000,
        compareAtPrice = null,
        stockQuantity = 5,
        trackStock = true,
        sku = null,
        status = "active",
    )

    @Test
    fun `create rejects a category that doesn't match the store's own category`() {
        val ex = assertThrows(ConflictException::class.java) {
            service.create(storeId, formInput("electronics"), listOf(image))
        }
        assert(ex.message!!.contains("handicrafts")) { "error should name the store's actual category" }
    }

    @Test
    fun `create succeeds when the category matches the store's category`() {
        every { productRepository.save(any()) } answers {
            (firstArg() as Product).apply {
                id = UUID.randomUUID()
                createdAt = java.time.Instant.now()
                updatedAt = java.time.Instant.now()
                images.forEach { it.id = UUID.randomUUID() }
            }
        }

        val result = service.create(storeId, formInput("handicrafts"), listOf(image))

        assertEquals("handicrafts", result.category)
    }

    @Test
    fun `create rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) {
            service.create(storeId, formInput("handicrafts"), listOf(image))
        }
    }

    @Test
    fun `update rejects changing category away from the store's own category`() {
        val productId = UUID.randomUUID()
        val product = Product(
            store = store,
            name = "Existing",
            slug = "existing",
            description = "description",
            category = "handicrafts",
            price = 500,
            stockQuantity = 1,
            status = ProductStatus.ACTIVE,
        )
        every { productRepository.findById(productId) } returns Optional.of(product)

        assertThrows(ConflictException::class.java) {
            service.update(productId, formInput("jewelry"), emptyList())
        }
    }
}
