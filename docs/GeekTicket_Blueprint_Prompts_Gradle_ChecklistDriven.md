# GeekTicket – Implementation Blueprint & Prompt Playbook

> Tài liệu này là lộ trình thực hiện theo từng cổng kiểm tra (gate).  
> Không chuyển sang prompt tiếp theo nếu phần hiện tại chưa chạy và chưa đạt tiêu chí kiểm tra.

---


# 0A. Checklist là nguồn yêu cầu chính

Trước khi dùng các prompt bên dưới:

1. Đặt file checklist vào repository tại:

```text
docs/GeekTicket_Assessment_Checklist.md
```

2. Dùng bản Gradle đã chỉnh, không dùng bản còn `pom.xml` hoặc `./mvnw`.

3. Mỗi prompt phải:
   - đọc checklist trước khi sửa code;
   - liệt kê chính xác các mục checklist đang xử lý;
   - không thay đổi phạm vi hoặc assumptions trong checklist;
   - chỉ đánh dấu hoàn thành khi có bằng chứng;
   - ghi kết quả vào `docs/implementation-progress.md`.

## Quy tắc cập nhật tiến độ

Tạo bảng sau trong `docs/implementation-progress.md`:

```text
| Checklist item | Status | Evidence | Remaining issue |
|---|---|---|---|
| Ngăn overselling | PASS | TicketBookingConcurrencyTest | None |
| Idempotency | IN PROGRESS | Unique constraint added | Concurrent replay test missing |
```

Trạng thái chỉ được dùng:

```text
NOT STARTED
IN PROGRESS
PASS
BLOCKED
DEFERRED
```

Không được ghi `PASS` chỉ vì đã tạo class hoặc viết code. `PASS` cần có test, API response,
database constraint hoặc lệnh Gradle thực tế làm bằng chứng.

## Mapping prompt với checklist

| Prompt | Phần checklist chính |
|---|---|
| 1 | Mục tiêu, phạm vi, assumptions, kế hoạch 48 giờ, checklist trước khi nộp |
| 2 | Công nghệ, cấu hình local, Docker, Swagger, Actuator, error handling |
| 3 | Database, constraints, indexes, migrations, seed data |
| 4 | Concert, ticket category, publish concert, inventory |
| 5 | Booking state machine, validation, price calculation |
| 6 | Create booking, transaction, atomic inventory, overselling |
| 7 | Idempotency và duplicate booking |
| 8 | Voucher validation, usage limit, voucher concurrency |
| 9 | Xem booking, hủy booking, rollback/restore inventory |
| 10 | Operation APIs, filters, manual status update, status history |
| 11 | Unit, integration, concurrency và rollback tests |
| 12 | Swagger/OpenAPI |
| 13 | Postman collection và local environment |
| 14 | README, CONTRIBUTING, system/database design, assumptions/limitations |
| 15 | Báo cáo kỹ thuật và traceability |
| 16 | Final checklist audit và đóng gói |

---

# 0. Nguyên tắc làm bài

## Thứ tự ưu tiên

1. Project chạy được ở local.
2. Database schema đúng và có seed data.
3. Concert và ticket inventory hoạt động.
4. Create booking không bán vượt vé.
5. Idempotency ngăn request retry tạo booking trùng.
6. Voucher không vượt giới hạn.
7. Customer theo dõi/hủy booking.
8. Operation xem và cập nhật booking.
9. Automated tests, đặc biệt là concurrency test.
10. Swagger và Postman.
11. README, coding guideline, design documents và báo cáo.
12. Final audit trên môi trường sạch.

## Phạm vi kỹ thuật đã chốt

- Java 21.
- Spring Boot.
- Gradle.
- Spring Web.
- Spring Data JPA.
- Spring Validation.
- PostgreSQL.
- Flyway.
- Swagger/OpenAPI.
- Docker Compose.
- JUnit 5, Mockito, Testcontainers.
- Không làm frontend.
- Không tích hợp payment gateway thật.
- Không dùng Redis, Kafka, RabbitMQ hoặc microservices.
- Authentication được đơn giản hóa bằng header.
- Kiến trúc Layered Architecture, package by layer.
- Sử dụng Gradle Wrapper (`./gradlew` trên macOS/Linux, `gradlew.bat` trên Windows).
- Các lệnh trong tài liệu mặc định dùng Gradle Groovy DSL (`build.gradle`); nếu project dùng Kotlin DSL thì giữ `build.gradle.kts`, nhưng lệnh chạy không đổi.

## Cấu trúc package bắt buộc giữ

```text
src/main/java/com/quyen/geekticket/
├── config/
├── controller/
├── domain/
│   ├── entity/
│   ├── dto/
│   ├── request/
│   └── response/
├── repository/
├── service/
│   └── impl/
└── util/
    ├── annotation/
    ├── constant/
    ├── error/
    ├── mapper/
    └── generator/
```

Không tự ý chuyển project sang package by feature, Clean Architecture hoặc Hexagonal Architecture.

---

# 1. Prompt dùng chung trước mọi prompt

Dán đoạn này ở đầu mỗi phiên làm việc với AI coding assistant:

```text
You are a senior Java/Spring Boot backend engineer working on GeekTicket, a concert ticket booking technical assessment.

Mandatory constraints:
- Java 21, Gradle Wrapper, Spring Boot, PostgreSQL, Flyway, Spring Data JPA, Validation, Swagger/OpenAPI, Docker Compose, JUnit 5, Mockito, Testcontainers.
- Keep the existing Gradle DSL consistently: `build.gradle` for Groovy DSL or `build.gradle.kts` for Kotlin DSL. Do not convert between DSLs without an explicit reason.
- Keep the existing package-by-layer structure under com.quyen.geekticket:
  config, controller, domain/entity, domain/dto, domain/request, domain/response,
  repository, service, service/impl, util/annotation, util/constant,
  util/error, util/mapper, util/generator.
- Do not reorganize the project into package-by-feature.
- Do not add frontend, payment gateway, Redis, Kafka, RabbitMQ, WebSocket, OAuth2, or microservices.
- PostgreSQL is the source of truth.
- Never return JPA entities directly from controllers.
- Controllers contain no business logic.
- Transaction boundaries belong in service methods.
- Money uses BigDecimal and database DECIMAL/NUMERIC.
- Time is stored as UTC.
- Database schema changes must be implemented through Flyway.
- Do not silently change existing endpoint contracts or assumptions.
- Before changing code, inspect the current files and report conflicts or missing prerequisites.
- After coding, list every changed file, explain the purpose, provide exact commands to verify it, and state any remaining limitation.
- Do not claim tests pass unless you actually run them.
```

---

# PROMPT 1 — Audit repository và khóa phạm vi

## Mục tiêu

- Biết project hiện tại có gì và thiếu gì.
- Không code vội khi dependency, package hoặc configuration còn sai.
- Tạo một kế hoạch triển khai bám sát 48 giờ.
- Phát hiện sớm những phần dư như WebSocket, Redis, payment hoặc event.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Audit the current repository before implementing features.

Tasks:
1. Inspect `build.gradle` or `build.gradle.kts`, `settings.gradle` or `settings.gradle.kts`, Gradle Wrapper files, application configuration, package structure, Docker files,
   Flyway migrations, existing entities, repositories, services, controllers, and tests.
