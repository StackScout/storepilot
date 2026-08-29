package com.storepilot.backend.product

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
import com.storepilot.backend.store.StoreSettingsRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/** Hard cap regardless of what a caller requests via `size` — a client-supplied page size has no other bound otherwise. */
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
    private val wishlistItemRepository: WishlistItemRepository,
    private val categoryRepository: CategoryRepository,
) {
    /**
     * GET /api/products — the matching row set is never fully materialized:
     * filtering (category/query/price) and sorting both happen in the SQL
     * query itself, and the DB is only ever asked for one page's worth of
     * rows, not "everything, then take()".
     *
     * A present [query] branches to the relevance-ranked full-text search
     * path (ProductRepository.searchFullText, see its doc comment) instead
     * of the plain Specification browse path below — substring matching has
     * no concept of "how well" something matched, so ordering a text search
     * by createdAt (this method's own default with no query) would be
     * actively misleading. An explicit [sort] still wins over relevance,
     * same as it already overrides the no-query default.
     */
    fun search(
        category: String?,
        query: String?,
        minPrice: Int?,
        maxPrice: Int?,
        sort: String?,
        page: Int,
        size: Int,
    ): PageResponse<ProductResponse> {
        val validatedCategory = category?.let { categoryRepository.requireCategory(it) }
        val trimmedQuery = query?.trim()
        val boundedPage = page.coerceAtLeast(0)
        val boundedSize = size.coerceIn(1, MAX_PAGE_SIZE)

        if (!trimmedQuery.isNullOrBlank()) {
            val sortMode = when (sort) {
                "price-asc", "price-desc", "rating" -> sort
                else -> "relevance"
            }
            val results = productRepository.searchFullText(
                category = validatedCategory,
                query = trimmedQuery,
                likePattern = "%${trimmedQuery.lowercase()}%",
                minPrice = minPrice,
                maxPrice = maxPrice,
                sortMode = sortMode,
                pageable = PageRequest.of(boundedPage, boundedSize),
            )
            return results.toPageResponse { it.toResponse(fileStorageService) }
        }

        val spec = Specification.allOf(
            ProductSpecifications.storeActive(),
            ProductSpecifications.notDraft(),
            ProductSpecifications.hasCategory(validatedCategory),
            ProductSpecifications.priceBetween(minPrice, maxPrice),
        )
        val sortOrder = when (sort) {
            "price-asc" -> Sort.by("price").ascending()
            "price-desc" -> Sort.by("price").descending()
            "rating" -> Sort.by("rating").descending()
            else -> Sort.by("createdAt").descending()
        }
        val pageable = PageRequest.of(boundedPage, boundedSize, sortOrder)
        val results = productRepository.findAll(spec, pageable)
        return results.toPageResponse { it.toResponse(fileStorageService) }
    }

    /**
     * A draft is invisible to anyone but its owning seller — reported as
     * NotFoundException (not Forbidden) so a stranger probing product IDs
     * can't distinguish "doesn't exist" from "exists but is a draft."
     * Shared by both the public product-detail lookup and the seller's own
     * edit-product page, so ownership (not just role) has to be checked.
     */
    fun getById(id: UUID): ProductResponse {
        val product = productRepository.findById(id).orElseThrow { NotFoundException("Product $id not found") }
        if (product.status == ProductStatus.DRAFT && !isOwnedByCurrentSeller(product.store)) {
            throw NotFoundException("Product $id not found")
        }
        return product.toResponse(fileStorageService)
    }

    /** For internal cross-service use (e.g. OrderService snapshotting item details) — returns the entity, not a DTO. */
    fun findEntity(id: UUID): Product? = productRepository.findById(id).orElse(null)

    /**
     * OrderService.createOrder() already rejects a checkout whose quantity
     * exceeds stock before this ever runs (for any trackStock product), so
     * the maxOf(0, ...) clamp here is just defense-in-depth against a
     * concurrent transaction racing the same product between that check and
     * this decrement — not the primary guard against overselling anymore.
     * Silently skipping a productId that no longer exists is still an
     * accepted gap (see docs/gaps-and-assumptions.md).
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

    /**
     * Reverses decrementStock — called when a cancelled order's stock was
     * never actually shipped (see OrderService.updateStatus's CANCELLED
     * branch). Flips OUT_OF_STOCK back to ACTIVE once restored quantity is
     * positive, mirroring decrementStock's forced OUT_OF_STOCK the other
     * way; DRAFT is left alone since that's an independent seller choice,
     * not a stock-derived state (see this class's doc comment). Also clears
     * any pending low-stock alert, same as a manual restock via update()
     * below — otherwise LowStockAlertJob's `lastLowStockAlertSentAt is null`
     * gate would never re-arm after a cancellation pushed stock back up.
     */
    @Transactional
    fun restoreStock(items: List<Pair<UUID, Int>>) {
        for ((productId, quantity) in items) {
            val product = productRepository.findById(productId).orElse(null) ?: continue
            if (!product.trackStock) continue
            product.stockQuantity += quantity
            product.lastLowStockAlertSentAt = null
            if (product.status == ProductStatus.OUT_OF_STOCK && product.stockQuantity > 0) {
                product.status = ProductStatus.ACTIVE
            }
            productRepository.save(product)
        }
    }

    /** Unpaged — internal cross-service use (e.g. SellerExportService's full data-export bundle). GET /api/stores/{storeId}/products uses the paged overload below. */
    fun listByStore(storeId: UUID): List<ProductResponse> {
        val products = if (isOwnedByCurrentSeller(storeId)) {
            productRepository.findByStoreIdOrderByUpdatedAtDesc(storeId)
        } else {
            productRepository.findByStoreIdAndStatusNotOrderByUpdatedAtDesc(storeId, ProductStatus.DRAFT)
        }
        return products.map { it.toResponse(fileStorageService) }
    }

    /**
     * Shared by the seller's own product list (needs every status,
     * including drafts) and the public storefront's per-store product grid
     * (must never show a draft) — same endpoint, so the response depends on
     * whether the caller owns this store, not on a query param a public
     * caller could just set themselves.
     */
    fun listByStore(storeId: UUID, page: Int, size: Int): PageResponse<ProductResponse> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        val products = if (isOwnedByCurrentSeller(storeId)) {
            productRepository.findByStoreIdOrderByUpdatedAtDesc(storeId, pageable)
        } else {
            productRepository.findByStoreIdAndStatusNotOrderByUpdatedAtDesc(storeId, ProductStatus.DRAFT, pageable)
        }
        return products.toPageResponse { it.toResponse(fileStorageService) }
    }

    @Transactional
    fun create(storeId: UUID, input: ProductFormInput, images: List<MultipartFile>): ProductResponse {
        require(images.isNotEmpty()) { "Upload at least one product image" }
        val store = requireStore(storeId)
        requireOwnership(store)
        val category = categoryRepository.requireCategory(input.category)
        requireCategoryMatchesStore(store, category)
        val trackStock = effectiveTrackStock(storeId, input.trackStock)
        val sku = input.sku?.trim()?.takeIf { it.isNotBlank() }
        requireUniqueSku(storeId, sku)
        val product = Product(
            store = store,
            name = input.name,
            slug = uniqueSlug(storeId, input.name),
            description = input.description,
            category = category,
            price = input.price,
            compareAtPrice = input.compareAtPrice,
            stockQuantity = input.stockQuantity,
            trackStock = trackStock,
            status = resolveStatus(input, trackStock),
            sku = sku,
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
        val category = categoryRepository.requireCategory(input.category)
        requireCategoryMatchesStore(product.store, category)
        product.name = input.name
        product.description = input.description
        product.category = category
        product.price = input.price
        product.compareAtPrice = input.compareAtPrice
        // A restock clears any pending low-stock alert so LowStockAlertJob can re-fire next time stock actually drops low again — see Product.lastLowStockAlertSentAt's doc comment.
        if (input.stockQuantity > product.stockQuantity) product.lastLowStockAlertSentAt = null
        product.stockQuantity = input.stockQuantity
        val trackStock = effectiveTrackStock(requireNotNull(product.store.id), input.trackStock)
        product.trackStock = trackStock
        product.status = resolveStatus(input, trackStock)
        val sku = input.sku?.trim()?.takeIf { it.isNotBlank() }
        requireUniqueSku(requireNotNull(product.store.id), sku, excludingProductId = product.id)
        product.sku = sku
        // Slug is NOT regenerated on rename — matches docs/features/product-management.md (URL stays stable).
        if (images.isNotEmpty()) {
            product.images.clear()
            storeImages(product, images)
        }
        return productRepository.save(product).toResponse(fileStorageService)
    }

    /**
     * Stores the FileStorageService reference (not a resolved URL) on each
     * ProductImage — resolved fresh at read time, see ProductMapper. [images]
     * order becomes sortOrder, so index 0 (the frontend's "primary" pick,
     * see ImageUploader) is always the first element on read-back too.
     */
    private fun storeImages(product: Product, images: List<MultipartFile>) {
        images.forEachIndexed { index, file ->
            val reference = fileStorageService.store(
                "product-images",
                file,
                FileUploadPolicies.IMAGE_CONTENT_TYPES,
                FileUploadPolicies.IMAGE_MAX_BYTES,
            )
            product.images.add(ProductImage(product = product, url = reference, alt = product.name, sortOrder = index))
        }
    }

    @Transactional
    fun delete(id: UUID) {
        val product = productRepository.findById(id).orElseThrow { NotFoundException("Product $id not found") }
        requireOwnership(product.store)
        productRepository.deleteById(id)
    }

    /** GET /api/products/{id}/wishlist — public route, works for a signed-out visitor too (reports false), same pattern as StoreService.isFollowing. */
    @Transactional
    fun isWishlisted(productId: UUID): Boolean {
        val buyer = currentActor.buyerOrNull() ?: return false
        return wishlistItemRepository.existsByBuyerIdAndProductId(requireNotNull(buyer.id), productId)
    }

    /** POST /api/products/{id}/wishlist — idempotent, mirrors StoreService.follow. */
    @Transactional
    fun addToWishlist(productId: UUID): Boolean {
        val buyer = currentActor.requireBuyer()
        val product = productRepository.findById(productId).orElseThrow { NotFoundException("Product $productId not found") }
        val buyerId = requireNotNull(buyer.id)
        if (!wishlistItemRepository.existsByBuyerIdAndProductId(buyerId, productId)) {
            wishlistItemRepository.save(WishlistItem(buyer = buyer, product = product))
        }
        return true
    }

    /** DELETE /api/products/{id}/wishlist — idempotent, mirrors StoreService.unfollow. */
    @Transactional
    fun removeFromWishlist(productId: UUID) {
        val buyer = currentActor.requireBuyer()
        val item = wishlistItemRepository.findByBuyerIdAndProductId(requireNotNull(buyer.id), productId) ?: return
        wishlistItemRepository.delete(item)
    }

    /** Unpaged — internal cross-service use (e.g. BuyerExportService's full data-export bundle). GET /api/me/wishlist uses the paged overload below. Mirrors OrderService.listByCurrentBuyer's doc comment on why this needs an explicit (write) @Transactional. */
    @Transactional
    fun listWishlist(): List<ProductResponse> {
        val buyerId = requireNotNull(currentActor.requireBuyer().id)
        return wishlistItemRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId).map { it.product.toResponse(fileStorageService) }
    }

    /** GET /api/me/wishlist — mirrors OrderService.listByCurrentBuyer's doc comment on why this needs an explicit (write) @Transactional. */
    @Transactional
    fun listWishlist(page: Int, size: Int): PageResponse<ProductResponse> {
        val buyerId = requireNotNull(currentActor.requireBuyer().id)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return wishlistItemRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable).toPageResponse { it.product.toResponse(fileStorageService) }
    }

    private fun resolveStatus(input: ProductFormInput, trackStock: Boolean): ProductStatus =
        if (trackStock && input.stockQuantity == 0) ProductStatus.OUT_OF_STOCK else wireValueOf(input.status)

    /** No-op for a blank/null SKU — SKU is optional, and only products that actually set one need to be unique against each other. [excludingProductId] lets update() ignore the product's own current row. */
    private fun requireUniqueSku(storeId: UUID, sku: String?, excludingProductId: UUID? = null) {
        if (sku == null) return
        val existing = productRepository.findByStoreIdAndSkuIgnoreCase(storeId, sku) ?: return
        if (existing.id != excludingProductId) {
            throw ConflictException("A product with SKU \"$sku\" already exists in this store")
        }
    }

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

    /** A product's category is locked to the store's own approved category — see task item 40's doc comment on Store.kt. */
    private fun requireCategoryMatchesStore(store: Store, category: String) {
        if (category != store.category) {
            throw ConflictException("Products must be listed under this store's category (${store.category})")
        }
    }

    /** Unlike requireOwnership, never throws — used where a non-owner (or a guest) is a legitimate caller, just with a narrower view. */
    private fun isOwnedByCurrentSeller(store: Store): Boolean = currentActor.sellerOrNull()?.id == store.seller.id

    private fun isOwnedByCurrentSeller(storeId: UUID): Boolean {
        val seller = currentActor.sellerOrNull() ?: return false
        val store = storeRepository.findById(storeId).orElse(null) ?: return false
        return store.seller.id == seller.id
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
