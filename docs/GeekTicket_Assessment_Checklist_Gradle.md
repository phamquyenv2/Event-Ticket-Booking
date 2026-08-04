# GeekTicket – Product Backend Technical Assessment

## Cách sử dụng checklist

- Đây là **nguồn yêu cầu chính** của dự án.
- Không tự thêm chức năng ngoài checklist nếu chưa hoàn thành các mục bắt buộc.
- Chỉ đổi `[ ]` thành `[x]` khi có bằng chứng chạy được.
- Bằng chứng hợp lệ gồm: file/method, Flyway migration, automated test, API response, Postman/Newman result hoặc lệnh Gradle đã chạy.
- Những mục chưa làm phải giữ nguyên `[ ]` và được ghi trong phần limitations.
- Mọi prompt triển khai phải nêu rõ đang xử lý mục nào trong checklist này.


## 1. Mục tiêu bài làm

Xây dựng một backend đơn giản cho nền tảng đặt vé concert, phục vụ hai nhóm luồng nghiệp vụ:

- Customer-facing booking flow.
- Internal operation workflow.

Trọng tâm của bài làm:

- Thiết kế kiến trúc backend rõ ràng.
- Thiết kế cơ sở dữ liệu hợp lý.
- Ngăn bán vượt số lượng vé.
- Ngăn tạo booking trùng do retry.
- Kiểm soát việc sử dụng voucher.
- Đảm bảo project có thể chạy và kiểm thử ở local.
- Ghi rõ phạm vi, giả định, phần đã làm, phần chưa làm và hạn chế.

---

## 2. Phạm vi đề xuất

### 2.1. Chức năng sẽ làm

#### Customer

- [ ] Xem danh sách concert đã được publish.
- [ ] Xem chi tiết concert.
- [ ] Xem các hạng vé và giá.
- [ ] Tạo booking.
- [ ] Áp dụng voucher khi tạo booking.
- [ ] Xem trạng thái booking.
- [ ] Hủy booking nếu trạng thái cho phép.

#### Operator / Administrator

- [ ] Xem danh sách booking.
- [ ] Lọc booking theo trạng thái.
- [ ] Xem chi tiết booking.
- [ ] Cập nhật trạng thái booking thủ công.
- [ ] Lưu lịch sử thay đổi trạng thái.
- [ ] Tạo concert.
- [ ] Tạo các hạng vé cho concert.
- [ ] Publish concert.
- [ ] Xem số lượng vé còn lại.
- [ ] Đánh dấu hoặc lọc booking đáng ngờ.

### 2.2. Chức năng có thể không làm

- [ ] Customer frontend.
- [ ] Operation dashboard frontend.
- [ ] Payment gateway thực tế.
- [ ] Chọn ghế cụ thể.
- [ ] Email hoặc SMS notification.
- [ ] Refund workflow.
- [ ] OAuth2 hoặc JWT hoàn chỉnh.
- [ ] Voucher update/delete API.
- [ ] Automatic fraud detection.
- [ ] Cloud deployment.
- [ ] Microservices.
- [ ] Kafka hoặc RabbitMQ.
- [ ] Redis distributed lock.

---

## 3. Các giả định cần ghi trong báo cáo

- [ ] Một booking chỉ thuộc một concert.
- [ ] Một booking có thể chứa một hoặc nhiều hạng vé.
- [ ] Inventory được quản lý theo hạng vé, không quản lý từng ghế.
- [ ] Một booking chỉ áp dụng tối đa một voucher.
- [ ] Một voucher có thể giới hạn số lần sử dụng trên toàn hệ thống.
- [ ] Một voucher có thể giới hạn số lần sử dụng theo từng user.
- [ ] Giá vé được snapshot tại thời điểm tạo booking.
- [ ] Tiền được lưu bằng `BigDecimal` và kiểu `DECIMAL` trong database.
- [ ] Thời gian được lưu theo UTC.
- [ ] User hiện tại có thể được giả lập bằng header hoặc dữ liệu seed.
- [ ] Payment gateway nằm ngoài phạm vi.
- [ ] Booking có thời gian giữ vé trước khi hết hạn.
- [ ] Operator chỉ được chuyển booking theo các trạng thái hợp lệ.
- [ ] Voucher có thể được seed sẵn nếu không làm API quản lý voucher.

