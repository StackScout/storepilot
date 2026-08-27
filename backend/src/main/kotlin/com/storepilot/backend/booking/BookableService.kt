package com.storepilot.backend.booking

import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import com.storepilot.backend.store.Store
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * A bookable appointment/service a store offers, parallel to Product but for
 * stores that sell time instead of goods — no stock/SKU/compareAtPrice
 * semantics apply. Named BookableService (not Service) to avoid colliding
 * with Spring's own @Service stereotype annotation throughout this codebase.
 * category is locked to the owning store's category, identical rule to
 * ProductService's category-lock check. Deletion is refused whenever any
 * non-terminal Booking still references this service (see
 * BookableServiceService) — unlike OrderItem.productId, Booking.service
 * stays a real FK, since a service can't be allowed to disappear out from
 * under a future appointment.
 */
@Entity
@Table(
    name = "bookable_services",
    uniqueConstraints = [UniqueConstraint(columnNames = ["store_id", "slug"])],
)
class BookableService(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false)
    var slug: String,
    @Column(nullable = false, columnDefinition = "text")
    var description: String,
    /** Validated against the admin-managed categories table (common.Category) at write time — see CategoryRepository.requireCategory. Plain varchar, not an FK — see Category.kt's doc comment. */
    @Column(nullable = false)
    var category: String,
    /** Cents (the currency's smallest unit) — see currency.ts#formatCurrency. */
    @Column(nullable = false)
    var price: Int,
    /** Drives slot-chunking — see AvailabilityService.computeSlots. */
    @Column(name = "duration_minutes", nullable = false)
    var durationMinutes: Int,
    /** Gap enforced after each booking of this service before the next slot opens. */
    @Column(name = "buffer_minutes", nullable = false)
    var bufferMinutes: Int = 0,
    @Column(nullable = false)
    var status: ServiceStatus,
    /**
     * When false (default), this service's availability is entirely derived
     * from the store's WeeklyAvailabilityRule template — see
     * AvailabilityService.computeSlots. When true, ServiceWeeklyAvailabilityRule
     * rows for this service are used instead. AvailabilityException rows stay
     * store-wide regardless (a holiday closure applies to every service) —
     * only the recurring weekly template is overridable per service.
     */
    @Column(name = "has_custom_availability", nullable = false)
    var hasCustomAvailability: Boolean = false,
    @OneToMany(mappedBy = "service", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("sortOrder asc")
    var images: MutableList<BookableServiceImage> = mutableListOf(),
) : BaseEntity()

/** Mirrors ProductImage's child-entity/sortOrder shape — see ProductImage's doc comment. */
@Entity
@Table(name = "bookable_service_images")
class BookableServiceImage(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    var service: BookableService,
    @Column(nullable = false)
    var url: String,
    @Column(nullable = false)
    var alt: String,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
) : BaseEntity()

/** Mirrors src/types/booking.ts's ServiceStatus. No "out-of-stock" analog — a service has no stock concept. */
enum class ServiceStatus(override val wireValue: String) : WireValue {
    ACTIVE("active"),
    DRAFT("draft"),
}

@Converter(autoApply = true)
class ServiceStatusConverter : WireValueEnumConverter<ServiceStatus>(ServiceStatus.entries.toTypedArray())
