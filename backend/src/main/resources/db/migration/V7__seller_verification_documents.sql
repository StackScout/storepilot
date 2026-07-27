-- Uploaded proof documents for seller verification (NIC always required;
-- business registration only for sellerType = 'business'). Nullable: local
-- storage stores a fetchable path, S3 storage stores an object key —
-- resolved to a URL at read time either way, never persisted as a fixed URL.
alter table store_settings add column nic_document_url varchar(500);
alter table store_settings add column business_reg_document_url varchar(500);