---

## 4. Công nghệ đề xuất

- Java 21.
- Spring Boot.
- Gradle Wrapper.
- Spring Web.
- Spring Data JPA.
- Spring Validation.
- PostgreSQL.
- Flyway Migration.
- Lombok.
- Spring Boot Actuator.
- Swagger / OpenAPI.
- Docker Compose.
- JUnit 5.
- Mockito.
- Testcontainers.
- Postman.

---

## 5. Kiến trúc hệ thống

Sử dụng **Modular Monolith kết hợp Layered Architecture**.

```text
Controller
    ↓
Application Service
    ↓
Domain
    ↓
Repository
    ↓
PostgreSQL
```

### Các module chính

```text
concert
booking
voucher
operation
common
```

### Cấu trúc package đề xuất

```text
src/main/java/com/quyen/geekticket/
├── GeekTicketApplication.java
│
├── config/
│   ├── CorsConfig.java
│   ├── JpaConfig.java
│   ├── OpenApiConfig.java
│   └── SecurityConfig.java
│
├── controller/
│   ├── ConcertController.java
│   ├── BookingController.java
│   ├── OperationBookingController.java
│   ├── OperationConcertController.java
│   └── VoucherController.java
│
├── domain/
│   ├── entity/
│   │   ├── User.java
│   │   ├── Concert.java
│   │   ├── TicketCategory.java
│   │   ├── Booking.java
│   │   ├── BookingItem.java
│   │   ├── Voucher.java
│   │   ├── VoucherRedemption.java
│   │   ├── IdempotencyRecord.java
│   │   └── BookingStatusHistory.java
│   │
│   ├── dto/
│   │   ├── PageResponse.java
│   │   └── ApiResponse.java
│   │
│   ├── request/
│   │   ├── CreateBookingRequest.java
│   │   ├── BookingItemRequest.java
│   │   ├── CancelBookingRequest.java
│   │   ├── UpdateBookingStatusRequest.java
│   │   ├── CreateConcertRequest.java
│   │   ├── CreateTicketCategoryRequest.java
│   │   └── CreateVoucherRequest.java
│   │
│   └── response/
│       ├── concert/
│       │   ├── ConcertSummaryResponse.java
│       │   ├── ConcertDetailResponse.java
│       │   └── TicketCategoryResponse.java
│       ├── booking/
│       │   ├── BookingResponse.java
│       │   ├── BookingDetailResponse.java
│       │   └── BookingItemResponse.java
│       └── voucher/
│           └── VoucherResponse.java
│
├── event/
│   ├── BookingCreatedEvent.java
│   ├── BookingConfirmedEvent.java
│   └── dto/
│       └── BookingEventPayload.java
│
├── repository/
│   ├── UserRepository.java
│   ├── ConcertRepository.java
│   ├── TicketCategoryRepository.java
│   ├── BookingRepository.java
│   ├── BookingItemRepository.java
│   ├── VoucherRepository.java
│   ├── VoucherRedemptionRepository.java
│   ├── IdempotencyRecordRepository.java
│   └── BookingStatusHistoryRepository.java
│
├── service/
│   ├── ConcertService.java
│   ├── BookingService.java
│   ├── VoucherService.java
│   ├── OperationBookingService.java
│   ├── OperationConcertService.java
│   └── impl/
│       ├── ConcertServiceImpl.java
│       ├── BookingServiceImpl.java
│       ├── VoucherServiceImpl.java
│       ├── OperationBookingServiceImpl.java
│       └── OperationConcertServiceImpl.java
│
└── util/
    ├── annotation/
    │   └── ApiMessage.java
    ├── constant/
    │   ├── BookingStatus.java
    │   ├── ConcertStatus.java
    │   ├── VoucherStatus.java
    │   ├── DiscountType.java
    │   └── UserRole.java
    ├── error/
    │   ├── GlobalExceptionHandler.java
    │   ├── BusinessException.java
    │   ├── ResourceNotFoundException.java
    │   ├── InvalidBookingStatusException.java
    │   ├── InsufficientTicketException.java
    │   └── ErrorCode.java
    ├── mapper/
    │   ├── ConcertMapper.java
    │   ├── BookingMapper.java
    │   └── VoucherMapper.java
    └── generator/
        ├── BookingCodeGenerator.java
        └── RequestHashGenerator.java
```

