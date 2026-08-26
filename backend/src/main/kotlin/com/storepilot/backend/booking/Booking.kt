package com.storepilot.backend.booking

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.store.Store
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * An appointment booked against a BookableService — parallel aggregate to
 * Order, not an extension of it: no delivery method/shipping fee/tracking
 * concepts apply to an appointment. paymentMethod/paymentStatus reuse
 * order.PaymentMethod/PaymentStatus verbatim (no new enum) — "Pay at venue"
 * is the COD wire value with different frontend copy in the booking
 * context. service stays a real FK (see BookableService's doc comment);
 * serviceName/servicePrice/serviceDurationMinutes are immutable snapshots
 * taken at booking-creation time, same principle as OrderItem.
 */
@Entity
@Table(name = "bookings")
class Booking(
    @Column(name = "booking_number", nullable = false, unique = true)
    var bookingNumber: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    var service: BookableService,
    @Column(name = "service_name", nullable = false)
    var serviceName: String,
    @Column(name = "service_price", nullable = false)
    var servicePrice: Int,
    @Column(name = "service_duration_minutes", nullable = false)
    var serviceDurationMinutes: Int,
    @Column(name = "scheduled_start", nullable = false)
    var scheduledStart: Instant,
    /** = scheduledStart + serviceDurationMinutes, stored explicitly so overlap queries are a plain range comparison. */
    @Column(name = "scheduled_end", nullable = false)
    var scheduledEnd: Instant,
    @Column(name = "platform_fee", nullable = false)
    var platformFee: Int,
    @Column(nullable = false)
    var total: Int,
    @Column(nullable = false)
    var status: BookingStatus = BookingStatus.PENDING,
    @Column(name = "payment_method", nullable = false)
    var paymentMethod: PaymentMethod,
    @Column(name = "payment_status", nullable = false)
    var paymentStatus: PaymentStatus,
    /** Bank-transfer proof, same as Order.receiptUrl. */
    @Column(name = "receipt_url")
    var receiptUrl: String? = null,
    @Column(name = "stripe_payment_intent_id")
    var stripePaymentIntentId: String? = null,
    @Column(name = "buyer_name", nullable = false)
    var buyerName: String,
    @Column(name = "buyer_phone", nullable = false)
    var buyerPhone: String,
    @Column(name = "buyer_email", nullable = false)
    var buyerEmail: String,
    /** Nullable — guest booking allowed, identical to Order.buyer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    var buyer: Buyer? = null,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    @Column(name = "cancellation_reason", columnDefinition = "text")
    var cancellationReason: String? = null,
    /** Null means never reminded — same idempotency shape as Order.lastReminderSentAt, see BookingReminderJob. One-shot (not repeating) since a booking only has one upcoming appointment to remind about. */
    @Column(name = "last_reminder_sent_at")
    var lastReminderSentAt: Instant? = null,
    /** Seller-facing counterpart to lastReminderSentAt (which is the buyer's email reminder) — see SellerBookingReminderJob. Independent one-shot flag since the two reminders fire at different offsets (StoreSettings.sellerBookingReminderMinutesBefore vs the buyer job's fixed global hours). */
    @Column(name = "seller_reminder_sent_at")
    var sellerReminderSentAt: Instant? = null,
    /** Shared by every occurrence of the same weekly-recurring series (see BookingService.createBooking's occurrenceCount branch) — null for a one-off booking. Each occurrence is otherwise a fully independent Booking row (own status/payment/timeline/cancellation). */
    @Column(name = "recurrence_group_id")
    var recurrenceGroupId: UUID? = null,
    /** Immutable snapshot of the coupon applied at checkout (if any) — same principle as Order.couponCode. Applied identically to every occurrence of a recurring series, but only recorded as one use of the coupon — see BookingService.createBooking. */
    @Column(name = "coupon_code")
    var couponCode: String? = null,
    @Column(name = "discount_amount", nullable = false)
    var discountAmount: Int = 0,
    @OneToMany(mappedBy = "booking", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("timestamp asc")
    var timeline: MutableList<BookingTimelineEntry> = mutableListOf(),
) : BaseEntity()

/** Mirrors OrderTimelineEntry — append-only, never edited/removed once written. */
@Entity
@Table(name = "booking_timeline_entries")
class BookingTimelineEntry(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    var booking: Booking,
    @Column(nullable = false)
    var status: BookingStatus,
    @Column(nullable = false)
    var label: String,
    @Column(nullable = false)
    var timestamp: Instant,
    @Column(columnDefinition = "text")
    var note: String? = null,
) : BaseEntity()

/**
 * Mirrors src/types/booking.ts's BookingStatus. State machine enforced in
 * BookingService (mirroring OrderService.ALLOWED_STATUS_TRANSITIONS):
 * PENDING -> CONFIRMED | CANCELLED; CONFIRMED -> COMPLETED | CANCELLED |
 * NO_SHOW; COMPLETED/CANCELLED/NO_SHOW terminal.
 */
enum class BookingStatus(override val wireValue: String) : WireValue {
    PENDING("pending"),
    CONFIRMED("confirmed"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    NO_SHOW("no-show"),
}

@Converter(autoApply = true)
class BookingStatusConverter : WireValueEnumConverter<BookingStatus>(BookingStatus.entries.toTypedArray())
