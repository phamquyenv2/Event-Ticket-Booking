# Cấu Trúc Dự Án (Project Structure Guidelines)

Tài liệu này quy định cấu trúc thư mục của dự án Spring Boot hiện tại (ShopLite) để các dự án sau dễ dàng kế thừa, tái sử dụng mã nguồn và duy trì tính nhất quán, dễ đọc, dễ bảo trì.

## Tổng quan cấu trúc thư mục

Tất cả code Java được đặt bên trong package gốc: `com.quyen.shoplite`

```text
src/main/java/com/quyen/shoplite/
├── config/             # Cấu hình ứng dụng (Security, CORS, Bean config, WebSocket, Redis, Jpa, v.v.)
├── controller/         # Các API Endpoints (REST Controllers)
├── domain/             # Entities và các đối tượng DTO (Data Transfer Object)
│   ├── dto/            # Các class DTO dùng chung
│   ├── request/        # Các class DTO dùng để nhận dữ liệu từ Client (Payload)
│   └── response/       # Các class DTO dùng để trả dữ liệu về cho Client (chia theo module)
├── event/              # Xử lý các Event (Event-Driven Architecture)
│   └── dto/            # DTO dành riêng cho Event payload
├── repository/         # Data Access Layer (Spring Data JPA Repositories)
├── service/            # Business Logic Layer (Xử lý nghiệp vụ)
│   └── payment/        # Chứa logic tích hợp thanh toán (Momo, SePay, v.v.)
└── util/               # Các class tiện ích (Utilities/Helpers) được dùng chung
    ├── annotation/     # Custom Annotations (ví dụ: @ApiMessage)
    ├── constant/       # Khai báo các hằng số (Constants/Enums)
    └── error/          # Cấu hình xử lý ngoại lệ (Global Exception Handler, Custom Exceptions)
```

## Quá trình Initialization (Khởi tạo và Migration Dữ Liệu)

Dự án ShopLite được thiết kế để có thể tự động nạp dữ liệu mồi (seed data) và cập nhật/sửa đổi schema bằng code trực tiếp. Các lớp này nằm trong package `config/`:

- **`DatabaseInitializer.java`**: Implements `CommandLineRunner`, chạy khi app khởi động (`@Order(0)`).
  - Tự động nạp danh sách các **Quyền (Permissions)** cho toàn bộ hệ thống API.
  - Tự động thiết lập các **Vai trò mặc định (Roles)** (`STORE_MANAGER`, `ORDER_STAFF`, `CASHIER`, `WAREHOUSE`) với các tập quyền tương ứng.
  - Khởi tạo tài khoản quản trị Admin (username: `1`, password: `1`) và gán cho cửa hàng mặc định (`Store`).
  - Nạp dữ liệu **Thực đơn điều hướng (Menus/Groups)** cho giao diện ứng dụng.
  - Khởi tạo danh sách Quỹ mặc định (`FundAccount`: Tiền mặt, Ngân hàng, Ví điện tử).
  - Khởi tạo một số dữ liệu mẫu (Sample Employees, Default Office) nếu hệ thống trống.
- **`DatabaseMigration.java`**: Dùng `@EventListener(ApplicationReadyEvent.class)`.
  - Có nhiệm vụ cập nhật hoặc dọn dẹp các cấu trúc bảng (VD: tự động thêm cột `store_id` vào toàn bộ các bảng để phục vụ kiến trúc Multi-Tenant - SaaS).
  - Backfill (bù đắp) dữ liệu cho các bảng khi có sự thay đổi quy trình (ví dụ: bóc tách `employee_salary_histories`).

## Chi tiết từng Package

### 1. `config/` (Configuration Layer)
- **Nhiệm vụ:** Chứa các class cấu hình của Spring Boot sử dụng annotation `@Configuration`.
- **Phân tích các tệp tiêu biểu cấu thành:**
  - `SecurityConfiguration.java`: Cấu hình bảo mật chính của Spring Security (phân quyền endpoints, JWT filter, cấu hình CSRF/Session).
  - `CorsConfig.java`: Cấu hình Cross-Origin Resource Sharing (CORS) cho phép frontend ở domain khác gọi API một cách an toàn.
  - `OpenAPIConfig.java`: Cấu hình tự động sinh tài liệu API bằng Swagger/OpenAPI.
  - `DatabaseInitializer.java` / `DatabaseMigration.java`: Khởi tạo và đồng bộ hóa schema / data cho database.
  - `CustomAuthenticationEntryPoint.java` / `CustomAccessDeniedHandler.java` / `UserDetailsCustom.java`: Tùy chỉnh xử lý ngoại lệ của Security khi bị lỗi xác thực, phân quyền và định nghĩa chi tiết entity để map với Spring Security context.
  - `DateTimeFormatConfiguration.java` / `JacksonConfig.java`: Điều chỉnh định dạng `LocalDate`, `LocalDateTime` và Jackson Object Mapper trên toàn bộ hệ thống API.
  - `AsyncConfig.java`: Cấu hình thread pool để xử lý bất đồng bộ (@Async).
  - `WebSocketConfig.java`: Cấu hình Message Broker cho các tính năng realtime (ví dụ: Push thông báo).
  - `RedisConfig.java`: Cấu hình kết nối và các Bean phục vụ caching / token management bằng Redis.
  - `JpaConfig.java`: Cấu hình JPA Auditing (ví dụ để tự động fill created_by, updated_by).
  - `FirebaseConfig.java`: Cấu hình khởi tạo SDK cho Firebase (hỗ trợ Push Notification FCM).

