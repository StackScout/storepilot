package com.storepilot.backend.booking

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.seller.SellerPlan
import com.storepilot.backend.store.StoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.math.round

/**
 * Booking analytics — a Pro-only add-on (see docs/feature-epics.md's
 * "premium booking analytics" backlog item), gated by SellerPlan.PRO the
 * same way COD/bank-transfer payment methods are, just applied to an
 * entire read endpoint rather than one request field.
 *
 * Aggregated in-memory over the store's full booking list rather than via
 * SQL aggregate queries — this is a small-business marketplace where a
 * single store's lifetime booking count is expected to stay in the
 * hundreds/low-thousands, so one bounded list fetch plus Kotlin
 * grouping/summing is simpler than several new JPQL aggregate queries and
 * costs nothing extra at this scale. Revisit if a store's booking volume
 * ever grows large enough for this to show up in profiling.
 */
@Service
@Transactional(readOnly = true)
class BookingAnalyticsService(
    private val bookingRepository: BookingRepository,
    private val storeRepository: StoreRepository,
    private val currentActor: CurrentActor,
) {
    fun getAnalytics(storeId: UUID): BookingAnalyticsResponse {
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val seller = currentActor.requireSeller()
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")
        if (seller.plan != SellerPlan.PRO) throw ForbiddenException("Booking analytics is a Pro feature — upgrade to view it")

        val bookings = bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
        val completed = bookings.filter { it.status == BookingStatus.COMPLETED }
        val completedAndPaid = completed.filter { it.paymentStatus == PaymentStatus.PAID }
        val cancelled = bookings.count { it.status == BookingStatus.CANCELLED }
        val noShow = bookings.count { it.status == BookingStatus.NO_SHOW }

        val topServices = completedAndPaid
            .groupBy { it.serviceName }
            .map { (name, group) -> ServiceAnalytics(serviceName = name, bookingCount = group.size, revenue = group.sumOf { it.total }) }
            .sortedByDescending { it.revenue }
            .take(5)

        val buyerBookingCounts = bookings.mapNotNull { it.buyer?.id }.groupingBy { it }.eachCount()
        val repeatBuyerRate = if (buyerBookingCounts.isEmpty()) {
            0.0
        } else {
            onePlace(buyerBookingCounts.values.count { it > 1 }.toDouble() / buyerBookingCounts.size * 100)
        }

        return BookingAnalyticsResponse(
            totalBookings = bookings.size,
            completedBookings = completed.size,
            cancelledBookings = cancelled,
            noShowBookings = noShow,
            noShowRate = if (bookings.isEmpty()) 0.0 else onePlace(noShow.toDouble() / bookings.size * 100),
            totalRevenue = completedAndPaid.sumOf { it.total },
            topServices = topServices,
            repeatBuyerRate = repeatBuyerRate,
        )
    }

    private fun onePlace(value: Double): Double = round(value * 10) / 10.0
}
