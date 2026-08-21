package com.storepilot.backend.seller

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.stripe.StripeProperties
import com.stripe.model.Customer
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.param.CustomerCreateParams
import com.stripe.param.checkout.SessionCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

private val ACTIVE_SUBSCRIPTION_STATUSES = setOf("active", "trialing")

/**
 * Seller Pro-plan billing — a real Stripe Subscription on the **platform's
 * own** Stripe account (Stripe.apiKey, set globally by StripeConfig), not a
 * connected account. Deliberately separate from the order-payment /
 * StripeConnectService flow: here the platform is charging the seller, not
 * the other way around, so no `Stripe-Account` request-option header is
 * ever set on any call in this file. See SellerBillingWebhookService for
 * the matching webhook receiver (its own Dashboard endpoint + signing
 * secret, listening to "Your account" events, not "Connected accounts").
 */
@Service
@Transactional(readOnly = true)
class SellerBillingService(
    private val sellerRepository: SellerRepository,
    private val currentActor: CurrentActor,
    private val platformConfigService: PlatformConfigService,
    private val stripeProperties: StripeProperties,
) {
    private val log = LoggerFactory.getLogger(SellerBillingService::class.java)

    /** GET /api/me/seller/plan */
    fun currentPlan(): SellerPlanResponse {
        val seller = currentActor.requireSeller()
        val config = platformConfigService.current()
        return seller.toPlanResponse(config.proMonthlyPriceCents, config.currencyCode)
    }

    /** POST /api/me/seller/billing/checkout — reuses the seller's Stripe Customer across repeat checkouts (e.g. resubscribing after a cancellation) instead of creating a new one every time. */
    @Transactional
    fun startCheckout(): CheckoutUrlResponse {
        val seller = currentActor.requireSeller()
        if (seller.plan == SellerPlan.PRO) throw ConflictException("Already on the Pro plan")

        val customerId = seller.stripeCustomerId ?: run {
            val customer = Customer.create(
                CustomerCreateParams.builder()
                    .setEmail(seller.email)
                    .setName(seller.name)
                    .build(),
            )
            seller.stripeCustomerId = customer.id
            sellerRepository.save(seller)
            customer.id
        }

        val config = platformConfigService.current()
        val params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(config.currencyCode.lowercase())
                            .setUnitAmount(config.proMonthlyPriceCents.toLong())
                            .setRecurring(
                                SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                    .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                                    .build(),
                            )
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName("${config.name} Pro")
                                    .setDescription("Unlocks Cash on Delivery and Bank transfer as payment options for your store.")
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            // Read by handleCheckoutCompleted below to know which seller this session belongs to.
            .setClientReferenceId(requireNotNull(seller.id).toString())
            .setSuccessUrl("${stripeProperties.billingSuccessUrlBase}?upgraded=true")
            .setCancelUrl(stripeProperties.billingCancelUrlBase)
            .build()

        val session = Session.create(params)
        return CheckoutUrlResponse(checkoutUrl = session.url)
    }

    /** POST /api/me/seller/billing/cancel — keeps Pro access through the period already paid for (standard SaaS UX), not an immediate downgrade. */
    @Transactional
    fun cancelAtPeriodEnd(): SellerPlanResponse {
        val seller = currentActor.requireSeller()
        val subscriptionId = seller.stripeSubscriptionId
            ?: throw ConflictException("No active Pro subscription to cancel")
        val subscription = Subscription.retrieve(subscriptionId).update(mapOf("cancel_at_period_end" to true))
        syncFromSubscription(seller, subscription)
        val config = platformConfigService.current()
        return seller.toPlanResponse(config.proMonthlyPriceCents, config.currencyCode)
    }

    /** POST /api/me/seller/billing/refresh — fallback for when the webhook is misconfigured or drops an event, same "check again" pattern as StripeConnectService.refreshAccountStatus. */
    @Transactional
    fun refreshFromStripe(): SellerPlanResponse {
        val seller = currentActor.requireSeller()
        seller.stripeSubscriptionId?.let { syncFromSubscription(seller, Subscription.retrieve(it)) }
        val config = platformConfigService.current()
        return seller.toPlanResponse(config.proMonthlyPriceCents, config.currencyCode)
    }

    /** Called by SellerBillingWebhookService for checkout.session.completed. */
    @Transactional
    fun handleCheckoutCompleted(session: Session) {
        if (session.mode != "subscription") return
        val sellerId = session.clientReferenceId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val seller = sellerId?.let { sellerRepository.findById(it).orElse(null) }
        if (seller == null) {
            log.warn("Stripe billing webhook: checkout.session.completed with no matching seller (clientReferenceId={})", session.clientReferenceId)
            return
        }
        seller.stripeCustomerId = session.customer
        val subscriptionId = session.subscription
        if (subscriptionId == null) {
            sellerRepository.save(seller)
            return
        }
        syncFromSubscription(seller, Subscription.retrieve(subscriptionId))
    }

    /** Called by SellerBillingWebhookService for customer.subscription.updated and .deleted — a deleted subscription still carries status="canceled", which syncFromSubscription already treats as FREE, so both events share this one handler. */
    @Transactional
    fun handleSubscriptionEvent(subscription: Subscription) {
        val seller = sellerRepository.findByStripeSubscriptionId(subscription.id)
        if (seller == null) {
            log.warn("Stripe billing webhook: no seller found for subscription {}", subscription.id)
            return
        }
        syncFromSubscription(seller, subscription)
    }

    /**
     * Seller-account-deletion step — cancels the Pro subscription
     * immediately (not at period end) and deletes the platform Customer
     * object. Both calls are idempotent in practice: cancelling an
     * already-canceled subscription or deleting an already-deleted customer
     * is treated by Stripe as already done, not a hard failure, so this is
     * safe to retry after a partial failure elsewhere in the deletion
     * sequence. Deleting the Customer also cancels any subscription as a
     * side effect per Stripe's docs — the subscription is cancelled
     * explicitly first anyway, for a clean, unambiguous object to reason
     * about. Does not touch the `Seller` row itself — the caller anonymizes
     * it afterward.
     */
    fun cancelAndDeleteCustomer(seller: Seller) {
        seller.stripeSubscriptionId?.let { Subscription.retrieve(it).cancel() }
        seller.stripeCustomerId?.let { Customer.retrieve(it).delete() }
    }

    private fun syncFromSubscription(seller: Seller, subscription: Subscription) {
        seller.stripeSubscriptionId = subscription.id
        seller.plan = if (subscription.status in ACTIVE_SUBSCRIPTION_STATUSES) SellerPlan.PRO else SellerPlan.FREE
        seller.planCancelAtPeriodEnd = subscription.cancelAtPeriodEnd ?: false
        // current_period_end moved from the Subscription object onto each
        // SubscriptionItem in this API version — see StripeEventDeserializer's
        // doc comment for the general shape of this "SDK/API version drift"
        // problem this app has already hit once with Connect events.
        seller.planCurrentPeriodEnd = subscription.items?.data?.firstOrNull()?.currentPeriodEnd
            ?.let { Instant.ofEpochSecond(it) }
        sellerRepository.save(seller)
    }
}
