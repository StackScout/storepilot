package com.storepilot.backend.common

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * A store/product/bookable-service category — admin-managed, seeded by
 * migration (see State.kt's doc comment, the exact same "small admin-
 * curated lookup list" pattern this mirrors) rather than hardcoded as a
 * Kotlin enum or a frontend/mobile constant array. [wireValue] is the
 * opaque string persisted on `stores.category`/`products.category`/
 * `bookable_services.category` — those columns stay plain varchar (no FK),
 * validated against this table at write time via CategoryRepository
 * .requireCategory(), the same "throws IllegalArgumentException on an
 * unknown value" contract wireValueOf<T>() used when category was a fixed
 * enum. [icon] is a frontend-resolved icon-name key (this backend is
 * content-agnostic about what it renders to); [active] lets an admin retire
 * a category from new-selection dropdowns without breaking every store/
 * product/service still using it (see CategoryController.delete's doc
 * comment for why hard deletion is guarded instead).
 */
@Entity
@Table(name = "categories", uniqueConstraints = [UniqueConstraint(columnNames = ["wire_value"])])
class Category(
    @Column(nullable = false)
    var name: String,
    @Column(name = "wire_value", nullable = false)
    var wireValue: String,
    @Column(nullable = false)
    var icon: String,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
    @Column(nullable = false)
    var active: Boolean = true,
) : BaseEntity()
