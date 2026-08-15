package com.storepilot.backend.review

import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.product.Product
import com.storepilot.backend.product.ProductRepository
import com.storepilot.backend.product.ProductStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class ReviewServiceTest {
    private val reviewRepository = mockk<ReviewRepository>()
    private val productRepository = mockk<ProductRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val orderRepository = mockk<OrderRepository>()
    private val bookingRepository = mockk<BookingRepository>()
    private val currentActor = mockk<CurrentActor>()

    private val service = ReviewService(reviewRepository, productRepository, storeRepository, orderRepository, bookingRepository, currentActor)

    private val buyerId = UUID.randomUUID()
    private val buyer = Buyer(name = "Amelia Clarke", email = "amelia@example.com").apply { id = buyerId }
    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
    private val storeId = UUID.randomUUID()
    private lateinit var store: Store
    private val productId = UUID.randomUUID()
    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller,
            slug = "blue-mountains-roasters",
            name = "Blue Mountains Roasters",
            tagline = "tagline",
            description = "description",
            category = StoreCategory.FOOD_BEVERAGE,
            address = StoreAddress(city = "Katoomba", state = "NSW"),
            whatsappNumber = "+61400000000",
        ).apply { id = storeId }
        product = Product(
            store = store,
            name = "Colombian Coffee Beans",
            slug = "colombian-coffee-beans",
            description = "description",
            category = StoreCategory.FOOD_BEVERAGE,
            price = 1800,
            stockQuantity = 10,
            status = ProductStatus.ACTIVE,
        ).apply { id = productId }
        every { currentActor.requireBuyer() } returns buyer
    }

    @Test
    fun `createProductReview rejects a buyer with no delivered order for that product`() {
        every { productRepository.findById(productId) } returns Optional.of(product)
        every { orderRepository.existsByBuyerIdAndStatusAndItems_ProductId(buyerId, OrderStatus.DELIVERED, productId) } returns false

        assertThrows(ForbiddenException::class.java) {
            service.createProductReview(productId, ReviewInput(rating = 5, comment = "Great!"))
        }
    }

    @Test
    fun `createProductReview rejects a second review from the same buyer for the same product`() {
        every { productRepository.findById(productId) } returns Optional.of(product)
        every { orderRepository.existsByBuyerIdAndStatusAndItems_ProductId(buyerId, OrderStatus.DELIVERED, productId) } returns true
        every { reviewRepository.existsByBuyerIdAndProductId(buyerId, productId) } returns true

        assertThrows(ConflictException::class.java) {
            service.createProductReview(productId, ReviewInput(rating = 5, comment = "Great!"))
        }
    }

    @Test
    fun `createProductReview 404s for a product that doesn't exist`() {
        every { productRepository.findById(productId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) {
            service.createProductReview(productId, ReviewInput(rating = 5, comment = null))
        }
    }

    @Test
    fun `createProductReview saves the review and recomputes the product's running average`() {
        product.rating = 4.0
        product.reviewCount = 3
        every { productRepository.findById(productId) } returns Optional.of(product)
        every { orderRepository.existsByBuyerIdAndStatusAndItems_ProductId(buyerId, OrderStatus.DELIVERED, productId) } returns true
        every { reviewRepository.existsByBuyerIdAndProductId(buyerId, productId) } returns false
        val savedReview = slot<Review>()
        every { reviewRepository.save(capture(savedReview)) } answers { savedReview.captured.apply { id = UUID.randomUUID(); createdAt = java.time.Instant.now() } }
        val savedProduct = slot<Product>()
        every { productRepository.save(capture(savedProduct)) } answers { savedProduct.captured }

        val response = service.createProductReview(productId, ReviewInput(rating = 5, comment = "Loved it"))

        assertEquals(5, response.rating)
        assertEquals(productId, response.productId)
        assertEquals("Amelia Clarke", response.buyerName)
        // (4.0 * 3 + 5) / 4 = 4.25 — running average, not a full rescan.
        assertEquals(4.25, savedProduct.captured.rating)
        assertEquals(4, savedProduct.captured.reviewCount)
    }

    @Test
    fun `createStoreReview accepts a completed booking even with no order at all`() {
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { orderRepository.existsByBuyerIdAndStoreIdAndStatus(buyerId, storeId, OrderStatus.DELIVERED) } returns false
        every { bookingRepository.existsByBuyerIdAndStoreIdAndStatus(buyerId, storeId, BookingStatus.COMPLETED) } returns true
        every { reviewRepository.existsByBuyerIdAndStoreIdAndProductIdIsNull(buyerId, storeId) } returns false
        val savedReview = slot<Review>()
        every { reviewRepository.save(capture(savedReview)) } answers { savedReview.captured.apply { id = UUID.randomUUID(); createdAt = java.time.Instant.now() } }
        every { storeRepository.save(any()) } answers { firstArg() }

        val response = service.createStoreReview(storeId, ReviewInput(rating = 4, comment = null))

        assertEquals(4, response.rating)
        assertEquals(null, response.productId)
        assertEquals(1, store.reviewCount)
    }

    @Test
    fun `createStoreReview rejects a buyer with neither a delivered order nor a completed booking`() {
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { orderRepository.existsByBuyerIdAndStoreIdAndStatus(buyerId, storeId, OrderStatus.DELIVERED) } returns false
        every { bookingRepository.existsByBuyerIdAndStoreIdAndStatus(buyerId, storeId, BookingStatus.COMPLETED) } returns false

        assertThrows(ForbiddenException::class.java) {
            service.createStoreReview(storeId, ReviewInput(rating = 3, comment = null))
        }
    }
}
