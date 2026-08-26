-- One row per registered device push token — see PushToken.kt.
create table push_tokens (
    id uuid primary key,
    seller_id uuid not null references sellers(id),
    token varchar(255) not null,
    platform varchar(20) not null,
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (token)
);
create index idx_push_tokens_seller on push_tokens (seller_id);
