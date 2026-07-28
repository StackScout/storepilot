package com.islandcart.backend.common

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * This deployment's administrative-division options for address forms
 * (using the common "state/province" field name — see StoreAddress.state's
 * doc comment), seeded by migration (see V1__init_schema.sql), not
 * hardcoded in Kotlin/TypeScript. Exposed to the frontend via GET
 * /api/states.
 *
 * No country column: each country gets its own separate database (see
 * PlatformSettings' doc comment — infra is per-country, never shared), so
 * this table only ever holds the one country's rows. A country identity
 * belongs on PlatformSettings, the one config table, not duplicated onto
 * every reference/data table.
 */
@Entity
@Table(name = "states")
class State(
    @Column(nullable = false)
    var name: String,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
) : BaseEntity()
