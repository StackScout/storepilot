package com.storepilot.backend.notification

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class RegisterPushTokenInput(
    @field:NotBlank(message = "A push token is required")
    val token: String,
    @field:Pattern(regexp = "ios|android", message = "platform must be ios or android")
    val platform: String,
)

data class UnregisterPushTokenInput(
    @field:NotBlank(message = "A push token is required")
    val token: String,
)
