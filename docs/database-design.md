# Database Design — GeekTicket

## Tổng quan

8 bảng chính, quản lý bằng Flyway (V1–V7). Dùng PostgreSQL 17.

## Bảng và mô tả

### users
Lưu thông tin user (customer, operator, admin).

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGSERIAL PK | |
| username | VARCHAR(50) UNIQUE | |
| email | VARCHAR(100) UNIQUE | |
| full_name | VARCHAR(100) | |
| role | VARCHAR(20) | CUSTOMER, OPERATOR, ADMIN |

### concerts
Thông tin concert.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGSERIAL PK | |
| title | VARCHAR(200) NOT NULL | |
| description | TEXT | |
| venue | VARCHAR(200) | |
| total_capacity | INT | |
| status | VARCHAR(20) | DRAFT, PUBLISHED, CANCELLED |
| sale_start_time | TIMESTAMP | Thời điểm mở bán |
| sale_end_time | TIMESTAMP | Thời điểm đóng bán |
| concert_start_time | TIMESTAMP | Thời điểm diễn ra |
| created_at, updated_at | TIMESTAMP | Audit |

### ticket_categories
Hạng vé của concert.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGSERIAL PK | |
| concert_id | BIGINT FK → concerts | |
| name | VARCHAR(100) | VIP, GA, Standard... |
| price | DECIMAL(19,2) | Giá 1 vé |
| total_quantity | INT | Tổng số vé ban đầu |
| available_quantity | INT | Số vé còn lại, trừ bằng atomic UPDATE |

### bookings
Đơn đặt vé.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGSERIAL PK | |
| booking_code | VARCHAR(30) UNIQUE | Format: BK-YYYYMMDD-XXXXXX |
| user_id | BIGINT FK → users | |
| concert_id | BIGINT FK → concerts | |
| status | VARCHAR(20) | RESERVED, CONFIRMED, CANCELLED, EXPIRED, FAILED |
| subtotal | DECIMAL(19,2) | Tổng trước giảm giá |
| discount_amount | DECIMAL(19,2) | Số tiền giảm |
| total_amount | DECIMAL(19,2) | Tổng sau giảm giá |
| suspicious | BOOLEAN DEFAULT false | Đánh dấu nghi vấn |
| suspicious_reason | TEXT | Lý do nghi vấn |
| created_at, updated_at | TIMESTAMP | |

### booking_items
Từng dòng vé trong 1 booking (1 booking có nhiều hạng vé).

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGSERIAL PK | |
| booking_id | BIGINT FK → bookings | |
| ticket_category_id | BIGINT FK → ticket_categories | |
| quantity | INT | Số lượng mua |
| unit_price | DECIMAL(19,2) | Giá tại thời điểm mua |
| subtotal | DECIMAL(19,2) | quantity × unit_price |

### vouchers
Mã giảm giá.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGSERIAL PK | |
| code | VARCHAR(50) UNIQUE | Mã voucher |
| discount_type | VARCHAR(20) | PERCENTAGE hoặc FIXED_AMOUNT |
| discount_value | DECIMAL(19,2) | 10 (nếu 10%) hoặc 300000 (nếu cố định) |
| max_discount_amount | DECIMAL(19,2) | Trần giảm giá (cho %) |
| min_order_amount | DECIMAL(19,2) | Đơn tối thiểu để áp |
| total_usage_limit | INT | Tổng lượt dùng tối đa |
| per_user_limit | INT | Mỗi user dùng tối đa |
| current_usage_count | INT | Đếm atomic bằng UPDATE |
| status | VARCHAR(20) | ACTIVE, INACTIVE |
| start_time, end_time | TIMESTAMP | Thời gian hiệu lực |
| concert_id | BIGINT FK → concerts NULL | Voucher riêng cho concert |

### voucher_redemptions
Ghi lại ai đã dùng voucher nào, cho booking nào.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGSERIAL PK | |
| voucher_id | BIGINT FK → vouchers | |
| booking_id | BIGINT FK → bookings UNIQUE | 1 booking chỉ dùng 1 voucher |
| user_id | BIGINT FK → users | |
| discount_amount | DECIMAL(19,2) | Số tiền thực tế được giảm |
| redeemed_at | TIMESTAMP | |

### idempotency_records
Chống request trùng lặp.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGSERIAL PK | |
| user_id | BIGINT | |
| idempotency_key | VARCHAR(100) | UUID từ client |
| request_hash | VARCHAR(64) | SHA-256 hash của request body |
| booking_id | BIGINT FK → bookings | Booking đã tạo |
| UNIQUE (user_id, idempotency_key) | | Claim key bằng INSERT ON CONFLICT |

### booking_status_histories
Audit log mọi thay đổi trạng thái.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGSERIAL PK | |
| booking_id | BIGINT FK → bookings | |
| from_status | VARCHAR(20) | NULL nếu lần đầu |
| to_status | VARCHAR(20) | |
| actor | VARCHAR(100) | "USER:1", "OPERATOR:3", "SYSTEM" |
| reason | TEXT | |
| changed_at | TIMESTAMP | |

## Indexes

```sql
CREATE INDEX idx_concerts_status_sale_time ON concerts(status, sale_start_time);
CREATE INDEX idx_ticket_categories_concert_id ON ticket_categories(concert_id);
CREATE INDEX idx_bookings_user_created ON bookings(user_id, created_at);
CREATE INDEX idx_bookings_status_created ON bookings(status, created_at);
CREATE INDEX idx_bookings_booking_code ON bookings(booking_code);
CREATE INDEX idx_vouchers_code ON vouchers(code);
CREATE INDEX idx_voucher_redemptions_voucher_user ON voucher_redemptions(voucher_id, user_id);
CREATE INDEX idx_idempotency_records_user_key ON idempotency_records(user_id, idempotency_key);
```

## Đảm bảo tính nhất quán kho vé

```sql
-- Trừ kho nguyên tử: 1 câu SQL, không cần lock riêng
UPDATE ticket_categories
SET available_quantity = available_quantity - :qty
WHERE id = :id AND available_quantity >= :qty;
-- affected rows = 0 → hết vé, throw exception
```

## Đảm bảo idempotency

```sql
-- Claim key trước khi xử lý
INSERT INTO idempotency_records (user_id, idempotency_key, request_hash)
VALUES (:userId, :key, :hash)
ON CONFLICT (user_id, idempotency_key) DO NOTHING;
-- RETURNING id = NULL → key đã tồn tại → so sánh hash
```

## Đảm bảo voucher không dùng quá

```sql
-- Trừ lượt dùng nguyên tử
UPDATE vouchers
SET current_usage_count = current_usage_count + 1
WHERE id = :id AND current_usage_count < total_usage_limit;
-- affected rows = 0 → hết lượt, throw exception
```
