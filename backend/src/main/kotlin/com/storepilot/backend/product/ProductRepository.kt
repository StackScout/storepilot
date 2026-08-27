package com.storepilot.backend.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

private const val PRODUCT_SEARCH_FILTERS = """
    s.verification_status = 'active'
    and p.status <> 'draft'
    and (cast(:category as varchar) is null or p.category = cast(:category as varchar))
    and (cast(:minPrice as integer) is null or p.price >= cast(:minPrice as integer))
    and (cast(:maxPrice as integer) is null or p.price <= cast(:maxPrice as integer))
    and (
      p.search_vector @@ websearch_to_tsquery('english', :query)
      or lower(p.name) like :likePattern
      or lower(p.description) like :likePattern
    )
"""

interface ProductRepository : JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    fun findByStoreIdOrderByUpdatedAtDesc(storeId: UUID): List<Product>

    /** Public/non-owner view of a store's products — see ProductService.listByStore. */
    fun findByStoreIdAndStatusNotOrderByUpdatedAtDesc(storeId: UUID, status: ProductStatus): List<Product>

    fun findByStoreIdAndSlug(storeId: UUID, slug: String): Product?

    /** Case-insensitive — see ProductService's duplicate-SKU check. Blank/null SKUs are never passed in, so no products-without-a-SKU false positive. */
    fun findByStoreIdAndSkuIgnoreCase(storeId: UUID, sku: String): Product?

    /** Guards CategoryController's delete — see its doc comment. */
    fun existsByCategory(category: String): Boolean

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

    /**
     * The text-search path of GET /api/products — relevance-ranked full-text
     * search (V29's generated `search_vector` column), not the substring
     * matching ProductSpecifications.matchesQuery does for the no-query
     * browse path. The gin-trigram `like` clauses stay as an OR'd recall
     * fallback so a query that doesn't tokenize into a real lexeme match
     * (a genuine typo, a partial word) still returns something, just ranked
     * below real matches — nothing that matched before this change stops
     * matching now.
     *
     * [sortMode] is one of "relevance"/"price-asc"/"price-desc"/"rating" —
     * a bind *value* compared with `=`, never concatenated into the SQL, so
     * there's no injection risk despite driving the ORDER BY. The `case
     * when :sortMode = '...' then ... end` trick lets one query serve every
     * sort mode: for every row where that branch doesn't apply, the
     * expression evaluates to NULL for every row alike, so the tie is
     * broken by the next ORDER BY key — Postgres's ordering is stable
     * across multiple keys, so this "cascades" cleanly instead of actually
     * sorting on a NULL column.
     *
     * All nullable params ([category]/[minPrice]/[maxPrice]) are explicitly
     * cast so the JDBC driver always has an unambiguous type to bind a null
     * against — see ProductSpecifications' doc comment for the exact
     * "function lower(bytea) does not exist" bug this sidesteps.
     *
     * [pageable] must be unsorted (`PageRequest.of(page, size)`, no `Sort`)
     * — the ORDER BY is already hardcoded in the query text; a Sort here
     * would make Spring Data try to append a second, conflicting ORDER BY.
     */
    @Query(
        value = """
            select p.* from products p
            join stores s on s.id = p.store_id
            where $PRODUCT_SEARCH_FILTERS
            order by
              case when :sortMode = 'price-asc' then p.price end asc,
              case when :sortMode = 'price-desc' then p.price end desc,
              case when :sortMode = 'rating' then p.rating end desc,
              case when :sortMode = 'relevance'
                   then ts_rank(p.search_vector, websearch_to_tsquery('english', :query)) end desc,
              p.created_at desc
        """,
        countQuery = """
            select count(*) from products p
            join stores s on s.id = p.store_id
            where $PRODUCT_SEARCH_FILTERS
        """,
        nativeQuery = true,
    )
    fun searchFullText(
        @Param("category") category: String?,
        @Param("query") query: String,
        @Param("likePattern") likePattern: String,
        @Param("minPrice") minPrice: Int?,
        @Param("maxPrice") maxPrice: Int?,
        @Param("sortMode") sortMode: String,
        pageable: Pageable,
    ): Page<Product>
}
