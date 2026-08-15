alter table bookings add column recurrence_group_id uuid;
create index idx_bookings_recurrence_group on bookings (recurrence_group_id) where recurrence_group_id is not null;