2. Compare the repository against the following Gradle-based target:
   - Java 21
   - Spring Boot REST API
   - PostgreSQL
   - Flyway
   - Swagger/OpenAPI
   - Actuator
   - Docker Compose
   - JUnit 5, Mockito, Testcontainers
3. Identify:
   - missing dependencies;
   - unnecessary dependencies;
   - package structure violations;
   - configuration risks;
   - compilation errors;
   - duplicated or premature abstractions;
   - features outside scope.
4. Produce docs/implementation-plan.md containing:
   - current state;
   - target state;
   - ordered implementation phases;
   - dependencies between phases;
   - risk list;
   - definition of done for each phase.
5. Do not implement business features in this step.
6. Run the existing build and tests, and report exact results.

Expected output:
- Repository audit.
- Changed files, ideally only docs/implementation-plan.md.
- Exact commands executed.
- A blocking-issues list.
```

## Prompt này đạt được gì?

- Một bản đồ dự án trước khi sửa.
- Danh sách dependency cần giữ/bỏ.
- Thứ tự triển khai phù hợp.
- Tránh việc AI tự tạo nhiều package và công nghệ không cần thiết.

## Cách kiểm tra

```bash
./gradlew clean test
git status
```

Mở `docs/implementation-plan.md` và kiểm tra có:

- Current state.
- Target state.
- Ordered phases.
- Risks.
- Definition of done.

## Gate để qua Prompt 2

- Project ít nhất phải build được bằng Gradle Wrapper.
- Biết rõ lỗi build hiện tại.
- Không có thay đổi business code ngoài ý muốn.
- Kế hoạch không có frontend, Redis, Kafka hoặc payment gateway.

## Dấu hiệu cần yêu cầu AI sửa lại

- AI bắt đầu tạo entity/controller trong bước audit.
- AI đề xuất microservices.
- AI đổi package structure.
- AI tuyên bố test pass nhưng không đưa log/lệnh đã chạy.

---

# PROMPT 2 — Bootstrap project, Docker, Swagger và common error

## Mục tiêu

Tạo nền tảng chạy được trước khi làm nghiệp vụ.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Implement the GeekTicket technical foundation only.

Required work:
1. Ensure `build.gradle` (or `build.gradle.kts` if the project uses Kotlin DSL) contains only the required plugins and dependencies:
   Spring Web, Spring Data JPA, Validation, PostgreSQL Driver, Flyway,
   Lombok if already chosen, Actuator, springdoc-openapi,
   Spring Boot Test, Testcontainers JUnit, and Testcontainers PostgreSQL.
2. Configure:
   - application.yml;
   - application-local.yml;
   - environment-based PostgreSQL connection;
   - JPA ddl-auto=validate;
   - Flyway enabled;
   - UTC/Jackson time configuration;
   - Actuator health endpoint;
   - Swagger/OpenAPI metadata.
3. Add:
   - Dockerfile;
   - docker-compose.yml;
   - .env.example;
   - .dockerignore if missing.
4. Create common API/error infrastructure:
   - ApiResponse<T> if the project uses response wrapping;
   - ErrorResponse;
   - ErrorCode;
   - BusinessException;
   - ResourceNotFoundException;
   - GlobalExceptionHandler.
5. Add a minimal application context test.
6. Do not add concert, booking, voucher, or operation business APIs yet.
7. Run:
   - `./gradlew clean test`;
   - docker compose config;
   - application startup with PostgreSQL;
   - actuator health check.

Acceptance criteria:
- `./gradlew clean test` succeeds.
- `docker compose up --build` starts PostgreSQL and the application.
- GET /actuator/health returns UP.
- Swagger UI is accessible.
- No JPA schema auto-generation is used.
- Error responses have a consistent contract.
```

## Prompt này đạt được gì?

- Project chạy bằng một lệnh.
- Có database local.
- Có Swagger và health check.
- Có error response chuẩn để các API sau không phải sửa lại.

## File mong đợi

```text
build.gradle
settings.gradle
gradlew
gradlew.bat
gradle/wrapper/
src/main/resources/application.yml
src/main/resources/application-local.yml
Dockerfile
docker-compose.yml
.env.example
src/main/java/com/quyen/geekticket/config/OpenApiConfig.java
src/main/java/com/quyen/geekticket/util/error/*
src/main/java/com/quyen/geekticket/domain/dto/ApiResponse.java
src/test/java/.../GeekTicketApplicationTests.java
```

## Cách kiểm tra

```bash
./gradlew clean test
docker compose config
docker compose up --build
```

Mở:

```text
http://localhost:8080/actuator/health
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

Kỳ vọng health:

```json
{
  "status": "UP"
}
```

## Kiểm tra lỗi validation/error contract

Tạm thời chưa có API nghiệp vụ, nhưng phải review class:

- `ErrorResponse` có `timestamp`, `status`, `code`, `message`, `path`.
- `GlobalExceptionHandler` xử lý:
  - validation;
  - business exception;
  - not found;
  - unexpected exception.
- Unexpected exception không trả stack trace cho client.

## Gate để qua Prompt 3

- `docker compose up --build` chạy thành công.
- Swagger mở được.
- Gradle test pass.
- `ddl-auto` không phải `create`, `create-drop` hoặc `update`.

---

# PROMPT 3 — Thiết kế database, Flyway, entity và repository

## Mục tiêu

Xây schema hoàn chỉnh trước khi tạo API.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Implement the database schema, JPA entities, enums, and repositories for GeekTicket.

Business assumptions:
- A booking belongs to exactly one concert.
- A booking may contain one or more ticket categories from that concert.
- Inventory is tracked by ticket category, not by seat.
- A booking can apply at most one voucher.
- One voucher can be limited globally and per user.
- Payment integration is out of scope.
- Booking statuses: RESERVED, CONFIRMED, CANCELLED, EXPIRED, FAILED.
- Concert statuses: DRAFT, PUBLISHED, CANCELLED, COMPLETED.
- Voucher statuses: ACTIVE, INACTIVE, EXPIRED.
- Discount types: PERCENTAGE, FIXED_AMOUNT.
- Use Long primary keys unless the repository already consistently uses UUID.

Create Flyway migrations:
- V1__create_schema.sql
- V2__create_indexes.sql
- V3__seed_data.sql

Required tables:
- users
- concerts
- ticket_categories
- bookings
- booking_items
- vouchers
- voucher_redemptions
- idempotency_records
- booking_status_histories

Required constraints:
- available_quantity >= 0
- total_quantity >= 0
- price >= 0
- booking item quantity > 0
- booking_code unique
- voucher code unique
- (user_id, idempotency_key) unique
- voucher redemption rules consistent with one-use-per-user assumption
- appropriate foreign keys
- timestamps and audit columns

Required indexes:
- concerts(status, sale_start_time)
- ticket_categories(concert_id)
- bookings(user_id, created_at)
- bookings(status, created_at)
- bookings(booking_code)
- vouchers(code)
- voucher_redemptions(voucher_id, user_id)
- idempotency_records(user_id, idempotency_key)

Implement JPA entities under domain/entity and repositories under repository.
Do not expose entities through controllers.
Do not create APIs yet.

Important:
- Map money to BigDecimal.
- Use EnumType.STRING.
- Avoid Lombok @Data on JPA entities.
- Define equals/hashCode safely.
- Use optimistic version only if there is a justified use; do not rely on it as the only overselling protection.
- Ensure Flyway schema and JPA mappings are consistent.
- Seed at least:
  customer user,
  operator user,
  one published concert,
  VIP and STANDARD categories,
  one active voucher.

Add repository-level integration tests using PostgreSQL Testcontainers.
Run migrations and tests.
```

