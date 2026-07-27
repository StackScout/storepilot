-- Stock tracking becomes opt-out at two levels: a store-wide switch (hides
-- the stock UI entirely and disables tracking for every product in that
-- store) and, when the store has it on, a per-product override. Both
-- default true so existing data keeps its current (always-tracked) behavior.
alter table products add column track_stock boolean not null default true;
alter table store_settings add column stock_management_enabled boolean not null default true;