---

## 6. Thiết kế database

### 6.1. Các bảng chính

- [ ] `users`
- [ ] `concerts`
- [ ] `ticket_categories`
- [ ] `bookings`
- [ ] `booking_items`
- [ ] `vouchers`
- [ ] `voucher_redemptions`
- [ ] `idempotency_records`
- [ ] `booking_status_histories`

### 6.2. Quan hệ chính

```text
Concert 1 --- N TicketCategory
Concert 1 --- N Booking
Booking 1 --- N BookingItem
TicketCategory 1 --- N BookingItem
Voucher 1 --- N VoucherRedemption
Booking 1 --- 0..1 VoucherRedemption
Booking 1 --- N BookingStatusHistory
```

### 6.3. Constraint quan trọng

- [ ] `available_quantity >= 0`
- [ ] `price >= 0`
- [ ] `quantity > 0`
- [ ] Unique `booking_code`
- [ ] Unique `voucher_code`
- [ ] Unique `(user_id, idempotency_key)`
- [ ] Kiểm soát voucher theo `(voucher_id, user_id)` hoặc `usage_limit_per_user`

### 6.4. Index cần có

- [ ] `concerts(status, sale_start_time)`
- [ ] `ticket_categories(concert_id)`
- [ ] `bookings(user_id, created_at)`
- [ ] `bookings(status, created_at)`
- [ ] `bookings(booking_code)`
- [ ] `vouchers(code)`
- [ ] `voucher_redemptions(voucher_id, user_id)`
- [ ] `idempotency_records(user_id, idempotency_key)`

---

## 7. Booking state machine

Trạng thái đề xuất:

```text
RESERVED
├── CONFIRMED
├── CANCELLED
├── EXPIRED
└── FAILED
```

### Chuyển trạng thái hợp lệ

| Current status | New status | Actor |
|---|---|---|
| RESERVED | CONFIRMED | Operator |
| RESERVED | CANCELLED | Customer / Operator |
| RESERVED | EXPIRED | System |
| RESERVED | FAILED | System / Operator |
| CONFIRMED | CANCELLED | Operator |

- [ ] Không cho phép `CANCELLED -> CONFIRMED`.
- [ ] Không cho phép client cập nhật status tùy ý.
- [ ] Mọi cập nhật thủ công phải lưu người thực hiện và lý do.

---

## 8. API cần triển khai

### 8.1. Customer APIs

```http
GET /api/v1/concerts
GET /api/v1/concerts/{concertId}
POST /api/v1/bookings
GET /api/v1/bookings/{bookingCode}
POST /api/v1/bookings/{bookingId}/cancel
```

### 8.2. Operation APIs

```http
GET /api/v1/operations/bookings
GET /api/v1/operations/bookings/{bookingId}
PATCH /api/v1/operations/bookings/{bookingId}/status

POST /api/v1/operations/concerts
POST /api/v1/operations/concerts/{concertId}/ticket-categories
PATCH /api/v1/operations/concerts/{concertId}/publish
```

### 8.3. API có thể không làm

