package com.islandcart.backend.product

import com.islandcart.backend.store.StoreCategory
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
}
