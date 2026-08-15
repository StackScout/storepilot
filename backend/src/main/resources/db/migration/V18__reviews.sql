create table reviews (
    id uuid primary key,
    buyer_id uuid not null references buyers (id) on delete cascade,
    store_id uuid not null references stores (id) on delete cascade,
    product_id uuid references products (id) on delete cascade,
    rating integer not null check (rating between 1 and 5),
    comment text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_reviews_product on reviews (product_id) where product_id is not null;
create index idx_reviews_store_only on reviews (store_id) where product_id is null;

-- One review per buyer per product, and separately one per buyer per
-- store — a plain composite unique constraint can't express "unique only
-- when product_id is null", hence two partial indexes instead of one.
create unique index idx_reviews_buyer_product on reviews (buyer_id, product_id) where product_id is not null;
create unique index idx_reviews_buyer_store_only on reviews (buyer_id, store_id) where product_id is null;
