-- Baseline schema, translated 1:1 from the current Kotlin entities under
-- src/main/kotlin/com/islandcart/backend. Table/column names match Spring's
-- default snake_case physical naming strategy applied to each entity's
-- Kotlin property names — keep this file in sync by hand if an entity
-- changes, since ddl-auto is set to "validate" (see application.yml), not
-- "update".
--
-- This is a squashed rewrite of what used to be 11 incremental migration
-- files (V1-V11) — collapsed into one now that the schema had settled and
-- no real production data exists yet to migrate forward. It also carries
-- the currency-agnostic column renames (e.g. price_lkr -> price) and the
-- district/province -> state address-model generalization from the
-- multi-country config refactor. If this repo is ever deployed against a
-- database that already ran the old V1-V11 files, do NOT apply this file
-- there — it's for a fresh database only (Flyway will refuse to run V1
-- again against a schema_history table that already has it recorded, which
-- is the intended guard).

create extension if not exists pg_trgm;

create table buyers (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    name varchar(255) not null,
    email varchar(255) not null unique,
    phone varchar(50),
    -- Guest-checkout buyer rows never get one; JIT-provisioned rows from a
    -- Cognito identity always do (see Buyer.kt).
    cognito_sub varchar(255) unique,
    shipping_full_name varchar(255),
    shipping_phone varchar(50),
    shipping_address_line1 varchar(500),
    shipping_city varchar(255),
    -- Generic "state/province" field — see StoreAddress's doc comment.
    shipping_state varchar(255),
    shipping_postal_code varchar(20)
);

create table sellers (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    cognito_sub varchar(255) not null unique,
    email varchar(255) not null unique,
    name varchar(255) not null
);

create table admins (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    cognito_sub varchar(255) not null unique,
    email varchar(255) not null unique,
    name varchar(255) not null
);

create table stores (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    seller_id uuid not null references sellers (id),
    slug varchar(255) not null unique,
    name varchar(255) not null,
    tagline varchar(500) not null,
    description text not null,
    logo_url varchar(1000) not null,
    banner_url varchar(1000) not null,
    category varchar(50) not null,
    city varchar(255) not null,
    -- Generic "state/province" field (not a separate district+province
    -- pair) so the same shape works for any country's address model — see
    -- StoreAddress.state's doc comment.
    state varchar(255) not null,
    whatsapp_number varchar(50) not null,
    rating double precision not null default 0,
    review_count integer not null default 0,
    product_count integer not null default 0,
    is_verified boolean not null default false,
    follower_count integer not null default 0,
    verification_status varchar(20) not null default 'pending',
    -- Public-facing social links, shown on the store page — nullable,
    -- optional per store. Lives here (public data), not store_settings
    -- (private payout/verification data).
    facebook_url varchar(500),
    instagram_url varchar(500),
    tiktok_url varchar(500)
);

create index idx_stores_verification_status on stores (verification_status);
create index idx_stores_name_trgm on stores using gin (lower(name) gin_trgm_ops);
create index idx_stores_tagline_trgm on stores using gin (lower(tagline) gin_trgm_ops);
create index idx_stores_city_trgm on stores using gin (lower(city) gin_trgm_ops);

create table store_settings (
    store_id uuid primary key references stores (id) on delete cascade,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    contact_email varchar(255) not null,
    contact_phone varchar(50) not null,
    bank_account_name varchar(255) not null,
    bank_account_number varchar(100) not null,
    bank_name varchar(255) not null,
    transaction_fee_percent numeric(5, 2) not null,
    cod_enabled boolean not null default true,
    online_payment_enabled boolean not null default true,
    bank_transfer_enabled boolean not null default false,
    seller_type varchar(20) not null,
    -- Individual-seller identity verification: driver's licence number.
    driver_licence_number varchar(50) not null,
    -- Australian Business Number — required when seller_type = 'business'.
    abn varchar(100),
    rejection_reason text,
    -- Uploaded proof documents for seller verification. Nullable: local
    -- storage stores a fetchable path, S3 storage stores an object key —
    -- resolved to a URL at read time either way, never persisted as a fixed URL.
    driver_licence_document_url varchar(500),
    abn_document_url varchar(500),
    -- Store-wide switch — when false, no product in this store tracks
    -- stock, and the new-product page hides the stock UI entirely.
    stock_management_enabled boolean not null default true
);

create table products (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    store_id uuid not null references stores (id) on delete cascade,
    name varchar(255) not null,
    slug varchar(255) not null,
    description text not null,
    category varchar(50) not null,
    price integer not null,
    compare_at_price integer,
    stock_quantity integer not null,
    -- When false, stock_quantity is ignored — status is never auto-forced
    -- to out-of-stock and decrementStock skips this product.
    track_stock boolean not null default true,
    status varchar(20) not null,
    -- Nullable — sellers who don't track their own SKU scheme shouldn't be
    -- forced to invent one; not unique yet, see docs/roadmap.md's
    -- "Duplicate-SKU validation" gap, a deliberate product decision.
    sku varchar(100),
    rating double precision not null default 0,
    review_count integer not null default 0,
    constraint uq_products_store_slug unique (store_id, slug)
);

create index idx_products_store_id on products (store_id);
create index idx_products_category on products (category);
create index idx_products_sku on products (sku);
create index idx_products_name_trgm on products using gin (lower(name) gin_trgm_ops);
create index idx_products_description_trgm on products using gin (lower(description) gin_trgm_ops);

