package com.storepilot.backend.booking

import com.storepilot.backend.common.CategoryRepository
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.requireCategory
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.common.storage.FileUploadPolicies
import com.storepilot.backend.common.toPageResponse
import com.storepilot.backend.common.wireValueOf
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

private val TERMINAL_STATUSES = setOf(BookingStatus.CANCELLED, BookingStatus.NO_SHOW)

/** Hard cap regardless of what a caller requests via `size` — same convention as ProductService/StoreService's own MAX_PAGE_SIZE. */
private const val MAX_PAGE_SIZE = 100

/** CRUD for bookable services — mirrors ProductService exactly (category-lock, ownership, image upload, slug uniqueness), minus every stock/SKU concept that doesn't apply to a service. */
@Service
@Transactional(readOnly = true)
class BookableServiceService(
    private val serviceRepository: BookableServiceRepository,
    private val bookingRepository: BookingRepository,
    private val storeRepository: StoreRepository,
    private val currentActor: CurrentActor,
    private val fileStorageService: FileStorageService,
    private val categoryRepository: CategoryRepository,
) {
    fun getById(id: UUID): BookableServiceResponse {
        val service = serviceRepository.findById(id).orElseThrow { NotFoundException("Service $id not found") }
        if (service.status == ServiceStatus.DRAFT && !isOwnedByCurrentSeller(service.store)) {
            throw NotFoundException("Service $id not found")
        }
        return service.toResponse(fileStorageService)
    }

    /** For internal cross-service use (BookingService snapshotting item details) — returns the entity, not a DTO. */
    fun findEntity(id: UUID): BookableService? = serviceRepository.findById(id).orElse(null)

    /** Same owner-vs-public split as ProductService.listByStore. */
    fun listByStore(storeId: UUID, page: Int, size: Int): PageResponse<BookableServiceResponse> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        val services = if (isOwnedByCurrentSeller(storeId)) {
            serviceRepository.findByStoreIdOrderByUpdatedAtDesc(storeId, pageable)
        } else {
            serviceRepository.findByStoreIdAndStatusNotOrderByUpdatedAtDesc(storeId, ServiceStatus.DRAFT, pageable)
        }
        return services.toPageResponse { it.toResponse(fileStorageService) }
    }

    /** Whether a store has any publicly-visible bookable service — drives the derived 3-mode storefront UI, see docs/features/bookings.md. */
    fun hasActiveServices(storeId: UUID): Boolean = serviceRepository.existsByStoreIdAndStatus(storeId, ServiceStatus.ACTIVE)

    @Transactional
    fun create(storeId: UUID, input: BookableServiceFormInput, images: List<MultipartFile>): BookableServiceResponse {
        val store = requireStore(storeId)
        requireOwnership(store)
        val category = categoryRepository.requireCategory(input.category)
        requireCategoryMatchesStore(store, category)
        val service = BookableService(
            store = store,
            name = input.name,
            slug = uniqueSlug(storeId, input.name),
            description = input.description,
            category = category,
            price = input.price,
            durationMinutes = input.durationMinutes,
            bufferMinutes = input.bufferMinutes,
            status = wireValueOf(input.status),
        )
        storeImages(service, images)
        return serviceRepository.save(service).toResponse(fileStorageService)
    }

    /** [images] empty = keep existing images unchanged; non-empty replaces the whole set — same convention as ProductService.update. */
    @Transactional
    fun update(id: UUID, input: BookableServiceFormInput, images: List<MultipartFile>): BookableServiceResponse {
        val service = serviceRepository.findById(id).orElseThrow { NotFoundException("Service $id not found") }
        requireOwnership(service.store)
        val category = categoryRepository.requireCategory(input.category)
        requireCategoryMatchesStore(service.store, category)
        service.name = input.name
        service.description = input.description
        service.category = category
        service.price = input.price
        service.durationMinutes = input.durationMinutes
        service.bufferMinutes = input.bufferMinutes
        service.status = wireValueOf(input.status)
        if (images.isNotEmpty()) {
            service.images.clear()
            storeImages(service, images)
        }
        return serviceRepository.save(service).toResponse(fileStorageService)
    }

    private fun storeImages(service: BookableService, images: List<MultipartFile>) {
        images.forEachIndexed { index, file ->
            val reference = fileStorageService.store(
                "service-images",
                file,
                FileUploadPolicies.IMAGE_CONTENT_TYPES,
                FileUploadPolicies.IMAGE_MAX_BYTES,
            )
            service.images.add(BookableServiceImage(service = service, url = reference, alt = service.name, sortOrder = index))
        }
    }

    /** Refused while any non-terminal booking still references this service — see BookableService's doc comment. */
    @Transactional
    fun delete(id: UUID) {
        val service = serviceRepository.findById(id).orElseThrow { NotFoundException("Service $id not found") }
        requireOwnership(service.store)
        if (bookingRepository.existsByServiceIdAndStatusNotIn(id, TERMINAL_STATUSES)) {
            throw ConflictException("Can't delete a service with upcoming or pending bookings — cancel or complete them first")
        }
        serviceRepository.deleteById(id)
    }

    private fun requireStore(storeId: UUID): Store =
        storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }

    private fun requireOwnership(store: Store) {
        val seller = currentActor.requireSeller()
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store ${store.id}")
    }

    /** A service's category is locked to the store's own approved category — identical rule to ProductService. */
    private fun requireCategoryMatchesStore(store: Store, category: String) {
        if (category != store.category) {
            throw ConflictException("Services must be listed under this store's category (${store.category})")
        }
    }

    private fun isOwnedByCurrentSeller(store: Store): Boolean = currentActor.sellerOrNull()?.id == store.seller.id

    private fun isOwnedByCurrentSeller(storeId: UUID): Boolean {
        val seller = currentActor.sellerOrNull() ?: return false
        val store = storeRepository.findById(storeId).orElse(null) ?: return false
        return store.seller.id == seller.id
    }

    private fun uniqueSlug(storeId: UUID, name: String): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "service" }
        var candidate = base
        var suffix = 1
        while (serviceRepository.findByStoreIdAndSlug(storeId, candidate) != null) {
            candidate = "$base-${++suffix}"
        }
        return candidate
    }
}
