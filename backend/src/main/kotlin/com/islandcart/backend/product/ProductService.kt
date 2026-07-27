package com.islandcart.backend.product

import com.islandcart.backend.common.ForbiddenException
import com.islandcart.backend.common.NotFoundException
import com.islandcart.backend.common.PageResponse
import com.islandcart.backend.common.security.CurrentActor
import com.islandcart.backend.common.storage.FileStorageService
import com.islandcart.backend.common.storage.FileUploadPolicies
import com.islandcart.backend.common.toPageResponse
import com.islandcart.backend.common.wireValueOf
import com.islandcart.backend.store.Store
import com.islandcart.backend.store.StoreCategory
import com.islandcart.backend.store.StoreRepository
import com.islandcart.backend.store.StoreSettingsRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/** Hard cap regardless of what a caller requests via `size` — see docs/gaps-and-assumptions.md's search-scalability note. */
private const val MAX_PAGE_SIZE = 100

/**
 * Business rule ported from docs/features/product-management.md: a product
 * whose stockQuantity is 0 always has status forced to "out-of-stock",
 * regardless of what the caller submitted — enforced here (server-side),
 * unlike the frontend mock where this rule only lived in the client.
 */
@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: ProductRepository,
    private val storeRepository: StoreRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val currentActor: CurrentActor,
    private val fileStorageService: FileStorageService,
) {
    /**
     * GET /api/products — the matching row set is never fully materialized:
     * filtering (category/query/price) and sorting both happen in the SQL
     * query itself (Specification + Pageable), and the DB is only ever
     * asked for one page's worth of rows, not "everything, then take()".
     */
    fun search(
        category: String?,
        query: String?,
        minPriceLkr: Int?,
        maxPriceLkr: Int?,
        sort: String?,
        page: Int,
        size: Int,
    ): PageResponse<ProductResponse> {
        val categoryEnum = category?.let { wireValueOf<StoreCategory>(it) }
        val spec = Specification.allOf(
            ProductSpecifications.hasCategory(categoryEnum),
            ProductSpecifications.matchesQuery(query?.trim()),
            ProductSpecifications.priceBetween(minPriceLkr, maxPriceLkr),
        )
        val sortOrder = when (sort) {
            "price-asc" -> Sort.by("priceLkr").ascending()
            "price-desc" -> Sort.by("priceLkr").descending()
            "rating" -> Sort.by("rating").descending()
            else -> Sort.by("createdAt").descending()
        }
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE), sortOrder)
        val results = productRepository.findAll(spec, pageable)
        return results.toPageResponse { it.toResponse(fileStorageService) }
    }

    fun getById(id: UUID): ProductResponse =
        productRepository.findById(id).orElseThrow { NotFoundException("Product $id not found") }.toResponse(fileStorageService)

    /** For internal cross-service use (e.g. OrderService snapshotting item details) — returns the entity, not a DTO. */
    fun findEntity(id: UUID): Product? = productRepository.findById(id).orElse(null)

    /**
     * Mirrors products.service.ts#decrementStock exactly: clamps to zero
     * rather than rejecting insufficient stock, and silently skips a
     * productId that no longer exists — both are documented, accepted gaps
     * (see docs/gaps-and-assumptions.md), not something to "fix" here.
     */
    @Transactional
    fun decrementStock(items: List<Pair<UUID, Int>>) {
        for ((productId, quantity) in items) {
            val product = productRepository.findById(productId).orElse(null) ?: continue
            if (!product.trackStock) continue
            product.stockQuantity = maxOf(0, product.stockQuantity - quantity)
            if (product.stockQuantity == 0) product.status = ProductStatus.OUT_OF_STOCK
            productRepository.save(product)
        }
    }

    fun listByStore(storeId: UUID): List<ProductResponse> =
        productRepository.findByStoreIdOrderByUpdatedAtDesc(storeId).map { it.toResponse(fileStorageService) }

    @Transactional
    fun create(storeId: UUID, input: ProductFormInput, images: List<MultipartFile>): ProductResponse {
        require(images.isNotEmpty()) { "Upload at least one product image" }
        val store = requireStore(storeId)
        requireOwnership(store)
        val trackStock = effectiveTrackStock(storeId, input.trackStock)
        val product = Product(
            store = store,
            name = input.name,
            slug = uniqueSlug(storeId, input.name),
            description = input.description,
            category = wireValueOf(input.category),
            priceLkr = input.priceLkr,
            compareAtPriceLkr = input.compareAtPriceLkr,
            stockQuantity = input.stockQuantity,
            trackStock = trackStock,
            status = resolveStatus(input, trackStock),
            sku = input.sku?.trim()?.takeIf { it.isNotBlank() },
        )
        storeImages(product, images)
        return productRepository.save(product).toResponse(fileStorageService)
    }

    /**
     * [images] is only the set of NEW files to upload — empty means "keep
     * the product's existing images unchanged" (editing price/description
     * shouldn't force a re-upload); non-empty REPLACES the whole set,
     * matching how the frontend form always resubmits a full image list
     * rather than diffing individual images.
     */
    @Transactional
    fun update(id: UUID, input: ProductFormInput, images: List<MultipartFile>): ProductResponse {
        val product = productRepository.findById(id).orElseThrow { NotFoundException("Product $id not found") }
        requireOwnership(product.store)
        product.name = input.name
        product.description = input.description
        product.category = wireValueOf(input.category)
        product.priceLkr = input.priceLkr
        product.compareAtPriceLkr = input.compareAtPriceLkr
        product.stockQuantity = input.stockQuantity
        val trackStock = effectiveTrackStock(requireNotNull(product.store.id), input.trackStock)
        product.trackStock = trackStock
        product.status = resolveStatus(input, trackStock)
        product.sku = input.sku?.trim()?.takeIf { it.isNotBlank() }
        // Slug is NOT regenerated on rename — matches docs/features/product-management.md (URL stays stable).
        if (images.isNotEmpty()) {
            product.images.clear()
            storeImages(product, images)
        }
        return productRepository.save(product).toResponse(fileStorageService)
    }

    /** Stores the FileStorageService reference (not a resolved URL) on each ProductImage — resolved fresh at read time, see ProductMapper. */
    private fun storeImages(product: Product, images: List<MultipartFile>) {
        images.forEach { file ->
            val reference = fileStorageService.store(
                "product-images",
                file,
                FileUploadPolicies.IMAGE_CONTENT_TYPES,
                FileUploadPolicies.IMAGE_MAX_BYTES,
            )
            product.images.add(ProductImage(product = product, url = reference, alt = product.name))
        }
    }

    @Transactional
    fun delete(id: UUID) {
        val product = productRepository.findById(id).orElseThrow { NotFoundException("Product $id not found") }
        requireOwnership(product.store)
        productRepository.deleteById(id)
    }

    private fun resolveStatus(input: ProductFormInput, trackStock: Boolean): ProductStatus =
        if (trackStock && input.stockQuantity == 0) ProductStatus.OUT_OF_STOCK else wireValueOf(input.status)

    /** The store-wide switch always wins: stock tracking is off for every product once a seller disables it at the store level, regardless of what the product itself requests. */
    private fun effectiveTrackStock(storeId: UUID, requestedTrackStock: Boolean): Boolean {
        val storeManagesStock = storeSettingsRepository.findById(storeId).orElse(null)?.stockManagementEnabled ?: true
        return requestedTrackStock && storeManagesStock
    }

    private fun requireStore(storeId: UUID): Store =
        storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }

    private fun requireOwnership(store: Store) {
        val seller = currentActor.requireSeller()
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store ${store.id}")
    }

    private fun uniqueSlug(storeId: UUID, name: String): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "product" }
        var candidate = base
        var suffix = 1
        while (productRepository.findByStoreIdAndSlug(storeId, candidate) != null) {
            candidate = "$base-${++suffix}"
        }
        return candidate
    }
}
