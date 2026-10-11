# SafeChat

Đồ án Cơ sở 4 của Lê Cao Sơn Tiến (24IT270, 24SE3).

Ứng dụng chat desktop Java 17 theo mô hình Client–Server. TCP/TLS truyền tài khoản, tin nhắn, lịch sử và tệp. UDP hỗ trợ tìm server LAN và trạng thái đang nhập. Server gọi OpenAI Moderation để cảnh báo ảnh bạo lực, giữ khóa API tại server.

## Chạy nhanh trên Windows

1. Cài Java 17 hoặc dùng JBR 17 của IntelliJ; `java` và `keytool` phải có trên PATH.
2. Chạy `CHAT_DEMO.cmd` ở thư mục DACS4, hoặc `scripts/start-demo.ps1` trong thư mục sản phẩm.
3. Lần đầu tạo TLS riêng trên máy và tài khoản giả lập `demo_alice`, `demo_bob`, `demo_carol`; mật khẩu **SafeChat-Demo-2026** chỉ dành cho demo local.
4. Đăng nhập hai cửa sổ bằng alice/bob, vào Trò chuyện, gửi tin nhắn hoặc chọn nhóm học tập.
5. Dừng server demo bằng `scripts/stop-demo.ps1`. Tắt các client bằng nút đóng cửa sổ.

Script dùng các JAR đã đóng gói; không cần Maven để chạy. Cổng mặc định TCP 5000, UDP 5001/5002. Database và ảnh nằm trong `data`, log demo trong `data/demo-logs`. Không chạy hai server trên cùng database. Không gửi thư mục `config/tls` hoặc `data` lên Git.

## Cấu hình AI trên server

Chạy `scripts/start-demo.ps1 -ConfigureAi` để nhập khóa API kín trong terminal. Khóa chỉ tồn tại trong môi trường tiến trình server của lần chạy đó. Có thể dùng biến môi trường `OPENAI_API_KEY` do người vận hành cấu hình trước khi chạy `scripts/run-server.ps1`; không đặt khóa trong source, properties, báo cáo hoặc chat với trợ lý.

Ảnh PNG/JPEG tối đa 5 MiB và 20 megapixel được gửi từ server tới `https://api.openai.com/v1/moderations` qua HTTPS. Client có thông báo về việc gửi ảnh tới dịch vụ AI. Mô hình `omni-moderation-latest` là mô hình có sẵn, không phải mô hình tự huấn luyện.

| Kết quả | Hành vi |
|---|---|
| CHECKING | Đang kiểm tra, chưa gửi |
| CLEAR | Chưa vượt ngưỡng, có thể gửi; AI có thể nhầm |
| WARNING | Người gửi xác nhận, người nhận xác nhận trước xem/tải |
| BLOCKED | Không được gửi hoặc tải |
| UNAVAILABLE | Thiếu khóa/lỗi/timeout; chưa gửi, có thể thử lại |

Chưa có khóa API thì chat văn bản và tệp thông thường vẫn hoạt động; ảnh báo UNAVAILABLE. Không có bộ nhận diện bằng tên ảnh hoặc màu sắc giả làm AI. Kết quả kiểm thử tự động dùng fixture chỉ xác minh logic tích hợp và chính sách, không chứng minh độ chính xác mô hình.

## Build và kiểm thử

Chạy `scripts/build-all.ps1 -Offline` khi có `.m2-local`, hoặc bỏ `-Offline` khi cần tải phụ thuộc. Dùng Maven 3.9 và Java 17. Script chạy toàn bộ kiểm thử trước khi tạo shaded JAR. Báo cáo JUnit trong `common/target/surefire-reports`, `server/target/surefire-reports`, `client/target/surefire-reports`.

Các lớp mới gồm OpenAiImageModeratorTest, ImageModerationPolicyTest và SafeChatRuntimeTest. Chúng kiểm tra hợp đồng API, lỗi/timeout, ảnh đổi MIME, quyền xác nhận và chính sách qua TCP/TLS thật. LoadRuntimeTest đo workload nền với AI tắt để tách chi phí chat/file khỏi độ trễ dịch vụ ngoài. Không diễn giải số đo này thành hiệu năng AI.

## Demo LAN

Tạo TLS với `scripts/init-local.ps1 -LanHost <IP-hoặc-DNS-server>` trên thư mục triển khai mới. Chỉ chia sẻ `truststore.p12` và mật khẩu truststore cho client; giữ `server.p12` ở server. Client nhập địa chỉ server hoặc chọn tìm server LAN. Mở TCP 5000 và UDP 5001/5002 trong firewall theo mạng thực tế. UDP discovery chỉ gợi ý địa chỉ; TLS kiểm tra danh tính server. Chưa nghiệm thu LAN nhiều máy trong phiên phát triển này.

## Tài liệu và nguồn kế thừa

Kế hoạch/workflow ở thư mục DACS4; đề cương và báo cáo ở `output/docs`. Source nền lấy từ `D:\AVKU\Lập trình mạng\chat_message`, được sao chép và phát triển trong `chat-java`; phần chat/TLS/UDP/SQLite kế thừa được ghi riêng trong báo cáo. Namespace `com.p2p` giữ tương thích mã nền, không có nghĩa sản phẩm hiện tại sử dụng P2P. Các module bảng tin nền còn source để tham khảo, không nằm trong navigation chính SafeChat.

Mã và tài liệu ghi đúng giới hạn hiện tại: cần cấu hình khóa và bộ ảnh có nhãn để đánh giá AI thật; không có số liệu Accuracy/Precision/Recall/F1 đã xác minh.
