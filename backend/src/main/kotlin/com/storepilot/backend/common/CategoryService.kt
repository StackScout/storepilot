package com.storepilot.backend.common

import com.storepilot.backend.booking.BookableServiceRepository
import com.storepilot.backend.product.ProductRepository
import com.storepilot.backend.store.StoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val storeRepository: StoreRepository,
    private val productRepository: ProductRepository,
    private val bookableServiceRepository: BookableServiceRepository,
) {
    /** GET /api/categories — active only, for onboarding/product/service-form dropdowns. */
    fun listActive(): List<CategoryResponse> = categoryRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc().map { it.toResponse() }

    /** GET /api/admin/categories — every category, including inactive ones, for the admin management page. */
    fun listAll(): List<CategoryResponse> = categoryRepository.findAllByOrderBySortOrderAscNameAsc().map { it.toResponse() }

    @Transactional
    fun create(input: CategoryFormInput): CategoryResponse {
        if (categoryRepository.existsByWireValue(input.wireValue)) {
            throw ConflictException("A category with wire value \"${input.wireValue}\" already exists")
        }
        val category = Category(name = input.name, wireValue = input.wireValue, icon = input.icon, sortOrder = input.sortOrder, active = input.active)
        return categoryRepository.save(category).toResponse()
    }

    @Transactional
    fun update(id: UUID, input: CategoryFormInput): CategoryResponse {
        val category = categoryRepository.findById(id).orElseThrow { NotFoundException("Category $id not found") }
        val existingWithSameWireValue = categoryRepository.findByWireValue(input.wireValue)
        if (existingWithSameWireValue != null && existingWithSameWireValue.id != id) {
            throw ConflictException("A category with wire value \"${input.wireValue}\" already exists")
        }
        category.name = input.name
        category.wireValue = input.wireValue
        category.icon = input.icon
        category.sortOrder = input.sortOrder
        category.active = input.active
        return categoryRepository.save(category).toResponse()
    }

    /**
     * Refused whenever any store/product/bookable-service still references
     * this category's wire value — since `category` columns are a plain
     * validated string, not a real FK, deleting the row out from under them
     * would leave every reference silently unresolvable (the next create/
     * update touching that store/product/service would fail category
     * validation, and the category would vanish from anywhere it's
     * displayed). Deactivating (input.active = false via update()) is the
     * intended way to retire a category that's still in use — mirrors this
     * codebase's other "outstanding obligation" delete guards (e.g.
     * StoreService.closeStore).
     */
    @Transactional
    fun delete(id: UUID) {
        val category = categoryRepository.findById(id).orElseThrow { NotFoundException("Category $id not found") }
        if (storeRepository.existsByCategory(category.wireValue) ||
            productRepository.existsByCategory(category.wireValue) ||
            bookableServiceRepository.existsByCategory(category.wireValue)
        ) {
            throw ConflictException("Can't delete a category still in use by a store, product, or service — deactivate it instead")
        }
        categoryRepository.delete(category)
    }
}
