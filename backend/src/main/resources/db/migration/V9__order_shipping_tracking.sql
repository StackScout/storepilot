-- Courier tracking info, required by the seller when marking an order
-- shipped; courier_receipt_url is an optional proof-of-handover upload.
-- All nullable — only ever set once an order reaches "shipped".
alter table orders add column tracking_number varchar(255);
alter table orders add column courier_service_name varchar(255);
alter table orders add column courier_receipt_url varchar(500);
