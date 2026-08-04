# Booking State Machine

```mermaid
stateDiagram-v2
    [*] --> RESERVED : Tạo booking thành công

    RESERVED --> CONFIRMED : Operator xác nhận<br/>(payment verified)
    RESERVED --> CANCELLED : Customer hủy<br/>hoặc Operator hủy
    RESERVED --> EXPIRED : Hết hạn giữ vé<br/>(chưa implement auto)

    CONFIRMED --> CANCELLED : Operator hủy<br/>(có lý do)

    CANCELLED --> [*]
    EXPIRED --> [*]
    FAILED --> [*]

    note right of RESERVED
        Trạng thái ban đầu khi tạo booking.
        Kho vé đã bị trừ.
        Voucher đã bị tính.
    end note

    note right of CANCELLED
        Khi hủy:
        - Hoàn kho vé (atomic +qty)
        - Hoàn voucher (usage -1)
        - Xóa voucher_redemption
    end note

    note right of CONFIRMED
        Operator xác nhận sau khi
        kiểm tra thanh toán.
        Không thể quay lại RESERVED.
    end note
```

## Chuyển trạng thái hợp lệ

| Từ | Sang | Ai được phép |
|---|---|---|
| (mới tạo) | RESERVED | System (khi tạo booking) |
| RESERVED | CONFIRMED | Operator |
| RESERVED | CANCELLED | Customer hoặc Operator |
| RESERVED | EXPIRED | System (scheduled, chưa implement) |
| CONFIRMED | CANCELLED | Operator (kèm lý do) |

Chuyển trạng thái không nằm trong bảng trên → bị chặn với **409 INVALID_BOOKING_STATUS_TRANSITION**.
