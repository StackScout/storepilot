-- Make the payout/fee-collection snapshot ref tables polymorphic so a
-- single batch can include both order-sourced and booking-sourced rows —
-- see PayoutSourceRef/FeeCollectionSourceRef's doc comments. Separate from
-- V13 since it alters existing live tables rather than only adding new ones.

alter table payout_order_refs
    add column booking_id uuid references bookings (id),
    add column booking_number varchar(50),
    alter column order_id drop not null,
    alter column order_number drop not null,
    add constraint chk_payout_order_refs_exactly_one_source
        check ((order_id is not null)::int + (booking_id is not null)::int = 1);

alter table fee_collection_order_refs
    add column booking_id uuid references bookings (id),
    add column booking_number varchar(50),
    alter column order_id drop not null,
    alter column order_number drop not null,
    add constraint chk_fee_collection_order_refs_exactly_one_source
        check ((order_id is not null)::int + (booking_id is not null)::int = 1);

create index idx_payout_order_refs_booking_id on payout_order_refs (booking_id) where booking_id is not null;
create index idx_fee_collection_order_refs_booking_id on fee_collection_order_refs (booking_id) where booking_id is not null;
