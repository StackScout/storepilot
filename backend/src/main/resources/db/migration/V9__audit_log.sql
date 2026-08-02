-- Generic admin-action audit trail — see AuditLog's doc comment. Every
-- row is one recorded action (store approval/rejection, admin invited,
-- payout/fee-collection marked settled, ...), never updated after insert.
create table audit_logs (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    actor_email varchar(255) not null,
    actor_id uuid,
    action varchar(50) not null,
    target_type varchar(50),
    target_id varchar(255),
    description text not null
);

create index idx_audit_logs_action on audit_logs (action);
create index idx_audit_logs_target_type on audit_logs (target_type);
create index idx_audit_logs_created_at on audit_logs (created_at desc);
