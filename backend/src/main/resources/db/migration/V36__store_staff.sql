create table store_staff_members (
    id uuid primary key,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    store_id uuid not null references stores(id),
    seller_id uuid not null unique references sellers(id)
);
create index idx_store_staff_members_store on store_staff_members (store_id);

create table store_staff_invites (
    id uuid primary key,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    store_id uuid not null references stores(id),
    email varchar(255) not null,
    name varchar(255) not null,
    token_hash varchar(64) not null unique,
    status varchar(20) not null default 'pending',
    expires_at timestamptz not null
);
create index idx_store_staff_invites_store on store_staff_invites (store_id);
create index idx_store_staff_invites_email on store_staff_invites (email);
