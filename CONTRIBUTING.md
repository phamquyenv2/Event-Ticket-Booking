# Hướng dẫn đóng góp — GeekTicket

## Cấu trúc package

```
com.quyen.geekticket/
├── controller/        ← REST endpoints, không chứa logic
├── service/           ← Interface
│   └── impl/          ← Logic nghiệp vụ, gắn @Transactional
├── repository/        ← JPA Repository + custom query
│   └── specification/ ← Dynamic filter (JPA Specification)
├── domain/
│   ├── entity/        ← JPA Entity (map DB table)
│   ├── dto/           ← ApiResponse, PageResponse
│   ├── request/       ← DTO nhận từ client
│   └── response/      ← DTO trả về client
├── util/
│   ├── constant/      ← Enum (BookingStatus, UserRole, ...)
│   ├── error/         ← ErrorCode, BusinessException, GlobalExceptionHandler
│   ├── mapper/        ← Entity ↔ DTO converter
│   └── generator/     ← BookingCodeGenerator, RequestHashGenerator
└── config/            ← JpaAuditingConfig
```

## Quy tắc đặt tên

- **Entity**: Tên số ít, PascalCase — `Booking`, `TicketCategory`
- **Repository**: `<Entity>Repository` — `BookingRepository`
- **Service**: `<Feature>Service` (interface) + `<Feature>ServiceImpl`
- **Controller**: `<Feature>Controller`
- **DTO Request**: `Create<Entity>Request`, `Update<Action>Request`
- **DTO Response**: `<Entity>Response`, `<Entity>DetailResponse`
- **Enum**: PascalCase, giá trị UPPER_SNAKE — `BookingStatus.RESERVED`
- **Test**: `<Class>Test` (unit), `<Feature>IntegrationTest` (integration)

## Thêm API mới

1. Tạo Request DTO trong `domain/request/` (có validation annotations)
2. Tạo Response DTO trong `domain/response/`
3. Thêm method vào Service interface
4. Implement trong ServiceImpl (gắn `@Transactional` nếu có ghi DB)
5. Thêm endpoint trong Controller (chỉ gọi service, không chứa logic)
6. Thêm mapper nếu cần trong `util/mapper/`
7. Viết test

## Entity vs DTO

- **Entity**: Map DB, có `@Entity`, không trả về client
- **Request DTO**: Nhận input, có `@Valid` annotations
- **Response DTO**: Trả về client, dùng `@Builder`
- **Mapper**: Convert entity → response DTO (trong `util/mapper/`)

## Flyway migration

Tạo file mới trong `src/main/resources/db/migration/`:

```
V<số>__<mô_tả>.sql
```

Ví dụ: `V8__add_payment_table.sql`

Quy tắc:
- Số version tăng dần, không trùng
- Chỉ thêm, không sửa file migration cũ
- Test bằng cách reset DB: `docker compose down -v && docker compose up -d postgres`

## Xử lý lỗi

Thêm error code mới vào `ErrorCode` enum:

```java
VOUCHER_NOT_FOUND(HttpStatus.NOT_FOUND, "Voucher not found"),
```

Throw trong service:

```java
throw new BusinessException(ErrorCode.VOUCHER_NOT_FOUND);
```

`GlobalExceptionHandler` tự bắt và trả về JSON chuẩn.

## Viết test

### Unit test (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock private MyRepository repo;
    @InjectMocks private MyServiceImpl service;

    @Test
    @DisplayName("mô tả hành vi")
    void method_condition_expected() {
        // Arrange → Act → Assert
    }
}
```

### Integration test (Testcontainers)

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MyIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mockMvc;
}
```

Chạy test:

```bash
.\gradlew.bat test --no-daemon                    # toàn bộ
.\gradlew.bat test --tests "com.quyen...MyTest"   # 1 class
```

## Cập nhật Swagger

Thêm annotations vào controller:

```java
@Operation(summary = "Mô tả ngắn")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "OK"),
    @ApiResponse(responseCode = "404", description = "Not found")
})
```

## Cập nhật Postman

Sửa file `postman/GeekTicket.postman_collection.json` hoặc import vào Postman, sửa, export lại.