```http
POST /api/v1/operations/vouchers
PATCH /api/v1/operations/vouchers/{voucherId}
DELETE /api/v1/operations/vouchers/{voucherId}
```

Có thể seed voucher và chỉ làm logic apply voucher khi tạo booking.

---

## 9. Luồng tạo booking

- [ ] Nhận request cùng `Idempotency-Key`.
- [ ] Kiểm tra idempotency record.
- [ ] Kiểm tra concert tồn tại và đang mở bán.
- [ ] Kiểm tra ticket category thuộc concert.
- [ ] Kiểm tra quantity hợp lệ.
- [ ] Bắt đầu transaction.
- [ ] Trừ ticket inventory bằng atomic update hoặc database lock.
- [ ] Validate voucher nếu có.
- [ ] Giữ lượt sử dụng voucher.
- [ ] Tính subtotal.
- [ ] Tính discount.
- [ ] Tính total amount.
- [ ] Tạo booking.
- [ ] Tạo booking items.
- [ ] Tạo voucher redemption.
- [ ] Lưu idempotency record.
- [ ] Commit transaction.
- [ ] Trả booking response.

Nếu một bước thất bại:

- [ ] Rollback inventory.
- [ ] Rollback voucher usage.
- [ ] Không tạo booking.

---

## 10. Xử lý các vấn đề chính

### 10.1. Ngăn overselling

Sử dụng atomic conditional update:

```sql
UPDATE ticket_categories
SET available_quantity = available_quantity - :quantity
WHERE id = :ticketCategoryId
  AND available_quantity >= :quantity;
```

- [ ] Nếu affected rows bằng `0`, trả lỗi không đủ vé.
- [ ] Không thực hiện kiểu đọc quantity rồi mới cập nhật ngoài transaction.

### 10.2. Ngăn duplicate booking

Header:

```http
Idempotency-Key: <uuid>
```

Quy tắc:

- [ ] Cùng user, cùng key, cùng body: trả booking cũ.
- [ ] Cùng user, cùng key, body khác: trả lỗi conflict.
- [ ] Unique constraint ở database.
- [ ] Không trừ vé lần thứ hai.

### 10.3. Ngăn voucher abuse

- [ ] Kiểm tra voucher tồn tại.
- [ ] Kiểm tra trạng thái ACTIVE.
- [ ] Kiểm tra thời gian hiệu lực.
- [ ] Kiểm tra concert áp dụng.
- [ ] Kiểm tra minimum order amount.
- [ ] Kiểm tra tổng số lượt sử dụng.
- [ ] Kiểm tra số lần sử dụng theo user.
- [ ] Voucher update và booking creation nằm trong cùng transaction.

### 10.4. Ổn định khi flash sale

- [ ] API stateless.
- [ ] Transaction ngắn.
- [ ] Có database index.
- [ ] Pagination cho danh sách.
- [ ] Không gọi external service bên trong transaction.
- [ ] Có timeout hợp lý.
- [ ] Có global exception handling.
- [ ] Có Actuator health check.
- [ ] Có thể ghi rate limiting vào future improvement.

---

## 11. Validation và error handling

### Validation cần có

- [ ] Quantity phải lớn hơn `0`.
- [ ] Quantity không vượt giới hạn mỗi booking.
- [ ] Ticket price không âm.
- [ ] Voucher code giới hạn độ dài.
- [ ] Sale start time phải trước sale end time.
- [ ] Concert start time phải sau sale start time.
- [ ] Status transition phải hợp lệ.

### Error codes đề xuất

```text
CONCERT_NOT_FOUND
CONCERT_NOT_ON_SALE
TICKET_CATEGORY_NOT_FOUND
INSUFFICIENT_TICKET_QUANTITY
BOOKING_LIMIT_EXCEEDED
BOOKING_NOT_FOUND
VOUCHER_NOT_FOUND
VOUCHER_EXPIRED
VOUCHER_USAGE_LIMIT_REACHED
VOUCHER_ALREADY_USED
INVALID_BOOKING_STATUS_TRANSITION
IDEMPOTENCY_KEY_REQUIRED
IDEMPOTENCY_KEY_CONFLICT
```

