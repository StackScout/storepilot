package com.storepilot.backend.booking

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
class BookableServiceController(
    private val bookableServiceService: BookableServiceService,
) {
    @GetMapping("/api/bookable-services/{id}")
    fun getById(@PathVariable id: UUID): BookableServiceResponse = bookableServiceService.getById(id)

    @GetMapping("/api/stores/{storeId}/bookable-services")
    fun listByStore(@PathVariable storeId: UUID): List<BookableServiceResponse> = bookableServiceService.listByStore(storeId)

    @PostMapping("/api/stores/{storeId}/bookable-services", consumes = ["multipart/form-data"])
    fun create(
        @PathVariable storeId: UUID,
        @Valid @RequestPart("data") input: BookableServiceFormInput,
        @RequestPart(value = "images", required = false) images: List<MultipartFile>?,
    ): ResponseEntity<BookableServiceResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(bookableServiceService.create(storeId, input, images.orEmpty()))

    @PatchMapping("/api/bookable-services/{id}", consumes = ["multipart/form-data"])
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestPart("data") input: BookableServiceFormInput,
        @RequestPart(value = "images", required = false) images: List<MultipartFile>?,
    ): BookableServiceResponse = bookableServiceService.update(id, input, images.orEmpty())

    @DeleteMapping("/api/bookable-services/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        bookableServiceService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
