-- Baseline schema, translated 1:1 from docs/database-model.md and the
-- Kotlin entities under src/main/kotlin/com/islandcart/backend. Table/column
-- names match Spring's default snake_case physical naming strategy applied
-- to each entity's Kotlin property names — keep this file in sync by hand
-- if an entity changes, since ddl-auto is set to "validate" (see
-- application.yml), not "update".

create table buyers (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    name varchar(255) not null,
    email varchar(255) not null unique,
    phone varchar(50),
    shipping_full_name varchar(255),
    shipping_phone varchar(50),
    shipping_address_line1 varchar(500),
    shipping_city varchar(255),
    shipping_district varchar(255),
    shipping_postal_code varchar(20)
);

create table stores (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    slug varchar(255) not null unique,
    name varchar(255) not null,
    tagline varchar(500) not null,
    description text not null,
    logo_url varchar(1000) not null,
    banner_url varchar(1000) not null,
    category varchar(50) not null,
    city varchar(255) not null,
    district varchar(255) not null,
    province varchar(255) not null,
    whatsapp_number varchar(50) not null,
    rating double precision not null default 0,
    review_count integer not null default 0,
    product_count integer not null default 0,
    is_verified boolean not null default false,
    follower_count integer not null default 0,
    verification_status varchar(20) not null default 'pending'
);

create index idx_stores_verification_status on stores (verification_status);

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
    seller_type varchar(20) not null,
    nic_number varchar(50) not null,
    business_registration_number varchar(100),
    rejection_reason text
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
    price_lkr integer not null,
    compare_at_price_lkr integer,
    stock_quantity integer not null,
    status varchar(20) not null,
    -- Not unique yet — see docs/roadmap.md's "Duplicate-SKU validation"
    -- gap; enforcing it here is a deliberate product decision, not a
    -- migration oversight.
    sku varchar(100) not null,
    rating double precision not null default 0,
    review_count integer not null default 0,
    constraint uq_products_store_slug unique (store_id, slug)
);

create index idx_products_store_id on products (store_id);
create index idx_products_category on products (category);
create index idx_products_sku on products (sku);

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
    subtotal_lkr integer not null,
    shipping_fee_lkr integer not null,
    platform_fee_lkr integer not null,
    total_lkr integer not null,
    status varchar(20) not null default 'pending',
    payment_method varchar(20) not null,
    payment_status varchar(20) not null,
    -- Embedded ShippingDetails — nullable at the DB level by design, see
    -- ShippingDetails.kt; "required for an order" is a DTO-layer rule.
    shipping_full_name varchar(255),
    shipping_phone varchar(50),
    shipping_address_line1 varchar(500),
    shipping_city varchar(255),
    shipping_district varchar(255),
    shipping_postal_code varchar(20),
    buyer_email varchar(255) not null,
    buyer_id uuid references buyers (id)
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
    unit_price_lkr integer not null,
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
    subtotal_lkr integer not null,
    platform_fee_lkr integer not null,
    net_lkr integer not null,
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
    subtotal_lkr integer not null,
    platform_fee_lkr integer not null,
    net_lkr integer not null
);

create index idx_payout_order_refs_payout_id on payout_order_refs (payout_id);
