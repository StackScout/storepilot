package com.storepilot.backend.seller

import com.storepilot.backend.booking.BookingResponse
import com.storepilot.backend.booking.BookingService
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.coupon.CouponRepository
import com.storepilot.backend.coupon.CouponResponse
import com.storepilot.backend.coupon.toResponse
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderResponse
import com.storepilot.backend.order.ReceiptStorageService
import com.storepilot.backend.order.toResponse
import com.storepilot.backend.payout.FeeCollectionRepository
import com.storepilot.backend.payout.FeeCollectionResponse
import com.storepilot.backend.payout.PayoutRepository
import com.storepilot.backend.payout.PayoutResponse
import com.storepilot.backend.payout.toResponse
import com.storepilot.backend.product.ProductResponse
import com.storepilot.backend.product.ProductService
import com.storepilot.backend.review.ReviewRepository
import com.storepilot.backend.review.ReviewResponse
import com.storepilot.backend.review.toResponse
import com.storepilot.backend.store.StoreResponse
import com.storepilot.backend.store.StoreService
import com.storepilot.backend.store.StoreSettingsResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class SellerExportResponse(
    val profile: SellerPlanResponse,
    val store: StoreResponse?,
    val storeSettings: StoreSettingsResponse?,
    val products: List<ProductResponse>,
    val orders: List<OrderResponse>,
    val bookings: List<BookingResponse>,
    val payouts: List<PayoutResponse>,
    val feeCollections: List<FeeCollectionResponse>,
    val reviews: List<ReviewResponse>,
    val coupons: List<CouponResponse>,
)

/**
 * GET /api/me/seller/export — a single JSON bundle of everything StorePilot
 * holds about the current seller, assembled from the same
 * services/mappers/repository methods every other seller-facing read path
 * already uses (no new exposure — this is the seller's own data, same as
 * what each individual "my X" dashboard page already shows). See the
 * plan's Export design section for scope.
 */
@Service
class SellerExportService(
    private val currentActor: CurrentActor,
    private val storeService: StoreService,
    private val productService: ProductService,
    private val bookingService: BookingService,
    private val platformConfigService: PlatformConfigService,
    private val orderRepository: OrderRepository,
    private val receiptStorageService: ReceiptStorageService,
    private val fileStorageService: FileStorageService,
    private val payoutRepository: PayoutRepository,
    private val feeCollectionRepository: FeeCollectionRepository,
    private val reviewRepository: ReviewRepository,
    private val couponRepository: CouponRepository,
) {
    @Transactional
    fun exportCurrentSeller(): SellerExportResponse {
        val seller = currentActor.requireSeller()
        val config = platformConfigService.current()
        val store = storeService.getMyStore()
        val storeId = store?.id

        return SellerExportResponse(
            profile = seller.toPlanResponse(config.proMonthlyPriceCents, config.currencyCode),
            store = store,
            storeSettings = storeId?.let { storeService.getSettings(it) },
            products = storeId?.let { productService.listByStore(it) } ?: emptyList(),
            orders = storeId?.let { orderRepository.findByStoreIdOrderByCreatedAtDesc(it) }
                ?.map { it.toResponse(receiptStorageService, fileStorageService) } ?: emptyList(),
            bookings = storeId?.let { bookingService.listByStore(it, status = null) } ?: emptyList(),
            payouts = storeId?.let { payoutRepository.findByStoreIdOrderByCreatedAtDesc(it) }?.map { it.toResponse() } ?: emptyList(),
            feeCollections = storeId?.let { feeCollectionRepository.findByStoreIdOrderByCreatedAtDesc(it) }?.map { it.toResponse() } ?: emptyList(),
            reviews = storeId?.let { reviewRepository.findByStoreIdOrderByCreatedAtDesc(it) }?.map { it.toResponse() } ?: emptyList(),
            coupons = storeId?.let { couponRepository.findByStoreIdOrderByCreatedAtDesc(it) }?.map { it.toResponse() } ?: emptyList(),
        )
    }
}
