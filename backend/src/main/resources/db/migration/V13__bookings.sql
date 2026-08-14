-- Bookable services + appointments — a second storefront mode alongside
-- products, for stores that sell time instead of goods. See
-- BookableService.kt/Booking.kt's doc comments and
-- docs/features/bookings.md for the full design (why these are parallel
-- aggregates to products/orders, not extensions of them).

create table bookable_services (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    store_id uuid not null references stores (id) on delete cascade,
    name varchar(255) not null,
    slug varchar(255) not null,
    description text not null,
    category varchar(50) not null,
    price integer not null,
    duration_minutes integer not null,
    buffer_minutes integer not null default 0,
    status varchar(20) not null,
    unique (store_id, slug)
);

create index idx_bookable_services_store_id on bookable_services (store_id);

create table bookable_service_images (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    service_id uuid not null references bookable_services (id) on delete cascade,
    url varchar(1000) not null,
    alt varchar(255) not null,
    sort_order integer not null default 0
);

create index idx_bookable_service_images_service_id on bookable_service_images (service_id);

-- Store-level weekly template + lead-time policy — shared by all of a
-- store's bookable services (no per-service schedules in v1).
create table store_availability (
    store_id uuid primary key references stores (id) on delete cascade,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    lead_time_minutes integer not null default 120
);

create table weekly_availability_rules (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    store_id uuid not null references stores (id) on delete cascade,
    -- 1 (Monday) .. 7 (Sunday), matches java.time.DayOfWeek.getValue().
    day_of_week integer not null,
    is_open boolean not null,
    open_time time,
    close_time time,
    unique (store_id, day_of_week),
    constraint chk_weekly_availability_open_times
        check (is_open = false or (open_time is not null and close_time is not null and open_time < close_time))
);

create table availability_exceptions (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    store_id uuid not null references stores (id) on delete cascade,
    exception_date date not null,
    is_open boolean not null,
    open_time time,
    close_time time,
    note varchar(500),
    unique (store_id, exception_date),
    constraint chk_availability_exception_open_times
        check (is_open = false or (open_time is not null and close_time is not null and open_time < close_time))
);

create index idx_availability_exceptions_store_date on availability_exceptions (store_id, exception_date);

-- Bookings — parallel aggregate to orders. See Booking.kt's doc comment
-- for why service snapshot fields are immutable and Booking.service stays
-- a real FK (unlike order_items.product_id).
create table bookings (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    booking_number varchar(50) not null unique,
    store_id uuid not null references stores (id) on delete cascade,
    service_id uuid not null references bookable_services (id),
    service_name varchar(255) not null,
    service_price integer not null,
    service_duration_minutes integer not null,
    scheduled_start timestamptz not null,
    scheduled_end timestamptz not null,
    platform_fee integer not null,
    total integer not null,
    status varchar(20) not null,
    payment_method varchar(20) not null,
    payment_status varchar(20) not null,
    receipt_url varchar(1000),
    stripe_payment_intent_id varchar(255),
    buyer_name varchar(255) not null,
    buyer_phone varchar(50) not null,
    buyer_email varchar(255) not null,
    buyer_id uuid references buyers (id),
    cancelled_at timestamptz,
    cancellation_reason text
);

create index idx_bookings_store_id on bookings (store_id);
create index idx_bookings_service_id on bookings (service_id);
create index idx_bookings_buyer_id on bookings (buyer_id);
-- The slot-availability query's hot path: "does anything overlap
-- [start,end) for this service" — see AvailabilityService.computeSlots.
create index idx_bookings_service_scheduled on bookings (service_id, scheduled_start, scheduled_end)
    where status not in ('cancelled', 'no-show');

create table booking_timeline_entries (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    booking_id uuid not null references bookings (id) on delete cascade,
    status varchar(20) not null,
    label varchar(255) not null,
    timestamp timestamptz not null,
    note text
);

create index idx_booking_timeline_entries_booking_id on booking_timeline_entries (booking_id);

-- bookingsEnabled toggle — plain opt-in, identical mechanism to
-- pickup_enabled. See StoreSettings.kt's doc comment.
alter table store_settings
    add column bookings_enabled boolean not null default false;

-- Deployment-wide IANA timezone, used to convert a resolved weekly
-- availability window into absolute booking-slot Instants — see
-- PlatformSettings.timezone's doc comment for why this isn't per-store.
alter table platform_settings
    add column timezone varchar(100) not null default 'Australia/Sydney';
