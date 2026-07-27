package com.islandcart.backend.product

import com.islandcart.backend.common.PageResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/** Matches the endpoints already documented in docs/api-contracts.md#products. */
@RestController
class ProductController(
    private val productService: ProductService,
) {
    @GetMapping("/api/products")
    fun search(
        @RequestParam category: String?,
        @RequestParam query: String?,
        @RequestParam minPriceLkr: Int?,
        @RequestParam maxPriceLkr: Int?,
        @RequestParam sort: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "24") size: Int,
    ): PageResponse<ProductResponse> = productService.search(category, query, minPriceLkr, maxPriceLkr, sort, page, size)

    @GetMapping("/api/products/{id}")
    fun getById(@PathVariable id: UUID): ProductResponse = productService.getById(id)

    @GetMapping("/api/stores/{storeId}/products")
    fun listByStore(@PathVariable storeId: UUID): List<ProductResponse> = productService.listByStore(storeId)

    @PostMapping("/api/stores/{storeId}/products", consumes = ["multipart/form-data"])
    fun create(
        @PathVariable storeId: UUID,
        @Valid @RequestPart("data") input: ProductFormInput,
        @RequestPart(value = "images", required = false) images: List<MultipartFile>?,
    ): ResponseEntity<ProductResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(productService.create(storeId, input, images.orEmpty()))

    @PatchMapping("/api/products/{id}", consumes = ["multipart/form-data"])
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestPart("data") input: ProductFormInput,
        @RequestPart(value = "images", required = false) images: List<MultipartFile>?,
    ): ProductResponse = productService.update(id, input, images.orEmpty())

    @DeleteMapping("/api/products/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        productService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
