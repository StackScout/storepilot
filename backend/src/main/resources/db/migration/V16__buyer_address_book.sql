-- Replaces buyers' single embedded default-shipping-address with a real
-- one-to-many address book. Existing default-shipping data is backfilled
-- into one address row per buyer (marked default) before the old columns
-- are dropped.

create table addresses (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    buyer_id uuid not null references buyers (id) on delete cascade,
    label varchar(50),
    shipping_full_name varchar(255),
    shipping_phone varchar(50),
    shipping_address_line1 varchar(255),
    shipping_city varchar(100),
    shipping_state varchar(100),
    shipping_postal_code varchar(20),
    is_default boolean not null default false
);

create index idx_addresses_buyer_id on addresses (buyer_id);

insert into addresses (
    id, created_at, updated_at, buyer_id,
    shipping_full_name, shipping_phone, shipping_address_line1, shipping_city, shipping_state, shipping_postal_code,
    is_default
)
select
    gen_random_uuid(), now(), now(), id,
    shipping_full_name, shipping_phone, shipping_address_line1, shipping_city, shipping_state, shipping_postal_code,
    true
from buyers
where shipping_address_line1 is not null;

alter table buyers
    drop column shipping_full_name,
    drop column shipping_phone,
    drop column shipping_address_line1,
    drop column shipping_city,
    drop column shipping_state,
    drop column shipping_postal_code;
