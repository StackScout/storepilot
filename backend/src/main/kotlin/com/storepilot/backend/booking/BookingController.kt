package com.storepilot.backend.booking

import com.storepilot.backend.common.sse.SseHub
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

/** Matches docs/api-contracts.md#bookings — mirrors OrderController's shape. */
@RestController
class BookingController(
    private val bookingService: BookingService,
    private val bookingAnalyticsService: BookingAnalyticsService,
    private val sseHub: SseHub,
) {
    @GetMapping("/api/stores/{storeId}/bookings")
    fun listByStore(@PathVariable storeId: UUID, @RequestParam status: String?): List<BookingResponse> =
        bookingService.listByStore(storeId, status)

    /** Pro-only — see BookingAnalyticsService's doc comment. */
    @GetMapping("/api/stores/{storeId}/booking-analytics")
    fun getAnalytics(@PathVariable storeId: UUID): BookingAnalyticsResponse = bookingAnalyticsService.getAnalytics(storeId)

    @GetMapping("/api/me/bookings")
    fun listByCurrentBuyer(): List<BookingResponse> = bookingService.listByCurrentBuyer()

    /** First step of guest lookup — see BookingService.requestLookupCode's doc comment. Always 204. */
    @PostMapping("/api/bookings/lookup/request-code")
    fun requestLookupCode(@Valid @RequestBody input: GuestLookupRequestInput): ResponseEntity<Void> {
        bookingService.requestLookupCode(input.bookingNumber, input.phone)
        return ResponseEntity.noContent().build()
    }

    /** Second step of guest lookup — replaces the old GET /api/bookings/lookup?bookingNumber=&phone=. */
    @PostMapping("/api/bookings/lookup/verify")
    fun verifyLookupCode(@Valid @RequestBody input: GuestLookupVerifyInput): BookingResponse =
        bookingService.verifyLookupCode(input.bookingNumber, input.phone, input.code)

    @GetMapping("/api/bookings/{id}")
    fun getById(@PathVariable id: UUID): BookingResponse = bookingService.getById(id)

    /** Live booking-status push — mirrors OrderController.subscribeToEvents exactly. */
    @GetMapping("/api/bookings/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribeToEvents(@PathVariable id: UUID): SseEmitter {
        bookingService.getById(id) // 404s if the booking doesn't exist, same as the GET above
        return sseHub.subscribe("booking:$id")
    }

    @PostMapping("/api/bookings")
    fun create(@Valid @RequestBody input: CheckoutBookingInput): ResponseEntity<BookingResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(input))

    /** Every occurrence of a recurring series, in chronological order — see BookingService.createBooking's occurrenceCount branch. */
    @GetMapping("/api/bookings/recurrence/{groupId}")
    fun listByRecurrenceGroup(@PathVariable groupId: UUID): List<BookingResponse> = bookingService.listByRecurrenceGroup(groupId)

    @PatchMapping("/api/bookings/{id}/status")
    fun updateStatus(@PathVariable id: UUID, @Valid @RequestBody input: BookingStatusUpdateInput): BookingResponse =
        bookingService.updateStatus(id, input)

    @PostMapping("/api/bookings/{id}/receipt", consumes = ["multipart/form-data"])
    fun uploadReceipt(@PathVariable id: UUID, @RequestPart file: MultipartFile): BookingResponse =
        bookingService.uploadReceipt(id, file)

    @PostMapping("/api/bookings/{id}/verify-bank-transfer")
    fun verifyBankTransfer(@PathVariable id: UUID, @RequestBody input: VerifyBookingBankTransferInput): BookingResponse =
        bookingService.verifyBankTransfer(id, input)

    @PostMapping("/api/bookings/{id}/cancel")
    fun cancel(@PathVariable id: UUID, @RequestBody(required = false) input: CancelBookingInput?): BookingResponse =
        bookingService.cancelBooking(id, input ?: CancelBookingInput())
}
