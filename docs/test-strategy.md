# Test Strategy — GeekTicket

## Nguyên tắc test

- **Không dùng H2**: Tất cả test DB đều chạy trên PostgreSQL thật (Testcontainers)
- **Isolated**: Mỗi test class tạo container riêng, không phụ thuộc nhau
- **Deterministic**: Dùng `CountDownLatch` cho concurrency, không dùng `Thread.sleep`
- **Arrange/Act/Assert**: Cấu trúc rõ ràng

## Phân loại test

### Unit tests (Mockito, không cần DB)

| Test class | Số test | Test cái gì |
|---|---|---|
| `BookingDomainTest` | 16 | State machine, tính tiền, validate |
| `VoucherDomainTest` | 5 | Tính giảm giá %, cố định, trần max, window |
| `OperationConcertServiceImplTest` | 7 | Quy tắc publish concert |
| `BookingCodeGeneratorTest` | 3 | Format BK-YYYYMMDD-XXXXXX, uniqueness |
| `RequestHashGeneratorTest` | 3 | SHA-256 canonical hash |

### Integration tests (Testcontainers PostgreSQL)

| Test class | Số test | Test cái gì |
|---|---|---|
| `RepositoryIntegrationTest` | 10 | Flyway migration, DDL constraints |
| `ConcertControllerIntegrationTest` | 10 | API concert customer + operator |
| `BookingControllerIntegrationTest` | 11 | Tạo booking, multi-item, rollback, hết vé |
| `BookingIdempotencyIntegrationTest` | 8 | Replay cùng key, payload conflict, 20 thread |
| `VoucherIntegrationTest` | 11 | Áp voucher, hết lượt, min order, per-user |
| `BookingCustomerLifecycleIntegrationTest` | 7 | Xem booking, hủy, hoàn kho + voucher |
| `OperationBookingWorkflowIntegrationTest` | 6 | Operator lọc, xem, đổi status, audit |

### Concurrency tests (Testcontainers PostgreSQL)

| Test | Setup | Kết quả đúng |
|---|---|---|
| 50 user tranh 10 vé | 50 thread + CountDownLatch | Đúng 10 booking, 40 bị 409, kho = 0 |
| 20 request cùng key | 20 thread + CountDownLatch | Đúng 1 booking, trừ kho 1 lần |
| 20 user tranh voucher cuối | 20 thread + CountDownLatch | Đúng 1 voucher applied, 19 bị 409 |

## Kết quả thực tế

```
Chạy lần 1: .\gradlew.bat clean test --no-daemon
→ BUILD SUCCESSFUL — 92 tests, 0 failures

Chạy lần 2: .\gradlew.bat test --rerun-tasks --no-daemon
→ BUILD SUCCESSFUL — 92 tests, 0 failures

Flaky: 0
```

## Giới hạn test

- Concurrency test chạy trên Testcontainers (1 container), chưa test multi-node
- Chưa có performance/load test (JMeter, Gatling)
- Chưa có contract test cho API versioning
- Postman test là sequential, không test được race condition