## Prompt này đạt được gì?

- Database có đủ dữ liệu cho toàn bộ workflow.
- Entity khớp schema.
- Có seed data để test Swagger/Postman ngay.
- Các unique/check/index quan trọng tồn tại ở database, không chỉ ở Java.

## Cách kiểm tra

```bash
./gradlew clean test
docker compose down -v
docker compose up --build
docker compose exec postgres psql -U <user> -d geekticket
```

Trong PostgreSQL:

```sql
\dt
SELECT * FROM concerts;
SELECT * FROM ticket_categories;
SELECT * FROM vouchers;
SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'public';
```

Kiểm tra migration:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## Review thủ công entity

- Không dùng `double` cho tiền.
- Enum dùng `STRING`.
- Không dùng `@Data`.
- Không có vòng lặp JSON do quan hệ hai chiều.
- Không có controller/service trong prompt này.
- `booking_items.unit_price` là snapshot giá.

## Gate để qua Prompt 4

- Xóa volume database rồi chạy lại vẫn thành công.
- Flyway chạy đủ V1–V3.
- Seed data tồn tại.
- Repository integration test chạy bằng PostgreSQL Testcontainers, không phải H2.

---

# PROMPT 4 — Concert và ticket inventory APIs

## Mục tiêu

Hoàn thành luồng đơn giản trước khi vào booking phức tạp.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Implement concert and ticket category workflows.

Customer APIs:
- GET /api/v1/concerts
  Return only PUBLISHED concerts, paginated.
- GET /api/v1/concerts/{concertId}
  Return concert detail and ticket categories.

Operation APIs:
- POST /api/v1/operations/concerts
- POST /api/v1/operations/concerts/{concertId}/ticket-categories
- PATCH /api/v1/operations/concerts/{concertId}/publish

Identity:
- Operation endpoints receive X-Operator-Id.
- Authentication is intentionally simplified; validate that the seeded user exists
  and has OPERATOR or ADMIN role if roles exist in the schema.

Business rules:
- Concert starts after sale start time.
- Sale end time is after sale start time.
- A concert can be published only when it has at least one ticket category.
- totalQuantity and availableQuantity are initialized consistently.
- A published concert cannot be silently edited in ways that invalidate sold inventory.
- Customer list must not expose DRAFT concerts.
- Use request/response DTOs and mappers.
- Controllers contain no business logic.

Add:
- service interfaces and implementations;
- validation annotations;
- business exceptions/error codes;
- unit tests for business validation;
- integration tests for all endpoints.

Document the endpoints in Swagger.
```

## Prompt này đạt được gì?

- Customer xem concert và hạng vé.
- Operation tạo/publish concert.
- Có inventory nền để booking sử dụng.
- Kiểm tra được layered architecture đã dùng đúng.

## Cách test bằng Postman tạm thời

### 1. List concerts

```http
GET {{baseUrl}}/api/v1/concerts?page=0&size=10
```

Kỳ vọng:

- `200 OK`.
- Chỉ có concert `PUBLISHED`.
- Có `VIP`, `STANDARD`.

### 2. Get concert detail

```http
GET {{baseUrl}}/api/v1/concerts/{{concertId}}
```

Kỳ vọng:

- `200 OK`.
- Có hạng vé, giá, available quantity.

### 3. Create concert

```http
POST {{baseUrl}}/api/v1/operations/concerts
X-Operator-Id: {{operatorId}}
Content-Type: application/json
```

```json
{
  "name": "Geek Music Festival 2026",
  "description": "Autumn concert",
  "venue": "Ho Chi Minh City",
  "saleStartTime": "2026-08-04T01:00:00Z",
  "saleEndTime": "2026-08-10T01:00:00Z",
  "startTime": "2026-08-20T12:00:00Z"
}
```

Kỳ vọng `201 Created`, status `DRAFT`.

### 4. Publish without ticket category

Kỳ vọng `409` hoặc `422` với business error rõ ràng.

### 5. Add category rồi publish

```json
{
  "name": "VIP",
  "price": 2000000,
  "totalQuantity": 100,
  "maxQuantityPerBooking": 4
}
```

Kỳ vọng publish thành công và customer list thấy concert.

## Automated test bắt buộc

- Draft concert không xuất hiện trong customer list.
- Publish không có category bị từ chối.
- Invalid time range bị từ chối.
- Negative price/quantity bị validation error.

## Gate để qua Prompt 5

- 3 API customer/operation chính chạy.
- Entity không được trả trực tiếp.
- Swagger mô tả request/response.
- Tests pass.

---

# PROMPT 5 — Booking domain, state machine và price calculation

## Mục tiêu

Chốt business rule trước khi viết create booking transaction.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Implement the booking domain behavior without completing voucher or idempotency yet.

Required:
1. Booking status model:
   RESERVED -> CONFIRMED
   RESERVED -> CANCELLED
   RESERVED -> EXPIRED
   RESERVED -> FAILED
   CONFIRMED -> CANCELLED only when explicitly allowed for operator workflow
   CANCELLED, EXPIRED, and FAILED cannot return to active states.
2. Implement business methods instead of unrestricted status setters:
   confirm(...)
   cancel(...)
   expire(...)
   markFailed(...)
3. Implement BookingCodeGenerator.
4. Implement price calculation using BigDecimal:
   subtotal = sum(unitPrice * quantity)
   discount defaults to zero
   total = subtotal - discount
   total cannot be negative
5. Ensure BookingItem stores unitPrice as a snapshot.
6. Add booking request/response DTOs and mapper skeletons needed for later prompts.
7. Add booking status history creation support.
8. Add unit tests for:
   - every allowed status transition;
   - every forbidden transition;
   - multi-item subtotal;
   - total not below zero;
   - booking code format and uniqueness expectations.

Do not implement the final POST /bookings flow yet.
Do not add voucher or idempotency logic in this step.
```

## Prompt này đạt được gì?

- State machine không bị viết tùy tiện trong controller.
- Có unit test business rule.
- Logic tiền tách khỏi transaction phức tạp.
- Các lỗi trạng thái được phát hiện trước.

## Cách kiểm tra

```bash
./gradlew test --tests '*Booking*Test'
```

Review code:

- Không có public `setStatus()` được dùng tùy ý.
- Các hàm transition kiểm tra trạng thái hiện tại.
- `BigDecimal.compareTo()` được dùng đúng.
- Không dùng `double`.
- `BookingItem.unitPrice` lấy từ category lúc booking tạo, không đọc động khi response.

## Gate để qua Prompt 6

- Tất cả transition tests pass.
- Mọi transition không hợp lệ trả `INVALID_BOOKING_STATUS_TRANSITION`.
- Price calculation có unit test.

---

# PROMPT 6 — Core Create Booking và chống overselling

## Mục tiêu

