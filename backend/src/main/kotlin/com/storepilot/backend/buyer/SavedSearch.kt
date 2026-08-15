package com.storepilot.backend.buyer

import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * A buyer's saved search — [queryString] is the raw URL query string from
 * /search (e.g. "q=necklace&category=jewelry&sort=rating"), replayed
 * verbatim by the frontend as /search?{queryString} rather than being
 * parsed/re-validated server-side. Unlike Follow/WishlistItem this isn't a
 * toggle against one target — a buyer can save the same filters under
 * multiple names, so there's no uniqueness constraint.
 */
@Entity
@Table(name = "saved_searches")
class SavedSearch(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    var buyer: Buyer,
    @Column(nullable = false)
    var name: String,
    @Column(name = "query_string", nullable = false)
    var queryString: String,
) : BaseEntity()
