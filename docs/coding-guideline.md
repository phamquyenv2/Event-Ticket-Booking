# Coding Guideline & Convention — GeekTicket

## 1. Cấu Trúc Mã Nguồn Chi Tiết (Package Structure)

```text
src/main/java/com/quyen/geekticket/
├── GeekticketApplication.java          # Main entry point
│
├── config/                             # Cấu hình hệ thống
│   ├── CorsConfig.java                 # Cấu hình CORS
│   ├── JpaAuditingConfig.java          # Tự động map created_at, updated_at
│   ├── OpenApiConfig.java              # Cấu hình Swagger / OpenAPI 3.0
│   └── SecurityConfig.java             # Cấu hình Spring Security
│
├── controller/                         # REST Controller Layer (Validate input & gọi Service)
│   ├── BookingController.java          # Customer APIs: Đặt vé, xem đơn, hủy vé
│   ├── ConcertController.java          # Customer APIs: Xem danh sách & chi tiết concert
│   ├── OperationBookingController.java # Operator APIs: Quản lý booking, cập nhật trạng thái
│   └── OperationConcertController.java # Operator APIs: Quản lý concert, publish, tạo hạng vé
│
├── domain/                             # Core Models & DTOs
│   ├── entity/                         # JPA Entities (Mapping bảng CSDL)
│   │   ├── Booking.java                # Đơn đặt vé
│   │   ├── BookingItem.java            # Chi tiết từng hạng vé trong đơn
│   │   ├── BookingStatusHistory.java   # Lịch sử chuyển trạng thái booking
│   │   ├── Concert.java                # Sự kiện âm nhạc
│   │   ├── IdempotencyRecord.java      # Lưu vết chống duplicate request
│   │   ├── TicketCategory.java         # Hạng vé & số lượng inventory
│   │   ├── User.java                   # Người dùng (Customer / Operator / Admin)
│   │   ├── Voucher.java                # Mã giảm giá
│   │   └── VoucherRedemption.java      # Lịch sử áp dụng voucher
│   ├── dto/                            # API Envelope & Paging
│   │   ├── ApiResponse.java            # Chuẩn response envelope {success, message, data}
│   │   └── PageResponse.java           # Chuẩn phân trang {content, page, size, totalElements}
│   ├── request/                        # Input DTOs từ Client
│   │   ├── CreateBookingRequest.java   # DTO tạo đơn đặt vé
│   │   ├── CreateConcertRequest.java   # DTO tạo concert mới
│   │   └── UpdateBookingStatusRequest.java # DTO cập nhật trạng thái
│   └── response/                       # Output DTOs trả về Client
│       ├── BookingResponse.java        # DTO thông tin booking
│       ├── ConcertDetailResponse.java  # DTO chi tiết concert & các hạng vé
│       └── VoucherResponse.java        # DTO thông tin voucher
│
├── event/                              # Domain Events
│   ├── BookingConfirmedEvent.java
│   └── BookingCreatedEvent.java
│
├── repository/                         # Data Access Layer (Spring Data JPA)
│   ├── BookingRepository.java
│   ├── ConcertRepository.java
│   ├── TicketCategoryRepository.java
│   ├── VoucherRepository.java
│   └── specification/                  # JPA Specification cho filter động
│       └── BookingSpecification.java
│
├── service/                            # Service Layer (Business Logic Interfaces)
│   ├── BookingService.java
│   ├── ConcertService.java
│   ├── OperationBookingService.java
│   └── impl/                           # Service Implementations (@Transactional)
│       ├── BookingServiceImpl.java     # Logic lõi: Atomic lock, check idempotency
│       ├── ConcertServiceImpl.java
│       └── OperationBookingServiceImpl.java
│
└── util/                               # Utilities & Helpers
    ├── constant/                       # Domain Enums
    │   ├── BookingStatus.java          # RESERVED, CONFIRMED, CANCELLED, EXPIRED
    │   ├── ConcertStatus.java          # DRAFT, PUBLISHED, CANCELLED
    │   └── DiscountType.java           # PERCENTAGE, FIXED_AMOUNT
    ├── error/                          # Exception Handling
    │   ├── BusinessException.java      # Custom RuntimeException
    │   ├── ErrorCode.java              # Enum chứa HttpStatus & Error Message
    │   ├── ErrorResponse.java          # Chuẩn JSON trả lỗi cho Client
    │   └── GlobalExceptionHandler.java # RestControllerAdvice bắt lỗi toàn hệ thống
    ├── generator/                      # Helper tạo mã
    │   ├── BookingCodeGenerator.java   # Sinh mã đơn BK-YYYYMMDD-XXXXXX
    │   └── RequestHashGenerator.java   # Hash SHA-256 body cho Idempotency
    └── mapper/                         # Object Mapping
        ├── BookingMapper.java          # Convert Entity ↔ Response DTO
        └── ConcertMapper.java
```

---

## 2. Quy Tắc Cốt Lõi

- **Phân tầng rõ ràng**: Controller ➔ Service ➔ Repository ➔ Database.
- **Controller Clean**: Không chứa business logic hay truy vấn CSDL.
- **Bọc DTO**: Không trả về Entity trực tiếp, luôn dùng Response DTO bọc trong `ApiResponse<T>`.
- **Quy trình thêm API**: Request DTO ➔ Response DTO ➔ Service ➔ Controller ➔ Test.
- **Chống Overselling**: Dùng Atomic SQL `UPDATE ... WHERE available_quantity >= :qty`.
- **Chạy Test**: `.\gradlew.bat test` (Windows) hoặc `./gradlew test` (Linux/Mac).
