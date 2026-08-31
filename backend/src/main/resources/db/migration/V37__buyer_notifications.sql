-- Buyer-side mirror of V30__push_tokens.sql / V35__seller_notifications.sql.
create table buyer_push_tokens (
    id uuid primary key,
    buyer_id uuid not null references buyers(id),
    token varchar(255) not null,
    platform varchar(20) not null,
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (token)
);
create index idx_buyer_push_tokens_buyer on buyer_push_tokens (buyer_id);

create table buyer_notifications (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    buyer_id uuid not null references buyers (id),
    type varchar(50) not null,
    title text not null,
    body text not null,
    entity_id uuid,
    read boolean not null default false
);

create index idx_buyer_notifications_buyer_id on buyer_notifications (buyer_id);
create index idx_buyer_notifications_buyer_id_read on buyer_notifications (buyer_id, read);
