-- Seller-submitted proposed changes to an already-approved store's
-- identity-verification fields — see StoreVerificationChangeRequest's doc
-- comment. Nothing here is a live value; it only becomes real once an
-- admin approves it (applied onto store_settings by
-- StoreVerificationChangeRequestService).
create table store_verification_change_requests (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    store_id uuid not null references stores (id),
    status varchar(20) not null,
    seller_type varchar(20),
    driver_licence_number varchar(255),
    abn varchar(255),
    nic_number varchar(255),
    business_registration_number varchar(255),
    driver_licence_document_url varchar(500),
    abn_document_url varchar(500),
    nic_document_url varchar(500),
    business_reg_document_url varchar(500),
    rejection_reason text,
    reviewed_at timestamptz,
    reviewed_by_email varchar(255)
);

create index idx_store_verification_change_requests_store_id on store_verification_change_requests (store_id);
create index idx_store_verification_change_requests_status on store_verification_change_requests (status);
