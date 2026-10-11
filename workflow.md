# Workflow vận hành và phát triển SafeChat

Tài liệu này mô tả workflow cho đề tài **Chat Java Client–Server sử dụng TCP/UDP tích hợp AI cảnh báo ảnh bạo lực**. Server là nơi quyết định quyền, trạng thái ảnh và dữ liệu bền vững.

## 0. Cổng phạm vi giao diện trước khi lập trình

Bố cục chat trong tài liệu Studocu chỉ là nguồn tham khảo cho cấu trúc trải nghiệm: thanh điều hướng, danh sách hội thoại, vùng chat, presence và cảnh báo ảnh. SafeChat triển khai lại bằng Swing/FlatLaf trên desktop, không sao chép code, asset hoặc cam kết giống từng pixel. Gọi video/WebRTC không thuộc phạm vi phiên bản đồ án cá nhân.

Trong giai đoạn hiện tại, workflow chỉ cho phép phân tích và sửa `plan.md`, `workflow.md` và `docs/06-desktop-ux.md`. Cổng `G0` được đóng khi sinh viên duyệt các tài liệu này; trước `G0` không sửa Java UI theo thiết kế mới.

## 1. Workflow phát triển

```text
Khảo sát source
  -> chốt requirement/acceptance criteria
  -> audit bố cục tham khảo + token UI (U0)
  -> sửa đề cương
  -> ổn định TCP/TLS + SQLite + chat
  -> hoàn thiện media transfer
  -> tích hợp AI ở server
  -> dựng app shell và chat 3 vùng (U1–U2)
  -> nối UI cảnh báo ở client (U3)
  -> polish/responsive/keyboard (U4)
  -> unit/integration/runtime test
  -> demo nhiều client + evidence
  -> đóng gói + báo cáo
```

Mỗi lát cắt đi theo thứ tự: common contract → server policy/service → client service/state → UI → test → tài liệu. Không sửa UI để bỏ qua kiểm tra server và không ghi “đã hoàn thành” khi chưa có test/evidence.

## 2. Khởi động và kết nối

### Server

1. Kiểm tra Java 17, Maven và thư mục làm việc.
2. Tạo TLS local; giữ `server.p12` và mật khẩu trên máy server.
3. Dùng database/storage riêng cho demo; cấu hình TCP, UDP discovery, UDP typing, quota, giới hạn ảnh và media root.
4. Nếu dùng API thật, đặt `OPENAI_API_KEY` trong môi trường tiến trình server; không ghi vào source, properties, log hay chat.
5. Khởi động và kiểm tra migration, bind TCP/UDP, TLS và moderation provider.

### Client

1. Nhận `truststore.p12` qua kênh tin cậy; không nhận private key.
2. Nhập IP/hostname hoặc chạy discovery.
3. Kết nối TLS đến TCP server; TLS fail thì báo lỗi, không tự trust certificate mới.
4. Gửi `HELLO`/version rồi mới cho phép login.

## 3. Đăng ký, đăng nhập và phiên

```text
TLS connect -> HELLO/version -> AUTH_REGISTER hoặc AUTH_LOGIN
-> server validate/rate-limit/hash/verify
-> authenticated session + UDP key tạm thời
-> profile/quyền/cursor -> STATE_SYNC history/thông báo
```

Actor lấy từ session server, không lấy từ `userId` tùy ý trong payload. Logout/revoke làm mất UDP key, transfer token và request đang chờ. Reconnect tạo transport/executor mới.

### 3.1. Luồng vào giao diện chính

```text
ServerConnectionPanel -> Login/RegisterPanel -> tải profile/conversation/unread
-> MainFrame mở ở hội thoại gần nhất hoặc trạng thái empty
```

MainFrame giữ một state kết nối authoritative và một hội thoại đang chọn. Chuyển mục điều hướng không tạo socket mới; chọn hội thoại mới tải history qua service, sau đó cập nhật vùng chat trên EDT. Khi mất mạng, banner và trạng thái `RECONNECTING` xuất hiện ở shell; không hiển thị người dùng là offline nếu presence chưa được server xác nhận.

### 3.2. Luồng bố cục chat ba vùng

1. Thanh điều hướng hiển thị mục hiện tại, unread badge và trạng thái TLS/UDP.
2. Conversation list tải preview đã kiểm quyền, sắp xếp theo tin mới nhất và giữ mục đang chọn sau reconnect.
3. Chat panel tải trang cuối, giữ scroll anchor khi tải tin cũ, render bubble theo sequence và deduplicate theo `messageId`.
4. Composer gửi text qua TCP; typing đi UDP; đính kèm ảnh hiển thị tiến trình và không phát bytes trước decision AI.
5. Panel thông tin chỉ là vùng phụ; đóng/mở không được làm mất message state hoặc quyền server.

### 3.3. Mapping trạng thái ảnh vào UI