### Error response mẫu

```json
{
  "timestamp": "2026-08-03T10:20:00Z",
  "status": 409,
  "code": "INSUFFICIENT_TICKET_QUANTITY",
  "message": "The requested ticket quantity is no longer available",
  "path": "/api/v1/bookings"
}
```

---

## 12. Testing cần làm

### 12.1. Unit tests

- [ ] Price calculation.
- [ ] Voucher discount calculation.
- [ ] Voucher validation.
- [ ] Booking status transition.
- [ ] Request validation.
- [ ] Booking code generation.

### 12.2. Integration tests

- [ ] Create booking transaction.
- [ ] Ticket inventory atomic update.
- [ ] Voucher usage update.
- [ ] Transaction rollback.
- [ ] Idempotency.
- [ ] Repository query.
- [ ] Operator status update.

### 12.3. Concurrency tests

#### Overselling test

- [ ] Có 10 vé.
- [ ] Gửi 50 request đồng thời.
- [ ] Tối đa 10 request thành công.
- [ ] Inventory cuối bằng 0.
- [ ] Inventory không âm.

#### Idempotency test

- [ ] Gửi hai request cùng key.
- [ ] Chỉ tạo một booking.
- [ ] Chỉ trừ vé một lần.
- [ ] Hai response trả cùng booking code.

#### Voucher concurrency test

- [ ] Voucher còn một lượt.
- [ ] Hai booking dùng đồng thời.
- [ ] Chỉ một booking được áp dụng discount.

#### Rollback test

- [ ] Inventory được trừ nhưng voucher xử lý thất bại.
- [ ] Booking không được tạo.
- [ ] Inventory được rollback.

---

## 13. Swagger và Postman

### Swagger

- [ ] Mô tả tất cả endpoint.
- [ ] Có request example.
- [ ] Có response example.
- [ ] Có header `Idempotency-Key`.
- [ ] Có response status.
- [ ] Có error response.
- [ ] Swagger chạy tại local.

Ví dụ:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

### Postman

Tạo:

```text
postman/
├── GeekTicket.postman_collection.json
└── GeekTicket-Local.postman_environment.json
```

Environment:

```text
baseUrl = http://localhost:8080
userId = customer-001
operatorId = operator-001
```

Collection nên có:

- [ ] List concerts.
- [ ] Get concert detail.
- [ ] Create booking.
- [ ] Retry create booking với cùng idempotency key.
- [ ] Get booking.
- [ ] Cancel booking.
- [ ] List operation bookings.
- [ ] Update booking status.
- [ ] Create concert.
- [ ] Publish concert.

---

## 14. Tài liệu cần nộp

### 14.1. Báo cáo chính

Tên đề xuất:

```text
GeekTicket-Technical-Report.pdf
```

Cấu trúc:

1. Introduction.
2. Requirement Analysis.
3. Scope and Assumptions.
4. System Architecture.
5. Database Design.
6. Core Business Workflows.
7. API Design.
8. Concurrency and Data Consistency.
9. Security and Operation Design.
10. Testing Strategy.
11. Local Setup and Development Guideline.
12. Implemented Features and Limitations.
13. Future Improvements.
14. Conclusion.

### 14.2. Các sơ đồ

- [ ] System Architecture Diagram.
- [ ] Database ERD.
- [ ] Create Booking Sequence Diagram.
- [ ] Booking State Machine Diagram.

### 14.3. README

README cần có:

- [ ] Project overview.
- [ ] Technology stack.
- [ ] Prerequisites.
- [ ] Environment variables.
- [ ] How to run local.
- [ ] How to run tests.
- [ ] Swagger URL.
- [ ] Postman guide.
- [ ] Seed data.
- [ ] Main design decisions.
- [ ] Known limitations.

