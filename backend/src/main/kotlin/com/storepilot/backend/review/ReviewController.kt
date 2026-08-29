package com.storepilot.backend.review

import com.storepilot.backend.common.PageResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ReviewController(
    private val reviewService: ReviewService,
) {
    @GetMapping("/api/products/{productId}/reviews")
    fun listByProduct(
        @PathVariable productId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ReviewResponse> = reviewService.listByProduct(productId, page, size)

    @PostMapping("/api/products/{productId}/reviews")
    fun createProductReview(@PathVariable productId: UUID, @Valid @RequestBody input: ReviewInput): ResponseEntity<ReviewResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createProductReview(productId, input))

    @GetMapping("/api/stores/{storeId}/reviews")
    fun listByStore(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ReviewResponse> = reviewService.listByStore(storeId, page, size)

    @PostMapping("/api/stores/{storeId}/reviews")
    fun createStoreReview(@PathVariable storeId: UUID, @Valid @RequestBody input: ReviewInput): ResponseEntity<ReviewResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createStoreReview(storeId, input))
}
