create table follows (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    buyer_id uuid not null references buyers (id) on delete cascade,
    store_id uuid not null references stores (id) on delete cascade,
    unique (buyer_id, store_id)
);

create index idx_follows_store on follows (store_id);
