CREATE INDEX idx_concerts_status_sale_time ON concerts(status, sale_start_time);
CREATE INDEX idx_ticket_categories_concert_id ON ticket_categories(concert_id);
CREATE INDEX idx_bookings_user_created ON bookings(user_id, created_at);
CREATE INDEX idx_bookings_status_created ON bookings(status, created_at);
CREATE INDEX idx_bookings_booking_code ON bookings(booking_code);
CREATE INDEX idx_vouchers_code ON vouchers(code);
CREATE INDEX idx_voucher_redemptions_voucher_user ON voucher_redemptions(voucher_id, user_id);
CREATE INDEX idx_idempotency_records_user_key ON idempotency_records(user_id, idempotency_key);
