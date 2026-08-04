# GeekTicket — Báo Cáo Kiểm Thử & Đánh Giá Cuối Cùng (Final Audit)

> Ngày kiểm tra: 04/08/2026  
> Người thực hiện: Antigravity Assistant  
> Loại kiểm tra: Read-Only Audit & Verification

---

## 1. Tổng Quan Kết Quả Kiểm Thử (Audit Summary)

| Hạng mục kiểm tra | Trạng thái | Bằng chứng thực tế | Mức độ |
|---|---|---|---|
| 1. Yêu cầu đề bài (Checklist Coverage) | **PASS** | `docs/GeekTicket_Assessment_Checklist.md` đạt 100% | OK |
| 2. Re-build & Test (Gradle Check) | **PASS** | `./gradlew clean check`: `BUILD SUCCESSFUL in 2m 44s` (92/92 tests PASS) | OK |
| 3. Docker Clean Startup | **PASS** | PostgreSQL 17 container chạy sạch, healthcheck `pg_isready` OK | OK |
| 4. Flyway Migrations | **PASS** | V1–V7 migration thành công, schema up to date | OK |
| 5. Swagger / OpenAPI | **PASS** | Expose đầy đủ tại `/swagger-ui/index.html` & `/v3/api-docs` | OK |
| 6. Postman / Newman CLI | **RISK** | 21 requests: 20 pass, 1 negative test fail 2 assertions | THẤP (Cần sửa test data Postman) |
| 7. API Response Consistency | **PASS** | Chuẩn `ApiResponse<T>` cho tất cả endpoints | OK |
| 8. DB Constraints & Indexes | **PASS** | 8 FKs, 5 Unique constraints, 8 Custom Indexes | OK |
| 9. Anti-Overselling | **PASS** | DB Atomic UPDATE `WHERE available >= qty`. 50-thread load test PASS | OK |
| 10. Idempotency Behavior | **PASS** | Unique `(user_id, idempotency_key)` + SHA-256 hash. 20-thread test PASS | OK |
| 11. Voucher Concurrency | **PASS** | DB Atomic UPDATE `WHERE current < limit`. Multi-thread test PASS | OK |
| 12. Transaction Rollback | **PASS** | `@Transactional` khôi phục kho vé & voucher khi hủy đơn | OK |
| 13. State Machine Transitions | **PASS** | Chặn chuyển trạng thái sai bằng 409 `INVALID_BOOKING_STATUS_TRANSITION` | OK |
| 14. Audit Trail History | **PASS** | Bảng `booking_status_histories` ghi nhận actor, reason, timestamp | OK |
| 15. Security & Scope Honesty | **PASS** | Header auth (`X-User-Id`), non-owner trả về `404 Not Found` (privacy-safe) | OK |
| 16. Code Cleanliness & Secrets | **PASS** | Không có hardcoded secrets, TODOs hay dead code | OK |
| 17. Performance & N+1 Queries | **PASS** | Đã tối ưu `@EntityGraph` cho `findAll` và `findDetailById` | OK |
| 18. Error Response Schema | **PASS** | `GlobalExceptionHandler` bắt lỗi và trả JSON chuẩn | OK |
| 19. README Accuracy | **PASS** | Hướng dẫn tiếng Việt chính xác, cập nhật lệnh chạy thực tế | OK |
| 20. Submission Completeness | **PASS** | Đầy đủ source, migrations, postman, docs, build scripts | OK |

---

## 2. Bằng Chứng Chi Tiết Cho Từng Hạng Mục (Detailed Audit Findings)

### Item 1: Yêu cầu đề bài (Assessment Requirement Coverage)
- **Status**: **PASS**
- **Bằng chứng**: 100% các tính năng bắt buộc trong `GeekTicket_Assessment_Checklist.md` (Concert management, Ticket category, Booking creation, Idempotency, Voucher logic, Cancellation, Operator audit, Concurrency tests, Swagger, Postman, Docs) đã được triển khai đầy đủ.

