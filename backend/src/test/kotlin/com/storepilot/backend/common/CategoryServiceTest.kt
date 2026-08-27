package com.storepilot.backend.common

import com.storepilot.backend.booking.BookableServiceRepository
import com.storepilot.backend.product.ProductRepository
import com.storepilot.backend.store.StoreRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class CategoryServiceTest {
    private val categoryRepository = mockk<CategoryRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val productRepository = mockk<ProductRepository>()
    private val bookableServiceRepository = mockk<BookableServiceRepository>()

    private val service = CategoryService(categoryRepository, storeRepository, productRepository, bookableServiceRepository)

    private fun category(wireValue: String = "handicrafts", active: Boolean = true) =
        Category(name = "Handicrafts", wireValue = wireValue, icon = "hand", sortOrder = 1, active = active)
            .apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    @BeforeEach
    fun setUp() {
        every { categoryRepository.save(any()) } answers {
            (firstArg() as Category).apply { if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = Instant.now() }
        }
    }

    @Test
    fun `listActive returns only active categories`() {
        every { categoryRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc() } returns listOf(category())

        assertEquals(1, service.listActive().size)
    }

    @Test
    fun `listAll returns every category including inactive`() {
        every { categoryRepository.findAllByOrderBySortOrderAscNameAsc() } returns listOf(category(active = false), category())

        assertEquals(2, service.listAll().size)
    }

    @Test
    fun `create rejects a duplicate wire value`() {
        every { categoryRepository.existsByWireValue("handicrafts") } returns true

        assertThrows(ConflictException::class.java) {
            service.create(CategoryFormInput(name = "Handicrafts", wireValue = "handicrafts", icon = "hand"))
        }
    }

    @Test
    fun `create saves a new category`() {
        every { categoryRepository.existsByWireValue("gadgets") } returns false

        val result = service.create(CategoryFormInput(name = "Gadgets", wireValue = "gadgets", icon = "smartphone", sortOrder = 9))

        assertEquals("gadgets", result.wireValue)
        assertEquals(9, result.sortOrder)
        assertTrue(result.active)
    }

    @Test
    fun `update throws for a missing category`() {
        val id = UUID.randomUUID()
        every { categoryRepository.findById(id) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) {
            service.update(id, CategoryFormInput(name = "X", wireValue = "x", icon = "hand"))
        }
    }

    @Test
    fun `update rejects renaming to a wire value already held by a different category`() {
        val existing = category(wireValue = "handicrafts")
        val other = category(wireValue = "jewelry")
        every { categoryRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { categoryRepository.findByWireValue("jewelry") } returns other

        assertThrows(ConflictException::class.java) {
            service.update(existing.id!!, CategoryFormInput(name = "Handicrafts", wireValue = "jewelry", icon = "hand"))
        }
    }

    @Test
    fun `update allows keeping the category's own current wire value`() {
        val existing = category(wireValue = "handicrafts")
        every { categoryRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { categoryRepository.findByWireValue("handicrafts") } returns existing

        val result = service.update(existing.id!!, CategoryFormInput(name = "Renamed", wireValue = "handicrafts", icon = "hand", sortOrder = 5, active = false))

        assertEquals("Renamed", result.name)
        assertEquals(5, result.sortOrder)
        assertFalseActive(result)
    }

    private fun assertFalseActive(response: CategoryResponse) {
        org.junit.jupiter.api.Assertions.assertFalse(response.active)
    }

    @Test
    fun `delete throws for a missing category`() {
        val id = UUID.randomUUID()
        every { categoryRepository.findById(id) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.delete(id) }
    }

    @Test
    fun `delete rejects a category still referenced by a store`() {
        val existing = category()
        every { categoryRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { storeRepository.existsByCategory("handicrafts") } returns true

        assertThrows(ConflictException::class.java) { service.delete(existing.id!!) }
    }

    @Test
    fun `delete rejects a category still referenced by a product`() {
        val existing = category()
        every { categoryRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { storeRepository.existsByCategory("handicrafts") } returns false
        every { productRepository.existsByCategory("handicrafts") } returns true

        assertThrows(ConflictException::class.java) { service.delete(existing.id!!) }
    }

    @Test
    fun `delete rejects a category still referenced by a bookable service`() {
        val existing = category()
        every { categoryRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { storeRepository.existsByCategory("handicrafts") } returns false
        every { productRepository.existsByCategory("handicrafts") } returns false
        every { bookableServiceRepository.existsByCategory("handicrafts") } returns true

        assertThrows(ConflictException::class.java) { service.delete(existing.id!!) }
    }

    @Test
    fun `delete removes a category with no remaining references`() {
        val existing = category()
        every { categoryRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { storeRepository.existsByCategory("handicrafts") } returns false
        every { productRepository.existsByCategory("handicrafts") } returns false
        every { bookableServiceRepository.existsByCategory("handicrafts") } returns false
        every { categoryRepository.delete(existing) } returns Unit

        service.delete(existing.id!!)

        io.mockk.verify { categoryRepository.delete(existing) }
    }
}
