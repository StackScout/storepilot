-- Seller-configurable fulfillment/delivery time, per-product override with
-- a store-wide default — see Product.kt/StoreSettings.kt.
alter table products
    add column fulfillment_time_hours int,
    add column delivery_time_hours int;

alter table store_settings
    add column default_fulfillment_time_hours int not null default 48,
    add column default_delivery_time_hours int not null default 120;

-- Resolved once at order-creation time and snapshotted here — see Order.kt's
-- doc comment on fulfillmentTimeHours for why this isn't a live Product join.
-- Backfilled to each store's current default so existing orders don't all
-- read as "just placed" once the reminder jobs start running.
alter table orders
    add column fulfillment_time_hours int,
    add column delivery_time_hours int,
    add column shipped_at timestamptz,
    add column fulfillment_reminder_sent_at timestamptz,
    add column fulfillment_overdue_reminder_sent_at timestamptz,
    add column delivery_reminder_sent_at timestamptz;

update orders o
set fulfillment_time_hours = coalesce(s.default_fulfillment_time_hours, 48),
    delivery_time_hours = coalesce(s.default_delivery_time_hours, 120)
from store_settings s
where s.store_id = o.store_id;

update orders set fulfillment_time_hours = 48 where fulfillment_time_hours is null;
update orders set delivery_time_hours = 120 where delivery_time_hours is null;

alter table orders
    alter column fulfillment_time_hours set not null,
    alter column delivery_time_hours set not null;

-- Existing shipped/delivered orders never had a fulfillment/delivery clock
-- running — mark every non-terminal reminder as already sent for them so
-- the new jobs don't retroactively fire on old data on first run.
update orders set fulfillment_reminder_sent_at = now(), fulfillment_overdue_reminder_sent_at = now()
where status not in ('pending', 'confirmed');
update orders set delivery_reminder_sent_at = now() where status <> 'shipped';
