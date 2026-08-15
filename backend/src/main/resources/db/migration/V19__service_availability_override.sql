alter table bookable_services add column has_custom_availability boolean not null default false;

create table service_weekly_availability_rules (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    service_id uuid not null references bookable_services (id) on delete cascade,
    day_of_week integer not null,
    is_open boolean not null,
    open_time time,
    close_time time,
    unique (service_id, day_of_week),
    constraint chk_service_weekly_availability_open_times
        check (is_open = false or (open_time is not null and close_time is not null and open_time < close_time))
);
