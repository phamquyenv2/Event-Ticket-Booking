# System Design — GeekTicket

## Kiến trúc tổng quan

Monolithic backend, chia theo layer:

```
Client (Postman/Browser)
    ↓ HTTP
Controller Layer (REST endpoints)
    ↓
Service Layer (business logic + @Transactional)
    ↓
Repository Layer (JPA + custom query)
    ↓
PostgreSQL (Flyway migrations)
```

## Tại sao Monolithic?

- Bài assessment yêu cầu 1 backend service
- Đơn giản triển khai, dễ debug
- Đủ xử lý flash-sale nhờ DB-level locking (không cần message queue)
- Nếu cần scale sau này thì tách service dễ vì đã chia layer rõ ràng

## Trách nhiệm từng layer

### Controller
- Nhận request, validate input (Bean Validation)
- Gọi service, trả response
- **Không chứa logic nghiệp vụ**

### Service
- Chứa toàn bộ logic nghiệp vụ
- Quản lý transaction (`@Transactional`)
- Gọi repository, xử lý entity, trả DTO

### Repository
- Giao tiếp DB qua JPA
- Custom query (`@Query`, `Specification`)
- `@EntityGraph` tối ưu fetch (tránh N+1)

### Domain
- Entity: map bảng DB
- DTO: request/response, không lộ entity ra ngoài

## Luồng đặt vé (request flow)

```
1. Client gửi POST /api/v1/bookings
   Header: X-User-Id, Idempotency-Key
   Body: concertId, items[], voucherCode?

2. Controller validate input → gọi BookingService

3. BookingService.createBooking():
   a. Hash request body (SHA-256)
   b. INSERT idempotency_keys ON CONFLICT DO NOTHING
      - Nếu key đã tồn tại + hash khớp → trả booking cũ
      - Nếu key đã tồn tại + hash khác → throw 409 CONFLICT
   c. Validate concert đang PUBLISHED + đang mở bán
   d. Với mỗi item:
      - UPDATE ticket_categories SET available_quantity = available_quantity - :qty
        WHERE id = :id AND available_quantity >= :qty
      - Nếu affected rows = 0 → throw 409 INSUFFICIENT
   e. Nếu có voucher:
      - Validate status ACTIVE, thời gian hợp lệ, min order
      - UPDATE vouchers SET current_usage_count = current_usage_count + 1
        WHERE id = :id AND current_usage_count < total_usage_limit
      - Kiểm tra per_user_limit
      - Tính discount amount
   f. Tạo Booking entity + BookingItems + VoucherRedemption
   g. Lưu BookingStatusHistory
   h. Return BookingResponse

4. Controller trả 201 Created
```

## Transaction boundaries

- Mỗi method service có `@Transactional`
- Toàn bộ bước 3 ở trên nằm trong 1 transaction
- Nếu bất kỳ bước nào fail → rollback hết (kho vé, voucher, booking)
- Đảm bảo atomicity: không bao giờ trừ vé mà không tạo booking

## Flash-sale: Cách giữ ổn định

| Vấn đề | Giải pháp |
|---|---|
| Bán quá số lượng | `UPDATE ... WHERE available_quantity >= :qty` — DB-level atomic |
| Request trùng lặp | Idempotency key + SHA-256 hash — claim key trước khi xử lý |
| Voucher dùng quá giới hạn | `UPDATE ... WHERE current_usage_count < total_usage_limit` |
| Deadlock | Sắp xếp update theo thứ tự cố định (ticket category ID tăng dần) |
| N+1 query | `@EntityGraph` cho findAll và findDetailById |

## Trade-offs

| Quyết định | Lợi | Hại |
|---|---|---|
| Monolithic | Đơn giản, dễ test | Khó scale horizontal |
| DB-level locking | Đảm bảo consistency | Giảm throughput khi cùng row |
| Không dùng Redis | Ít dependency | Không có distributed lock |
| Idempotency key per user | Scope nhỏ, ít collision | Client phải quản lý key |
| Header-based auth | Đơn giản cho assessment | Không an toàn production |