Hoàn thành phần quan trọng nhất: nhiều người tranh cùng vé nhưng không được bán vượt.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Implement the core customer booking creation flow with transactional ticket inventory protection.
Do not add voucher or idempotency yet; those are the next phases.

Endpoint:
POST /api/v1/bookings
Header:
X-User-Id: <user id>

Request:
{
  "concertId": 1,
  "items": [
    {
      "ticketCategoryId": 10,
      "quantity": 2
    }
  ]
}

Required business validation:
- user exists;
- concert exists and is PUBLISHED;
- current UTC time is within sale period;
- every ticket category belongs to the selected concert;
- quantity is positive;
- quantity does not exceed maxQuantityPerBooking;
- duplicate ticketCategoryId values in one request are rejected or normalized consistently;
- all items must be validated before persistence.

Overselling protection:
- Use a PostgreSQL atomic conditional update in TicketCategoryRepository:
  UPDATE ticket_categories
  SET available_quantity = available_quantity - :quantity
  WHERE id = :ticketCategoryId
    AND available_quantity >= :quantity
- If affected rows is zero, throw INSUFFICIENT_TICKET_QUANTITY.
- Do not use read-then-write as the inventory protection.
- The entire operation must be one database transaction.
- Create booking, booking items, status history, and inventory decrements in that transaction.
- If any item fails, all previous decrements and inserts must roll back.

Response:
- booking id;
- booking code;
- status;
- items;
- subtotal;
- discountAmount = 0;
- totalAmount;
- createdAt;
- expiresAt if implemented.

Tests:
- successful single-item booking;
- successful multi-item booking;
- ticket category from another concert rejected;
- max quantity exceeded;
- insufficient quantity;
- rollback when the second item fails;
- concurrent booking test using PostgreSQL Testcontainers.

The concurrency test must:
- seed exactly 10 available tickets;
- start 50 concurrent attempts at the same time using CountDownLatch or CyclicBarrier;
- assert exactly 10 tickets are sold;
- assert final available quantity is 0;
- assert quantity is never negative;
- assert only successful booking rows exist.

Run all tests and report exact results.
```

## Prompt này đạt được gì?

- Core business value hoàn thành.
- Không oversell.
- Multi-item transaction rollback đúng.
- Có bằng chứng automated concurrency test.

## Cách test Postman

```http
POST {{baseUrl}}/api/v1/bookings
X-User-Id: {{customerId}}
Content-Type: application/json
```

```json
{
  "concertId": {{concertId}},
  "items": [
    {
      "ticketCategoryId": {{vipCategoryId}},
      "quantity": 2
    }
  ]
}
```

Kỳ vọng:

- `201 Created`.
- `status = RESERVED`.
- `subtotal = unitPrice * 2`.
- Inventory giảm đúng 2.

### Test sold out

Gửi quantity lớn hơn available quantity.

Kỳ vọng:

```text
409 Conflict
code = INSUFFICIENT_TICKET_QUANTITY
```

### Kiểm tra database

```sql
SELECT available_quantity
FROM ticket_categories
WHERE id = <vipCategoryId>;

SELECT b.id, b.booking_code, b.status, bi.quantity, bi.unit_price
FROM bookings b
JOIN booking_items bi ON bi.booking_id = b.id
ORDER BY b.id DESC;
```

## Cách chạy concurrency test

```bash
./gradlew test --tests '*Concurrency*Test'
```

Postman Runner không chứng minh concurrency vì chạy tuần tự. Phần concurrency phải kiểm bằng automated integration test.

## Gate để qua Prompt 7

- Concurrency test pass ổn định nhiều lần.
- Không quantity âm.
- Multi-item rollback test pass.
- API create booking chạy thực tế.

---

# PROMPT 7 — Idempotency cho request retry

## Mục tiêu

Cùng một request bị gửi lại không tạo booking thứ hai và không trừ vé lần thứ hai.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Add production-style idempotency to POST /api/v1/bookings.

Header:
Idempotency-Key: required UUID-like string
Identity scope:
(user_id, idempotency_key)

Required behavior:
1. Same user + same key + same normalized request body:
   return the original booking and do not decrement inventory again.
2. Same user + same key + different normalized request body:
   return 409 IDEMPOTENCY_KEY_CONFLICT.
3. Different users may use the same key independently.
4. Missing or blank key:
   return validation/business error IDEMPOTENCY_KEY_REQUIRED.
5. Concurrent requests using the same key:
   create exactly one booking.

Implementation requirements:
- Generate a deterministic request hash from a canonical representation of the business request.
- Do not hash irrelevant fields or raw JSON formatting.
- Use the existing unique constraint on (user_id, idempotency_key).
- Avoid catching a unique-constraint exception inside a transaction in a way that marks the whole transaction rollback-only.
- Prefer a PostgreSQL INSERT ... ON CONFLICT DO NOTHING claim operation.
- The idempotency record and booking must commit atomically.
- If booking creation fails and the transaction rolls back, a valid retry must be able to try again.
- Store request_hash, booking_id, and timestamps.
- Replayed response must map from the persisted booking.

Tests:
- sequential replay returns same booking;
- inventory decremented once;
- same key different payload returns 409;
- same key different user works independently;
- 20 concurrent requests with same key create exactly one booking;
- failed first transaction does not permanently poison the key.

Update Swagger to document Idempotency-Key.
```

## Prompt này đạt được gì?

- Retry an toàn.
- Có idempotency ở database, không chỉ cache trong memory.
- Concurrency cùng key được kiểm thử.

## Test Postman

### Request A — Create Booking New

Pre-request Script:

```javascript
pm.environment.set("idempotencyKey", pm.variables.replaceIn("{{$guid}}"));
```

Headers:

```text
X-User-Id: {{customerId}}
Idempotency-Key: {{idempotencyKey}}
```

Tests:

```javascript
pm.test("Status is 201 or 200", function () {
  pm.expect([200, 201]).to.include(pm.response.code);
});

const body = pm.response.json();
const data = body.data ?? body;

pm.environment.set("bookingId", data.id);
pm.environment.set("bookingCode", data.bookingCode);
pm.environment.set("firstBookingCode", data.bookingCode);
```

### Request B — Retry Same Booking

Không tạo key mới. Dùng nguyên:

```text
Idempotency-Key: {{idempotencyKey}}
```

Tests:

```javascript
pm.test("Replay returns original booking", function () {
  pm.expect(pm.response.code).to.be.oneOf([200, 201]);
  const body = pm.response.json();
  const data = body.data ?? body;
  pm.expect(data.bookingCode).to.eql(pm.environment.get("firstBookingCode"));
});
```

### Request C — Same key, different body

Đổi quantity từ 1 sang 2 nhưng giữ nguyên key.

Kỳ vọng:

```text
409
IDEMPOTENCY_KEY_CONFLICT
```

## Kiểm tra database

```sql
SELECT user_id, idempotency_key, request_hash, booking_id
FROM idempotency_records
WHERE idempotency_key = '<key>';

SELECT COUNT(*)
FROM bookings
WHERE id = <bookingId>;
```

## Gate để qua Prompt 8

- Retry trả cùng booking code.
- Inventory chỉ giảm một lần.
- Concurrent same-key test pass.
- Different payload bị 409.

---

# PROMPT 8 — Voucher validation, discount và concurrency

