-- Every money field in this codebase moves from a whole-dollar integer to
-- an integer count of the currency's smallest unit (cents for AUD) — see
-- Product.price's doc comment. This is a one-time value rewrite (×100) of
-- existing rows, safe on this still-local/pre-launch dataset; DataSeeder's
-- own literals were updated separately to seed fresh data already in cents.

-- compare_at_price * 100 stays null on rows where it's already null.
update products set
    price = price * 100,
    compare_at_price = compare_at_price * 100;

update order_items set unit_price = unit_price * 100;

update orders set
    subtotal = subtotal * 100,
    shipping_fee = shipping_fee * 100,
    platform_fee = platform_fee * 100,
    total = total * 100;

update payouts set
    subtotal = subtotal * 100,
    platform_fee = platform_fee * 100,
    net = net * 100;

update payout_order_refs set
    subtotal = subtotal * 100,
    platform_fee = platform_fee * 100,
    net = net * 100;

update platform_settings set flat_shipping_fee = flat_shipping_fee * 100;
