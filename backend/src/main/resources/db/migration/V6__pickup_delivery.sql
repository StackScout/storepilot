-- Pickup-in-store delivery option — see Order.deliveryMethod and
-- StoreSettings.pickupEnabled doc comments.

alter table orders
    add column delivery_method varchar(20) not null default 'shipping';

-- Opt-in and off by default, same reasoning as bank_transfer_enabled /
-- stripe_enabled: not every seller has a physical location buyers can
-- collect from, so it shouldn't switch on silently.
alter table store_settings
    add column pickup_enabled boolean not null default false;
