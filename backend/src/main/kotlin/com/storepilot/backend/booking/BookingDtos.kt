package com.storepilot.backend.booking

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class BookingTimelineEntryResponse(
    val status: String,
    val label: String,
    val timestamp: Instant,
    val note: String?,
)

/** Shape matches src/types/booking.ts's Booking exactly. */
data class BookingResponse(
    val id: UUID,
    val bookingNumber: String,
    val storeId: UUID,
    val storeName: String,
    val storeSlug: String,
    val serviceId: UUID,
    val serviceName: String,
    val servicePrice: Int,
    val serviceDurationMinutes: Int,
    val scheduledStart: Instant,
    val scheduledEnd: Instant,
    val platformFee: Int,
    val total: Int,
    val status: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val receiptUrl: String?,
    val buyerName: String,
    val buyerPhone: String,
    val buyerEmail: String,
    val buyerId: UUID?,
    val cancellationReason: String?,
    val timeline: List<BookingTimelineEntryResponse>,
    val createdAt: Instant,
)

/**
 * Mirrors src/types/order.ts's CheckoutInput — POST /api/bookings. No
 * buyerId field, same reasoning as CheckoutInput: derived server-side only
 * from CurrentActor.buyerOrNull(), never client-supplied.
 */
data class CheckoutBookingInput(
    val storeId: UUID,
    @field:NotNull(message = "Select a service")
    val serviceId: UUID,
    @field:NotNull(message = "Select a time slot")
    val scheduledStart: Instant,
    @field:NotBlank(message = "Select a payment method")
    val paymentMethod: String,
    @field:NotBlank(message = "Enter your name")
    val buyerName: String,
    @field:NotBlank(message = "Enter a valid phone number")
    val buyerPhone: String,
    @field:Email(message = "Enter a valid email")
    @field:NotBlank(message = "Enter a valid email")
    val buyerEmail: String,
)

data class BookingStatusUpdateInput(
    @field:NotBlank(message = "Status is required")
    val status: String,
    val note: String? = null,
)

/** POST /api/bookings/{id}/verify-bank-transfer — seller accepts or rejects the buyer's uploaded receipt, mirrors VerifyBankTransferInput. */
data class VerifyBookingBankTransferInput(
    val approved: Boolean,
    val note: String? = null,
)

/** POST /api/bookings/{id}/cancel. */
data class CancelBookingInput(
    val reason: String? = null,
)
