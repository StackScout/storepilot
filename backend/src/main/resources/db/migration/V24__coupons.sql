create table coupons (
    id uuid primary key,
    code varchar(50) not null unique,
    store_id uuid references stores(id),
    discount_type varchar(20) not null,
    discount_value int not null,
    applies_to_orders boolean not null default true,
    applies_to_bookings boolean not null default true,
    max_uses int,
    used_count int not null default 0,
    min_subtotal int not null default 0,
    expires_at timestamptz,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_coupons_store on coupons (store_id);

alter table orders add column coupon_code varchar(50);
alter table orders add column discount_amount int not null default 0;
alter table bookings add column coupon_code varchar(50);
alter table bookings add column discount_amount int not null default 0;
