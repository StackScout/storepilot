package com.storepilot.backend.common

/**
 * Matches the error shape already documented in
 * docs/api-contracts.md#error-response-convention-recommended — kept in
 * sync deliberately so the frontend's error handling doesn't need to change
 * shape when it's pointed at this API instead of the mock layer.
 */
data class ApiError(
    val error: ErrorBody,
) {
    data class ErrorBody(
        val code: String,
        val message: String,
        val fields: Map<String, String>? = null,
    )

    companion object {
        fun of(code: String, message: String, fields: Map<String, String>? = null) =
            ApiError(ErrorBody(code, message, fields))
    }
}
