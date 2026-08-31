package com.storepilot.backend.notification

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Matched by permissions.yml's broad /api/me path rule (BUYER) — no new matcher needed, see PushTokenController's identical doc comment. */
@RestController
class BuyerPushTokenController(
    private val buyerPushTokenService: BuyerPushTokenService,
) {
    @PostMapping("/api/me/buyer/push-tokens")
    fun register(@Valid @RequestBody input: RegisterPushTokenInput): ResponseEntity<Void> {
        buyerPushTokenService.register(input)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/api/me/buyer/push-tokens")
    fun unregister(@Valid @RequestBody input: UnregisterPushTokenInput): ResponseEntity<Void> {
        buyerPushTokenService.unregister(input)
        return ResponseEntity.noContent().build()
    }
}
