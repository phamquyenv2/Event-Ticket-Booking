-- Seed Users
INSERT INTO users (id, username, email, full_name, role) VALUES
(1, 'customer01', 'customer01@example.com', 'Alice Customer', 'CUSTOMER'),
(2, 'customer02', 'customer02@example.com', 'Bob Customer', 'CUSTOMER'),
(3, 'operator01', 'operator01@example.com', 'Carol Operator', 'OPERATOR'),
(4, 'admin01', 'admin01@example.com', 'Dave Admin', 'ADMIN')
ON CONFLICT (id) DO NOTHING;

-- Seed Concerts
INSERT INTO concerts (id, title, description, venue, total_capacity, status, sale_start_time, sale_end_time, concert_start_time) VALUES
(1, 'World Tour 2026 Live in HCMC', 'Grand live concert featuring international top artists', 'Saigon Exhibition and Convention Center (SECC)', 5000, 'PUBLISHED', NOW() - INTERVAL '1 day', NOW() + INTERVAL '30 days', NOW() + INTERVAL '45 days'),
(2, 'Acoustic Night Unplugged', 'Intimate acoustic music session', 'Youth Cultural Center', 500, 'DRAFT', NOW() + INTERVAL '5 days', NOW() + INTERVAL '20 days', NOW() + INTERVAL '25 days')
ON CONFLICT (id) DO NOTHING;

-- Seed Ticket Categories
INSERT INTO ticket_categories (id, concert_id, name, description, price, total_quantity, available_quantity) VALUES
(1, 1, 'VIP Experience', 'Front row seating with exclusive gift pack', 2500000.00, 500, 500),
(2, 1, 'GA Standing', 'General admission standing area near stage', 1200000.00, 2500, 2500),
(3, 1, 'Standard Seating', 'Standard balcony seated area', 600000.00, 2000, 2000)
ON CONFLICT (id) DO NOTHING;

-- Seed Vouchers
INSERT INTO vouchers (id, code, description, discount_type, discount_value, max_discount_amount, min_order_amount, total_usage_limit, per_user_limit, current_usage_count, status, start_time, end_time, concert_id) VALUES
(1, 'WELCOME2026', '10% discount for all concerts', 'PERCENTAGE', 10.00, 200000.00, 500000.00, 100, 1, 0, 'ACTIVE', NOW() - INTERVAL '1 day', NOW() + INTERVAL '60 days', NULL),
(2, 'VIPFLASHSALE', 'Fixed 300k VND discount for VIP tickets', 'FIXED_AMOUNT', 300000.00, 300000.00, 2000000.00, 50, 1, 0, 'ACTIVE', NOW() - INTERVAL '1 day', NOW() + INTERVAL '10 days', 1)
ON CONFLICT (id) DO NOTHING;

-- Reset sequences
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('concerts_id_seq', (SELECT MAX(id) FROM concerts));
SELECT setval('ticket_categories_id_seq', (SELECT MAX(id) FROM ticket_categories));
SELECT setval('vouchers_id_seq', (SELECT MAX(id) FROM vouchers));
