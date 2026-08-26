-- Seller-facing push notifications for bookings — see BookingNotifier's
-- sellerBookingCreated/sellerBookingReminder and SellerBookingReminderJob.
alter table store_settings
    add column seller_booking_reminder_minutes_before int not null default 60;

alter table bookings
    add column seller_reminder_sent_at timestamptz;
