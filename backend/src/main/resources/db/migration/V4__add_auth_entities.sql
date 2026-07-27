-- Cognito-backed accounts for sellers and admins, plus linking buyers to
-- their Cognito identity. See Seller.kt/Admin.kt/Buyer.kt doc comments:
-- cognito_sub is nullable+unique on buyers (guest-checkout rows never get
-- one), not-null+unique on sellers/admins (both are only ever created from
-- an authenticated Cognito identity).

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

alter table buyers add column cognito_sub varchar(255) unique;

-- No real production data exists yet (pre-launch), so this is a hard NOT
-- NULL rather than a nullable-then-backfilled column — a fresh/empty
-- database (or any environment applying this migration from scratch)
-- handles it with no special casing. An already-seeded local dev database
-- needs its demo data truncated and reseeded (DataSeeder creates a seller
-- per store) rather than this migration attempting an in-place backfill.
alter table stores add column seller_id uuid not null references sellers (id);
