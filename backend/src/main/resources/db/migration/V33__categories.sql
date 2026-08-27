create table categories (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    name varchar(255) not null,
    wire_value varchar(100) not null,
    icon varchar(50) not null,
    sort_order integer not null default 0,
    active boolean not null default true,
    constraint uq_categories_wire_value unique (wire_value)
);

-- Preserves the exact wire values the StoreCategory enum used to emit —
-- stores/products/bookable_services.category already store these strings
-- (a plain varchar column, never an enum at the DB level), so no data
-- migration is needed on those tables, only this new lookup table.
insert into categories (id, created_at, updated_at, name, wire_value, icon, sort_order) values
    (gen_random_uuid(), now(), now(), 'Fashion & Apparel', 'fashion', 'shirt', 1),
    (gen_random_uuid(), now(), now(), 'Food & Beverage', 'food-beverage', 'utensils', 2),
    (gen_random_uuid(), now(), now(), 'Beauty & Wellness', 'beauty', 'sparkles', 3),
    (gen_random_uuid(), now(), now(), 'Handicrafts', 'handicrafts', 'hand', 4),
    (gen_random_uuid(), now(), now(), 'Electronics', 'electronics', 'smartphone', 5),
    (gen_random_uuid(), now(), now(), 'Home & Living', 'home-living', 'home', 6),
    (gen_random_uuid(), now(), now(), 'Jewelry & Gems', 'jewelry', 'gem', 7),
    (gen_random_uuid(), now(), now(), 'Grocery & Organic', 'grocery', 'shopping-basket', 8);
