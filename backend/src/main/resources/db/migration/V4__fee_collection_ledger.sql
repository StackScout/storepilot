-- The reverse-direction ledger from payouts — see FeeCollection.kt's doc
-- comment. Mirrors payouts/payout_order_refs exactly.

create table fee_collections (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    store_id uuid not null references stores (id),
    subtotal integer not null,
    platform_fee integer not null,
    status varchar(20) not null default 'pending',
    collected_at timestamptz,
    reference varchar(255)
);

create index idx_fee_collections_store_id on fee_collections (store_id);

create table fee_collection_order_refs (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    fee_collection_id uuid not null references fee_collections (id) on delete cascade,
    -- Not a foreign key, same reasoning as payout_order_refs.order_id.
    order_id uuid not null,
    order_number varchar(50) not null,
    subtotal integer not null,
    platform_fee integer not null
);

create index idx_fee_collection_order_refs_fee_collection_id on fee_collection_order_refs (fee_collection_id);
