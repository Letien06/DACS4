# Nhật ký triển khai SafeChat

Ngày 07/10/2026. Nền: ứng dụng Kết Nối trong `D:\AVKU\Lập trình mạng\chat_message`.

## Đã thực hiện

- Đọc source, cấu hình, protocol và trạng thái nền; sao chép source vào workspace DACS4, giữ dữ liệu/khóa riêng tại nguồn.
- Chốt Java 17, Swing/FlatLaf, TCP/TLS, UDP, SQLite; AI qua API nhận diện ảnh.
- Tạo kế hoạch và workflow; đầu ra học phần chuyển sang `output/docs`.

## Đang triển khai

- Đề cương theo mẫu khoa.
- Server moderation, client cảnh báo, kiểm thử, build/release và báo cáo.

## Chưa có bằng chứng

- Chưa chạy API AI thật hoặc đánh giá độ chính xác trên bộ ảnh có nhãn.
- Chưa nghiệm thu LAN trên nhiều máy hoặc bảo vệ trước hội đồng.

## Xác nhận triển khai ngày 08/10/2026

- Full Maven reactor test bằng JDK 17 và Maven bundled: **95 test, 0 failure, 0 error, 0 skipped** (common 6, server 76, client 13).
- `scripts/build-all.ps1 -Offline` tạo shaded JAR cho server và client thành công.
- `scripts/verify-smoke.ps1` đã chạy server TLS đóng gói, hai client fixture, UDP discovery/typing, gửi text, warning gate và Swing paint thành công.
- Smoke production không có `OPENAI_API_KEY` xác nhận ảnh chuyển sang `UNAVAILABLE`, ảnh chưa kiểm tra bị từ chối gửi và tin nhắn văn bản vẫn hoạt động.
- `scripts/start-demo.ps1` đã được harden để xóa `OPENAI_API_KEY` khỏi môi trường trước khi mở client; khóa chỉ được thừa hưởng bởi tiến trình server.
- Bản phát hành mới nhất được tạo tại `release/safechat-20261008-131734.zip`.

Các smoke này dùng fixture cho nhánh cảnh báo và không phải phép đo độ chính xác của mô hình AI thật. Chưa có bằng chứng LAN nhiều máy hoặc đánh giá trên bộ ảnh có nhãn.

- Archive verification của `release/safechat-20261008-131734.zip` không phát hiện private key, database, media, API key hoặc file secret.


- Client upload guard và test moderation response không hợp lệ đã bổ sung; full build/test **95 test** pass (common 6, server 76, client 13).





- TCP/TLS runtime test mở rộng đủ bốn trạng thái moderation (CLEAR, WARNING, BLOCKED, UNAVAILABLE) và retry UNAVAILABLE; 4/4 parameterized cases PASS.



