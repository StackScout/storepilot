-- Stripe Connect (Standard accounts, direct charges) — see StoreSettings.kt
-- and StripeConnectService's doc comments.

alter table store_settings
    add column stripe_account_id varchar(255),
    add column stripe_charges_enabled boolean not null default false,
    add column stripe_payouts_enabled boolean not null default false,
    add column stripe_enabled boolean not null default false;

alter table orders
    add column stripe_payment_intent_id varchar(255);
