# Kế hoạch triển khai đồ án SafeChat

**Ngày cập nhật:** 08/10/2026  
**Sinh viên:** Lê Cao Sơn Tiến — MSSV 24IT270 — Lớp 24SE3  
**GVHD:** ThS. Nguyễn Đỗ Công Pháp

## 1. Tên và mục tiêu

**Xây dựng ứng dụng nhắn tin Java theo mô hình Client–Server sử dụng TCP/UDP tích hợp AI cảnh báo ảnh bạo lực.** Tên sản phẩm là **SafeChat**.

Mục tiêu là xây dựng ứng dụng desktop cho nhóm học tập hoặc mạng LAN. Nhiều client kết nối đến một server trung tâm để đăng nhập, chat riêng/nhóm, trao đổi tệp/ảnh và xem lịch sử. Server quyết định xác thực, lưu trữ, phân phối dữ liệu, quyền truy cập và kết quả kiểm tra ảnh.

Đồ án tập trung vào phân tích, thiết kế, lập trình mạng Java và tích hợp một mô hình nhận diện có sẵn qua API ở phía server. Không đặt mục tiêu tự huấn luyện mô hình hoặc tuyên bố AI chính xác tuyệt đối.

### 1.1. Tài liệu tham khảo giao diện và giới hạn chuyển đổi

Tài liệu Studocu có tiêu đề **“Xây dựng web chat nội bộ tích hợp AI nhận diện ảnh bạo lực”** được dùng như nguồn tham khảo về bài toán và cách tổ chức trải nghiệm chat: điều hướng bên trái, danh sách hội thoại, vùng tin nhắn trung tâm, trạng thái online, gửi ảnh và cảnh báo trước khi xem ảnh có nguy cơ. Đây là tham khảo chức năng và ngôn ngữ bố cục, không phải yêu cầu sao chép mã nguồn, hình ảnh, thương hiệu hoặc giao diện web.

SafeChat là ứng dụng Java desktop dùng Swing/FlatLaf. Vì vậy, mục tiêu là tái hiện tương đương về cấu trúc, trạng thái và thao tác trên cửa sổ desktop; không cam kết giống từng pixel với React/Tailwind hoặc các ảnh chụp trong tài liệu tham khảo. Phần gọi video/WebRTC có trong một số mô tả tham khảo không đưa vào phạm vi đồ án cá nhân này; chỉ xem xét sau khi chat, TCP/UDP và AI đã nghiệm thu.

**Cổng phê duyệt:** trong giai đoạn hiện tại chỉ cập nhật tài liệu Markdown và đề cương. Chưa sửa mã giao diện theo mục tiêu mới cho đến khi sinh viên duyệt `plan.md`, `workflow.md` và `docs/06-desktop-ux.md`.

## 2. Khảo sát mã nguồn nền

Mã nguồn nền được sao chép từ `D:\AVKU\Lập trình mạng\chat_message` vào `D:\AVKU\DACS4\chat-java`. Thư mục gốc không được sửa; database, dữ liệu cá nhân, certificate private, khóa API và mật khẩu không đưa vào release.

### 2.1. Cấu trúc và chức năng kế thừa

| Khu vực | Chức năng thực tế |
|---|---|
| `common` | DTO, message envelope, JSON/frame codec, TLS context và kiểu dùng chung |
| `server` | TCP/TLS, session, xác thực, nghiệp vụ, SQLite, media, UDP và dashboard |
| `client` | Swing/FlatLaf, kết nối bất đồng bộ, chat, media, trạng thái và UDP |
| `scripts`/`config` | Build, TLS local, chạy, seed, test, đóng gói và cấu hình |
| `docs` | Hiện trạng, kiến trúc, dữ liệu, protocol, test và release |

Phần nền đã có Java 17, Maven multi-module, tài khoản, hồ sơ, bạn bè/chặn, chat riêng/nhóm, lịch sử offline, receipt, truyền tệp qua server relay, SQLite, TCP/TLS, UDP discovery/typing và bộ kiểm thử. Client A không mở socket trực tiếp tới Client B; dữ liệu đi qua server. Package `com.p2p` chỉ là tên kế thừa, không đại diện cho kiến trúc đang chạy.

