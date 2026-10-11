# Tài liệu Đặc tả Thiết kế Giao diện (UI/UX Design Specification) SafeChat

**Dự án:** SafeChat — Ứng dụng Nhắn tin Desktop Bảo mật với AI Moderation  
**Ngôn ngữ:** Java 17 (Java Swing + FlatLaf)  
**Tiêu chuẩn thiết kế:** Modern Desktop Messaging Pattern (Telegram Desktop / Slack / Messenger)  

---

## 1. Hệ thống Design Tokens

### 1.1. Bảng màu (Color Palette)

| Mã Token | Giá trị Hex | Màu hiển thị / Mục đích |
|---|---|---|
| `BG_APP` | `#0D1117` | Nền tổng thể của toàn bộ ứng dụng (Dark Slate) |
| `BG_SIDEBAR` | `#161B22` | Nền thanh điều hướng bên trái và danh sách hội thoại |
| `BG_SURFACE` | `#21262D` | Nền card, popover, thanh tìm kiếm, composer |
| `BG_CHAT` | `#0D1117` | Nền khung trò chuyện chính |
| `BG_BUBBLE_SELF` | `#4F46E5` | Nền bong bóng tin nhắn của chính mình (Indigo hiện đại) |
| `BG_BUBBLE_PEER` | `#21262D` | Nền bong bóng tin nhắn của đối phương / thành viên khác |
| `BG_HOVER` | `#30363D` | Màu nền khi rê chuột lên item/nút |
| `PRIMARY` | `#6366F1` | Màu chủ đạo (Action, Active Tabs, Highlights) |
| `PRIMARY_HOVER` | `#4F46E5` | Màu khi hover lên nút chính |
| `TEXT_PRIMARY` | `#F0F6FC` | Chữ chính, độ tương phản cao, dễ đọc |
| `TEXT_SECONDARY` | `#8B949E` | Chữ phụ, ghi chú thời gian, mô tả, placeholder |
| `TEXT_MUTED` | `#6E7681` | Chữ mờ, trạng thái không khả dụng |
| `BORDER` | `#30363D` | Viền ngăn cách giữa các cột và panel |
| `STATUS_ONLINE` | `#10B981` | Màu xanh lục chỉ trạng thái đang hoạt động (Online) |
| `STATUS_WARNING`| `#F59E0B` | Màu hổ phách cảnh báo AI (WARNING / Cần xác nhận) |
| `STATUS_DANGER` | `#EF4444` | Màu đỏ chặn nội dung (BLOCKED / Lỗi / Nguy hiểm) |
| `STATUS_INFO`   | `#3B82F6` | Màu xanh dương thông tin / Đang kiểm tra (CHECKING) |

### 1.2. Kiểu chữ (Typography)
- **Font gia đình:** `"Segoe UI"`, fallback `"Helvetica Neue"`, `"Arial"`, `"Sans-serif"`.
- **Heading 1:** Bold, 22–24pt (Tiêu đề ứng dụng, Server Title).
- **Heading 2:** Bold, 16–18pt (Tên cuộc trò chuyện, Card Title).
- **Body Regular:** Plain, 13–14pt (Nội dung tin nhắn, nhãn trường, menu).
- **Body Bold:** Bold, 13–14pt (Tên người gửi, tên nhóm, nút bấm).
- **Caption / Meta:** Plain, 11–12pt (Thời gian, trạng thái đã đọc, phụ đề).
- **Badge / Micro:** Bold, 10–11pt (Số tin chưa đọc, huy hiệu AI).

### 1.3. Bo góc & Khoảng cách (Corner Radii & Spacing)
- **Corner Radii:**
  - Nút bấm (Button): `8px` đến `12px` (hoặc `999px` kiểu viên thuốc - pill button).
  - Bong bóng tin nhắn (Message Bubble): `14px` bo tròn mềm mại.
  - Avatar: `999px` (tròn hoàn toàn) kèm viền online.
  - Card & Container: `12px` đến `16px`.
  - Ô nhập liệu (Input Fields): `10px`.
- **Spacing:**
  - Cột Nav: rộng `72px` (thu gọn) hoặc `210px` (mở rộng).
  - Cột Danh sách hội thoại: `300px` cố định.
  - Khung Chat: Tự động co giãn (Flexible `BorderLayout.CENTER`).
  - Info Panel: `280px` (có thể bật/tắt).

---

## 2. Bản vẽ Wireframe Kiến trúc Giao diện

### 2.1. Wireframe Màn hình Đăng nhập (Auth Screen)
```text
+-----------------------------------------------------------------------+
|                                                                       |
|                     +---------------------------+                     |
|                     |        [🛡️ SafeChat]      |                     |
|                     |   Nhắn tin an toàn & AI   |                     |
|                     |                           |                     |
|                     | [Tên đăng nhập          ] |                     |
|                     | [Mật khẩu               ] |                     |
|                     |                           |                     |
|                     |    [   ĐĂNG NHẬP   ]      |                     |
|                     |   [ Tạo tài khoản mới ]   |                     |
|                     |                           |                     |
|                     | > Cài đặt kết nối nâng cao|                     |
|                     |   (Máy chủ, TLS, LAN)     |                     |
|                     |                           |                     |
|                     | ● Kết nối an toàn TLS     |                     |
|                     +---------------------------+                     |
|                                                                       |
+-----------------------------------------------------------------------+
```

