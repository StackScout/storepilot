alter table store_settings
    add column bank_transfer_enabled boolean not null default false;

alter table orders
    add column receipt_url varchar(500);
