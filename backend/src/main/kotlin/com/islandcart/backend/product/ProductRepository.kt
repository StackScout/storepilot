package com.islandcart.backend.product

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface ProductRepository : JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    fun findByStoreIdOrderByUpdatedAtDesc(storeId: UUID): List<Product>

    fun findByStoreIdAndSlug(storeId: UUID, slug: String): Product?
}
