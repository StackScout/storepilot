package com.storepilot.backend.order

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
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
    /**
     * Stripe Connect direct charge — settles automatically at charge time
     * (seller's connected account gets the net, the platform's
     * application_fee_amount is its whole take), so unlike every other
     * method it never enters the Payout or FeeCollection ledgers. See
     * StripeService and PayoutService/FeeCollectionService's eligibility
     * filters.
     */
    STRIPE("stripe"),
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
