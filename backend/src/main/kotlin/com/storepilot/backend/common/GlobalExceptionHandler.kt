package com.storepilot.backend.common

import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /** E.g. a non-UUID string in a {id} path segment — a client bug, not a server error. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("VALIDATION_ERROR", "Invalid value for parameter '${ex.name}'"))

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.of("FORBIDDEN_OWNERSHIP", ex.message ?: "Forbidden"))

    @ExceptionHandler(UnauthenticatedException::class)
    fun handleUnauthenticated(ex: UnauthenticatedException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiError.of("UNAUTHENTICATED", ex.message ?: "Invalid credentials"))

    @ExceptionHandler(EmailNotVerifiedException::class)
    fun handleEmailNotVerified(ex: EmailNotVerifiedException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.of("EMAIL_NOT_VERIFIED", ex.message ?: "Please verify your email before signing in"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("VALIDATION_ERROR", ex.message ?: "Invalid request"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val fields = ex.bindingResult.fieldErrors
            .associate { it.field to (it.defaultMessage ?: "Invalid value") }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("VALIDATION_ERROR", "Validation failed", fields))
    }

    /**
     * Every Stripe SDK call (Connect onboarding/checkout, buyer Stripe
     * checkout, refunds, seller Pro billing) previously had no dedicated
     * handler, so any StripeException — a bad/missing API key
     * (AuthenticationException), an unsupported currency
     * (InvalidRequestException), a network blip (ApiConnectionException) —
     * fell through to handleUnexpected below as an opaque 500 with no
     * detail. Logging the real Stripe error code/message server-side here
     * is what makes that class of failure diagnosable at all; the client
     * still just sees a generic message, since Stripe's own wording isn't
     * meant for end users.
     */
    @ExceptionHandler(StripeException::class)
    fun handleStripeException(ex: StripeException): ResponseEntity<ApiError> {
        log.error("Stripe API call failed (code={}, requestId={})", ex.code, ex.requestId, ex)
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ApiError.of("PAYMENT_PROVIDER_ERROR", "The payment provider couldn't complete this request. Please try again shortly."))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiError> {
        log.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of("INTERNAL_ERROR", "Something went wrong"))
    }
}
