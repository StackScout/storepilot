package com.storepilot.backend.buyer

import com.storepilot.backend.booking.BookingResponse
import com.storepilot.backend.booking.BookingService
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.messaging.ConversationResponse
import com.storepilot.backend.messaging.MessageResponse
import com.storepilot.backend.messaging.MessagingService
import com.storepilot.backend.order.OrderResponse
import com.storepilot.backend.order.OrderService
import com.storepilot.backend.product.ProductResponse
import com.storepilot.backend.product.ProductService
import com.storepilot.backend.review.ReviewRepository
import com.storepilot.backend.review.ReviewResponse
import com.storepilot.backend.review.toResponse
import com.storepilot.backend.store.FollowRepository
import com.storepilot.backend.store.StoreResponse
import com.storepilot.backend.store.toResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class BuyerExportResponse(
    val profile: BuyerResponse,
    val addresses: List<AddressResponse>,
    val orders: List<OrderResponse>,
    val bookings: List<BookingResponse>,
    val reviews: List<ReviewResponse>,
    val conversations: List<BuyerExportConversation>,
    val savedSearches: List<SavedSearchResponse>,
    val wishlist: List<ProductResponse>,
    val follows: List<StoreResponse>,
)

data class BuyerExportConversation(
    val conversation: ConversationResponse,
    val messages: List<MessageResponse>,
)

/**
 * GET /api/me/export — a single JSON bundle of everything StorePilot holds
 * about the current buyer, assembled from the same services/mappers every
 * other buyer-facing read path already uses (no new exposure — this is the
 * buyer's own data, same as what each individual "my X" endpoint already
 * returns). See the plan's Export design section for scope.
 */
@Service
class BuyerExportService(
    private val currentActor: CurrentActor,
    private val addressService: AddressService,
    private val savedSearchService: SavedSearchService,
    private val orderService: OrderService,
    private val bookingService: BookingService,
    private val productService: ProductService,
    private val messagingService: MessagingService,
    private val reviewRepository: ReviewRepository,
    private val followRepository: FollowRepository,
    private val fileStorageService: FileStorageService,
) {
    @Transactional
    fun exportCurrentBuyer(): BuyerExportResponse {
        val buyer = currentActor.requireBuyer()
        val buyerId = requireNotNull(buyer.id)

        val conversations = messagingService.listMyConversations().map { conversation ->
            BuyerExportConversation(conversation, messagingService.listMessages(conversation.id))
        }

        return BuyerExportResponse(
            profile = buyer.toResponse(),
            addresses = addressService.list(),
            orders = orderService.listByCurrentBuyer(),
            bookings = bookingService.listByCurrentBuyer(),
            reviews = reviewRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId).map { it.toResponse() },
            conversations = conversations,
            savedSearches = savedSearchService.list(),
            wishlist = productService.listWishlist(),
            follows = followRepository.findByBuyerId(buyerId).map { it.store.toResponse(fileStorageService) },
        )
    }
}
