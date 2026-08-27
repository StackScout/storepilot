package com.storepilot.backend.common

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class CategoryController(
    private val categoryService: CategoryService,
) {
    /** Public (see permissions.yml) — mirrors GET /api/states. */
    @GetMapping("/api/categories")
    fun listActive(): List<CategoryResponse> = categoryService.listActive()

    // --- Admin-scoped (matched by SecurityConfig's /api/admin/** catch-all) ---

    @GetMapping("/api/admin/categories")
    fun listAll(): List<CategoryResponse> = categoryService.listAll()

    @PostMapping("/api/admin/categories")
    fun create(@Valid @RequestBody input: CategoryFormInput): ResponseEntity<CategoryResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(input))

    @PatchMapping("/api/admin/categories/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody input: CategoryFormInput): CategoryResponse =
        categoryService.update(id, input)

    @DeleteMapping("/api/admin/categories/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        categoryService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
