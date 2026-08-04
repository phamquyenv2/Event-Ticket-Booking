-- ====================================================================
-- GeekTicket — Sample Test Data Script (Dữ Liệu Mẫu Thử Nghiệm)
-- CSDL: PostgreSQL 17
-- ====================================================================

-- 1. CLEANUP OLD DATA (Reset nếu cần)
TRUNCATE TABLE booking_status_histories, voucher_redemptions, idempotency_records, 
               booking_items, bookings, vouchers, ticket_categories, concerts, users RESTART IDENTITY CASCADE;

-- 2. SEED USERS (Người dùng hệ thống)
INSERT INTO users (id, username, email, full_name, role, created_at, updated_at) VALUES
(1, 'customer01', 'customer01@geekticket.vn', 'Nguyen Van Customer', 'CUSTOMER', NOW(), NOW()),
(2, 'customer02', 'customer02@geekticket.vn', 'Tran Thi Customer', 'CUSTOMER', NOW(), NOW()),
(3, 'operator01', 'operator01@geekticket.vn', 'Le Van Operator', 'OPERATOR', NOW(), NOW()),
(4, 'admin01', 'admin01@geekticket.vn', 'Pham Quy Admin', 'ADMIN', NOW(), NOW());

-- 3. SEED CONCERTS (Sự kiện âm nhạc)
INSERT INTO concerts (id, title, description, venue, total_capacity, status, sale_start_time, sale_end_time, concert_start_time, created_at, updated_at) VALUES
(1, 'Sơn Tùng M-TP - Sky Tour 2027 Live in HCMC', 'Đêm nhạc hoành tráng nhất năm, quẩy tới bến cùng Sky!', 'Nhà Thi Đấu Phú Thọ, TP.HCM', 1000, 'PUBLISHED', '2026-01-01 00:00:00', '2027-12-31 23:59:59', '2028-01-15 19:00:00', NOW(), NOW()),
(2, 'Acoustic Night - Đêm Nhạc Nhẹ Nhàng', 'Đêm nhạc acoustic ấm cúng cùng dàn nghệ sĩ indie', 'Phòng Trà Không Tên, TP.HCM', 200, 'DRAFT', '2027-01-01 00:00:00', '2027-06-01 00:00:00', '2027-06-15 20:00:00', NOW(), NOW());

-- 4. SEED TICKET CATEGORIES (Hạng vé)
INSERT INTO ticket_categories (id, concert_id, name, description, price, total_quantity, available_quantity, max_quantity_per_booking, created_at, updated_at) VALUES
(1, 1, 'VIP Ngồi Hàng Đầu', 'Hàng ghế VIP sát sân khấu, tặng lightstick độc quyền', 2500000.00, 100, 98, 4, NOW(), NOW()),
(2, 1, 'GA Standing Quẩy Hết Mình', 'Khu vực đứng gần sân khấu, quẩy sung nhất', 1200000.00, 500, 500, 4, NOW(), NOW()),
(3, 1, 'Standard Ngồi Tầng 2', 'Hàng ghế tầng 2 tầm nhìn toàn cảnh', 600000.00, 400, 400, 4, NOW(), NOW());

-- 5. SEED VOUCHERS (Mã giảm giá)
INSERT INTO vouchers (id, code, discount_type, discount_value, max_discount_amount, min_order_amount, total_usage_limit, per_user_limit, current_usage_count, status, start_time, end_time, concert_id, created_at, updated_at) VALUES
(1, 'WELCOME2026', 'PERCENTAGE', 10.00, 300000.00, 500000.00, 1000, 1, 1, 'ACTIVE', '2026-01-01 00:00:00', '2028-12-31 23:59:59', NULL, NOW(), NOW()),
(2, 'VIPFLASHSALE', 'FIXED_AMOUNT', 300000.00, NULL, 2000000.00, 50, 1, 0, 'ACTIVE', '2026-01-01 00:00:00', '2028-12-31 23:59:59', 1, NOW(), NOW());

-- 6. SEED SAMPLE BOOKING (Đơn hàng đã tạo mẫu)
INSERT INTO bookings (id, booking_code, user_id, concert_id, status, subtotal, discount_amount, total_amount, suspicious, suspicious_reason, created_at, updated_at) VALUES
(1, 'BK-20260804-000001', 1, 1, 'RESERVED', 2500000.00, 250000.00, 2250000.00, false, NULL, NOW(), NOW());

INSERT INTO booking_items (id, booking_id, ticket_category_id, quantity, unit_price, subtotal) VALUES
(1, 1, 1, 1, 2500000.00, 2500000.00);

INSERT INTO voucher_redemptions (id, voucher_id, booking_id, user_id, discount_amount, redeemed_at) VALUES
(1, 1, 1, 1, 250000.00, NOW());

INSERT INTO booking_status_histories (id, booking_id, from_status, to_status, actor, reason, changed_at) VALUES
(1, 1, NULL, 'RESERVED', 'USER:1', 'Khách hàng khởi tạo đơn hàng thành công', NOW());

-- Reset ID Sequences cho PostgreSQL
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('concerts_id_seq', (SELECT MAX(id) FROM concerts));
SELECT setval('ticket_categories_id_seq', (SELECT MAX(id) FROM ticket_categories));
SELECT setval('vouchers_id_seq', (SELECT MAX(id) FROM vouchers));
SELECT setval('bookings_id_seq', (SELECT MAX(id) FROM bookings));
SELECT setval('booking_items_id_seq', (SELECT MAX(id) FROM booking_items));
SELECT setval('voucher_redemptions_id_seq', (SELECT MAX(id) FROM voucher_redemptions));
SELECT setval('booking_status_histories_id_seq', (SELECT MAX(id) FROM booking_status_histories));
