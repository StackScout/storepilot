package com.islandcart.backend.product

import com.islandcart.backend.common.NotFoundException
import com.islandcart.backend.common.wireValueOf
import com.islandcart.backend.store.Store
import com.islandcart.backend.store.StoreCategory
import com.islandcart.backend.store.StoreRepository
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

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
) {
    fun search(category: String?, query: String?, limit: Int?): List<ProductResponse> {
        val categoryEnum = category?.let { wireValueOf<StoreCategory>(it) }
        val spec = Specification.allOf(
            ProductSpecifications.hasCategory(categoryEnum),
            ProductSpecifications.matchesQuery(query?.trim()),
        )
        val results = productRepository.findAll(spec)
        return results.let { if (limit != null) it.take(limit) else it }.map { it.toResponse() }
    }

    fun getById(id: UUID): ProductResponse =
        productRepository.findById(id).orElseThrow { NotFoundException("Product $id not found") }.toResponse()

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
            product.stockQuantity = maxOf(0, product.stockQuantity - quantity)
            if (product.stockQuantity == 0) product.status = ProductStatus.OUT_OF_STOCK
            productRepository.save(product)
        }
    }

    fun listByStore(storeId: UUID): List<ProductResponse> =
        productRepository.findByStoreIdOrderByUpdatedAtDesc(storeId).map { it.toResponse() }

    @Transactional
    fun create(storeId: UUID, input: ProductFormInput): ProductResponse {
        val store = requireStore(storeId)
        val product = Product(
            store = store,
            name = input.name,
            slug = uniqueSlug(storeId, input.name),
            description = input.description,
            category = wireValueOf(input.category),
            priceLkr = input.priceLkr,
            compareAtPriceLkr = input.compareAtPriceLkr,
            stockQuantity = input.stockQuantity,
            status = resolveStatus(input),
            sku = input.sku,
        )
        product.images.add(ProductImage(product = product, url = input.imageUrl, alt = input.name))
        return productRepository.save(product).toResponse()
    }

    @Transactional
    fun update(id: UUID, input: ProductFormInput): ProductResponse {
        val product = productRepository.findById(id).orElseThrow { NotFoundException("Product $id not found") }
        product.name = input.name
        product.description = input.description
        product.category = wireValueOf(input.category)
        product.priceLkr = input.priceLkr
        product.compareAtPriceLkr = input.compareAtPriceLkr
        product.stockQuantity = input.stockQuantity
        product.status = resolveStatus(input)
        product.sku = input.sku
        // Slug is NOT regenerated on rename — matches docs/features/product-management.md (URL stays stable).
        if (product.images.isNotEmpty()) {
            product.images[0].url = input.imageUrl
            product.images[0].alt = input.name
        } else {
            product.images.add(ProductImage(product = product, url = input.imageUrl, alt = input.name))
        }
        return productRepository.save(product).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        if (!productRepository.existsById(id)) throw NotFoundException("Product $id not found")
        productRepository.deleteById(id)
    }

    private fun resolveStatus(input: ProductFormInput): ProductStatus =
        if (input.stockQuantity == 0) ProductStatus.OUT_OF_STOCK else wireValueOf(input.status)

    private fun requireStore(storeId: UUID): Store =
        storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }

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
