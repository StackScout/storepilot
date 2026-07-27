-- Public-facing social media links, shown on the store page — nullable,
-- optional per store. Lives on stores (public data), not store_settings
-- (private payout/verification data).
alter table stores add column facebook_url varchar(500);
alter table stores add column instagram_url varchar(500);
alter table stores add column tiktok_url varchar(500);
