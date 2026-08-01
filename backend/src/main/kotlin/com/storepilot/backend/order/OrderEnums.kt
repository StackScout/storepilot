package com.storepilot.backend.order

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

/**
 * Mirrors src/types/order.ts's OrderStatus. The allowed-transition state
 * machine (pending → confirmed|cancelled → shipped → delivered) is enforced
 * server-side in OrderService.updateStatus (see ALLOWED_STATUS_TRANSITIONS),
 * mirroring the frontend's OrderStatusSelect — this enum only defines the
 * possible values.
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

/**
 * A pickup order still carries a ShippingDetails (fullName/phone only —
 * OrderService.createOrder skips the address-field requirement for
 * pickup), and shippingFee is forced to 0 — see OrderService.createOrder.
 * There's no separate pickup-location field: buyers coordinate the actual
 * meeting point/time with the seller over WhatsApp
 * (Store.whatsappNumber), matching this marketplace's existing
 * WhatsApp-first contact model rather than adding a second address entity.
 */
enum class DeliveryMethod(override val wireValue: String) : WireValue {
    SHIPPING("shipping"),
    PICKUP("pickup"),
}

@Converter(autoApply = true)
class DeliveryMethodConverter : WireValueEnumConverter<DeliveryMethod>(DeliveryMethod.entries.toTypedArray())
