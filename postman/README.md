# Postman cho GeekTicket

## File gì ở đây

- `GeekTicket.postman_collection.json` — 21 request, 9 folder
- `GeekTicket-Local.postman_environment.json` — biến chạy local

## Chạy trước

```bash
cd geekticket
docker compose up -d postgres
.\gradlew.bat bootRun
```

Check: `http://localhost:8080/actuator/health` → thấy `UP` là được.

## Import

Mở Postman → bấm Import → kéo 2 file JSON vào → xong.

Nhớ chọn environment **GeekTicket Local** ở góc trên phải

## Chạy theo thứ tự

| Folder | Nội dung |
|---|---|
| 00 Health | Check app sống |
| 01 Concert Customer | Xem concert, chi tiết |
| 02 Concert Operation | Tạo concert → thêm vé → publish |
| 03 Booking Happy Path | Đặt vé, xem booking |
| 04 Idempotency | Gửi lại cùng key → cùng booking. Đổi body → 409 |
| 05 Voucher | Đặt vé có voucher |
| 06 Booking Cancellation | Tạo rồi hủy |
| 07 Operation Booking | Operator xem, xác nhận, thử chuyển sai trạng thái |
| 08 Negative Cases | Hết vé, thiếu key, voucher đã dùng, user khác xem |

Chạy đúng thứ tự vì folder sau xài biến folder trước tạo.

## Newman (chạy bằng CLI)

```bash
npm install -g newman
newman run postman/GeekTicket.postman_collection.json -e postman/GeekTicket-Local.postman_environment.json
```

Chạy 1 folder:
```bash
newman run postman/GeekTicket.postman_collection.json -e postman/GeekTicket-Local.postman_environment.json --folder "03 Booking Happy Path"
```

## Reset DB chạy lại

```bash
cd geekticket
docker compose down -v
docker compose up -d postgres
.\gradlew.bat bootRun
```
