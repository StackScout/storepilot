package com.islandcart.backend.order

/**
 * Everything the frontend needs to build and submit the hidden HTML form
 * PayHere's Checkout API expects — hash is computed server-side so
 * merchant_secret never reaches the browser. See
 * https://support.payhere.lk/api-&-mobile-sdk/checkout-api
 */
data class PayHereCheckoutResponse(
    val actionUrl: String,
    val merchantId: String,
    val orderId: String,
    val items: String,
    val amount: String,
    val currency: String,
    val hash: String,
    val notifyUrl: String,
    val returnUrl: String,
    val cancelUrl: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val address: String,
    val city: String,
    val country: String,
)