Các module bảng tin, bài viết, thích, bình luận và quản trị giữ để tương thích nhưng không phải trọng tâm demo SafeChat. Báo cáo phải tách rõ chức năng kế thừa và phần SafeChat bổ sung.

### 2.2. Mục tiêu giao diện SafeChat theo bố cục tham khảo

Màn hình chính dùng bố cục co giãn ba vùng, lấy kích thước kiểm thử chuẩn là 1.120×780 và kích thước tối thiểu là 900×620:

```text
Thanh điều hướng 72–205 px | Danh sách hội thoại 280–340 px | Vùng chat co giãn
                                                └─ panel thông tin tùy chọn khi đủ rộng
```

- **Thanh điều hướng:** avatar/tên người dùng, Tin nhắn, Nhóm, Bạn bè, Cảnh báo ảnh, Thông báo, Hồ sơ/Cài đặt và trạng thái kết nối. Ở cửa sổ hẹp thanh này thu gọn thành biểu tượng có tooltip.
- **Danh sách hội thoại:** ô tìm kiếm, bộ lọc cá nhân/nhóm, avatar, tên, bản xem trước tin nhắn cuối, thời gian, unread badge và presence. Preview phải lấy từ state đã được server kiểm quyền.
- **Vùng chat:** header có avatar, tên, online/last seen và nút thông tin; lịch sử bubble theo người gửi; trạng thái `PENDING/STORED/DELIVERED/READ`; composer có đính kèm, tiến trình và gửi.
- **Panel thông tin:** thành viên nhóm, tệp/ảnh trong cuộc trò chuyện và tùy chọn rời/chặn khi chiều rộng cho phép; có thể đóng để ưu tiên vùng chat.
- **Ảnh có AI:** card/placeholder phải hiển thị chữ và biểu tượng cùng nhau, không chỉ dựa vào màu. `CHECKING` có tiến trình; `WARNING` yêu cầu xác nhận; `BLOCKED` nêu lý do chính sách; `UNAVAILABLE` cho phép thử lại nhưng không gọi ảnh là an toàn.

Ngôn ngữ hiển thị là tiếng Việt, font và khoảng cách ưu tiên khả năng đọc. Token ban đầu dùng nền tối FlatLaf đang có: nền ứng dụng `#10131A`, panel `#171B24`, card `#202532`, accent `#5C66EB`, chữ chính `#F4F6FB`, chữ phụ `#A7AFBF`, thành công `#43D19E`, cảnh báo `#F0B429`, nguy hiểm `#E56B6F`. Có thể điều chỉnh token sau khi có screenshot chạy thật, nhưng không đổi luồng nghiệp vụ để phục vụ trang trí.

## 3. Phạm vi SafeChat

### Trong phạm vi

- Đăng ký/đăng nhập, phiên người dùng và đồng bộ sau reconnect.
- Chat riêng/nhóm, lịch sử, receipt và thông báo.
- Gửi tệp/ảnh qua TCP/TLS theo chunk, giới hạn kích thước, checksum, resume và kiểm quyền.
- UDP discovery tìm server LAN và UDP typing indicator có TTL/HMAC.
- Kiểm tra ảnh ở server trước khi phát cho người nhận.
- Cảnh báo sender/receiver, chặn ảnh nguy cơ cao, log quyết định và evidence.
- Build, test, demo nhiều client và đóng gói.

### Ngoài phạm vi

- Tự huấn luyện computer vision, cloud/microservices, mobile, video/voice hoặc multi-server.
- Mã hóa đầu cuối khiến server không thể kiểm tra ảnh.
- Dùng UDP cho tin nhắn hoặc ảnh cần lưu.
- Công bố Accuracy/Precision/Recall/F1 khi chưa có bộ ảnh có nhãn và lần chạy API thật.

## 4. Kiến trúc và phân công giao thức

```text
Client A (Swing) -- TCP/TLS --> Server <-- TCP/TLS -- Client B
       \-- UDP discovery/typing --/       |
                                  SQLite + media storage
                                          |
                                  Image Moderator HTTPS API
```

### TCP/TLS

Dùng cho HELLO, đăng nhập, thao tác chat, ACK/receipt, history, upload/download, xác nhận cảnh báo và đồng bộ lại. Bật TLS 1.2+, ưu tiên 1.3; demo LAN dùng certificate tự ký và truststore riêng, không trust-all. Server là nguồn sự thật và kiểm tra lại mọi payload từ client.

