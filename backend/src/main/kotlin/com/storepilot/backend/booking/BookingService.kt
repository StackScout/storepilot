package com.storepilot.backend.booking

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.GuestLookupOtpService
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.wireValueOf
import com.storepilot.backend.notification.BookingNotifier
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.order.ReceiptStorageService
import com.storepilot.backend.seller.SellerPlan
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.stripe.StripeService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.UUID
import kotlin.random.Random

private val BOOKING_NUMBER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendValue(ChronoField.YEAR, 4)
    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
    .appendValue(ChronoField.DAY_OF_MONTH, 2)
    .toFormatter()

private val STATUS_LABELS = mapOf(
    BookingStatus.PENDING to "Booking requested",
    BookingStatus.CONFIRMED to "Booking confirmed",
    BookingStatus.COMPLETED to "Appointment completed",
    BookingStatus.CANCELLED to "Cancelled",
    BookingStatus.NO_SHOW to "Marked as no-show",
)

/** Mirrors OrderService.ALLOWED_STATUS_TRANSITIONS — no "shipped" analog, but adds NO_SHOW off CONFIRMED. */
private val ALLOWED_STATUS_TRANSITIONS: Map<BookingStatus, Set<BookingStatus>> = mapOf(
    BookingStatus.PENDING to setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
    BookingStatus.CONFIRMED to setOf(BookingStatus.CONFIRMED, BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.NO_SHOW),
    BookingStatus.COMPLETED to setOf(BookingStatus.COMPLETED),
    BookingStatus.CANCELLED to setOf(BookingStatus.CANCELLED),
    BookingStatus.NO_SHOW to setOf(BookingStatus.NO_SHOW),
)