### 2. `controller/` (Presentation Layer)
- **Nhiệm vụ:** Tiếp nhận request từ client, điều hướng đến Service xử lý và trả về phản hồi (response).
- **Quy tắc:** 
  - Đánh dấu với `@RestController`.
  - KHÔNG chứa logic nghiệp vụ phức tạp ở đây, chỉ làm nhiệm vụ gọi Service.
  - Validate dữ liệu đầu vào (sử dụng `@Valid`).

### 3. `domain/` (Data Model Layer)
Chứa tất cả các Model/Class đại diện cho dữ liệu.
- **Root `domain/`:** Chứa các class Entities map trực tiếp với các bảng trong cơ sở dữ liệu (đánh dấu `@Entity`).
- **`dto/`:** Các Data Transfer Objects dùng chung trong nội bộ ứng dụng.
- **`request/`:** Chứa các DTO nhận dữ liệu payload từ phía người dùng (tạo mới, cập nhật). Ví dụ: `ReqOrderCreate`, `ReqProductCreate`.
- **`response/`:** Chứa các DTO trả dữ liệu (đã được định dạng hoặc che giấu thông tin nhạy cảm) về phía Client. 
  - **Lưu ý:** Thư mục này được chia nhỏ ra thành các package con theo từng module/thực thể (ví dụ: `attendance/`, `product/`, `order/`, `user/`) để dễ quản lý.

### 4. `event/` (Event-Driven Architecture)
- **Nhiệm vụ:** Quản lý các sự kiện sinh ra trong hệ thống và truyền thông điệp giữa các service nhằm giảm kết dính (decoupling).
- **Cấu trúc:**
  - Chứa các class định nghĩa Event (ví dụ: `InventoryChangedEvent`, `OrderCompletedEvent`).
  - **`dto/`:** Chứa DTO đại diện cho payload của Event (ví dụ: `InventoryChangeDTO`).

### 5. `repository/` (Data Access Layer)
- **Nhiệm vụ:** Tương tác với Database để CRUD dữ liệu.
- **Quy tắc:** 
  - Thường là các interface kế thừa `JpaRepository` hoặc `JpaSpecificationExecutor`.
  - Hạn chế viết native query trừ khi quá phức tạp để tối ưu hiệu suất.

### 6. `service/` (Business Logic Layer)
- **Nhiệm vụ:** Xử lý nghiệp vụ chính của ứng dụng.
- **Quy tắc:**
  - Được tiêm (inject) các Repository hoặc các Service khác.
  - Controller chỉ được phép giao tiếp với hệ thống qua Service.
  - Sử dụng Event Publisher cho các hành động ảnh hưởng đa module thay vì gọi Service chéo chằng chịt.
- **Package con:**
  - **`impl/`:** Chứa các class triển khai (implementation) của các Service interface.
  - **`payment/`:** Xử lý chiến lược thanh toán (Payment Provider Pattern) và tích hợp các bên thứ 3 (Momo, SePay, v.v.).

### 7. `util/` (Utility & Cross-cutting Concerns)
Nơi chứa các công cụ hỗ trợ chung, xử lý chéo cho cả dự án, có thể được gọi từ bất kỳ Layer nào.
- **Các thành phần gốc của `util/`:**
  - `FormatRestResponse.java`: Class chứa `ResponseBodyAdvice`. Mục đích là chặn (intercept) tất cả data trả về (ResponseEntity) và đóng gói vào 1 Object duy nhất JSON tiêu chuẩn ví dụ `{"statusCode": 200, "message": "...", "data": {...}}`.
  - `SecurityUtil.java`: Cung cấp các hàm tĩnh tiện ích lấy User đang đăng nhập từ `SecurityContextHolder`.
  - `DTOMapper.java`: Chứa tiện ích convert Entity sang DTO.
  - `ProductSpecification.java`: Chứa helper xây dựng các dynamic query cho JPA.
- **`annotation/`**: Chứa Custom Annotation do dev định nghĩa:
  - `ApiMessage.java`: Dùng để annotate lên hàm API ở Controller, truyền tham số "message" cần trả ra, `FormatRestResponse` sẽ lấy được thông báo đó gắn vào JSON đầu ra.
- **`constant/`**: Chứa hằng số dùng chung (Enum) giúp loại bỏ chuỗi hard-code (Ví dụ: `RoleEnum`, `StatusEnum`, `PaymentMethodEnum`...).
- **`error/`**: Tập trung mọi logic bắt và xử lý lỗi Exception:
  - `GlobalException.java`: Lớp `@RestControllerAdvice`, bắt tất cả các ngoại lệ của dự án (`MethodArgumentNotValidException`, `IdInvalidException`...) và trả về Error JSON thống nhất.
  - **Custom Exceptions**: Các class lỗi tự định nghĩa (kế thừa `Exception` / `RuntimeException`). Ví dụ `IdInvalidException` (không tìm thấy/ID lỗi), `PermissionException` (truy cập quá quyền hạn), `ResourceNotFoundException`.
