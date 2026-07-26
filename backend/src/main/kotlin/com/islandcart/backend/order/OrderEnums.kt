package com.islandcart.backend.order

import com.islandcart.backend.common.WireValue
import com.islandcart.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

/**
 * Mirrors src/types/order.ts's OrderStatus. The allowed-transition state
 * machine (pending → confirmed|cancelled → shipped → delivered) lives only
 * in the frontend's OrderStatusSelect today — docs/gaps-and-assumptions.md
 * flags this as a must-fix-server-side gap. Enforce it in OrderService, not
 * here — this enum only defines the possible values.
 */
enum class OrderStatus(override val wireValue: String) : WireValue {
    PENDING("pending"),
    CONFIRMED("confirmed"),
    SHIPPED("shipped"),
    DELIVERED("delivered"),
    CANCELLED("cancelled"),
}

@Converter(autoApply = true)
class OrderStatusConverter : WireValueEnumConverter<OrderStatus>(OrderStatus.entries.toTypedArray())

enum class PaymentMethod(override val wireValue: String) : WireValue {
    PAYHERE("payhere"),
    COD("cod"),
    BANK_TRANSFER("bank-transfer"),
}

@Converter(autoApply = true)
class PaymentMethodConverter : WireValueEnumConverter<PaymentMethod>(PaymentMethod.entries.toTypedArray())

enum class PaymentStatus(override val wireValue: String) : WireValue {
    UNPAID("unpaid"),
    PAID("paid"),
    REFUNDED("refunded"),
}

@Converter(autoApply = true)
class PaymentStatusConverter : WireValueEnumConverter<PaymentStatus>(PaymentStatus.entries.toTypedArray())