/** Mirrors OrderService end to end — fee computation, payment-method Pro/country gating, status state machine — see docs/features/bookings.md. */
@Service
@Transactional(readOnly = true)
class BookingService(
    private val bookingRepository: BookingRepository,
    private val bookableServiceRepository: BookableServiceRepository,
    private val storeRepository: StoreRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val storeAvailabilityRepository: StoreAvailabilityRepository,
    private val receiptStorageService: ReceiptStorageService,
    private val bookingNotifier: BookingNotifier,
    private val currentActor: CurrentActor,
    private val platformConfigService: PlatformConfigService,
    private val stripeService: StripeService,
    private val guestLookupOtpService: GuestLookupOtpService,
) {
    fun listByStore(storeId: UUID, status: String?): List<BookingResponse> {
        val seller = currentActor.requireSeller()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")
        val statusEnum = status?.let { wireValueOf<BookingStatus>(it) }
        val bookings = bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
        return (if (statusEnum != null) bookings.filter { it.status == statusEnum } else bookings)
            .map { it.toResponse(receiptStorageService) }
    }

    /** Explicitly @Transactional (not readOnly) — requireBuyer() may JIT-provision a row, same reasoning as OrderService.listByCurrentBuyer. */
    @Transactional
    fun listByCurrentBuyer(): List<BookingResponse> {
        val buyerId = requireNotNull(currentActor.requireBuyer().id)
        return bookingRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId).map { it.toResponse(receiptStorageService) }
    }

    fun getById(id: UUID): BookingResponse =
        bookingRepository.findById(id).orElseThrow { NotFoundException("Booking $id not found") }.toResponse(receiptStorageService)

    /** For internal cross-service use (PayHere/Stripe webhook fallback lookup) — returns the entity, not a DTO. */
    fun findEntity(id: UUID): Booking? = bookingRepository.findById(id).orElse(null)

    /** Booking number (exact, case-insensitive) + last 9 digits of phone — the first factor of guest lookup, mirrors OrderService.resolveByNumberAndPhone. */
    private fun resolveByNumberAndPhone(bookingNumber: String, phone: String): Booking? {
        val normalizedInput = phone.replace(Regex("\\s+"), "")
        val suffix = normalizedInput.takeLast(9)
        val booking = bookingRepository.findByBookingNumberIgnoreCase(bookingNumber.trim()) ?: return null
        val storedPhone = booking.buyerPhone.replace(Regex("\\s+"), "")
        return if (storedPhone.endsWith(suffix)) booking else null
    }

    /** First step of guest lookup — mirrors OrderService.requestLookupCode's doc comment exactly. */
    @Transactional
    fun requestLookupCode(bookingNumber: String, phone: String) {
        val booking = resolveByNumberAndPhone(bookingNumber, phone) ?: return
        guestLookupOtpService.requestCode(
            targetType = "booking",
            targetId = requireNotNull(booking.id),
            email = booking.buyerEmail,
            recipientName = booking.buyerName,
        )
    }

    /** Second step of guest lookup — replaces the old GET /api/bookings/lookup?bookingNumber=&phone=. */
    @Transactional
    fun verifyLookupCode(bookingNumber: String, phone: String, code: String): BookingResponse {
        val booking = resolveByNumberAndPhone(bookingNumber, phone) ?: throw NotFoundException("Booking not found")
        guestLookupOtpService.verifyCode("booking", requireNotNull(booking.id), code)
        return booking.toResponse(receiptStorageService)
    }

    /** POST /api/bookings — booking checkout. */
    @Transactional
    fun createBooking(input: CheckoutBookingInput): BookingResponse {
        val service = bookableServiceRepository.findById(input.serviceId)
            .orElseThrow { NotFoundException("Service ${input.serviceId} not found") }
        val store = service.store
        val storeSettings = store.id?.let { storeSettingsRepository.findById(it).orElse(null) }
        if (storeSettings?.bookingsEnabled != true) {
            throw ConflictException("This store isn't accepting bookings")
        }

        val platformConfig = platformConfigService.current()
        val leadTimeMinutes = store.id?.let { storeAvailabilityRepository.findById(it).orElse(null)?.leadTimeMinutes } ?: 120
        val scheduledEnd = input.scheduledStart.plusSeconds(service.durationMinutes * 60L)

        // Re-validate the slot is still free inside this transaction — the
        // client only saw a snapshot of availability when it rendered the
        // slot picker; another booking may have taken it since. Same
        // same-service-only overlap rule as AvailabilityService.computeSlots
        // (independent per-service capacity, see docs/features/bookings.md).
        if (input.scheduledStart < Instant.now().plusSeconds(leadTimeMinutes * 60L)) {
            throw ConflictException("This time slot is no longer available")
        }
        val excludedStatuses = setOf(BookingStatus.CANCELLED, BookingStatus.NO_SHOW)
        val overlapping = bookingRepository.findByServiceIdAndStatusNotInAndScheduledStartLessThanAndScheduledEndGreaterThan(
            requireNotNull(service.id),
            excludedStatuses,
            scheduledEnd,
            input.scheduledStart,
        )
        if (overlapping.isNotEmpty()) throw ConflictException("This time slot is no longer available")

        val feePercent = storeSettings.transactionFeePercent
        val platformFee = (BigDecimal(service.price) * feePercent)
            .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
            .toInt()

        val paymentMethod = wireValueOf<PaymentMethod>(input.paymentMethod)
        // Same defense-in-depth as OrderService.createOrder — bank-transfer
        // and "Pay at venue" (COD) are Pro-only for bookings too, mirroring
        // the confirmed product decision (see docs/features/bookings.md).
        if ((paymentMethod == PaymentMethod.COD || paymentMethod == PaymentMethod.BANK_TRANSFER) && store.seller.plan != SellerPlan.PRO) {
            throw ConflictException("This store doesn't offer ${paymentMethod.wireValue} payments")
        }
        if (paymentMethod == PaymentMethod.PAYHERE && platformConfig.countryCode != "LK") {
            throw ConflictException("This store doesn't offer ${paymentMethod.wireValue} payments")
        }
        if (paymentMethod == PaymentMethod.STRIPE && platformConfig.countryCode != "AU") {
            throw ConflictException("This store doesn't offer ${paymentMethod.wireValue} payments")
        }

        val now = Instant.now()
        val buyer = currentActor.buyerOrNull()
        val booking = Booking(
            bookingNumber = generateBookingNumber(now, platformConfig.countryCode),
            store = store,
            service = service,
            serviceName = service.name,
            servicePrice = service.price,
            serviceDurationMinutes = service.durationMinutes,
            scheduledStart = input.scheduledStart,
            scheduledEnd = scheduledEnd,
            platformFee = platformFee,
            total = service.price,
            status = BookingStatus.PENDING,
            paymentMethod = paymentMethod,
            paymentStatus = PaymentStatus.UNPAID,
            buyerName = input.buyerName,
            buyerPhone = input.buyerPhone,
            buyerEmail = input.buyerEmail,
            buyer = buyer,
        )
        booking.timeline.add(
            BookingTimelineEntry(booking = booking, status = BookingStatus.PENDING, label = STATUS_LABELS.getValue(BookingStatus.PENDING), timestamp = now),
        )

        val saved = bookingRepository.save(booking)
        bookingNotifier.bookingCreated(saved)
        return saved.toResponse(receiptStorageService)
    }

    /** PATCH /api/bookings/{id}/status — seller-driven transitions. */
    @Transactional
    fun updateStatus(id: UUID, input: BookingStatusUpdateInput): BookingResponse {
        val booking = bookingRepository.findById(id).orElseThrow { NotFoundException("Booking $id not found") }
        requireSellerOwnsBooking(booking)
        val status = wireValueOf<BookingStatus>(input.status)
        val allowedNext = ALLOWED_STATUS_TRANSITIONS.getValue(booking.status)
        if (status !in allowedNext) {
            throw ConflictException("Booking ${booking.id} can't move from \"${booking.status.wireValue}\" to \"${status.wireValue}\"")
        }

        booking.status = status
        // "Pay at venue" (COD) flips to paid once the appointment actually
        // happened — same principle as OrderService's DELIVERED+COD->PAID.
        if (status == BookingStatus.COMPLETED && booking.paymentMethod == PaymentMethod.COD) {
            booking.paymentStatus = PaymentStatus.PAID
        }
        if (status == BookingStatus.CANCELLED) applyCancellationSideEffects(booking)

        booking.timeline.add(
            BookingTimelineEntry(booking = booking, status = status, label = STATUS_LABELS.getValue(status), timestamp = Instant.now(), note = input.note),
        )
        val saved = bookingRepository.save(booking)
        if (status == BookingStatus.CONFIRMED) bookingNotifier.bookingConfirmed(saved)
        if (status == BookingStatus.CANCELLED) bookingNotifier.bookingCancelled(saved)
        return saved.toResponse(receiptStorageService)
    }

    /** POST /api/bookings/{id}/receipt — buyer uploads proof of a bank transfer, mirrors OrderService.uploadReceipt. */
    @Transactional
    fun uploadReceipt(id: UUID, file: MultipartFile): BookingResponse {
        val booking = bookingRepository.findById(id).orElseThrow { NotFoundException("Booking $id not found") }
        if (booking.paymentMethod != PaymentMethod.BANK_TRANSFER) {
            throw ConflictException("Booking $id is not a bank transfer payment")
        }
        if (booking.paymentStatus != PaymentStatus.UNPAID) {
            throw ConflictException("Booking $id is already ${booking.paymentStatus.wireValue}")
        }
        booking.receiptUrl = receiptStorageService.store(file)
        booking.timeline.add(
            BookingTimelineEntry(booking = booking, status = booking.status, label = "Payment receipt uploaded", timestamp = Instant.now(), note = "Awaiting seller verification"),
        )
        return bookingRepository.save(booking).toResponse(receiptStorageService)
    }

    /** POST /api/bookings/{id}/verify-bank-transfer — mirrors OrderService.verifyBankTransfer. */
    @Transactional
    fun verifyBankTransfer(id: UUID, input: VerifyBookingBankTransferInput): BookingResponse {
        val booking = bookingRepository.findById(id).orElseThrow { NotFoundException("Booking $id not found") }
        requireSellerOwnsBooking(booking)
        if (booking.paymentMethod != PaymentMethod.BANK_TRANSFER) {
            throw ConflictException("Booking $id is not a bank transfer payment")
        }
        if (booking.paymentStatus != PaymentStatus.UNPAID) {
            throw ConflictException("Booking $id is already ${booking.paymentStatus.wireValue}")
        }

        if (input.approved) {
            booking.paymentStatus = PaymentStatus.PAID
            if (booking.status == BookingStatus.PENDING) booking.status = BookingStatus.CONFIRMED
            booking.timeline.add(
                BookingTimelineEntry(booking = booking, status = booking.status, label = "Payment confirmed by seller", timestamp = Instant.now(), note = input.note),
            )
        } else {
            booking.receiptUrl = null
            booking.timeline.add(
                BookingTimelineEntry(booking = booking, status = booking.status, label = "Payment receipt rejected", timestamp = Instant.now(), note = input.note),
            )
        }
        val saved = bookingRepository.save(booking)
        if (input.approved) bookingNotifier.bookingConfirmed(saved)
        return saved.toResponse(receiptStorageService)
    }

    /**
     * POST /api/bookings/{id}/cancel — buyer- or seller-initiated, reachable
     * unauthenticated (same "booking ID is proof enough" model as
     * GET/receipt upload). Cutoff = the store's leadTimeMinutes, same number
     * reused both directions — see StoreAvailability.leadTimeMinutes's doc
     * comment.
     */
    @Transactional
    fun cancelBooking(id: UUID, input: CancelBookingInput): BookingResponse {
        val booking = bookingRepository.findById(id).orElseThrow { NotFoundException("Booking $id not found") }
        if (booking.status !in setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED)) {
            throw ConflictException("Booking $id can no longer be cancelled")
        }
        val leadTimeMinutes = booking.store.id?.let { storeAvailabilityRepository.findById(it).orElse(null)?.leadTimeMinutes } ?: 120
        if (booking.scheduledStart < Instant.now().plusSeconds(leadTimeMinutes * 60L)) {
            throw ConflictException("This booking is too close to its appointment time to cancel — contact the store directly")
        }

        booking.status = BookingStatus.CANCELLED
        booking.cancellationReason = input.reason
        applyCancellationSideEffects(booking)
        booking.timeline.add(
            BookingTimelineEntry(booking = booking, status = BookingStatus.CANCELLED, label = "Cancelled", timestamp = Instant.now(), note = input.reason),
        )
        val saved = bookingRepository.save(booking)
        bookingNotifier.bookingCancelled(saved)
        return saved.toResponse(receiptStorageService)
    }

    /** Shared by updateStatus and cancelBooking's CANCELLED transition — mirrors OrderService.updateStatus's refund branch. */
    private fun applyCancellationSideEffects(booking: Booking) {
        if (booking.paymentStatus == PaymentStatus.PAID) {
            if (booking.paymentMethod == PaymentMethod.STRIPE) {
                stripeService.refundBookingPayment(booking)
            }
            booking.paymentStatus = PaymentStatus.REFUNDED
        }
    }

    private fun generateBookingNumber(now: Instant, countryCode: String): String {
        val datePart = BOOKING_NUMBER_DATE_FORMAT.format(now.atZone(java.time.ZoneOffset.UTC))
        val randomPart = Random.nextInt(1000, 10000)
        return "BK-$countryCode-$datePart-$randomPart"
    }

    private fun requireSellerOwnsBooking(booking: Booking) {
        val seller = currentActor.requireSeller()
        if (booking.store.seller.id != seller.id) throw ForbiddenException("You don't own booking ${booking.id}")
    }
}