create table product_images (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    product_id uuid not null references products (id) on delete cascade,
    url varchar(1000) not null,
    alt varchar(500) not null
);

create index idx_product_images_product_id on product_images (product_id);

create table orders (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    order_number varchar(50) not null unique,
    store_id uuid not null references stores (id),
    subtotal integer not null,
    shipping_fee integer not null,
    platform_fee integer not null,
    total integer not null,
    status varchar(20) not null default 'pending',
    payment_method varchar(20) not null,
    payment_status varchar(20) not null,
    receipt_url varchar(500),
    last_reminder_sent_at timestamptz,
    -- Embedded ShippingDetails — nullable at the DB level by design, see
    -- ShippingDetails.kt; "required for an order" is a DTO-layer rule.
    shipping_full_name varchar(255),
    shipping_phone varchar(50),
    shipping_address_line1 varchar(500),
    shipping_city varchar(255),
    shipping_state varchar(255),
    shipping_postal_code varchar(20),
    buyer_email varchar(255) not null,
    buyer_id uuid references buyers (id),
    -- Courier tracking info, required by the seller when marking an order
    -- shipped; courier_receipt_url is an optional proof-of-handover upload.
    -- All nullable — only ever set once an order reaches "shipped".
    tracking_number varchar(255),
    courier_service_name varchar(255),
    courier_receipt_url varchar(500)
);

create index idx_orders_store_id on orders (store_id);
create index idx_orders_buyer_id on orders (buyer_id);
create index idx_orders_status on orders (status);
create index idx_orders_created_at on orders (created_at);

create table order_items (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    order_id uuid not null references orders (id) on delete cascade,
    -- Not a foreign key — an immutable snapshot, decoupled from the live
    -- Product on purpose. See database-model.md#orderitem-embedded.
    product_id uuid not null,
    product_name varchar(255) not null,
    product_image_url varchar(1000) not null,
    unit_price integer not null,
    quantity integer not null
);

create index idx_order_items_order_id on order_items (order_id);

create table order_timeline_entries (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    order_id uuid not null references orders (id) on delete cascade,
    status varchar(20) not null,
    label varchar(255) not null,
    "timestamp" timestamptz not null,
    note text
);

create index idx_order_timeline_entries_order_id on order_timeline_entries (order_id);

create table payouts (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    store_id uuid not null references stores (id),
    subtotal integer not null,
    platform_fee integer not null,
    net integer not null,
    status varchar(20) not null default 'scheduled',
    paid_at timestamptz,
    bank_reference varchar(255)
);

create index idx_payouts_store_id on payouts (store_id);

create table payout_order_refs (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    payout_id uuid not null references payouts (id) on delete cascade,
    -- Not a foreign key, same reasoning as order_items.product_id — a
    -- point-in-time snapshot of the order's totals when the batch was made.
    order_id uuid not null,
    order_number varchar(50) not null,
    subtotal integer not null,
    platform_fee integer not null,
    net integer not null
);

create index idx_payout_order_refs_payout_id on payout_order_refs (payout_id);

-- Admin-facing activity feed — not per-admin-account, any admin can read or
-- dismiss any row (matches how ROLE_ADMIN authorization isn't per-admin
-- scoped elsewhere in this app either).
create table admin_notifications (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    type varchar(50) not null,
    message text not null,
    store_id uuid,
    read boolean not null default false
);

create index idx_admin_notifications_read on admin_notifications (read);

-- The live, DB-backed platform configuration — a single row, seeded once by
-- DataSeeder from PlatformProperties' bootstrap env-var values. See
-- PlatformSettings.kt's doc comment: from that first insert on, this row
-- (not application.yml) is what the running app reads, so a deployment can
-- be reconfigured by updating it directly, no rebuild/redeploy needed.
create table platform_settings (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    name varchar(255) not null,
    tagline varchar(500) not null,
    country_name varchar(255) not null,
    country_code varchar(2) not null,
    currency_code varchar(3) not null,
    currency_symbol varchar(10) not null,
    currency_locale varchar(20) not null,
    platform_fee_percent numeric(5, 2) not null,
    flat_shipping_fee integer not null,
    default_cod_enabled boolean not null,
    default_online_payment_enabled boolean not null,
    default_bank_transfer_enabled boolean not null,
    support_email varchar(255) not null,
    company_location varchar(255) not null
);

-- This deployment's administrative-division options for address forms —
-- real reference data, not hardcoded in Kotlin/TypeScript (see State.kt's
-- doc comment). No country column: each country gets its own separate
-- database (infra is per-country, never shared — see PlatformSettings'
-- doc comment), so this table only ever holds the one country's rows.
-- Seeded here with Australia's states/territories, the near-term
-- deployment target; a Sri Lanka database would seed its own districts
-- instead, in its own copy of this migration.
create table states (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    name varchar(255) not null,
    sort_order integer not null default 0
);

insert into states (id, created_at, updated_at, name, sort_order) values
    (gen_random_uuid(), now(), now(), 'New South Wales', 1),
    (gen_random_uuid(), now(), now(), 'Victoria', 2),
    (gen_random_uuid(), now(), now(), 'Queensland', 3),
    (gen_random_uuid(), now(), now(), 'Western Australia', 4),
    (gen_random_uuid(), now(), now(), 'South Australia', 5),
    (gen_random_uuid(), now(), now(), 'Tasmania', 6),
    (gen_random_uuid(), now(), now(), 'Australian Capital Territory', 7),
    (gen_random_uuid(), now(), now(), 'Northern Territory', 8);