| State server | Sender UI | Receiver UI |
|---|---|---|
| `CHECKING` | tiến trình “Đang kiểm tra” và khóa gửi lặp | chưa hiển thị ảnh |
| `CLEAR` | card ảnh gửi thành công | thumbnail/tải theo quyền |
| `WARNING` | lý do + Confirm/Cancel | placeholder + View/Download cần xác nhận |
| `BLOCKED` | thông báo bị từ chối và lý do ngắn | không có bytes/thumbnail |
| `UNAVAILABLE` | “Chưa kiểm tra được” + Retry | không nhận ảnh |

Nội dung, màu và icon chỉ biểu đạt decision đã nhận; client không tự chuyển `WARNING` thành `CLEAR`.

## 4. Kết nối Client–Server và TCP chat

```text
Client A -- TLS/TCP --> Server <-- TLS/TCP -- Client B
```

Client không kết nối trực tiếp đến client khác. Server đọc frame, giải mã envelope, kiểm version/session, gọi service, commit DB/outbox rồi enqueue response/event. Mỗi connection có một writer; queue giới hạn frame/byte.

1. Client validate độ dài, tạo `clientMessageId`, gửi `MESSAGE_SEND`.
2. Server kiểm session, membership, block/privacy, rate limit và idempotency.
3. Server commit message, sequence và outbox trong transaction ngắn.
4. Server trả ACK sender và phát event cho thành viên online.
5. Client cập nhật theo messageId/sequence; event trùng bị deduplicate.
6. Mất kết nối sau gửi thì retry cùng `clientMessageId`; login lại thì fetch sau cursor/sequence.

Tin nhắn văn bản không phụ thuộc API AI.

## 5. UDP discovery và typing

### Discovery

```text
Client broadcast DISCOVER(nonce, version)
-> Server OFFER(serverId, serverName, tcpPort, nonce)
-> Client kiểm nonce/version/port -> TLS xác minh -> login
```

Offer chỉ gợi ý địa chỉ. Bỏ offer sai; nếu không có offer thì nhập IP thủ công.

### Typing

1. Sau login TCP, server cấp UDP key/session token có hạn.
2. Client đăng ký endpoint bằng sequence và HMAC.
3. Gửi `TYPING` với trường `typing=true/false`, có TTL ngắn khi người dùng gõ.
4. Server kiểm HMAC, sequence, session và membership rồi forward.
5. Client tự hết hạn indicator; packet mất/trùng/hết hạn bị bỏ và không ghi DB.
6. UDP lỗi thì chat TCP vẫn hoạt động.

## 6. Gửi tệp thông thường

```text
Chọn file -> MEDIA_UPLOAD_INIT -> server kiểm kích thước/quota
-> MEDIA_UPLOAD_CHUNK -> MEDIA_UPLOAD_COMMIT -> media READY
-> MESSAGE_SEND(mediaId) -> kiểm quyền conversation -> media ATTACHED
-> receiver MEDIA_DOWNLOAD_INIT -> nhận chunk -> verify SHA-256
```

Đây là protocol v2 đang chạy; `FILE_OFFER` là luồng legacy. Upload được lưu trên server trước khi đính kèm message; không cần receiver online hoặc accept trước upload. Server không tin filename, MIME hoặc size do client khai báo. Timeout/abort dọn file tạm; download kiểm quyền hiện tại.

## 7. Gửi ảnh có AI

### Upload và validate

1. Client chọn PNG/JPEG, hiển thị giới hạn 5 MiB/20 megapixel.
2. Gửi chunk qua TCP/TLS với transferId.
3. Server giới hạn bytes, tính SHA-256, kiểm magic bytes và decode ảnh thật.
4. Sai format, quá lớn, checksum sai, quota đầy hoặc thiếu quyền thì hủy.
5. Hợp lệ thì tạo decision `CHECKING`; chưa tạo message ảnh phát cho receiver.

### Gọi provider

```text
MediaService -> kiểm policy/limits -> ImageModerator adapter
-> HTTPS provider -> parse/map decision -> lưu decision/audit
```

API key chỉ ở server. Timeout, HTTP error, thiếu key, malformed response hoặc provider không hỗ trợ ảnh đều thành `UNAVAILABLE`; không map lỗi thành `CLEAR`; không log ảnh/API key.

### Mapping quyết định

| Decision | Sender | Receiver | Storage/message |
|---|---|---|---|
| `CLEAR` | Hoàn tất gửi | Xem/tải theo quyền | Commit media + message + outbox |
| `WARNING` | Hiện reason, bắt buộc confirm/cancel | Placeholder, confirm trước xem/tải | Lưu decision; không auto-thumbnail |
| `BLOCKED` | Thông báo từ chối | Không nhận ảnh | Không phát message ảnh; audit |
| `UNAVAILABLE` | “Chưa kiểm tra”, retry | Không nhận ảnh | Giữ/xóa tạm theo retention; không commit |

## 8. Xác nhận cảnh báo

### Sender