## Mục tiêu

Voucher không được dùng quá tổng số lượt và không bị một user lạm dụng.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Integrate vouchers into POST /api/v1/bookings.

Request adds optional:
"voucherCode": "FLASH20"

Assumptions:
- One booking can apply at most one voucher.
- A user can use the same voucher once.
- Voucher applies to one concert when concert_id is present.
- Voucher usage is reserved/consumed when the booking is successfully created.
- Clearly document whether cancellation restores voucher usage.
  Prefer implementing restore on valid cancellation if it can be done atomically;
  otherwise document that cancellation does not restore it.

Validation:
- voucher exists;
- status ACTIVE;
- current UTC time is between validFrom and validUntil;
- voucher applies to the selected concert;
- subtotal meets minimumOrderAmount;
- global usage is below totalUsageLimit;
- user has not used the voucher before.

Concurrency protection:
- Use an atomic update:
  UPDATE vouchers
  SET used_count = used_count + 1
  WHERE id = :voucherId
    AND used_count < total_usage_limit
- If affected rows is zero, return VOUCHER_USAGE_LIMIT_REACHED.
- Create voucher_redemption in the same booking transaction.
- Enforce per-user usage with a database constraint/consistent transaction design.
- If voucher fails, inventory decrement and booking creation must roll back.
- Calculate:
  percentage discount with optional maxDiscountAmount;
  fixed discount;
  totalAmount cannot be negative.

Tests:
- active valid voucher;
- expired voucher;
- inactive voucher;
- wrong concert;
- minimum amount not met;
- percentage max discount;
- fixed discount larger than subtotal;
- user reuses voucher;
- voucher global limit;
- concurrent last-voucher test;
- voucher failure rolls back inventory;
- idempotent replay does not increment voucher usage again.

Run all tests.
```

## Prompt này đạt được gì?

- Voucher đúng business rule.
- Không vượt global limit.
- Không user reuse.
- Voucher và inventory cùng transaction.

## Test Postman

Request:

```json
{
  "concertId": {{concertId}},
  "items": [
    {
      "ticketCategoryId": {{vipCategoryId}},
      "quantity": 1
    }
  ],
  "voucherCode": "{{voucherCode}}"
}
```

Kỳ vọng:

- `subtotal > totalAmount`.
- `discountAmount` đúng.
- `voucherCode` xuất hiện trong response nếu contract có.

### Dùng lại voucher bằng key mới

Tạo idempotency key mới nhưng giữ user và voucher.

Kỳ vọng:

```text
409
VOUCHER_ALREADY_USED
```

### Voucher hết lượt

Kỳ vọng:

```text
409
VOUCHER_USAGE_LIMIT_REACHED
```

## Kiểm tra database

```sql
SELECT code, total_usage_limit, used_count
FROM vouchers
WHERE code = 'FLASH20';

SELECT voucher_id, user_id, booking_id, status
FROM voucher_redemptions
ORDER BY id DESC;
```

## Gate để qua Prompt 9

- Voucher tests pass.
- Voucher lỗi không làm mất vé.
- Replay cùng idempotency key không tăng `used_count`.

---

# PROMPT 9 — Customer booking query và cancel

## Mục tiêu

Customer theo dõi trạng thái và hủy booking hợp lệ.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Implement customer booking retrieval and cancellation.

Endpoints:
- GET /api/v1/bookings/{bookingCode}
  Header: X-User-Id
- POST /api/v1/bookings/{bookingId}/cancel
  Header: X-User-Id
  Body:
  {
    "reason": "Cannot attend"
  }

Rules:
- Customer can access only their own booking.
- Return 404 or a privacy-safe equivalent for another user's booking.
- Customer can cancel only RESERVED bookings.
- Cancellation must be idempotent or explicitly return a stable business response.
- On successful cancellation:
  - transition status to CANCELLED;
  - record status history;
  - restore each ticket category inventory atomically;
  - handle voucher redemption according to the documented assumption;
  - execute all changes in one transaction.
- Repeated cancellation must not restore inventory twice.
- CONFIRMED, EXPIRED, FAILED, and CANCELLED behavior must be explicitly tested.

Tests:
- owner retrieves booking;
- other user cannot retrieve it;
- reserved booking cancellation;
- inventory restored exactly once;
- repeated cancel;
- invalid state cancellation;
- status history written;
- voucher restoration behavior if implemented.
```

## Prompt này đạt được gì?

- Hoàn thành customer booking lifecycle tối thiểu.
- Hủy không cộng vé hai lần.
- Có audit history.

## Test Postman

### Get booking

```http
GET {{baseUrl}}/api/v1/bookings/{{bookingCode}}
X-User-Id: {{customerId}}
```

### Cancel

```http
POST {{baseUrl}}/api/v1/bookings/{{bookingId}}/cancel
X-User-Id: {{customerId}}
Content-Type: application/json
```

```json
{
  "reason": "Cannot attend"
}
```

Tests:

```javascript
pm.test("Booking is cancelled", function () {
  pm.expect(pm.response.code).to.be.oneOf([200, 204]);
  if (pm.response.code !== 204) {
    const body = pm.response.json();
    const data = body.data ?? body;
    pm.expect(data.status).to.eql("CANCELLED");
  }
});
```

Gọi lại lần hai và kiểm tra inventory không tăng thêm.

## Gate để qua Prompt 10

- Customer chỉ xem booking của mình.
- Cancel đúng state.
- Inventory restore đúng một lần.
- Status history tồn tại.

---

# PROMPT 10 — Operation booking workflow

## Mục tiêu

Hoàn thành backend cho Operation Dashboard mà không cần frontend.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Implement internal operation booking workflows.

Identity:
- X-Operator-Id header.
- Validate operator/admin identity using seeded data or the simplified role model.

Endpoints:
1. GET /api/v1/operations/bookings
   Filters:
   - status
   - concertId
   - userId
   - suspicious
   - createdFrom
   - createdTo
   Pagination and sorting required.
2. GET /api/v1/operations/bookings/{bookingId}
3. PATCH /api/v1/operations/bookings/{bookingId}/status
   Body:
   {
     "status": "CONFIRMED",
     "reason": "Payment verified manually"
   }
4. Optional if time allows:
   PATCH /api/v1/operations/bookings/{bookingId}/suspicious
   {
     "suspicious": true,
     "reason": "Repeated voucher attempts"
   }

Rules:
- Status transition must use the existing booking domain rules.
- Manual status changes require a nonblank reason.
- Save old status, new status, actor, reason, and timestamp in booking_status_histories.
- Invalid transitions return 409.
- Operation list returns DTOs and is paginated.
- Avoid N+1 query problems in list/detail endpoints.
- Do not implement automatic fraud detection.

