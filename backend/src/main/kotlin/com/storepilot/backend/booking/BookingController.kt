package com.storepilot.backend.booking

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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
import java.util.UUID

/** Matches docs/api-contracts.md#bookings — mirrors OrderController's shape. */
@RestController
class BookingController(
    private val bookingService: BookingService,
) {
    @GetMapping("/api/stores/{storeId}/bookings")
    fun listByStore(@PathVariable storeId: UUID, @RequestParam status: String?): List<BookingResponse> =
        bookingService.listByStore(storeId, status)

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

    @PostMapping("/api/bookings")
    fun create(@Valid @RequestBody input: CheckoutBookingInput): ResponseEntity<BookingResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(input))

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
