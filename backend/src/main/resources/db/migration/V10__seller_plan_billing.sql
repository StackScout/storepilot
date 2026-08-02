-- Seller plan tiers (free/pro) + the Stripe Subscription state backing
-- Pro billing — see SellerPlan.kt and SellerBillingService.kt. Deliberately
-- separate from the existing Stripe Connect columns on store_settings:
-- this is the seller paying the platform (their own Stripe Customer/
-- Subscription on the platform's own account), not a connected account.
alter table sellers
    add column plan varchar(20) not null default 'free',
    add column stripe_customer_id varchar(255),
    add column stripe_subscription_id varchar(255),
    add column plan_current_period_end timestamptz,
    add column plan_cancel_at_period_end boolean not null default false;

create unique index idx_sellers_stripe_subscription_id on sellers (stripe_subscription_id) where stripe_subscription_id is not null;

-- Cents, like every other money field — see Product.price's doc comment.
-- Live-configurable without a redeploy, same as platform_fee_percent.
alter table platform_settings
    add column pro_monthly_price_cents integer not null default 990;
