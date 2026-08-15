create table conversations (
    id uuid primary key,
    store_id uuid not null references stores(id),
    buyer_id uuid not null references buyers(id),
    last_message_at timestamptz,
    buyer_unread_count int not null default 0,
    seller_unread_count int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (store_id, buyer_id)
);
create index idx_conversations_buyer on conversations (buyer_id);
create index idx_conversations_store on conversations (store_id);

create table messages (
    id uuid primary key,
    conversation_id uuid not null references conversations(id),
    sender_type varchar(20) not null,
    body text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_messages_conversation on messages (conversation_id, created_at);
