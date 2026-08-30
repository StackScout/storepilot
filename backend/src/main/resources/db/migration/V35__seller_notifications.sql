create table seller_notifications (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    seller_id uuid not null references sellers (id),
    type varchar(50) not null,
    title text not null,
    body text not null,
    entity_id uuid,
    read boolean not null default false
);

create index idx_seller_notifications_seller_id on seller_notifications (seller_id);
create index idx_seller_notifications_seller_id_read on seller_notifications (seller_id, read);
