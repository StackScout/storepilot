alter table bookings add column last_reminder_sent_at timestamptz;
alter table products add column last_low_stock_alert_sent_at timestamptz;
