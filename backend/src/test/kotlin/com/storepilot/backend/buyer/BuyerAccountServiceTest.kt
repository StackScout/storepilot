package com.storepilot.backend.buyer

import com.storepilot.backend.admin.AuditAction
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.booking.Booking
import com.storepilot.backend.booking.BookableService
import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.booking.ServiceStatus
import com.storepilot.backend.common.ShippingDetails
import com.storepilot.backend.common.security.CognitoProperties
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.product.Product
import com.storepilot.backend.product.ProductStatus
import com.storepilot.backend.product.WishlistItem
import com.storepilot.backend.product.WishlistItemRepository
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Follow
import com.storepilot.backend.store.FollowRepository
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUserGlobalSignOutRequest
import java.time.Instant
import java.util.UUID

class BuyerAccountServiceTest {
    private val currentActor = mockk<CurrentActor>()
    private val buyerRepository = mockk<BuyerRepository>()
    private val addressRepository = mockk<AddressRepository>(relaxed = true)
    private val savedSearchRepository = mockk<SavedSearchRepository>(relaxed = true)
    private val wishlistItemRepository = mockk<WishlistItemRepository>(relaxed = true)
    private val followRepository = mockk<FollowRepository>(relaxed = true)
    private val storeRepository = mockk<StoreRepository>(relaxed = true)
    private val orderRepository = mockk<OrderRepository>()
    private val bookingRepository = mockk<BookingRepository>()
    private val auditLogService = mockk<AuditLogService>(relaxed = true)
    private val cognitoClient = mockk<CognitoIdentityProviderClient>(relaxed = true)
    private val cognitoProperties = CognitoProperties(userPoolId = "pool-1")

    private val service = BuyerAccountService(
        currentActor,
        buyerRepository,
        addressRepository,
        savedSearchRepository,
        wishlistItemRepository,
        followRepository,
        storeRepository,
        orderRepository,
        bookingRepository,
        auditLogService,
        cognitoClient,
        cognitoProperties,
    )

