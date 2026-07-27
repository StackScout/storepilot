-- Admin-facing activity feed — not per-admin-account, any admin can read or
-- dismiss any row (matches how ROLE_ADMIN authorization isn't per-admin
-- scoped elsewhere in this app either).
create table admin_notifications (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    type varchar(50) not null,
    message text not null,
    store_id uuid,
    read boolean not null default false
);

create index idx_admin_notifications_read on admin_notifications (read);