Tests:
- filter by status;
- filter by concert;
- pagination;
- operator access validation;
- valid manual confirmation;
- invalid CANCELLED -> CONFIRMED;
- history contains correct actor and reason;
- suspicious flag if implemented.
```

## Prompt này đạt được gì?

- Có API đủ để dashboard tương lai sử dụng.
- Manual updates có audit.
- Không cần làm frontend.

## Test Postman

### List

```http
GET {{baseUrl}}/api/v1/operations/bookings?status=RESERVED&page=0&size=10
X-Operator-Id: {{operatorId}}
```

### Confirm booking

```http
PATCH {{baseUrl}}/api/v1/operations/bookings/{{bookingId}}/status
X-Operator-Id: {{operatorId}}
Content-Type: application/json
```

```json
{
  "status": "CONFIRMED",
  "reason": "Payment verified manually"
}
```

Kỳ vọng:

- `200 OK`.
- Status `CONFIRMED`.
- History có actor/reason.

### Invalid transition

Đưa booking về `CANCELLED`, sau đó thử chuyển `CONFIRMED`.

Kỳ vọng:

```text
409
INVALID_BOOKING_STATUS_TRANSITION
```

## Gate để qua Prompt 11

- List/filter/pagination chạy.
- Status history đúng.
- Invalid transition bị chặn.
- Không có N+1 nghiêm trọng trong endpoint list.

---

# PROMPT 11 — Hoàn thiện automated test suite

## Mục tiêu

Biến các tuyên bố thiết kế thành bằng chứng chạy được.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Review and complete the automated test suite for GeekTicket.

Testing rules:
- Use JUnit 5 and Mockito for unit tests.
- Use PostgreSQL Testcontainers for repository, transaction, and concurrency tests.
- Do not use H2 to prove locking, atomic updates, idempotency, or voucher concurrency.
- Tests must be deterministic and isolated.
- Do not depend on the developer's local database.

Required unit tests:
- concert publish rules;
- booking state transitions;
- subtotal/discount/total calculation;
- voucher rules;
- booking code generator;
- request hash canonicalization.

Required integration tests:
- Flyway migrations;
- create booking;
- multi-item rollback;
- insufficient inventory;
- idempotent replay;
- idempotency conflict;
- voucher redemption;
- cancellation inventory restore;
- operation status update and history;
- API validation/error response.

Required concurrency tests:
1. 50 users compete for 10 tickets:
   exactly 10 tickets sold, inventory 0, never negative.
2. 20 requests use the same idempotency key:
   exactly one booking created and one inventory decrement.
3. Two users compete for the last voucher usage:
   exactly one voucher application succeeds.

Quality requirements:
- Name tests using behavior-oriented names.
- Arrange/Act/Assert structure.
- No arbitrary Thread.sleep as the main synchronization strategy.
- Use CountDownLatch, CyclicBarrier, or equivalent.
- Add a test summary to docs/test-strategy.md.
- Run the complete test suite at least twice to detect flaky tests.
- Report total test count and failures based on actual execution.
```

## Prompt này đạt được gì?

- Chứng minh 3 rủi ro chính bằng test.
- Tránh bài chỉ “nói” chống concurrency nhưng không chứng minh.
- Có test strategy cho báo cáo.

## Cách kiểm tra

```bash
./gradlew clean test
./gradlew test
```

Nếu project cấu hình integration test thành task/source set riêng, chạy thêm task tương ứng, ví dụ:

```bash
./gradlew integrationTest
```

Để chạy toàn bộ verification tasks:

```bash
./gradlew check
```

Kiểm tra report:

```text
build/reports/tests/test/index.html
build/test-results/test/
```

Nếu có task `integrationTest`, report thường nằm tại:

```text
build/reports/tests/integrationTest/index.html
build/test-results/integrationTest/
```

## Review chất lượng test

- Không dùng H2.
- Không mock repository trong concurrency test.
- Không dùng `Thread.sleep(5000)` để “hy vọng” request chạy đồng thời.
- Test kiểm tra database cuối cùng, không chỉ HTTP status.
- Chạy hai lần liên tiếp vẫn pass.

## Gate để qua Prompt 12

- Test suite pass hai lần.
- Có ít nhất ba concurrency tests trọng tâm.
- Không có flaky test đã biết.

---

# PROMPT 12 — Chuẩn hóa Swagger/OpenAPI

## Mục tiêu

Người chấm hiểu và gọi API mà không phải đọc code.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Review and complete Swagger/OpenAPI documentation for every implemented API.

Requirements:
- Group customer and operation APIs clearly.
- Document:
  method, path, summary, description, headers, parameters,
  request schema, response schema, status codes, and error examples.
- Explicitly document:
  X-User-Id,
  X-Operator-Id,
  Idempotency-Key.
- Add examples for:
  create concert,
  add ticket category,
  create booking with and without voucher,
  idempotent replay,
  cancel booking,
  operation status update.
- Document the common error response and business error codes.
- Ensure generated OpenAPI matches actual controller behavior.
- Do not duplicate extensive Swagger annotations when global components can be reused.
- Add API version and project contact/description metadata.
- Run the application and verify /v3/api-docs is valid JSON.
- Review all endpoints for missing documentation.

Create docs/api-overview.md containing a concise endpoint table and important workflow order.
```

## Prompt này đạt được gì?

- API docs đúng với code.
- Header quan trọng không bị quên.
- Có examples cho reviewer.

## Cách kiểm tra

Mở:

```text
http://localhost:8080/swagger-ui.html
```

Thử trực tiếp:

1. List concert.
2. Create booking.
3. Retry với cùng key.
4. Get booking.
5. Operation update status.

Kiểm tra OpenAPI JSON:

```bash
curl -f http://localhost:8080/v3/api-docs > openapi.json
```

Tìm endpoint:

```bash
grep -n 'Idempotency-Key' openapi.json
grep -n '/api/v1/bookings' openapi.json
```

## Gate để qua Prompt 13

- Mọi API thực hiện đều xuất hiện trên Swagger.
- Header và error response hiển thị.
- Example copy ra gọi được.

---

# PROMPT 13 — Tạo Postman collection hoàn chỉnh

## Mục tiêu

Cung cấp một luồng kiểm thử local có thể chạy từ đầu đến cuối.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Create a complete Postman collection and local environment for GeekTicket.

Files:
- postman/GeekTicket.postman_collection.json
- postman/GeekTicket-Local.postman_environment.json
- postman/README.md

Environment variables:
- baseUrl = http://localhost:8080
- customerId
- secondCustomerId
- operatorId
- concertId
- vipCategoryId
- standardCategoryId
- voucherCode
- bookingId
- bookingCode
- idempotencyKey
- firstBookingCode

Folders and order:
00 Health
01 Concert Customer
02 Concert Operation
03 Booking Happy Path
04 Idempotency
05 Voucher
06 Booking Cancellation
07 Operation Booking
08 Negative Cases

Required requests:
- health check;
- list concerts;
- get concert detail;
- create concert;
- add ticket category;
- publish concert;
- create booking without voucher;
- create booking with voucher;
- replay same idempotency key;
- same key different payload;
- get booking;
- cancel booking;
- list operation bookings;
- operation booking detail;
- update status;
- invalid transition;
- insufficient ticket;
- expired/invalid voucher.

Scripts:
- Generate a new GUID only in requests explicitly named "New Request".
- Save created ids/codes into environment variables.
- Reuse the exact same idempotency key in replay requests.
- Add assertions for status, response schema basics, business status, and error code.
- Never hide test failure by accepting every status code.
- Document the recommended execution order.
- Make all requests work against the Docker local setup.
- Do not include secrets.

Also provide Newman commands in postman/README.md.
```

## Prompt này đạt được gì?

- Reviewer import và chạy được.
- Có happy path, retry, voucher, cancel, operation và negative cases.
- Environment không cần sửa URL thủ công.

