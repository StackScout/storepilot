package com.storepilot.backend.notification

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Matched by SecurityConfig's existing "/api/me/seller" wildcard rule ->
 * hasRole("SELLER") — no new security matcher needed. DELETE takes the
 * token in the body rather than as a path variable: an Expo push token
 * contains square brackets (ExponentPushToken[...]), which would need
 * percent-encoding in a path segment for no real benefit over just sending
 * it as JSON.
 */
@RestController
class PushTokenController(
    private val pushTokenService: PushTokenService,
) {
    @PostMapping("/api/me/seller/push-tokens")
    fun register(@Valid @RequestBody input: RegisterPushTokenInput): ResponseEntity<Void> {
        pushTokenService.register(input)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/api/me/seller/push-tokens")
    fun unregister(@Valid @RequestBody input: UnregisterPushTokenInput): ResponseEntity<Void> {
        pushTokenService.unregister(input)
        return ResponseEntity.noContent().build()
    }
}
