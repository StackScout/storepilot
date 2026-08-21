-- Seller-declared GST registration (turnover-based, optional below A$75k/year,
-- so this can never be inferred from ABN presence alone) plus the per-order
-- tax-invoice snapshot it feeds — see StoreSettings.kt / Order.kt doc comments.
alter table store_settings add column gst_registered boolean not null default false;

alter table orders add column seller_abn varchar(255);
alter table orders add column gst_amount integer;
