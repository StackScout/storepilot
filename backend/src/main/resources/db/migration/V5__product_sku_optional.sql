-- SKU becomes optional: sellers who don't track their own SKU scheme
-- shouldn't be forced to invent one. The product page hides the SKU row
-- entirely when it's null rather than showing a blank value.
alter table products alter column sku drop not null;
