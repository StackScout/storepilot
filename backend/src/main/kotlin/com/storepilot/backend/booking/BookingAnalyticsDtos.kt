package com.storepilot.backend.booking

data class ServiceAnalytics(
    val serviceName: String,
    val bookingCount: Int,
    val revenue: Int,
)

/** GET /api/stores/{storeId}/booking-analytics — Pro-only, see BookingAnalyticsService's doc comment. */
data class BookingAnalyticsResponse(
    val totalBookings: Int,
    val completedBookings: Int,
    val cancelledBookings: Int,
    val noShowBookings: Int,
    /** Percent, 0-100, one decimal place. */
    val noShowRate: Double,
    /** Cents — sum of total across completed + paid bookings. */
    val totalRevenue: Int,
    /** Top 5 services by revenue (completed + paid bookings only), highest first. */
    val topServices: List<ServiceAnalytics>,
    /** Percent, 0-100, one decimal place — buyers with more than one booking at this store, of all buyers who've booked at all. Guest bookings (no buyer account) are excluded from both the numerator and denominator. */
    val repeatBuyerRate: Double,
)
