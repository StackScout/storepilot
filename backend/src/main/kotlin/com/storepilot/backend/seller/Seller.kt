package com.storepilot.backend.seller

import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

/**
 * The account behind a Store (see Store.sellerId). Created explicitly during
 * seller onboarding (POST /api/stores) alongside the Cognito `seller` group
 * assignment — never JIT-provisioned like Buyer/Admin, since onboarding
 * already collects real business data a JIT row couldn't fabricate.
 * `cognitoSub` links this row to the Cognito identity (the JWT's `sub`
 * claim); this row is a profile-data cache only — `ROLE_SELLER`
 * authorization always comes from the JWT's `cognito:groups` claim, never
 * from this row's existence (see CurrentActor).
 */
@Entity
@Table(name = "sellers")
class Seller(
    @Column(name = "cognito_sub", nullable = false, unique = true)
    var cognitoSub: String,
    @Column(nullable = false, unique = true)
    var email: String,
    @Column(nullable = false)
    var name: String,
    /** Pro is billed via a real Stripe Subscription on the platform's own account — see SellerBillingService. Every other Seller field above is a Cognito profile cache; these plan/billing fields are this app's own source of truth (synced from Stripe by webhook, not derivable from Cognito at all). */
    @Column(nullable = false)
    var plan: SellerPlan = SellerPlan.FREE,
    @Column(name = "stripe_customer_id")
    var stripeCustomerId: String? = null,
    @Column(name = "stripe_subscription_id")
    var stripeSubscriptionId: String? = null,
    @Column(name = "plan_current_period_end")
    var planCurrentPeriodEnd: Instant? = null,
    /** True once the seller has cancelled — they keep Pro access until planCurrentPeriodEnd, then the subscription.deleted webhook flips plan back to FREE. */
    @Column(name = "plan_cancel_at_period_end", nullable = false)
    var planCancelAtPeriodEnd: Boolean = false,
) : BaseEntity()
