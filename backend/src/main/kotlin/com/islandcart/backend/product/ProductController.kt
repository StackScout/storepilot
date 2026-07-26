package com.islandcart.backend.product

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
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
        @RequestParam limit: Int?,
    ): List<ProductResponse> = productService.search(category, query, limit)

    @GetMapping("/api/products/{id}")
    fun getById(@PathVariable id: UUID): ProductResponse = productService.getById(id)

    @GetMapping("/api/stores/{storeId}/products")
    fun listByStore(@PathVariable storeId: UUID): List<ProductResponse> = productService.listByStore(storeId)

    @PostMapping("/api/stores/{storeId}/products")
    fun create(
        @PathVariable storeId: UUID,
        @Valid @RequestBody input: ProductFormInput,
    ): ResponseEntity<ProductResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(productService.create(storeId, input))

    @PatchMapping("/api/products/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody input: ProductFormInput,
    ): ProductResponse = productService.update(id, input)

    @DeleteMapping("/api/products/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        productService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