### 2.2. Wireframe Màn hình Chính (Main Three-Column Chat Layout)
```text
+------+-----------------------+----------------------------------------+------------------+
| Nav  | Cuộc trò chuyện   [+] | [Avatar] Bạn B             [⚙️][ℹ️]    | Thông tin nhóm   |
+------+-----------------------+----------------------------------------+------------------+
| [Av] | [🔍 Tìm kiếm...     ] |       Hôm nay, 10:24                   | [Avatar lớn]     |
|      | [Tất cả] [Bạn] [Nhóm] |                                        | Nhóm Lập Trình   |
| [💬] |                       |   [Avatar B]                           | 5 thành viên     |
| Chat | [●] Bạn A             |   +-------------------------------+    |                  |
|      | Xin chào bạn!   10:30 |   | Chào buổi sáng cả nhà!       |    | Thành viên:      |
| [👥] | [● 2]                 |   | 10:25                         |    | - Tiến (Bạn)     |
| Bạn  |                       |   +-------------------------------+    | - Alice (Chủ)    |
|      | [👥] Nhóm Học Tập     |                                        | - Bob            |
| [🔔] | Bob: Tệp đã tải xong  |                  +------------------+  |                  |
| Báo  |                 10:28 |                  | Chào bạn! Mình   |  | Tệp & Ảnh:       |
|      |                       |                  | vừa nộp bài tập. |  | - de_cuong.pdf   |
| [⚙️] |                       |                  | 10:26         ✓✓ |  | - anh_nhom.png   |
| Cài  |                       |                  +------------------+  |                  |
|      |                       |                                        | [ Rời nhóm ]     |
| [🚪] |                       | Bạn B đang nhập...                     |                  |
+------+-----------------------+----------------------------------------+                  |
| TLS● |                       | [😊] [📎] [ Nhập tin nhắn...        ] [➤] | [ Đóng panel ]   |
+------+-----------------------+----------------------------------------+------------------+
```

### 2.3. Wireframe Thẻ Cảnh báo AI (AI Moderation Image Card)
```text
+-------------------------------------------------------+
|  +-------------------------------------------------+  |
|  |  ⚠️ CẢNH BÁO NỘI DUNG NHẠY CẢM                   |  |
|  |  AI phát hiện hình ảnh có dấu hiệu bạo lực.     |  |
|  |  (Mức độ tin cậy: 88% bạo lực)                  |  |
|  |                                                 |  |
|  |  [   Xem ảnh có cảnh báo   ]   [ Bỏ qua / Hủy ] |  |
|  +-------------------------------------------------+  |
|  Gửi lúc 10:27 · Chưa hiển thị nội dung cho đến khi mở|
+-------------------------------------------------------+
```

### 2.4. Wireframe Dashboard Quản trị Server (Server Admin Dashboard)
```text
+-----------------------------------------------------------------------------------------------+
|  🛡️ SafeChat Server Dashboard                   ● ĐANG HOẠT ĐỘNG (TCP: 5000 | UDP: 5001)     |
+-----------------------------------------------------------------------------------------------+
| [ 👥 12 ]       [ 🟢 8 ]         [ 💬 15 ]        [ ✉️ 1,420 ]     [ 🖼️ 85 ]      [ ⚠️ 4 ]     |
| Kết nối TCP     Users Online     Cuộc trò chuyện  Tin nhắn        Ảnh đã duyệt   Cảnh báo AI  |
+-----------------------------------------------------------------------------------------------+
| PHIÊN HOẠT ĐỘNG (SESSIONS)                      | THỐNG KÊ DUYỆT ẢNH AI (MODERATION)          |
| +---------------------------------------------+ |  Tổng ảnh đã xử lý: 85                      |
| | Username | Địa chỉ IP       | Lúc   | HĐ    | |  - [CLEAR]       76 ảnh (89%)  [█████████░] |
| | alice    | 192.168.1.10:5021| 09:15 | [Ngắt]| |  - [WARNING]      6 ảnh ( 7%)  [█░░░░░░░░░] |
| | bob      | 192.168.1.12:5024| 09:30 | [Ngắt]| |  - [BLOCKED]      2 ảnh ( 2%)  [░░░░░░░░░░] |
| | carol    | 127.0.0.1:5042   | 10:01 | [Ngắt]| |  - [UNAVAILABLE]  1 ảnh ( 1%)  [░░░░░░░░░░] |
| +---------------------------------------------+ +---------------------------------------------+
| NHẬT KÝ SERVER (LOGS)                           | Bộ lọc: [TẤT CẢ] [INFO] [WARN] [ERROR]      |
| +-------------------------------------------------------------------------------------------+ |
| | [10:30:12 INFO] Client alice kết nối thành công từ 192.168.1.10:5021                      | |
| | [10:30:25 INFO] Kiểm duyệt ảnh media-842: CLEAR (OpenAI omni-moderation-latest)            | |
| | [10:31:02 WARN] Kiểm duyệt ảnh media-843: WARNING (violence score: 0.82)                   | |
| +-------------------------------------------------------------------------------------------+ |
| [ 🛑 Dừng Server ]  [ 🔄 Làm mới ]  [ 📁 Thư mục dữ liệu ]  [ 💾 Xuất file log ]              |
+-----------------------------------------------------------------------------------------------+
```