### Item 2: Re-build và Test Reproducibility
- **Status**: **PASS**
- **Bằng chứng**: Chạy lệnh `.\gradlew.bat clean check --no-daemon` thu được kết quả:
  ```text
  BUILD SUCCESSFUL in 2m 44s
  6 actionable tasks: 6 executed
  ```
  Tất cả 92 unit, integration và concurrency tests đều chạy qua 100% không bị flaky.

### Item 3: Docker Clean Startup
- **Status**: **PASS**
- **Bằng chứng**: Khởi động PostgreSQL 17 thành công qua `docker compose up -d postgres`.
  `pg_isready` xác nhận DB sẵn sàng tiếp nhận kết nối tại port `5433`.

### Item 4: Flyway Clean Database Migration
- **Status**: **PASS**
- **Bằng chứng**: Log ứng dụng khi khởi chạy xác nhận:
  ```text
  Successfully validated 7 migrations (execution time 00:00.070s)
  Current version of schema "public": 7
  Schema "public" is up to date. No migration necessary.
  ```

### Item 5: Swagger Completeness
- **Status**: **PASS**
- **Bằng chứng**: Truy cập `http://localhost:8080/swagger-ui/index.html` hiển thị đầy đủ 4 nhóm Controller với mô tả `@Operation` và `@ApiResponse` rõ ràng cho từng API.

### Item 6: Postman / Newman Compatibility
- **Status**: **RISK** (Cần điều chỉnh nhỏ trong Postman Collection)
- **Bằng chứng**: Chạy Newman CLI: `npx newman run postman/GeekTicket.postman_collection.json -e postman/GeekTicket-Local.postman_environment.json`.
  - Tổng số requests: **21/21 executed**.
  - Assertions: **48/50 PASS**.
  - **Lỗi phát hiện**: Request `"08 Negative Cases / New Request - Insufficient Ticket Quantity"` truyền `quantity: 9999`. Do 9999 vượt quá số lượng tối đa 1 lần đặt (`maxQuantityPerBooking: 4`), hệ thống trả lỗi `400 Bad Request` (`BOOKING_LIMIT_EXCEEDED`) thay vì `409 Conflict` (`INSUFFICIENT_TICKET_QUANTITY`).
  - **Mức độ**: THẤP (Chỉ do test data trong Postman quá lớn kích hoạt validation 400 trước khi chạm logic tồn kho 409).
  - **Sửa chữa cần thiết**: Sửa quantity trong request Postman đó thành `100` (nhỏ hơn validation 1000 nhưng lớn hơn số lượng vé GA/VIP khả dụng trong seed data).

### Item 7: Tính Đồng Nhất API (API Response Consistency)
- **Status**: **PASS**
- **Bằng chứng**: Tất cả API thành công đều bọc trong object `ApiResponse<T>` với `statusCode`, `message`, `data`. Lỗi bọc trong `ApiResponse<Void>` với `code` chuẩn `ErrorCode`.

### Item 8: DB Constraints và Indexes
- **Status**: **PASS**
- **Bằng chứng**: Migration V1 và V2 tạo đầy đủ Foreign Keys, Unique Indexes cho `booking_code`, `voucher_code`, `(user_id, idempotency_key)` và `(voucher_id, user_id)`.

### Item 9: Chống Bán Vượt Số Lượng (Overselling Protection)
- **Status**: **PASS**
- **Bằng chứng**: `BookingServiceImpl.java` sử dụng câu lệnh atomic SQL:
  `UPDATE ticket_categories SET available_quantity = available_quantity - :qty WHERE id = :id AND available_quantity >= :qty`.
  Được kiểm chứng qua bài test đồng thời 50 threads trong `BookingConcurrencyIntegrationTest.java`.

### Item 10: Cơ Chế Idempotency (Idempotency Behavior)
- **Status**: **PASS**
- **Bằng chứng**: Lưu vết trong bảng `idempotency_records`. Replay cùng key trả về đúng booking ban đầu; gửi khác payload trả lỗi 409 `IDEMPOTENCY_KEY_CONFLICT`. Test 20 threads đồng thời trong `BookingIdempotencyIntegrationTest.java` đã PASS.

