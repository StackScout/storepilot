-- One-time email codes for guest order/booking lookup — see
-- GuestLookupOtpService's doc comment.
create table guest_lookup_codes (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    target_type varchar(20) not null,
    target_id uuid not null,
    code_hash varchar(64) not null,
    expires_at timestamptz not null,
    attempts integer not null default 0,
    unique (target_type, target_id)
);
