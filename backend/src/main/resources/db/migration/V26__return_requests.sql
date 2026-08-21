-- Buyer post-delivery return/refund requests — see ReturnRequest.kt's doc
-- comment. Order.status is never touched by this feature; only
-- order.payment_status flips paid -> refunded, once money has actually
-- moved.
create table return_requests (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    order_id uuid not null references orders (id),
    reason_category varchar(30) not null,
    reason_note text,
    status varchar(20) not null default 'requested',
    seller_decision_note text,
    refund_reference varchar(255),
    settlement_reconciliation_note text,
    decided_at timestamptz,
    refunded_at timestamptz
);

create index idx_return_requests_order_id on return_requests (order_id);
create index idx_return_requests_status on return_requests (status);

-- Configurable per PlatformSettings.kt — see returnWindowDays' doc comment.
alter table platform_settings add column return_window_days int not null default 30;
