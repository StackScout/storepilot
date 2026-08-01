package com.storepilot.backend.product

import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreVerificationStatus
import org.springframework.data.jpa.domain.Specification

/**
 * Built with Specifications rather than a hand-written JPQL string with
 * nullable bind parameters — a `:query is null or lower(...) like ...`
 * JPQL clause hits a real Postgres/JDBC bug (the driver can't infer a type
 * for a null parameter used only inside a function call, and errors with
 * "function lower(bytea) does not exist"). Specifications sidestep it
 * entirely: a predicate is only added to the query when its filter value is
 * actually present.
 */
object ProductSpecifications {
    /**
     * Public product search must only surface products whose owning store is
     * ACTIVE — a pending/rejected store's products otherwise show up in
     * results but 404 on click, since the storefront and product pages
     * reject non-active stores (see StoreService.getBySlug). The path
     * expression root.get("store").get(...) creates the implicit join to
     * the stores table; mirrors StoreService.search's activeOnly filter.
     */
    fun storeActive(): Specification<Product> =
        Specification { root, _, cb ->
            cb.equal(
                root.get<Store>("store").get<StoreVerificationStatus>("verificationStatus"),
                StoreVerificationStatus.ACTIVE,
            )
        }

    /** Public product search must never surface a draft — it's still being prepared, not ready for buyers. Out-of-stock products stay visible (they're a real, published listing, just unavailable to buy right now). */
    fun notDraft(): Specification<Product> =
        Specification { root, _, cb -> cb.notEqual(root.get<ProductStatus>("status"), ProductStatus.DRAFT) }

    fun hasCategory(category: StoreCategory?): Specification<Product> =
        Specification { root, _, cb ->
            if (category == null) null else cb.equal(root.get<StoreCategory>("category"), category)
        }

    fun matchesQuery(query: String?): Specification<Product> =
        Specification { root, _, cb ->
            if (query.isNullOrBlank()) {
                null
            } else {
                val pattern = "%${query.lowercase()}%"
                cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                )
            }
        }

    fun priceBetween(minPrice: Int?, maxPrice: Int?): Specification<Product> =
        Specification { root, _, cb ->
            when {
                minPrice != null && maxPrice != null -> cb.between(root.get("price"), minPrice, maxPrice)
                minPrice != null -> cb.greaterThanOrEqualTo(root.get("price"), minPrice)
                maxPrice != null -> cb.lessThanOrEqualTo(root.get("price"), maxPrice)
                else -> null
            }
        }
}
