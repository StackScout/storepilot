package com.storepilot.backend.review

import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.toPageResponse
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.product.ProductRepository
import com.storepilot.backend.store.StoreRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Hard cap regardless of what a caller requests via `size` — same convention as ProductService/StoreService's own MAX_PAGE_SIZE. */
private const val MAX_PAGE_SIZE = 100

@Service
@Transactional(readOnly = true)
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val productRepository: ProductRepository,
    private val storeRepository: StoreRepository,
    private val orderRepository: OrderRepository,
    private val bookingRepository: BookingRepository,
    private val currentActor: CurrentActor,
) {
    fun listByProduct(productId: UUID, page: Int, size: Int): PageResponse<ReviewResponse> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable).toPageResponse { it.toResponse() }
    }

    fun listByStore(storeId: UUID, page: Int, size: Int): PageResponse<ReviewResponse> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return reviewRepository.findByStoreIdAndProductIdIsNullOrderByCreatedAtDesc(storeId, pageable).toPageResponse { it.toResponse() }
    }

    /**
     * "Verified purchase" for a product means the reviewing buyer has a
     * DELIVERED order containing this exact product — not merely any order
     * from the product's store (see createStoreReview for that weaker
     * gate). One review per buyer per product, enforced both here (a clear
     * 409) and by V18__reviews.sql's partial unique index (a race-condition
     * backstop, same pattern as ProductService.requireUniqueSku).
     */
    @Transactional
    fun createProductReview(productId: UUID, input: ReviewInput): ReviewResponse {
        val buyer = currentActor.requireBuyer()
        val product = productRepository.findById(productId).orElse(null) ?: throw NotFoundException("Product not found")
        if (!orderRepository.existsByBuyerIdAndStatusAndItems_ProductId(requireNotNull(buyer.id), OrderStatus.DELIVERED, productId)) {
            throw ForbiddenException("You can only review products from a delivered order")
        }
        if (reviewRepository.existsByBuyerIdAndProductId(requireNotNull(buyer.id), productId)) {
            throw ConflictException("You've already reviewed this product")
        }
        val review = reviewRepository.save(
            Review(buyer = buyer, store = product.store, productId = productId, rating = input.rating, comment = input.comment),
        )
        product.reviewCount += 1
        product.rating = ((product.rating * (product.reviewCount - 1)) + input.rating) / product.reviewCount
        productRepository.save(product)
        return review.toResponse()
    }

    /**
     * "Verified purchase" for a store means any DELIVERED order or
     * COMPLETED booking with that store — a weaker, store-wide gate than
     * createProductReview's exact-product match, since a store review is
     * about the overall experience (communication, delivery, service
     * quality), not any one item.
     */
    @Transactional
    fun createStoreReview(storeId: UUID, input: ReviewInput): ReviewResponse {
        val buyer = currentActor.requireBuyer()
        val store = storeRepository.findById(storeId).orElse(null) ?: throw NotFoundException("Store not found")
        val buyerId = requireNotNull(buyer.id)
        val hasQualifyingOrder = orderRepository.existsByBuyerIdAndStoreIdAndStatus(buyerId, storeId, OrderStatus.DELIVERED)
        val hasQualifyingBooking = bookingRepository.existsByBuyerIdAndStoreIdAndStatus(buyerId, storeId, BookingStatus.COMPLETED)
        if (!hasQualifyingOrder && !hasQualifyingBooking) {
            throw ForbiddenException("You can only review a store after a completed order or booking there")
        }
        if (reviewRepository.existsByBuyerIdAndStoreIdAndProductIdIsNull(buyerId, storeId)) {
            throw ConflictException("You've already reviewed this store")
        }
        val review = reviewRepository.save(
            Review(buyer = buyer, store = store, productId = null, rating = input.rating, comment = input.comment),
        )
        store.reviewCount += 1
        store.rating = ((store.rating * (store.reviewCount - 1)) + input.rating) / store.reviewCount
        storeRepository.save(store)
        return review.toResponse()
    }
}
