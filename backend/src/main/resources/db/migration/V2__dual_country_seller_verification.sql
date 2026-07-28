-- Restores Sri Lanka's seller-verification fields alongside the Australian
-- ones so the same schema/codebase can serve either country as a separate
-- deployment (see StoreService's country-conditional validation, keyed off
-- platform_settings.country_code). A given deployment only ever populates
-- one pair; the other stays null.

alter table store_settings
    alter column driver_licence_number drop not null;

alter table store_settings
    add column nic_number varchar(50),
    add column business_registration_number varchar(100),
    add column nic_document_url varchar(500),
    add column business_reg_document_url varchar(500);