### UDP

- **Discovery:** client broadcast nonce/version, server trả offer IP/TCP port. Offer chỉ là gợi ý; nếu UDP bị chặn thì nhập IP thủ công và vẫn chat TCP.
- **Typing:** packet có sequence, HMAC/khóa phiên và TTL ngắn. Mất/trùng/hết hạn chỉ bỏ trạng thái tạm, không mất tin nhắn và không ghi DB.

### AI

Dùng interface `ImageModerator` và adapter gọi provider qua HTTPS tại server. API key chỉ ở server, lấy từ biến môi trường/secret runtime. Timeout, HTTP error, JSON sai, thiếu key hoặc provider lỗi phải thành `UNAVAILABLE`, không thành `CLEAR`; chat văn bản vẫn hoạt động.

## 5. Chính sách ảnh

Server nhận upload tạm qua TCP/TLS, kiểm magic bytes/nội dung thật, PNG/JPEG, tối đa 5 MiB, tối đa 20 megapixel, SHA-256, quota, quyền conversation và path traversal. Ảnh chưa có quyết định không được phát.

| Trạng thái | Hành vi |
|---|---|
| `CHECKING` | Đang đánh giá; chưa tạo message ảnh phát cho người nhận |
| `CLEAR` | Không có tín hiệu vượt ngưỡng cấu hình; cho phép hoàn tất gửi |
| `WARNING` | Sender phải xác nhận; receiver xác nhận trước xem/tải; không auto-thumbnail |
| `BLOCKED` | Từ chối phát ảnh, audit và dọn/cô lập bản tạm |
| `UNAVAILABLE` | Chưa kiểm tra được; không gửi; cho phép thử lại |

Lưu `mediaId`, SHA-256, provider, model, reason, threshold/policy version và timestamp UTC. Client không tự sửa quyết định; download luôn kiểm tra quyền hiện tại.

## 6. Dữ liệu, bảo mật và vận hành

Các bảng đang dùng: `users`, `profiles`, `relationships`, `blocks`, `conversations`, `conversation_members`, `messages`, `media`, `media_bindings`, `file_transfers`, `idempotency_records`, `image_moderations`, `audit_logs`, `outbox_events`. Session được quản lý trong runtime; receipt dùng sequence trong `conversation_members`. Không tạo thêm bảng chỉ để khớp tên khái niệm trong kế hoạch.

SQLite phù hợp một server đồ án. Ghi DB qua write executor/transaction ngắn; không giữ transaction khi chờ API. File nằm ngoài DB theo `mediaId`/checksum; DB giữ metadata. Message có `clientMessageId` để retry không tạo bản ghi trùng; outbox commit cùng mutation.

Password hash, prepared statement, giới hạn frame/string/collection, rate limit, connection/queue/chunk/timeout và kiểm quyền phải ở server. Hash file, gọi AI, đọc socket và ghi đĩa không chạy trên Swing EDT. Log không ghi password, API key hoặc ảnh nhạy cảm.

## 7. Giai đoạn triển khai

| Giai đoạn | Thời gian | Đầu ra |
|---|---:|---|
| P0 Khảo sát | 08–10/10 | Audit source, sơ đồ, phần kế thừa/mới |
| P1 Đề cương | 08–11/10 | Đề cương đúng tên, phạm vi và lịch |
| P2 Ổn định nền | 12–18/10 | Java 17, TLS, auth, chat, history, reconnect |
| P3 Media | 19–25/10 | Chunk, size/type/hash/quota, quyền, resume |
| P4 AI server | 26/10–01/11 | Adapter, states, persistence, timeout/error, fixture |
| P5 AI client | 02–08/11 | Dialog warning, xác nhận sender/receiver, UI không treo |
| P6 UDP/hardening | 09–15/11 | Discovery, typing, HMAC/TTL, audit, privacy regression |
| P7 Test/demo | 16–22/11 | Test report, nhiều client, evidence |
| P8 Báo cáo | 23/11–01/12 | JAR, hướng dẫn, báo cáo, slide |

Chỉ ghi “đã hoàn thành” khi có test hoặc evidence tương ứng.

### 7.1. Lớp triển khai giao diện