## Cách kiểm tra thủ công

1. Import collection.
2. Import environment.
3. Chọn `GeekTicket Local`.
4. Chạy folder theo thứ tự.
5. Kiểm tra environment đã lưu IDs.

## Cách chạy Newman

```bash
npm install -g newman
newman run postman/GeekTicket.postman_collection.json \
  -e postman/GeekTicket-Local.postman_environment.json
```

Kỳ vọng:

- Không request fail.
- Không assertion fail.
- Luồng retry trả cùng booking.
- Negative cases trả đúng error code.

## Lưu ý

Postman/Newman chủ yếu kiểm tra API workflow. Không dùng nó thay cho concurrency tests.

## Gate để qua Prompt 14

- Import được.
- Chạy từ local clean setup.
- Newman pass.
- Collection không chứa URL hard-code ngoài `{{baseUrl}}`.

---

# PROMPT 14 — README, CONTRIBUTING và tài liệu thiết kế

## Mục tiêu

Đáp ứng trực tiếp các deliverable tài liệu của đề.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Create and review the project documentation based strictly on the implemented system.
Do not claim unimplemented features.

Required files:

1. README.md
   - project overview;
   - business context;
   - implemented features;
   - technology stack;
   - prerequisites;
   - environment variables;
   - clean local setup;
   - Docker commands;
   - test commands;
   - Swagger URL;
   - Postman/Newman instructions;
   - seed data;
   - main design decisions;
   - known limitations.

2. CONTRIBUTING.md
   - package structure;
   - naming conventions;
   - how to add a new API;
   - DTO/entity/repository/service/controller responsibilities;
   - how to add Flyway migration;
   - exception and error-code conventions;
   - how to write and run unit/integration tests;
   - how to update Swagger and Postman.

3. docs/GeekTicket_Assessment_Checklist.md
docs/implementation-progress.md
docs/assumptions-scope-limitations.md
   - assumptions;
   - in scope;
   - out of scope;
   - implemented;
   - not implemented;
   - current limitations;
   - future improvements.

4. docs/system-design.md
   - Layered Architecture;
   - component responsibilities;
   - request flow;
   - transaction boundaries;
   - why a monolithic backend was selected;
   - trade-offs;
   - flash-sale stability considerations.

5. docs/database-design.md
   - entity descriptions;
   - relationships;
   - constraints;
   - indexes;
   - inventory consistency;
   - idempotency data;
   - voucher redemption data;
   - audit history.

6. docs/test-strategy.md
   - unit, integration, concurrency, API tests;
   - real executed results;
   - limitations of testing.

7. Mermaid source diagrams:
   - docs/diagrams/system-architecture.md
   - docs/diagrams/database-erd.md
   - docs/diagrams/booking-sequence.md
   - docs/diagrams/booking-state-machine.md

Documentation must match the current source code, database migrations, Swagger, and Postman.
Add a requirement traceability table mapping assessment requirements to code/tests/docs.
```

## Prompt này đạt được gì?

- Bao quát System Design, Database Design, assumptions, done/not done, limitations.
- Có coding guideline.
- Có hướng dẫn chạy/test.
- Có traceability để người chấm tìm nhanh.

## Cách kiểm tra

Đọc README như một người chưa biết project:

- Có thể chạy mà không hỏi tác giả không?
- Có lệnh chính xác không?
- Có tài khoản/ID seed không?
- Có URL Swagger không?
- Có hướng dẫn Postman không?

Kiểm tra chéo:

- Endpoint trong docs có tồn tại trong Swagger.
- Table trong database-design khớp migration.
- Features “implemented” có test hoặc API.
- Features chưa làm không bị mô tả như đã làm.

## Gate để qua Prompt 15

- Clone mới đọc README có thể chạy.
- Không có claim sai.
- Có đủ 4 sơ đồ nguồn.
- Có requirement traceability.

---

# PROMPT 15 — Tạo báo cáo kỹ thuật

## Mục tiêu

Tổng hợp thành tài liệu nộp chính, không lặp lại source code dài dòng.

## Prompt

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Create docs/GeekTicket-Technical-Report.md based only on the implemented repository and existing project documents.

Required structure:
1. Introduction
2. Requirement Analysis
3. Scope and Assumptions
4. System Architecture
5. Database Design
6. Core Business Workflows
7. API Design
8. Concurrency and Data Consistency
9. Operation and Simplified Security Design
10. Testing Strategy and Actual Results
11. Local Setup and Development Guideline
12. Implemented Features and Limitations
13. Future Improvements
14. Conclusion
15. Appendices:
    - endpoint list;
    - error code list;
    - booking state transition table;
    - requirement traceability.

Writing requirements:
- Explain why each major decision was selected.
- Include trade-offs, not only advantages.
- Clearly separate implemented behavior from future design.
- Include actual test results only.
- Reference the project diagrams.
- Keep the report concise and reviewer-friendly.
- Do not claim production readiness.
- Do not include large source-code dumps.
- Highlight:
  overselling prevention,
  idempotency,
  voucher concurrency,
  transaction rollback,
  operation audit history.
```

## Prompt này đạt được gì?

- Có báo cáo chính bao quát tất cả yêu cầu.
- Thể hiện engineering thinking và trade-off.
- Không biến báo cáo thành bản copy README.

## Cách kiểm tra

- Mỗi rủi ro chính có một giải pháp và một test tương ứng.
- Mọi con số test là số thật.
- “Future Improvements” không bị viết như đã triển khai.
- Báo cáo có link hoặc tham chiếu sơ đồ.
- Không có đoạn code dài hơn mức cần thiết.

## Gate để qua Prompt 16

- Báo cáo đọc độc lập vẫn hiểu hệ thống.
- Có implemented/not implemented/limitations.
- Có test results thật.

---

# PROMPT 16 — Final audit, gap analysis và đóng gói

## Mục tiêu

Không sửa mù quáng. Đầu tiên audit, sau đó chỉ sửa blocker.

## Prompt 16A — Audit không sửa

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Perform a final read-only audit of the entire GeekTicket repository.
Do not modify files in this pass.

Verify:
1. Assessment requirement coverage.
2. Build and test reproducibility.
3. Docker clean startup.
4. Flyway clean database migration.
5. Swagger completeness.
6. Postman/Newman compatibility.
7. API and documentation consistency.
8. Database constraints and indexes.
9. Overselling protection.
10. Idempotency behavior.
11. Voucher concurrency behavior.
12. Transaction rollback.
13. Booking state transitions.
14. Operation audit history.
15. Security assumptions and scope honesty.
16. Secrets, debug logs, TODOs, dead code, unused dependencies.
17. N+1 queries and obvious performance issues.
18. Error response consistency.
19. README accuracy.
20. Submission folder completeness.

Run:
- ./gradlew clean check
- docker compose down -v
- docker compose up --build -d
- health check
- Newman collection
- any documented smoke-test commands

Produce docs/final-audit.md with:
- PASS;
- FAIL;
- RISK;
- exact evidence;
- severity;
- required fix;
- optional improvement.

Do not claim PASS without evidence.
```

## Prompt 16B — Chỉ sửa blocker

Sau khi đọc `final-audit.md`, dùng:

```text
Before implementing this prompt:

