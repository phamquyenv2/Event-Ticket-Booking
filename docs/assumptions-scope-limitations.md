# Assumptions, Scope & Limitations

## Giả định

- Không cần auth thật, dùng header `X-User-Id` và `X-Operator-Id` để phân biệt user
- User, Operator đã tồn tại sẵn trong DB (seed data)
- Mỗi booking có 1 idempotency key duy nhất trong phạm vi user
- Booking tạo xong ở trạng thái RESERVED, chưa có thanh toán thật
- Voucher chỉ áp 1 lần per user per voucher
- Không có booking expiry tự động
- Hệ thống monolithic, 1 service duy nhất

## Trong phạm vi (đã làm)

- Quản lý Concert (CRUD + publish)
- Quản lý hạng vé (ticket categories)
- Đặt vé (1 hoặc nhiều hạng vé, có/không voucher)
- Trừ kho nguyên tử — không bán quá số lượng
- Idempotency — gửi lại request không trừ vé 2 lần
- Voucher (% giảm, cố định, giới hạn tổng + per-user)
- Hủy vé — hoàn kho + hoàn voucher
- Operator quản lý booking (lọc, xem, đổi trạng thái, audit)
- Audit log cho mọi thay đổi trạng thái
- Đánh dấu booking nghi vấn
- Test concurrency: 50 user tranh 10 vé, 20 request cùng key, 2 user tranh voucher cuối
- Swagger/OpenAPI docs
- Postman collection 21 request
- Flyway migrations V1–V7

## Ngoài phạm vi (chưa làm)

- Authentication / Authorization (JWT, OAuth)
- Payment gateway
- Booking expiry tự động (scheduled job RESERVED → EXPIRED)
- Email/SMS notification
- Rate limiting
- Caching (Redis)
- Frontend
- CI/CD pipeline
- Monitoring/logging tập trung
- Multi-region deployment

## Giới hạn hiện tại

- Header-based auth: ai biết user ID thì gọi được, không an toàn cho production
- Chưa có cron job expire booking RESERVED quá lâu
- Chưa có payment, nên flow chỉ đến RESERVED rồi operator confirm thủ công
- Test concurrency chạy trên Testcontainers, chưa test trên cluster thật
- Voucher chưa có API CRUD (chỉ seed sẵn)

## Cải tiến tương lai

- Thêm Spring Security + JWT
- Scheduled task expire booking
- Tích hợp payment (VNPay, Momo)
- Redis cache cho concert detail
- WebSocket notification khi hết vé
- Rate limiter chống spam
