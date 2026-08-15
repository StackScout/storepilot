package com.storepilot.backend.common

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * Mirrors src/types/order.ts's ShippingDetails. Shared between
 * `Order.shipping` and `Address.shipping` (see buyer/Address.kt) — lives in
 * `common` rather than the `order` package specifically so `order` and
 * `buyer` don't need a circular package reference. Columns are nullable at
 * the JPA/DB level even though Order always requires a value, because an
 * Address's usage context varies (a pickup order has none at all);
 * "required for an order" is enforced by Bean Validation on the request
 * DTO, not a DB constraint.
 */
@Embeddable
class ShippingDetails(
    @Column(name = "shipping_full_name")
    var fullName: String? = null,
    @Column(name = "shipping_phone")
    var phone: String? = null,
    @Column(name = "shipping_address_line1")
    var addressLine1: String? = null,
    @Column(name = "shipping_city")
    var city: String? = null,
    /** Generic "state/province" field (district, state, ...) — see StoreAddress.state's doc comment. */
    @Column(name = "shipping_state")
    var state: String? = null,
    @Column(name = "shipping_postal_code")
    var postalCode: String? = null,
)
