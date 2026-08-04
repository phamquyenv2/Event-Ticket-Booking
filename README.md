# GeekTicket — Hệ thống đặt vé concert

## Tổng quan

Backend API đặt vé concert, xử lý được nhiều người mua cùng lúc mà không bán quá số lượng.

Xây bằng Java 21 + Spring Boot 4.1 + PostgreSQL. Không dùng frontend.

## Tính năng đã làm

- **Concert**: Tạo concert, thêm hạng vé, publish, xem danh sách/chi tiết
- **Đặt vé**: Chọn hạng vé, tính tiền, trừ kho nguyên tử (không bán quá)
- **Idempotency**: Gửi lại cùng request → nhận lại cùng kết quả, không trừ vé 2 lần
- **Voucher**: Áp mã giảm giá (% hoặc cố định), giới hạn lượt dùng, per-user limit
- **Hủy vé**: Hủy booking → hoàn kho vé + hoàn voucher
- **Quản lý Operator**: Xem danh sách booking, lọc, cập nhật trạng thái, đánh dấu nghi vấn
- **Audit log**: Mọi thay đổi trạng thái đều ghi lịch sử (ai đổi, lý do, thời gian)

## Tech stack

| Thành phần | Phiên bản |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Gradle | 9.5.1 (Wrapper) |
| PostgreSQL | 17 |
| Flyway | Migration V1–V7 |
| Lombok | Compile-time |
| Swagger/OpenAPI | springdoc-openapi |
| JUnit 5 + Testcontainers | Test |
| Docker Compose | Local setup |

## Cần cài trước

- Java 21
- Docker Desktop
- Git

## Biến môi trường

Xem file `geekticket/.env.example`. Mặc định đã config sẵn cho local.

## Chạy local

```bash
cd geekticket

# 1. Bật PostgreSQL
docker compose up -d postgres

# 2. Chạy app
.\gradlew.bat bootRun
```

App chạy ở `http://localhost:8080`

Kiểm tra: `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`

## Chạy test

```bash
cd geekticket

# Chạy toàn bộ test
.\gradlew.bat clean test --no-daemon

# Kết quả: 92 tests, 0 failures
```

## Swagger

Mở trình duyệt: `http://localhost:8080/swagger-ui/index.html`

API docs JSON: `http://localhost:8080/v3/api-docs`

## Postman

File ở thư mục `postman/`:
- `GeekTicket.postman_collection.json` — 21 request, 9 folder
- `GeekTicket-Local.postman_environment.json` — biến local

Import vào Postman → chọn environment **GeekTicket Local** → chạy theo thứ tự folder 00 → 08.

Newman (CLI):
```bash
npm install -g newman
newman run postman/GeekTicket.postman_collection.json -e postman/GeekTicket-Local.postman_environment.json
```

## Seed data

Flyway V3 tạo sẵn data test:

| Dữ liệu | Chi tiết |
|---|---|
| Users | customer01 (ID=1), customer02 (ID=2), operator01 (ID=3), admin01 (ID=4) |
| Concerts | "World Tour 2026 Live in HCMC" (PUBLISHED, ID=1), "Acoustic Night" (DRAFT, ID=2) |
| Hạng vé | VIP 2.5M (ID=1), GA Standing 1.2M (ID=2), Standard 600K (ID=3) |
| Vouchers | WELCOME2026 (10%, ID=1), VIPFLASHSALE (300K fixed, ID=2) |

## Quyết định thiết kế chính

1. **Trừ kho nguyên tử**: Dùng `UPDATE ... WHERE available_quantity >= :qty` — 1 câu SQL duy nhất, không đọc rồi mới ghi
2. **Idempotency**: `INSERT ON CONFLICT DO NOTHING` claim key, so sánh SHA-256 hash, không bao giờ trừ vé 2 lần
3. **Voucher atomic**: `UPDATE ... WHERE current_usage_count < total_usage_limit` — không vượt quá giới hạn
4. **Monolithic**: Đơn giản, đủ cho bài assessment, không cần microservice
5. **Testcontainers**: Test trên PostgreSQL thật, không dùng H2

## Giới hạn hiện tại

- Chưa có authentication/authorization thật (chỉ dùng header X-User-Id, X-Operator-Id)
- Chưa có booking expiry tự động (hết hạn RESERVED → EXPIRED)
- Chưa có payment gateway
- Chưa có rate limiting
- Chưa có caching
- Chưa có notification (email/SMS)