### 14.4. Coding guideline

Tên đề xuất:

```text
CONTRIBUTING.md
```

Nội dung:

- [ ] Package structure.
- [ ] Naming convention.
- [ ] Cách thêm API mới.
- [ ] Cách tạo request/response DTO.
- [ ] Controller không chứa business logic.
- [ ] Không trả Entity trực tiếp.
- [ ] Cách xử lý exception.
- [ ] Cách thêm migration.
- [ ] Cách viết unit test.
- [ ] Cách chạy test.
- [ ] Cách cập nhật Swagger và Postman.

### 14.5. Scope document

Tên đề xuất:

```text
docs/assumptions-scope-limitations.md
```

Nội dung:

- [ ] Assumptions.
- [ ] Implemented features.
- [ ] Not implemented features.
- [ ] Limitations.
- [ ] Future improvements.

---

## 15. Docker và local setup

### File cần có

- [ ] `Dockerfile`
- [ ] `compose.yml` hoặc `docker-compose.yml`
- [ ] `.env.example`
- [ ] Flyway migrations
- [ ] Seed data

### Lệnh chạy đề xuất

```bash
docker compose up --build
```

Hoặc:

```bash
docker compose up -d postgres
./gradlew bootRun
```

### URL kiểm tra

```text
Application: http://localhost:8080
Swagger: http://localhost:8080/swagger-ui.html
Health: http://localhost:8080/actuator/health
```

---

## 16. Cấu trúc thư mục nộp bài

```text
geekticket/
├── README.md
├── CONTRIBUTING.md
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── src/
│   ├── main/
│   └── test/
├── docs/
│   ├── GeekTicket-Technical-Report.pdf
│   ├── assumptions-scope-limitations.md
│   ├── system-design.md
│   ├── database-design.md
│   └── diagrams/
│       ├── system-architecture.png
│       ├── database-erd.png
│       ├── booking-sequence.png
│       └── booking-state-machine.png
├── postman/
│   ├── GeekTicket.postman_collection.json
│   └── GeekTicket-Local.postman_environment.json
└── src/main/resources/db/migration/
    ├── V1__create_schema.sql
    ├── V2__create_indexes.sql
    └── V3__seed_data.sql
```

---

## 17. Kế hoạch thực hiện trong 48 giờ

### Giai đoạn 1 – Phân tích và thiết kế

- [ ] Chốt scope.
- [ ] Chốt assumptions.
- [ ] Chốt booking states.
- [ ] Thiết kế ERD.
- [ ] Thiết kế architecture.
- [ ] Chốt API list.
- [ ] Chốt concurrency strategy.

### Giai đoạn 2 – Khởi tạo project

- [ ] Tạo Spring Boot project.
- [ ] Cấu hình PostgreSQL.
- [ ] Cấu hình Flyway.
- [ ] Tạo Docker Compose.
- [ ] Tạo common response.
- [ ] Tạo global exception handler.
- [ ] Cấu hình Swagger.
- [ ] Cấu hình Actuator.

### Giai đoạn 3 – Concert và inventory

- [ ] Tạo concert entity.
- [ ] Tạo ticket category entity.
- [ ] API list concert.
- [ ] API concert detail.
- [ ] API create concert.
- [ ] API create ticket category.
- [ ] API publish concert.

### Giai đoạn 4 – Core booking

- [ ] Booking entity.
- [ ] Booking item entity.
- [ ] Booking state machine.
- [ ] Create booking API.
- [ ] Atomic inventory update.
- [ ] Idempotency.
- [ ] Booking lookup.
- [ ] Cancel booking.

### Giai đoạn 5 – Voucher

- [ ] Voucher entity.
- [ ] Voucher redemption.
- [ ] Seed voucher.
- [ ] Validate voucher.
- [ ] Apply discount.
- [ ] Voucher usage concurrency.