| Pha UI | Nội dung | Tiêu chí đầu ra |
|---|---|---|
| U0 Audit tham khảo | Chốt màn hình, token, kích thước, trạng thái và phần ngoài phạm vi | Tài liệu được duyệt; chưa sửa code |
| U1 App shell | MainFrame, thanh điều hướng, vùng nội dung, trạng thái kết nối, scaling | Mở được shell ở 100/150/200%, không treo EDT |
| U2 Conversation/chat | Danh sách hội thoại, tìm kiếm, unread, presence, bubble, composer | Hai client chat và reconnect giữ đúng hội thoại đang chọn |
| U3 Media safety | Card ảnh, tiến trình, dialog sender/receiver, bốn trạng thái AI | Có screenshot CLEAR/WARNING/BLOCKED/UNAVAILABLE và test tương ứng |
| U4 Polish | Khoảng cách, icon, keyboard, empty/error/loading, panel thông tin | Checklist UX đạt trên cửa sổ chuẩn và tối thiểu |

U1–U4 chỉ bắt đầu sau cổng phê duyệt tài liệu ở mục 1.1. Mỗi pha phải giữ nguyên contract TCP/UDP và chính sách server; UI không được tự quyết định quyền xem ảnh.

## 8. Kiểm thử và nghiệm thu

Unit/contract: codec, DTO, format, size, magic bytes, SHA-256, policy mapping, idempotency, quyền media và adapter response hợp lệ/sai/timeout/error/missing key.

Integration/runtime: hai client qua TCP/TLS; login, chat riêng/nhóm, history, reconnect; ảnh hợp lệ/đổi đuôi/quá lớn/checksum sai; WARNING cần xác nhận; BLOCKED không phát; UNAVAILABLE retry; UDP mất gói không ảnh hưởng chat; revoked session không tải media.

Fixture provider chỉ kiểm tra logic tích hợp, không chứng minh độ chính xác AI thật.

SafeChat đạt nghiệm thu khi: một server và ít nhất hai client chạy được; chat/history/reconnect không tạo tin trùng; file/ảnh qua server có kiểm tra; UDP discovery/typing là phụ trợ; ảnh luôn qua `CHECKING`; bốn trạng thái có hành vi riêng; secret không nằm trong source/release; build/test/demo chạy từ bản sao sạch; báo cáo phân biệt kế thừa, SafeChat mới, fixture và API thật.

## 9. Demo, rủi ro và tài liệu đầu ra

Demo: khởi động DB mới/TLS, mở Alice/Bob, chứng minh A → Server → B, gửi text và history, thử discovery/typing, chạy CLEAR/WARNING/BLOCKED/UNAVAILABLE, lưu log/mediaId/screenshot. Screenshot UI tối thiểu gồm login, shell ba vùng, danh sách hội thoại, chat/composer, dialog WARNING và card BLOCKED/UNAVAILABLE. Không dùng màu giao diện, tên ảnh hoặc checksum làm nhận diện bạo lực.

Rủi ro chính gồm API chậm/lỗi/thiếu key, cảnh báo nhầm, lộ ảnh, UDP bị chặn, SQLite lock và scope lớn. Xử lý bằng timeout/`UNAVAILABLE`, wording không tuyệt đối, HTTPS/log tối thiểu, fallback IP, transaction ngắn/queue/chunk/quota và giữ module ngoài scope ở chế độ tham khảo.

Đầu ra: `chat-java/plan.md`, `chat-java/workflow.md`, đề cương `output/docs/De-cuong-chi-tiet-DACS4-SafeChat-Le-Cao-Son-Tien.docx`, báo cáo sau implementation và test/release evidence. Không viết số liệu AI thật trước khi có dữ liệu và lần chạy tương ứng.

## 10. Trạng thái triển khai 08/10/2026

Code SafeChat đã có adapter moderation, trạng thái bền vững và UI xác nhận; tiếp tục phát triển trên bản này. Full Maven package hiện có 95 test PASS (common 6, server 76, client 13). Smoke JAR có TLS, UDP và giao diện Swing; smoke riêng không có khóa xác nhận UNAVAILABLE và chat text vẫn chạy. Chi tiết và giới hạn tại `docs/safechat-implementation.md`. Các mốc lịch ở mục 7 là lịch dự kiến, không phải bằng chứng mọi hạng mục đã nghiệm thu.
