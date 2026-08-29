package com.storepilot.backend.booking

import com.storepilot.backend.common.CategoryRepository
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.mock.web.MockMultipartFile
import java.time.Instant
import java.util.Optional
import java.util.UUID

class BookableServiceServiceTest {
    private val serviceRepository = mockk<BookableServiceRepository>()
    private val bookingRepository = mockk<BookingRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val currentActor = mockk<CurrentActor>()
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)
    private val categoryRepository = mockk<CategoryRepository>()

    private val service = BookableServiceService(serviceRepository, bookingRepository, storeRepository, currentActor, fileStorageService, categoryRepository)

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
    private val storeId: UUID = UUID.randomUUID()
    private lateinit var store: Store
    private val image = MockMultipartFile("images", "photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller, slug = "studio", name = "Studio", tagline = "tagline", description = "description",
            category = "handicrafts", address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000", verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = storeId }
        every { currentActor.requireSeller() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { serviceRepository.findByStoreIdAndSlug(storeId, any()) } returns null
        every { categoryRepository.existsByWireValue(any()) } returns true
    }

    private fun formInput(category: String = "handicrafts") = BookableServiceFormInput(
        name = "A service", description = "A description long enough", category = category,
        price = 5000, durationMinutes = 30, bufferMinutes = 0, status = "active",
    )

    private fun bookableService(status: ServiceStatus = ServiceStatus.ACTIVE) = BookableService(
        store = store, name = "Existing", slug = "existing", description = "description", category = "handicrafts",
        price = 5000, durationMinutes = 30, status = status,
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = Instant.now() }

    @Test
    fun `create rejects a category that doesn't match the store's own category`() {
        val ex = assertThrows(ConflictException::class.java) { service.create(storeId, formInput("electronics"), listOf(image)) }
        assertTrue(ex.message!!.contains("handicrafts"))
    }

    @Test
    fun `create rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) { service.create(storeId, formInput(), listOf(image)) }
    }

    @Test
    fun `create succeeds when the category matches the store's category`() {
        every { serviceRepository.save(any()) } answers {
            (firstArg() as BookableService).apply { id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = Instant.now(); images.forEach { it.id = UUID.randomUUID() } }
        }

        val result = service.create(storeId, formInput(), listOf(image))

        assertEquals("handicrafts", result.category)
    }

    @Test
    fun `create de-dupes the slug against an existing one in the same store`() {
        every { serviceRepository.findByStoreIdAndSlug(storeId, "a-service") } returns bookableService()
        every { serviceRepository.findByStoreIdAndSlug(storeId, "a-service-2") } returns null
        val savedSlot = slot<BookableService>()
        every { serviceRepository.save(capture(savedSlot)) } answers {
            (firstArg() as BookableService).apply { id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = Instant.now(); images.forEach { it.id = UUID.randomUUID() } }
        }

        service.create(storeId, formInput(), listOf(image))

        assertEquals("a-service-2", savedSlot.captured.slug)
    }

    @Test
    fun `create works with no images`() {
        every { serviceRepository.save(any()) } answers {
            (firstArg() as BookableService).apply { id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = Instant.now() }
        }

        val result = service.create(storeId, formInput(), emptyList())

        assertEquals(0, result.images.size)
    }

    @Test
    fun `update rejects changing category away from the store's own category`() {
        val existing = bookableService()
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)

        assertThrows(ConflictException::class.java) { service.update(existing.id!!, formInput("jewelry"), emptyList()) }
    }

    @Test
    fun `update rejects a non-owning seller`() {
        val existing = bookableService()
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)

        assertThrows(ForbiddenException::class.java) { service.update(existing.id!!, formInput(), emptyList()) }
    }

    @Test
    fun `update throws for a missing service`() {
        val id = UUID.randomUUID()
        every { serviceRepository.findById(id) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.update(id, formInput(), emptyList()) }
    }

    @Test
    fun `update keeps existing images when none are provided`() {
        val existing = bookableService()
        existing.images.add(BookableServiceImage(service = existing, url = "old.jpg", alt = "old", sortOrder = 0).apply { id = UUID.randomUUID() })
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { serviceRepository.save(any()) } answers { firstArg() }

        service.update(existing.id!!, formInput(), emptyList())

        assertEquals(1, existing.images.size)
        assertEquals("old.jpg", existing.images.first().url)
    }

    @Test
    fun `update replaces the whole image set when new files are provided`() {
        val existing = bookableService()
        existing.images.add(BookableServiceImage(service = existing, url = "old.jpg", alt = "old", sortOrder = 0).apply { id = UUID.randomUUID() })
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { serviceRepository.save(any()) } answers { (firstArg() as BookableService).apply { images.forEach { if (it.id == null) it.id = UUID.randomUUID() } } }

        service.update(existing.id!!, formInput(), listOf(image))

        assertEquals(1, existing.images.size)
        assertTrue(existing.images.first().url != "old.jpg")
    }

    @Test
    fun `update applies every field from the input`() {
        val existing = bookableService()
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { serviceRepository.save(any()) } answers { firstArg() }

        service.update(existing.id!!, formInput().copy(name = "Renamed", price = 6000, durationMinutes = 45, status = "draft"), emptyList())

        assertEquals("Renamed", existing.name)
        assertEquals(6000, existing.price)
        assertEquals(45, existing.durationMinutes)
        assertEquals(ServiceStatus.DRAFT, existing.status)
    }

    @Test
    fun `delete rejects a non-owning seller`() {
        val existing = bookableService()
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)

        assertThrows(ForbiddenException::class.java) { service.delete(existing.id!!) }
    }

    @Test
    fun `delete throws for a missing service`() {
        val id = UUID.randomUUID()
        every { serviceRepository.findById(id) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.delete(id) }
    }

    @Test
    fun `delete rejects when a non-terminal booking still references the service`() {
        val existing = bookableService()
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { bookingRepository.existsByServiceIdAndStatusNotIn(existing.id!!, any()) } returns true

        assertThrows(ConflictException::class.java) { service.delete(existing.id!!) }
    }

    @Test
    fun `delete succeeds when no non-terminal booking references the service`() {
        val existing = bookableService()
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { bookingRepository.existsByServiceIdAndStatusNotIn(existing.id!!, any()) } returns false
        every { serviceRepository.deleteById(existing.id!!) } returns Unit

        service.delete(existing.id!!)

        verify { serviceRepository.deleteById(existing.id!!) }
    }

    @Test
    fun `getById throws for a missing service`() {
        val id = UUID.randomUUID()
        every { serviceRepository.findById(id) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.getById(id) }
    }

    @Test
    fun `getById hides a draft from a non-owning caller as not found`() {
        val existing = bookableService(status = ServiceStatus.DRAFT)
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { currentActor.sellerOrNull() } returns null

        assertThrows(NotFoundException::class.java) { service.getById(existing.id!!) }
    }

    @Test
    fun `getById shows a draft to its owning seller`() {
        val existing = bookableService(status = ServiceStatus.DRAFT)
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { currentActor.sellerOrNull() } returns seller

        assertEquals(existing.id, service.getById(existing.id!!).id)
    }

    @Test
    fun `getById shows an active service to anyone`() {
        val existing = bookableService(status = ServiceStatus.ACTIVE)
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)
        every { currentActor.sellerOrNull() } returns null

        assertEquals(existing.id, service.getById(existing.id!!).id)
    }

    @Test
    fun `findEntity returns null for a missing service`() {
        val id = UUID.randomUUID()
        every { serviceRepository.findById(id) } returns Optional.empty()

        assertEquals(null, service.findEntity(id))
    }

    @Test
    fun `findEntity returns the entity when present`() {
        val existing = bookableService()
        every { serviceRepository.findById(existing.id!!) } returns Optional.of(existing)

        assertEquals(existing, service.findEntity(existing.id!!))
    }

    @Test
    fun `listByStore shows drafts to the owning seller`() {
        every { currentActor.sellerOrNull() } returns seller
        every { serviceRepository.findByStoreIdOrderByUpdatedAtDesc(storeId, any()) } returns
            PageImpl(listOf(bookableService()))

        assertEquals(1, service.listByStore(storeId, 0, 20).content.size)
        verify { serviceRepository.findByStoreIdOrderByUpdatedAtDesc(storeId, any()) }
    }

    @Test
    fun `listByStore hides drafts from a non-owning caller`() {
        every { currentActor.sellerOrNull() } returns null
        every { serviceRepository.findByStoreIdAndStatusNotOrderByUpdatedAtDesc(storeId, ServiceStatus.DRAFT, any()) } returns
            PageImpl(listOf(bookableService()))

        assertEquals(1, service.listByStore(storeId, 0, 20).content.size)
        verify { serviceRepository.findByStoreIdAndStatusNotOrderByUpdatedAtDesc(storeId, ServiceStatus.DRAFT, any()) }
    }

    @Test
    fun `hasActiveServices delegates to the repository`() {
        every { serviceRepository.existsByStoreIdAndStatus(storeId, ServiceStatus.ACTIVE) } returns true

        assertTrue(service.hasActiveServices(storeId))
    }

    @Test
    fun `hasActiveServices reports false when the store has none`() {
        every { serviceRepository.existsByStoreIdAndStatus(storeId, ServiceStatus.ACTIVE) } returns false

        assertFalse(service.hasActiveServices(storeId))
    }
}
