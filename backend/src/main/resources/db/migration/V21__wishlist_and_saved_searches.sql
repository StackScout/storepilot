create table wishlist_items (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    buyer_id uuid not null references buyers (id) on delete cascade,
    product_id uuid not null references products (id) on delete cascade,
    unique (buyer_id, product_id)
);

create index idx_wishlist_items_product on wishlist_items (product_id);

create table saved_searches (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    buyer_id uuid not null references buyers (id) on delete cascade,
    name varchar(200) not null,
    query_string text not null
);

create index idx_saved_searches_buyer on saved_searches (buyer_id);