    private val buyer = Buyer(name = "Jane Buyer", email = "buyer@example.com", cognitoSub = "buyer-sub").apply { id = UUID.randomUUID() }
    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
    private lateinit var store: Store

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller,
            slug = "store",
            name = "Store",
            tagline = "tagline",
            description = "description",
            category = "handicrafts",
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.ACTIVE,
            followerCount = 5,
        ).apply { id = UUID.randomUUID() }

        every { currentActor.requireBuyer() } returns buyer
        every { buyerRepository.save(any()) } answers { firstArg() }
        every { storeRepository.save(any()) } answers { firstArg() }
        every { orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.id!!) } returns emptyList()
        every { bookingRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.id!!) } returns emptyList()
        every { addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyer.id!!) } returns emptyList()
        every { savedSearchRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.id!!) } returns emptyList()
        every { wishlistItemRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.id!!) } returns emptyList()
        every { followRepository.findByBuyerId(buyer.id!!) } returns emptyList()

        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("buyer-sub")
            .claim("username", "buyer-sub")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt, emptyList())
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun order() = Order(
        orderNumber = "AU-20260101-1234",
        store = store,
        subtotal = 1000,
        shippingFee = 0,
        platformFee = 50,
        total = 1000,
        status = OrderStatus.DELIVERED,
        paymentMethod = PaymentMethod.COD,
        paymentStatus = PaymentStatus.PAID,
        shipping = ShippingDetails(fullName = "Jane Buyer", phone = "0400000000", addressLine1 = "1 Main St", city = "Sydney", state = "NSW", postalCode = "2000"),
        buyerEmail = buyer.email,
        buyer = buyer,
        fulfillmentTimeHours = 48,
        deliveryTimeHours = 120,
    ).apply { id = UUID.randomUUID() }

    private fun booking() = Booking(
        bookingNumber = "AU-20260101-5678",
        store = store,
        service = BookableService(
            store = store, name = "Service", slug = "service", description = "description",
            category = "handicrafts", price = 500, durationMinutes = 60, status = ServiceStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() },
        serviceName = "Service",
        servicePrice = 500,
        serviceDurationMinutes = 60,
        scheduledStart = Instant.now(),
        scheduledEnd = Instant.now().plusSeconds(3600),
        platformFee = 25,
        total = 500,
        status = BookingStatus.COMPLETED,
        paymentMethod = PaymentMethod.COD,
        paymentStatus = PaymentStatus.PAID,
        buyerName = "Jane Buyer",
        buyerPhone = "0400000000",
        buyerEmail = buyer.email,
        buyer = buyer,
    ).apply { id = UUID.randomUUID() }

    @Test
    fun `deleteCurrentBuyer redacts order shipping and contact details without touching the order-buyer link`() {
        val o = order()
        every { orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.id!!) } returns listOf(o)

        service.deleteCurrentBuyer()

        assertEquals("Deleted user", o.shipping.fullName)
        assertNull(o.shipping.phone)
        assertNull(o.shipping.addressLine1)
        assertEquals("deleted-buyer-${buyer.id}@storepilot.invalid", o.buyerEmail)
        assertEquals(buyer, o.buyer)
    }

    @Test
    fun `deleteCurrentBuyer redacts booking contact details`() {
        val b = booking()
        every { bookingRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.id!!) } returns listOf(b)

        service.deleteCurrentBuyer()

        assertEquals("Deleted user", b.buyerName)
        assertEquals("deleted", b.buyerPhone)
        assertEquals("deleted-buyer-${buyer.id}@storepilot.invalid", b.buyerEmail)
    }

    @Test
    fun `deleteCurrentBuyer deletes addresses, saved searches, and wishlist items entirely`() {
        val address = Address(buyer = buyer, shipping = ShippingDetails(fullName = "Jane", phone = "0400000000"))
        val savedSearch = SavedSearch(buyer = buyer, name = "Necklaces", queryString = "q=necklace")
        val product = Product(
            store = store, name = "Product", slug = "product", description = "description",
            category = "handicrafts", price = 1000, stockQuantity = 5, status = ProductStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }
        val wishlistItem = WishlistItem(buyer = buyer, product = product)
        every { addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyer.id!!) } returns listOf(address)
        every { savedSearchRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.id!!) } returns listOf(savedSearch)
        every { wishlistItemRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.id!!) } returns listOf(wishlistItem)

        service.deleteCurrentBuyer()

        verify { addressRepository.deleteAll(listOf(address)) }
        verify { savedSearchRepository.deleteAll(listOf(savedSearch)) }
        verify { wishlistItemRepository.deleteAll(listOf(wishlistItem)) }
    }

    @Test
    fun `deleteCurrentBuyer unfollows every store and decrements each follower count`() {
        val follow = Follow(buyer = buyer, store = store)
        every { followRepository.findByBuyerId(buyer.id!!) } returns listOf(follow)

        service.deleteCurrentBuyer()

        assertEquals(4, store.followerCount)
        verify { storeRepository.save(store) }
        verify { followRepository.delete(follow) }
    }

    @Test
    fun `deleteCurrentBuyer never drops a store's follower count below zero`() {
        store.followerCount = 0
        val follow = Follow(buyer = buyer, store = store)
        every { followRepository.findByBuyerId(buyer.id!!) } returns listOf(follow)

        service.deleteCurrentBuyer()

        assertEquals(0, store.followerCount)
    }

    @Test
    fun `deleteCurrentBuyer anonymizes the buyer's own identity fields`() {
        service.deleteCurrentBuyer()

        assertEquals("Deleted user", buyer.name)
        assertEquals("deleted-buyer-${buyer.id}@storepilot.invalid", buyer.email)
        assertNull(buyer.phone)
        assertNull(buyer.cognitoSub)
    }

    @Test
    fun `deleteCurrentBuyer records an audit entry before anonymizing`() {
        service.deleteCurrentBuyer()

        verify {
            auditLogService.recordAsBuyer(
                buyer,
                AuditAction.BUYER_ACCOUNT_DELETED,
                "buyer",
                buyer.id.toString(),
                match { it.contains("Jane Buyer") },
            )
        }
    }

    @Test
    fun `deleteCurrentBuyer signs out and deletes the Cognito user by their username claim`() {
        service.deleteCurrentBuyer()

        verify {
            cognitoClient.adminUserGlobalSignOut(
                match<AdminUserGlobalSignOutRequest> { it.userPoolId() == "pool-1" && it.username() == "buyer-sub" },
            )
        }
        verify {
            cognitoClient.adminDeleteUser(
                match<AdminDeleteUserRequest> { it.userPoolId() == "pool-1" && it.username() == "buyer-sub" },
            )
        }
    }
}
