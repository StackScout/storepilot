package com.storepilot.backend.product

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ProductRepository : JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    fun findByStoreIdOrderByUpdatedAtDesc(storeId: UUID): List<Product>

    /** Public/non-owner view of a store's products — see ProductService.listByStore. */
    fun findByStoreIdAndStatusNotOrderByUpdatedAtDesc(storeId: UUID, status: ProductStatus): List<Product>

    fun findByStoreIdAndSlug(storeId: UUID, slug: String): Product?

    /** Case-insensitive — see ProductService's duplicate-SKU check. Blank/null SKUs are never passed in, so no products-without-a-SKU false positive. */
    fun findByStoreIdAndSkuIgnoreCase(storeId: UUID, sku: String): Product?

    /**
     * Tracked-stock, active products that have dropped to/below [threshold]
     * but aren't fully out of stock (that's already visible via status) and
     * haven't been alerted yet — see Product.lastLowStockAlertSentAt's doc
     * comment and LowStockAlertJob.
     */
    @Query(
        """
        select p from Product p
        where p.trackStock = true
          and p.status = com.storepilot.backend.product.ProductStatus.ACTIVE
          and p.stockQuantity > 0 and p.stockQuantity <= :threshold
          and p.lastLowStockAlertSentAt is null
        """,
    )
    fun findLowStock(@Param("threshold") threshold: Int): List<Product>
}
