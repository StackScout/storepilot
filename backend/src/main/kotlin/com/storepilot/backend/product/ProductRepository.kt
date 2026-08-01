package com.storepilot.backend.product

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface ProductRepository : JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    fun findByStoreIdOrderByUpdatedAtDesc(storeId: UUID): List<Product>

    /** Public/non-owner view of a store's products — see ProductService.listByStore. */
    fun findByStoreIdAndStatusNotOrderByUpdatedAtDesc(storeId: UUID, status: ProductStatus): List<Product>

    fun findByStoreIdAndSlug(storeId: UUID, slug: String): Product?
}