1. Read `docs/GeekTicket_Assessment_Checklist.md` completely.
2. Treat that checklist as the source of truth for scope, assumptions, architecture,
   database, APIs, testing, documentation, and submission deliverables.
3. Identify the exact checklist items covered by this prompt and list them before coding.
4. Do not add features that are outside the checklist or marked optional unless all
   mandatory items for this phase are already complete.
5. Do not change an assumption or API contract from the checklist silently.
6. Do not mark an item complete merely because code was written.
7. At the end, update `docs/implementation-progress.md` with:
   - checklist item;
   - NOT STARTED / IN PROGRESS / PASS / BLOCKED / DEFERRED;
   - concrete evidence;
   - remaining issue.
8. Mark a checklist item PASS only when supported by an executed Gradle test,
   verified API response, database migration/constraint, Postman/Newman result,
   or another concrete artifact.
9. Follow all GeekTicket project constraints defined at the beginning of this document,
   including Java 21, Gradle Wrapper, package-by-layer structure, PostgreSQL,
   Flyway, DTO-based APIs, service-layer transactions, and the prohibited technologies.
10. After implementation, report:
    - checklist coverage;
    - changed files;
    - design decisions and trade-offs;
    - exact commands executed;
    - actual test results;
    - remaining checklist items.

Fix only BLOCKER and HIGH severity issues listed in docs/final-audit.md.

Rules:
- Do not introduce new features.
- Do not refactor working code for style only.
- Preserve API contracts unless the audit proves they are broken.
- Add or update tests for every fix.
- Update documentation only where behavior changed.
- Run the complete verification again.
- Append the final results to docs/final-audit.md.
```

## Prompt này đạt được gì?

- Có final QA độc lập.
- Không phát sinh feature phút cuối.
- Có bằng chứng project chạy trên database sạch.

## Cách kiểm tra cuối

```bash
git status
./gradlew clean check

docker compose down -v
docker compose up --build -d

curl -f http://localhost:8080/actuator/health

newman run postman/GeekTicket.postman_collection.json \
  -e postman/GeekTicket-Local.postman_environment.json
```

Kiểm tra secret:

```bash
git grep -n -I -E \
'password\s*=|secret\s*=|api[_-]?key|BEGIN PRIVATE KEY'
```

Kiểm tra TODO:

```bash
git grep -n -I -E 'TODO|FIXME|HACK'
```

Kiểm tra file nộp:

```text
README.md
CONTRIBUTING.md
build.gradle
settings.gradle
gradlew
gradlew.bat
gradle/wrapper/
Dockerfile
docker-compose.yml
.env.example
src/main
src/test
docs/GeekTicket-Technical-Report.md
docs/assumptions-scope-limitations.md
docs/system-design.md
docs/database-design.md
docs/test-strategy.md
docs/final-audit.md
docs/diagrams/*
postman/GeekTicket.postman_collection.json
postman/GeekTicket-Local.postman_environment.json
postman/README.md
```

---

# 17. Thứ tự commit đề xuất

```text
chore: bootstrap spring boot and docker environment
feat: add database schema entities and seed data
feat: implement concert and ticket category APIs
feat: add booking domain rules and price calculation
feat: implement transactional booking and inventory protection
feat: add idempotent booking creation
feat: add voucher validation and redemption
feat: add customer booking query and cancellation
feat: add operation booking workflows and audit history
test: add integration and concurrency coverage
docs: add swagger and postman collection
docs: add system design database design and guidelines
chore: final audit and submission cleanup
```

Mỗi commit phải build được. Không để một commit khổng lồ chứa toàn bộ dự án.

---

# 18. Ma trận Prompt → Kết quả → Bằng chứng

| Prompt | Kết quả chính | Bằng chứng bắt buộc |
|---|---|---|
| 1 | Audit và kế hoạch | `implementation-plan.md`, build log |
| 2 | Project local chạy | Health UP, Swagger mở, Docker build |
| 3 | Schema và entity | Flyway history, Testcontainers repository tests |
| 4 | Concert APIs | Swagger/Postman và integration tests |
| 5 | State machine | Unit tests transition và price |
| 6 | Booking không oversell | 50 requests/10 tickets concurrency test |
| 7 | Idempotency | Same-key test, inventory trừ một lần |
| 8 | Voucher protection | Last-voucher concurrency test |
| 9 | Query/cancel | Inventory restore exactly once |
| 10 | Operation workflow | Filter, status update, audit history tests |
| 11 | Test suite | `mvn clean verify` pass hai lần |
| 12 | Swagger | `/v3/api-docs`, endpoint/header examples |
| 13 | Postman | Newman pass |
| 14 | Project docs | README, CONTRIBUTING, design docs |
| 15 | Technical report | Báo cáo có trade-off và traceability |
| 16 | Final audit | Clean Docker startup, full verification |

---

# 19. Khi thời gian không đủ

## Không được cắt

- Create booking.
- Atomic inventory update.
- Transaction rollback.
- Idempotency.
- Voucher validation/limit.
- Booking status query.
- Operation booking list/status update.
- Automated concurrency tests.
- Swagger.
- Postman.
- README.
- Scope/assumptions/limitations.

## Có thể cắt trước

- Suspicious booking endpoint.
- Voucher CRUD.
- Automatic booking expiration scheduler.
- Restore voucher usage on cancellation nếu đã ghi rõ limitation.
- Full role security.
- Advanced metrics.
- Event package.
- Notification.
- Frontend.

---

# 20. Cách review câu trả lời của AI sau mỗi prompt

Sau mỗi prompt, không chỉ nhìn code. Yêu cầu AI trả lời đủ:

1. **Changed files**  
   Có đúng phạm vi prompt không?

2. **Design explanation**  
   Có giải thích transaction, constraint, trade-off không?

3. **Commands executed**  
   Có lệnh thật không?

4. **Test results**  
   Có số test/pass/fail không?

5. **Remaining limitations**  
   Có minh bạch không?

6. **Contract changes**  
   Có tự ý đổi endpoint/request/response không?

7. **Database changes**  
   Có migration không, hay sửa schema bằng `ddl-auto`?

8. **Scope control**  
   Có tự thêm Redis/Kafka/Security/payment không?

Mẫu review tiếp theo:

```text
Review your previous implementation against both:
1. the acceptance criteria of the current prompt; and
2. `docs/GeekTicket_Assessment_Checklist.md`.

Do not add new features.
Do not mark checklist items PASS without executed evidence.

For each criterion, mark PASS or FAIL and provide evidence:
- file and method;
- test name;
- command output;
- API response;
- database constraint/migration.

Then list:
1. correctness issues;
2. transaction/concurrency risks;
3. API contract mismatches;
4. missing tests;
5. documentation mismatches.

Fix only confirmed FAIL items and rerun the relevant tests.
```

---

# 21. Kết luận

Không làm toàn bộ hệ thống trong một prompt lớn.  
Mỗi prompt phải tạo ra một phần có thể chạy, có test và có gate rõ ràng.

Ba phần quan trọng nhất để bài GeekTicket có giá trị:

1. Không oversell.
2. Không duplicate do retry.
3. Không vượt voucher limit.

Mọi quyết định thiết kế phải có:

```text
Problem → Decision → Trade-off → Automated evidence
```