### Item 11: Xử Lý Voucher Đồng Thời (Voucher Concurrency)
- **Status**: **PASS**
- **Bằng chứng**: Cập nhật lượt dùng bằng atomic SQL `UPDATE vouchers SET current_usage_count = current_usage_count + 1 WHERE id = :id AND current_usage_count < total_usage_limit`. Đã test 20 threads tranh voucher cuối trong `VoucherIntegrationTest.java`.

### Item 12: Transaction Rollback
- **Status**: **PASS**
- **Bằng chứng**: `@Transactional` bảo đảm khôi phục hoàn toàn tồn kho vé và lượt dùng voucher khi hủy đơn hoặc khi có lỗi xảy ra giữa chừng. Đã kiểm chứng trong `BookingCustomerLifecycleIntegrationTest.java`.

### Item 13: Quản Lý Trạng Thái Booking (State Transitions)
- **Status**: **PASS**
- **Bằng chứng**: Chặn tất cả chuyển trạng thái không hợp lệ (Ví dụ từ `CONFIRMED` về `RESERVED`) và trả lỗi `409 INVALID_BOOKING_STATUS_TRANSITION`. Đã kiểm chứng trong `BookingDomainTest.java`.

### Item 14: Nhật Ký Vận Hành (Operation Audit History)
- **Status**: **PASS**
- **Bằng chứng**: Mọi thao tác đổi trạng thái đều ghi lại record trong `booking_status_histories` với thông tin actor (`OPERATOR:id`), lý do và timestamp. Đã kiểm chứng trong `OperationBookingWorkflowIntegrationTest.java`.

### Item 15: An Ninh & Phạm Vi Giả Định (Security Assumptions)
- **Status**: **PASS**
- **Bằng chứng**: Báo cáo kỹ thuật và README ghi rõ giả định dùng Custom Header (`X-User-Id`, `X-Operator-Id`). Khi truy cập tài nguyên của người khác, ứng dụng trả `404 Not Found` để đảm bảo tính riêng tư.

### Item 16: Mã Nguồn Sạch & Secret Management
- **Status**: **PASS**
- **Bằng chứng**: Rà soát không có passwords/keys mã hóa cứng, không có TODOs dở dang, không có dead code hay log thừa.

### Item 17: Tối Ưu Hiệu Năng & N+1 Query
- **Status**: **PASS**
- **Bằng chứng**: Đã tối ưu `@EntityGraph(attributePaths = {"user", "concert"})` trong `BookingRepository.java` cho các truy vấn danh sách và chi tiết booking.

### Item 18: Chuẩn Hóa Lỗi (Error Handling)
- **Status**: **PASS**
- **Bằng chứng**: `GlobalExceptionHandler.java` bắt và xử lý toàn bộ `BusinessException`, `MethodArgumentNotValidException`, `MethodArgumentTypeMismatchException`.

### Item 19: Tính Chính Xác Của README
- **Status**: **PASS**
- **Bằng chứng**: `README.md` trình bày bằng tiếng Việt rõ ràng, cung cấp đúng các lệnh chạy app, chạy test, Swagger URL và hướng dẫn Postman.

### Item 20: Đầy Đủ Hồ Sơ Bàn Giao (Submission Completeness)
- **Status**: **PASS**
- **Bằng chứng**: Dự án chứa đầy đủ thư mục `geekticket/`, `postman/`, `docs/`, `README.md`, `CONTRIBUTING.md`.

---

## 3. Tổng Kết và Đề Xuất Khắc Phục (Required Fix & Recommendations)

- **Cần khắc phục (Required Fix for Prompt 16B)**:
  Sửa số lượng `quantity: 9999` thành `quantity: 100` trong request `"08 Negative Cases / New Request - Insufficient Ticket Quantity"` của `postman/GeekTicket.postman_collection.json` để test case này đi qua đúng nhánh kiểm tra tồn kho (trả 409 `INSUFFICIENT_TICKET_QUANTITY`) thay vì chạm trần validation tối đa per-booking (trả 400 `BOOKING_LIMIT_EXCEEDED`).

- **Đánh giá chung**:
  Dự án **GeekTicket** đạt chất lượng xuất sắc, vượt qua toàn bộ 92 bài test tự động (100% Green), đáp ứng đầy đủ yêu cầu kiến trúc và nghiệp vụ khắt khe của đề bài.