### Giai đoạn 6 – Operation APIs

- [ ] List bookings.
- [ ] Filter bookings.
- [ ] Booking details.
- [ ] Update booking status.
- [ ] Booking status history.
- [ ] Suspicious flag nếu còn thời gian.

### Giai đoạn 7 – Testing

- [ ] Unit tests.
- [ ] Integration tests.
- [ ] Concurrency tests.
- [ ] Transaction rollback test.
- [ ] Postman collection.

### Giai đoạn 8 – Documentation

- [ ] README.
- [ ] CONTRIBUTING.
- [ ] Scope and assumptions.
- [ ] System design.
- [ ] Database design.
- [ ] Diagrams.
- [ ] Technical report.
- [ ] Final limitations.

---

## 18. Checklist trước khi nộp

### Source code

- [ ] Project build thành công.
- [ ] Không commit secret.
- [ ] Không commit file IDE không cần thiết.
- [ ] Code format thống nhất.
- [ ] Không còn TODO quan trọng.
- [ ] Không trả Entity trực tiếp qua API.
- [ ] Transaction boundary hợp lý.

### Local setup

- [ ] Clone project về máy sạch.
- [ ] Chạy bằng đúng lệnh trong README.
- [ ] Database tự khởi tạo.
- [ ] Migration chạy thành công.
- [ ] Seed data có sẵn.
- [ ] Swagger mở được.
- [ ] Health check trả `UP`.

### API

- [ ] Tất cả API trong Swagger chạy được.
- [ ] Postman dùng đúng `baseUrl` local.
- [ ] Postman environment được đính kèm.
- [ ] Idempotency test chạy đúng.
- [ ] Overselling test chạy đúng.
- [ ] Voucher limit chạy đúng.
- [ ] Error response thống nhất.

### Documentation

- [ ] Có System Design.
- [ ] Có Database Design.
- [ ] Có phân tích và giải thích trade-off.
- [ ] Có assumptions.
- [ ] Có implemented features.
- [ ] Có not implemented features.
- [ ] Có limitations.
- [ ] Có coding guideline.
- [ ] Có hướng dẫn thêm API mới.
- [ ] Có hướng dẫn chạy test.
- [ ] Có hướng dẫn chạy local.

### Final submission

- [ ] Gom tất cả vào một folder.
- [ ] Kiểm tra lại link repository hoặc Drive.
- [ ] Quyền truy cập link ở chế độ người nhận có thể xem.
- [ ] Không gửi nhầm branch.
- [ ] README nằm ở thư mục gốc.
- [ ] Báo cáo PDF mở được.
- [ ] Postman collection import được.
- [ ] Gửi bài đúng thời hạn đã thông báo.

---

## 19. Ưu tiên nếu không đủ thời gian

### Bắt buộc hoàn thành trước

1. Create booking.
2. Ngăn overselling.
3. Idempotency.
4. Voucher validation và usage limit.
5. Transaction rollback.
6. Get booking status.
7. Operation list và update booking status.
8. Swagger.
9. Postman.
10. README và scope document.

### Có thể để sau

1. Full voucher CRUD.
2. Authentication hoàn chỉnh.
3. Suspicious booking automation.
4. Booking expiration scheduler.
5. Notification.
6. Frontend.
7. Redis.
8. Message queue.
9. Cloud deployment.

---

## 20. Kết quả cuối cùng cần giao

- [ ] Source code backend.
- [ ] File cấu hình Docker.
- [ ] Database migrations và seed data.
- [ ] Swagger/OpenAPI.
- [ ] Postman collection và local environment.
- [ ] README hướng dẫn chạy.
- [ ] Coding guideline.
- [ ] Tài liệu System Design.
- [ ] Tài liệu Database Design.
- [ ] Tài liệu assumptions, scope và limitations.
- [ ] Các sơ đồ hệ thống.
- [ ] Automated tests.
- [ ] Báo cáo kỹ thuật PDF.