```text
WARNING -> dialog lý do -> Cancel hoặc Confirm
-> server kiểm token/decision/quyền -> commit nếu policy cho phép
```

Confirm là sự đồng ý của người dùng, không đổi WARNING thành CLEAR. Client không tự sửa decision.

### Receiver

```text
Metadata WARNING -> placeholder -> View/Download -> dialog
-> Confirm -> MEDIA_DOWNLOAD_INIT(warningAcknowledged=true)
-> server kiểm quyền/decision -> transfer gắn session, có hạn
-> tải chunk + verify SHA-256
```

Cancel không phát bytes. Rời phòng/bị block thì server từ chối dù UI còn message cũ.

## 9. BLOCKED, UNAVAILABLE và reconnect

**BLOCKED:** trả lý do chính sách cần thiết, không retry vô hạn, dọn/cô lập bản tạm và ghi audit.

**UNAVAILABLE:** hiển thị “chưa kiểm tra được”, không hiển thị “ảnh an toàn”; cho thử lại; chat văn bản và file không cần moderation vẫn chạy.

Khi EOF/timeout: client offline, giữ pending ID, TLS reconnect, HELLO, LOGIN, STATE_SYNC; server trả trạng thái idempotency; upload resume nếu token còn hạn; fetch sau cursor và deduplicate theo eventId/messageId. Policy hiện tại áp dụng khi membership/quyền đã đổi.

## 10. Workflow kiểm thử

Trước mỗi thay đổi: đọc contract/test liên quan, chạy test module bị ảnh hưởng và kiểm diff không có secret/data thật.

Bộ tối thiểu: compile/test Java 17; unit codec/format/size/checksum/policy/idempotency/quyền; fixture clear/warning/blocked/timeout/HTTP error/malformed/missing key; runtime hai client TCP/TLS; runtime AI warning/blocked/unavailable/retry; runtime UDP discovery/HMAC/TTL/mất gói; regression path traversal, đổi extension/MIME, revoked session và unauthorized download.

Fixture chỉ kiểm tra logic tích hợp, không chứng minh accuracy AI thật.

### 10.1. Kiểm thử giao diện và screenshot

- Chụp login, shell ba vùng, conversation list, chat/composer, panel thông tin và dialog WARNING ở 100%, 150% và 200% scaling.
- Kiểm tra cửa sổ chuẩn 1.120×780 và tối thiểu 900×620; ở chiều rộng hẹp panel thông tin đóng được, không cắt nút gửi hoặc dialog.
- Kiểm tra Tab/Enter/Shift+Enter/Esc, emoji và tiếng Việt, loading/empty/error/disconnected, double-click khi request pending.
- Chạy chậm mạng và upload lớn để xác nhận EDT vẫn kéo được cửa sổ, điều hướng và hiển thị progress.
- Screenshot chỉ là evidence giao diện đã chạy; wireframe hoặc tài liệu không thay thế runtime evidence.

## 11. Workflow demo, release và báo cáo

1. Dùng DB mới và TLS local. Script smoke inject fixture riêng; demo production giữ kiểm tra ảnh bắt buộc, thiếu khóa thì UNAVAILABLE.
2. Mở Alice/Bob, login, chứng minh A → Server → B, gửi text và history sau reconnect.
3. Chạy discovery/typing rồi tắt UDP để chứng minh TCP vẫn hoạt động.
4. Chạy CLEAR, WARNING, BLOCKED, UNAVAILABLE; lưu mediaId, log và screenshot.
5. Nếu API thật, thông báo ảnh gửi tới provider, giữ key ngoài màn hình và không commit ảnh nhạy cảm.
6. Build source sạch bằng Java 17/Maven, chạy test, lưu JUnit/log, package JAR/script/config mẫu/checksum.
7. Loại private key, password.local, database, media upload, API key và Maven cache khỏi release.

Evidence ghi ngày giờ, source version, command, môi trường, test case, kết quả và giới hạn; tách local/LAN, fixture/mock, API thật và chưa kiểm chứng. Báo cáo đi theo bài toán → yêu cầu → source kế thừa → TCP/UDP → AI policy → dữ liệu/bảo mật → triển khai → test → demo → hạn chế. Không ghi Accuracy/Precision/Recall/F1 khi chưa có bộ ảnh có nhãn và phép đo lặp lại.

## 12. Checklist cuối

- [ ] Server và ít nhất hai client chạy TCP/TLS.
- [ ] Chat riêng/nhóm, history, receipt, reconnect.
- [ ] File/ảnh qua server, limit/checksum/resume/quyền.
- [ ] UDP chỉ discovery/typing; mất UDP không mất chat.
- [ ] Ảnh luôn qua CHECKING trước khi phát.
- [ ] CLEAR/WARNING/BLOCKED/UNAVAILABLE có hành vi và test riêng.
- [ ] Secret/private key/database/media cá nhân không nằm trong source/release.
- [ ] Evidence và báo cáo phân biệt kế thừa, SafeChat mới, fixture và API thật.
