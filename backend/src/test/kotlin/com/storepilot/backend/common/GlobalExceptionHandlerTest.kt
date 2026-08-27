package com.storepilot.backend.common

import com.stripe.exception.ApiConnectionException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleTypeMismatch returns 400 with the offending parameter name`() {
        val ex = mockk<MethodArgumentTypeMismatchException>()
        every { ex.name } returns "id"

        val response = handler.handleTypeMismatch(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
        assertEquals("Invalid value for parameter 'id'", response.body?.error?.message)
    }

    @Test
    fun `handleNotFound returns 404 with the exception message`() {
        val response = handler.handleNotFound(NotFoundException("Store not found"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("NOT_FOUND", response.body?.error?.code)
        assertEquals("Store not found", response.body?.error?.message)
    }

    @Test
    fun `handleConflict returns 409`() {
        val response = handler.handleConflict(ConflictException("Already exists"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("CONFLICT", response.body?.error?.code)
    }

    @Test
    fun `handleForbidden returns 403 with the ownership code`() {
        val response = handler.handleForbidden(ForbiddenException("Not your store"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN_OWNERSHIP", response.body?.error?.code)
    }

    @Test
    fun `handleUnauthenticated returns 401`() {
        val response = handler.handleUnauthenticated(UnauthenticatedException("Invalid credentials"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("UNAUTHENTICATED", response.body?.error?.code)
    }

    @Test
    fun `handleEmailNotVerified returns 403 with a distinct code from handleForbidden`() {
        val response = handler.handleEmailNotVerified(EmailNotVerifiedException("Please verify your email"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("EMAIL_NOT_VERIFIED", response.body?.error?.code)
    }

    @Test
    fun `handleIllegalArgument returns 400`() {
        val response = handler.handleIllegalArgument(IllegalArgumentException("Bad input"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
        assertEquals("Bad input", response.body?.error?.message)
    }

    @Test
    fun `handleValidation collects every field error into the fields map`() {
        val bindingResult = mockk<BindingResult>()
        every { bindingResult.fieldErrors } returns listOf(
            FieldError("input", "email", "must not be blank"),
            FieldError("input", "name", "must not be blank"),
        )
        val ex = mockk<MethodArgumentNotValidException>()
        every { ex.bindingResult } returns bindingResult

        val response = handler.handleValidation(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(mapOf("email" to "must not be blank", "name" to "must not be blank"), response.body?.error?.fields)
    }

    @Test
    fun `handleUnreadableBody returns a generic 400 without leaking parser internals`() {
        val ex = mockk<HttpMessageNotReadableException>(relaxed = true)

        val response = handler.handleUnreadableBody(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("The request body is missing a required field or is malformed", response.body?.error?.message)
    }

    @Test
    fun `handleStripeException maps to 502 with a generic message`() {
        val ex = ApiConnectionException("network blip")

        val response = handler.handleStripeException(ex)

        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode)
        assertEquals("PAYMENT_PROVIDER_ERROR", response.body?.error?.code)
    }

    @Test
    fun `handleEmailDelivery maps to 502 with a generic message`() {
        val ex = EmailDeliveryException("SES rejected the recipient", RuntimeException("cause"))

        val response = handler.handleEmailDelivery(ex)

        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode)
        assertEquals("EMAIL_DELIVERY_ERROR", response.body?.error?.code)
    }

    @Test
    fun `handleUnexpected maps any other exception to a generic 500`() {
        val response = handler.handleUnexpected(RuntimeException("something exploded"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.error?.code)
        assertEquals("Something went wrong", response.body?.error?.message)
    }
}
