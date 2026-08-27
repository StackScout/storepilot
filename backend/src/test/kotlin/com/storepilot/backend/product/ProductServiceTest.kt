package com.storepilot.backend.product

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.SellerType
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreSettings
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class ProductServiceTest {
    private val productRepository = mockk<ProductRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val currentActor = mockk<CurrentActor>()
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)
    private val wishlistItemRepository = mockk<WishlistItemRepository>()

    private val service = ProductService(productRepository, storeRepository, storeSettingsRepository, currentActor, fileStorageService, wishlistItemRepository)

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
            category = StoreCategory.HANDICRAFTS,
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = storeId }
        every { currentActor.requireSeller() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { storeSettingsRepository.findById(storeId) } returns Optional.empty()
        every { productRepository.findByStoreIdAndSlug(storeId, any()) } returns null
    }

    private fun product(
        stockQuantity: Int = 5,
        trackStock: Boolean = true,
        status: ProductStatus = ProductStatus.ACTIVE,
        sku: String? = null,
    ) = Product(
        store = store, name = "Existing", slug = "existing", description = "description", category = StoreCategory.HANDICRAFTS,
        price = 500, stockQuantity = stockQuantity, trackStock = trackStock, status = status, sku = sku,
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = Instant.now() }

    private fun storeSettings(stockManagementEnabled: Boolean) = StoreSettings(
        store = store, contactEmail = "contact@store.example.com", contactPhone = "+61400000001",
        bankAccountName = "Store Account", bankAccountNumber = "123456789", bankName = "Test Bank",
        transactionFeePercent = BigDecimal("3.5"), sellerType = SellerType.INDIVIDUAL, stockManagementEnabled = stockManagementEnabled,
    )

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
            category = StoreCategory.HANDICRAFTS,
            price = 500,
            stockQuantity = 1,
            status = ProductStatus.ACTIVE,
        )
        every { productRepository.findById(productId) } returns Optional.of(product)

        assertThrows(ConflictException::class.java) {
            service.update(productId, formInput("jewelry"), emptyList())
        }
    }

    @Test
    fun `create requires at least one image`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.create(storeId, formInput("handicrafts"), emptyList())
        }
    }

    @Test
    fun `create rejects a duplicate SKU within the same store`() {
        every { productRepository.findByStoreIdAndSkuIgnoreCase(storeId, "SKU-1") } returns product(sku = "SKU-1")

        val ex = assertThrows(ConflictException::class.java) {
            service.create(storeId, formInput("handicrafts").copy(sku = "SKU-1"), listOf(image))
        }
        assertTrue(ex.message!!.contains("SKU-1"))
    }

    @Test
    fun `create disables trackStock when the store has disabled stock management`() {
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(storeSettings(stockManagementEnabled = false))
        val savedSlot = slot<Product>()
        every { productRepository.save(capture(savedSlot)) } answers {
            (firstArg() as Product).apply { id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = Instant.now(); images.forEach { it.id = UUID.randomUUID() } }
        }

        service.create(storeId, formInput("handicrafts"), listOf(image))

        assertFalse(savedSlot.captured.trackStock)
    }

    @Test
    fun `create forces out-of-stock status when trackStock is on and quantity is zero`() {
        val savedSlot = slot<Product>()
        every { productRepository.save(capture(savedSlot)) } answers {
            (firstArg() as Product).apply { id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = Instant.now(); images.forEach { it.id = UUID.randomUUID() } }
        }

        service.create(storeId, formInput("handicrafts").copy(stockQuantity = 0), listOf(image))

        assertEquals(ProductStatus.OUT_OF_STOCK, savedSlot.captured.status)
    }

    @Test
    fun `create de-dupes the slug against an existing one in the same store`() {
        every { productRepository.findByStoreIdAndSlug(storeId, "a-product") } returns product()
        every { productRepository.findByStoreIdAndSlug(storeId, "a-product-2") } returns null
        val savedSlot = slot<Product>()
        every { productRepository.save(capture(savedSlot)) } answers {
            (firstArg() as Product).apply { id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = Instant.now(); images.forEach { it.id = UUID.randomUUID() } }
        }

        service.create(storeId, formInput("handicrafts"), listOf(image))

        assertEquals("a-product-2", savedSlot.captured.slug)
    }

    @Test
    fun `update rejects a non-owning seller`() {
        val productId = UUID.randomUUID()
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        every { productRepository.findById(productId) } returns Optional.of(product())

        assertThrows(ForbiddenException::class.java) { service.update(productId, formInput("handicrafts"), emptyList()) }
    }

    @Test
    fun `update throws for a missing product`() {
        val productId = UUID.randomUUID()
        every { productRepository.findById(productId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.update(productId, formInput("handicrafts"), emptyList()) }
    }

    @Test
    fun `update clears the pending low-stock alert once a restock raises quantity`() {
        val existing = product(stockQuantity = 2).apply { lastLowStockAlertSentAt = Instant.now() }
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { productRepository.save(any()) } answers { firstArg() }

        service.update(existing.id!!, formInput("handicrafts").copy(stockQuantity = 10), emptyList())

        assertEquals(null, existing.lastLowStockAlertSentAt)
    }

    @Test
    fun `update does not clear the low-stock alert when quantity doesn't increase`() {
        val existing = product(stockQuantity = 5).apply { lastLowStockAlertSentAt = Instant.now() }
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { productRepository.save(any()) } answers { firstArg() }

        service.update(existing.id!!, formInput("handicrafts").copy(stockQuantity = 3), emptyList())

        assertTrue(existing.lastLowStockAlertSentAt != null)
    }

    @Test
    fun `update replaces images only when new files are provided`() {
        val existing = product()
        existing.images.add(ProductImage(product = existing, url = "old.jpg", alt = "old", sortOrder = 0).apply { id = UUID.randomUUID() })
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { productRepository.save(any()) } answers { (firstArg() as Product).apply { images.forEach { if (it.id == null) it.id = UUID.randomUUID() } } }

        service.update(existing.id!!, formInput("handicrafts"), emptyList())
        assertEquals(1, existing.images.size)

        service.update(existing.id!!, formInput("handicrafts"), listOf(image))
        assertEquals(1, existing.images.size)
        assertEquals("", existing.images.first().url)
    }

    @Test
    fun `update rejects a duplicate SKU held by a different product`() {
        val existing = product()
        val conflicting = product(sku = "SKU-1")
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { productRepository.findByStoreIdAndSkuIgnoreCase(storeId, "SKU-1") } returns conflicting

        assertThrows(ConflictException::class.java) {
            service.update(existing.id!!, formInput("handicrafts").copy(sku = "SKU-1"), emptyList())
        }
    }

    @Test
    fun `update allows keeping a product's own existing SKU`() {
        val existing = product(sku = "SKU-1")
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { productRepository.findByStoreIdAndSkuIgnoreCase(storeId, "SKU-1") } returns existing
        every { productRepository.save(any()) } answers { firstArg() }

        service.update(existing.id!!, formInput("handicrafts").copy(sku = "SKU-1"), emptyList())
    }

    @Test
    fun `delete rejects a non-owning seller`() {
        val existing = product()
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)

        assertThrows(ForbiddenException::class.java) { service.delete(existing.id!!) }
    }

    @Test
    fun `delete throws for a missing product`() {
        val productId = UUID.randomUUID()
        every { productRepository.findById(productId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.delete(productId) }
    }

    @Test
    fun `delete removes the owner's own product`() {
        val existing = product()
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { productRepository.deleteById(existing.id!!) } returns Unit

        service.delete(existing.id!!)

        verify { productRepository.deleteById(existing.id!!) }
    }

    @Test
    fun `getById throws for a missing product`() {
        val productId = UUID.randomUUID()
        every { productRepository.findById(productId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.getById(productId) }
    }

    @Test
    fun `getById hides a draft from a non-owning caller as not found`() {
        val existing = product(status = ProductStatus.DRAFT)
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { currentActor.sellerOrNull() } returns null

        assertThrows(NotFoundException::class.java) { service.getById(existing.id!!) }
    }

    @Test
    fun `getById shows a draft to its owning seller`() {
        val existing = product(status = ProductStatus.DRAFT)
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { currentActor.sellerOrNull() } returns seller

        val result = service.getById(existing.id!!)

        assertEquals(existing.id, result.id)
    }

    @Test
    fun `getById shows an active product to anyone`() {
        val existing = product(status = ProductStatus.ACTIVE)
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { currentActor.sellerOrNull() } returns null

        val result = service.getById(existing.id!!)

        assertEquals(existing.id, result.id)
    }

    @Test
    fun `findEntity returns null for a missing product`() {
        val productId = UUID.randomUUID()
        every { productRepository.findById(productId) } returns Optional.empty()

        assertEquals(null, service.findEntity(productId))
    }

    @Test
    fun `decrementStock reduces stock and forces out-of-stock at zero`() {
        val existing = product(stockQuantity = 2)
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { productRepository.save(any()) } answers { firstArg() }

        service.decrementStock(listOf(existing.id!! to 2))

        assertEquals(0, existing.stockQuantity)
        assertEquals(ProductStatus.OUT_OF_STOCK, existing.status)
    }

    @Test
    fun `decrementStock clamps at zero rather than going negative`() {
        val existing = product(stockQuantity = 2)
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { productRepository.save(any()) } answers { firstArg() }

        service.decrementStock(listOf(existing.id!! to 5))

        assertEquals(0, existing.stockQuantity)
    }

    @Test
    fun `decrementStock skips a product that doesn't track stock`() {
        val existing = product(stockQuantity = 2, trackStock = false)
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)

        service.decrementStock(listOf(existing.id!! to 2))

        assertEquals(2, existing.stockQuantity)
        verify(exactly = 0) { productRepository.save(any()) }
    }

    @Test
    fun `decrementStock silently skips a product that no longer exists`() {
        val missingId = UUID.randomUUID()
        every { productRepository.findById(missingId) } returns Optional.empty()

        service.decrementStock(listOf(missingId to 1))

        verify(exactly = 0) { productRepository.save(any()) }
    }

    @Test
    fun `restoreStock increases stock and flips back to active`() {
        val existing = product(stockQuantity = 0, status = ProductStatus.OUT_OF_STOCK).apply { lastLowStockAlertSentAt = Instant.now() }
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { productRepository.save(any()) } answers { firstArg() }

        service.restoreStock(listOf(existing.id!! to 3))

        assertEquals(3, existing.stockQuantity)
        assertEquals(ProductStatus.ACTIVE, existing.status)
        assertEquals(null, existing.lastLowStockAlertSentAt)
    }

    @Test
    fun `restoreStock leaves a draft product's status untouched`() {
        val existing = product(stockQuantity = 0, status = ProductStatus.DRAFT)
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { productRepository.save(any()) } answers { firstArg() }

        service.restoreStock(listOf(existing.id!! to 3))

        assertEquals(ProductStatus.DRAFT, existing.status)
    }

    @Test
    fun `restoreStock skips a product that doesn't track stock`() {
        val existing = product(stockQuantity = 0, trackStock = false)
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)

        service.restoreStock(listOf(existing.id!! to 3))

        assertEquals(0, existing.stockQuantity)
        verify(exactly = 0) { productRepository.save(any()) }
    }

    @Test
    fun `listByStore shows drafts to the owning seller`() {
        every { currentActor.sellerOrNull() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { productRepository.findByStoreIdOrderByUpdatedAtDesc(storeId) } returns listOf(product())

        val result = service.listByStore(storeId)

        assertEquals(1, result.size)
        verify { productRepository.findByStoreIdOrderByUpdatedAtDesc(storeId) }
    }

    @Test
    fun `listByStore hides drafts from a non-owning caller`() {
        every { currentActor.sellerOrNull() } returns null
        every { productRepository.findByStoreIdAndStatusNotOrderByUpdatedAtDesc(storeId, ProductStatus.DRAFT) } returns listOf(product())

        val result = service.listByStore(storeId)

        assertEquals(1, result.size)
        verify { productRepository.findByStoreIdAndStatusNotOrderByUpdatedAtDesc(storeId, ProductStatus.DRAFT) }
    }

    @Test
    fun `search uses the full-text path when a query is present`() {
        every {
            productRepository.searchFullText(
                category = null, query = "vase", likePattern = "%vase%", minPrice = null, maxPrice = null, sortMode = "relevance", pageable = any(),
            )
        } returns PageImpl(listOf(product()), PageRequest.of(0, 20), 1)

        val result = service.search(category = null, query = "vase", minPrice = null, maxPrice = null, sort = null, page = 0, size = 20)

        assertEquals(1, result.totalElements)
    }

    @Test
    fun `search coerces an out-of-range page size down to the max`() {
        every {
            productRepository.searchFullText(any(), any(), any(), any(), any(), any(), any())
        } returns PageImpl(emptyList(), PageRequest.of(0, 100), 0)

        service.search(category = null, query = "vase", minPrice = null, maxPrice = null, sort = null, page = 0, size = 1000)

        verify { productRepository.searchFullText(any(), any(), any(), any(), any(), any(), match { it.pageSize == 100 }) }
    }

    @Test
    fun `search falls back to the spec-based browse path when there is no query`() {
        every { productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>()) } returns
            PageImpl(listOf(product()), PageRequest.of(0, 20), 1)

        val result = service.search(category = null, query = null, minPrice = null, maxPrice = null, sort = null, page = 0, size = 20)

        assertEquals(1, result.totalElements)
    }

    @Test
    fun `isWishlisted reports false for a signed-out visitor`() {
        every { currentActor.buyerOrNull() } returns null

        assertFalse(service.isWishlisted(UUID.randomUUID()))
    }

    @Test
    fun `isWishlisted checks the repository for a signed-in buyer`() {
        val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        val productId = UUID.randomUUID()
        every { currentActor.buyerOrNull() } returns buyer
        every { wishlistItemRepository.existsByBuyerIdAndProductId(buyer.id!!, productId) } returns true

        assertTrue(service.isWishlisted(productId))
    }

    @Test
    fun `addToWishlist is idempotent`() {
        val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        val existing = product()
        every { currentActor.requireBuyer() } returns buyer
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { wishlistItemRepository.existsByBuyerIdAndProductId(buyer.id!!, existing.id!!) } returns true

        service.addToWishlist(existing.id!!)

        verify(exactly = 0) { wishlistItemRepository.save(any()) }
    }

    @Test
    fun `addToWishlist saves a new item when not already wishlisted`() {
        val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        val existing = product()
        every { currentActor.requireBuyer() } returns buyer
        every { productRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { wishlistItemRepository.existsByBuyerIdAndProductId(buyer.id!!, existing.id!!) } returns false
        every { wishlistItemRepository.save(any()) } answers { firstArg() }

        service.addToWishlist(existing.id!!)

        verify { wishlistItemRepository.save(any()) }
    }

    @Test
    fun `removeFromWishlist is a no-op when the item doesn't exist`() {
        val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        every { currentActor.requireBuyer() } returns buyer
        every { wishlistItemRepository.findByBuyerIdAndProductId(buyer.id!!, any()) } returns null

        service.removeFromWishlist(UUID.randomUUID())

        verify(exactly = 0) { wishlistItemRepository.delete(any()) }
    }

    @Test
    fun `removeFromWishlist deletes an existing item`() {
        val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        val existing = product()
        val item = WishlistItem(buyer = buyer, product = existing).apply { id = UUID.randomUUID() }
        every { currentActor.requireBuyer() } returns buyer
        every { wishlistItemRepository.findByBuyerIdAndProductId(buyer.id!!, existing.id!!) } returns item
        every { wishlistItemRepository.delete(item) } returns Unit

        service.removeFromWishlist(existing.id!!)

        verify { wishlistItemRepository.delete(item) }
    }

    @Test
    fun `listWishlist maps each item's product`() {
        val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        val existing = product()
        val item = WishlistItem(buyer = buyer, product = existing).apply { id = UUID.randomUUID() }
        every { currentActor.requireBuyer() } returns buyer
        every { wishlistItemRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.id!!) } returns listOf(item)

        val result = service.listWishlist()

        assertEquals(1, result.size)
        assertEquals(existing.id, result.first().id)
    }
}
