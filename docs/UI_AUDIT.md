# Báo cáo Đánh giá Giao diện Hiện tại (UI/UX Audit) & Hướng Cải tiến SafeChat

**Dự án:** SafeChat — Ứng dụng nhắn tin Desktop Java 17 bảo mật với AI Moderation  
**Tác giả:** Senior Java Desktop UI/UX Engineer  
**Ngày thực hiện:** 10/10/2026  

---

## 1. Tổng quan & Khảo sát Hiện trạng

Hệ thống SafeChat hiện được xây dựng trên:
- **Ngôn ngữ & Nền tảng:** Java 17 (LTS), thư viện giao diện Java Swing kết hợp Look and Feel **FlatLaf 3.6.1**.
- **Kiến trúc mạng:** Client–Server trung tâm qua giao thức TCP/TLS cho mọi dữ liệu nghiệp vụ quan trọng (đăng nhập, tin nhắn, tệp, ảnh, lịch sử); UDP dùng cho tìm kiếm máy chủ LAN (Discovery) và trạng thái gõ phím thời gian thực (Typing indicator).
- **Lưu trữ & AI:** SQLite WAL ở Server; OpenAI Moderation API kiểm tra hình ảnh bạo lực độc lập ở phía Server.
- **Giao diện Client hiện tại:** Lớp `SocialWindow.java` đảm nhiệm toàn bộ màn hình từ đăng nhập đến cửa sổ nhắn tin chính.
- **Giao diện Server hiện tại:** Lớp `ServerMonitor.java` chỉ là một cửa sổ Swing tối giản gồm 1 `JTextArea` hiển thị thông số thô dạng văn bản và 1 nút dừng.

---

## 2. Các vấn đề UI/UX đã xác định (9 vấn đề trọng yếu)

### Vấn đề 1: Giao diện Client chưa mang ngôn ngữ thiết kế nhắn tin hiện đại
- Layout hiện tại tạo cảm giác như một biểu mẫu quản trị hệ thống cũ thay vì một ứng dụng nhắn tin mượt mà, thân thiện như Telegram hay Messenger.
- Các khoảng cách (spacing/padding) chưa được căn chuẩn tỉ lệ vàng; màu sắc thiếu độ tương phản phân cấp thị giác (visual hierarchy).

### Vấn đề 2: Bố cục khó hiểu, lộ quá nhiều nút và thuật ngữ kỹ thuật
- Các nút chức năng như "Đồng bộ", "Tải lịch sử cũ", "Tiếp tục tải tệp", "Bảng tin", "Đối chiếu bản mới", "Khôi phục tài khoản"... nằm rải rác trên màn hình chính, làm người dùng phổ thông bối rối.
- Thông điệp hiển thị các thuật ngữ backend như "SHA-256 hợp lệ", "Opaque cursor", "Expected version", "Idempotency key".

### Vấn đề 3: Màn hình đăng nhập rườm rà, thiếu thẩm mỹ
- Màn hình Auth hiển thị các trường kỹ thuật như IP máy chủ, Port, đường dẫn file `truststore.p12`, mật khẩu truststore ngay trước mắt người dùng.
- Thẻ đăng nhập không có nhận diện thương hiệu SafeChat tinh tế (chỉ là nhãn chữ thông thường), thiếu trạng thái kết nối trực quan sinh động.

### Vấn đề 4: Danh sách cuộc trò chuyện (Conversation List) chưa trực quan
- Các dòng hội thoại thiếu phân cấp rõ ràng giữa tiêu đề, tin nhắn xem trước và thời gian gửi.
- Unread badge chỉ là một chuỗi văn bản phụ ` · 3` thay vì huy hiệu pill/badge nổi bật trên nền tương phản.
- Thiếu chỉ báo phân biệt rõ ràng giữa cuộc trò chuyện cá nhân (Direct) và nhóm (Group) bằng biểu tượng.
- Trạng thái trống (Empty State) khi chưa có hội thoại chỉ hiển thị 1 dòng text khô khan.

### Vấn đề 5: Khu vực chat thiếu thứ bậc thị giác và bong bóng tin nhắn chưa tối ưu
- Bong bóng tin nhắn (message bubble) hiện dùng chung kiểu dáng của card chữ nhật thông thường, làm giảm cảm giác hội thoại sinh động.
- Chưa phân định rõ ràng giữa bong bóng gửi đi (căn phải, màu nhấn hiện đại) và bong bóng nhận về (căn trái, màu nền card dễ đọc).
- Thiếu biểu tượng trạng thái tin nhắn tinh tế (Đang gửi 🕒, Đã gửi ✓, Đã nhận ✓✓, Đã đọc ✓✓ màu xanh).
- Chưa có màn hình chào / Empty state sang trọng khi người dùng chưa chọn cuộc trò chuyện nào.

### Vấn đề 6: Khung soạn thảo (Composer) bất tiện và thiếu tính năng
- Ô nhập liệu tin nhắn là `JTextArea` thô, chưa có các nút chức năng hỗ trợ tiện ích: nút Emoji chọn biểu tượng cảm xúc nhanh, nút gửi đính kèm tệp/ảnh trực quan.
- Nút "Gửi" vẫn bật kể cả khi ô nhập liệu hoàn toàn rỗng, thiếu phản hồi trực quan hover/press.

### Vấn đề 7: Trải nghiệm kiểm duyệt hình ảnh AI (Moderation UI) chưa đủ rõ ràng
- Khi người nhận gặp ảnh bị cảnh báo (WARNING), hệ thống chỉ hiển thị một dòng chữ vàng nhỏ; thiếu thẻ cảnh báo (Warning Card) có hình ảnh che mờ (blur/placeholder), biểu tượng cảnh báo và nút xác nhận mở ảnh rõ ràng.
- Các trạng thái kiểm tra (CHECKING, CLEAR, WARNING, BLOCKED, UNAVAILABLE) cần có icon trực quan, huy hiệu màu sắc chuẩn mực và văn bản tiếng Việt dễ hiểu.

### Vấn đề 8: Giao diện Server giống console/terminal tạm bợ
- `ServerMonitor` hiện tại chỉ có một khung text trắng đen và nút dừng thô sơ.
- Quản trị viên không thể quan sát trực quan các chỉ số tải: số kết nối, người dùng online, tổng số tin nhắn, thống kê AI moderation (số ảnh clear/warning/blocked), danh sách các phiên client đang kết nối và nhật ký hoạt động có lọc màu.

### Vấn đề 9: Người dùng phổ thông phải tiếp xúc với các khái niệm hạ tầng mạng
- Người dùng chỉ muốn mở ứng dụng, nhập tài khoản và trò chuyện. Các thông số TCP, UDP, TLS, Truststore cần được đưa vào menu "Cài đặt kết nối nâng cao" và có giá trị mặc định thông minh.

---

## 3. Hướng giải quyết & Kế hoạch Nâng cấp

1. **Thiết kế lại Design System (`Theme.java` & `AppIcon.java`):**
   - Thiết lập bảng màu chuyên nghiệp (Telegram Dark / Obsidian Slate theme): Màu nền `#0F1218`, Màu Sidebar `#151A23`, Màu Chat Panel `#1A202C`, Màu Bong bóng tin nhắn của mình `#4F46E5` (Indigo hiện đại) / `#5C66EB`, Bong bóng đối phương `#252D3D`.
   - Bổ sung bộ Icon Vector tùy biến không phụ thuộc hình ảnh ngoài: SEND, ATTACHMENT, EMOJI, INFO, GROUP, USER, SEARCH, PLUS, CHECK, DOUBLE_CHECK, WARNING, BLOCK, CLOCK, SERVER, LOGS, REFRESH.
   - Bo góc mềm mại hiện đại: `arc` 16-24px cho card và bubble, avatar tròn có viền chỉ báo online (`#10B981`).

2. **Tái cấu trúc Màn hình Đăng nhập (Auth View):**
   - Thiết kế dạng Hero Card căn giữa màn hình với logo SafeChat, icon khiên bảo mật rực rỡ, khẩu hiệu "Nhắn tin an toàn & Bảo vệ bằng AI".
   - Thu gọn cài đặt Máy chủ (Host:Port) và Truststore vào một ngăn có thể mở rộng (Collapsible Accordion / Advanced Settings Panel), ẩn mặc định.
   - Thêm bộ chọn tìm kiếm LAN tự động thân thiện.

3. **Tái cấu trúc Màn hình Chính (Desktop Shell):**
   - Cột 1: Navigation Rail thanh lịch (70px compact hoặc 210px expanded) với avatar người dùng, icon Tin nhắn, Bạn bè, Cài đặt, Thông báo, Đăng xuất.
   - Cột 2: Conversation List (300px) có thanh tìm kiếm với icon kính lúp, bộ lọc Tất cả / Cá nhân / Nhóm dạng segment buttons hoặc tabs hiện đại, danh sách item có unread badge tròn nổi bật, indicator nhóm/cá nhân, online status.
   - Cột 3: Vùng Chat trung tâm: Header sang trọng hiển thị avatar lớn, tên, trạng thái hoạt động và nút mở thông tin. Lịch sử hiển thị bong bóng chat trái/phải với đuôi bo góc, timestamp, status icon.
   - Cột 4 (Trượt/Mở khi cần): Conversation Info Panel hiển thị chi tiết thành viên, tệp chia sẻ, các nút quản lý nhóm tiện lợi.
   - Responsive: Khi cửa sổ dưới 1050px, tự động thu gọn nav rail và ẩn info panel để tối ưu diện tích chat.

4. **Trải nghiệm Soạn thảo & Gửi tin (Composer UX):**
   - Ô nhập bo góc tinh tế, phím Enter gửi tin, Shift+Enter xuống dòng.
   - Tự động vô hiệu hóa nút gửi khi ô nhập trống và không có tệp đính kèm.
   - Nút Emoji Popup hỗ trợ chèn nhanh các emoji phổ biến (😊, 👍, ❤️, 🔥, 🎉, 👋, ✨, 🚀, 👏, 💡...).
   - Nút đính kèm ảnh/tệp với icon rõ nét.

5. **Nâng cấp Thẻ Cảnh báo AI (AI Moderation UX):**
   - Thẻ hình ảnh có trạng thái rõ ràng:
     - `CHECKING`: Huy hiệu xanh dương nhạt + icon xoay/đồng hồ: "Đang kiểm tra bằng AI".
     - `CLEAR`: Huy hiệu xanh lá + icon tích xanh: "Đã kiểm duyệt an toàn".
     - `WARNING`: Thẻ cảnh báo màu vàng cam hổ phách có khiên cảnh báo: "Nội dung cảnh báo: Ảnh có dấu hiệu bạo lực. Nhấn để xác nhận xem ảnh".
     - `BLOCKED`: Thẻ viền đỏ: "Ảnh bị chặn theo chính sách an toàn".
     - `UNAVAILABLE`: Thẻ màu xám kèm nút "Thử lại".

6. **Xây dựng Server Admin Dashboard toàn diện (`ServerDashboard.java`):**
   - Giao diện Dark theme chuyên nghiệp, đồng bộ với FlatLaf.
   - 6 thẻ Metric trực quan (Connections, Online Users, Conversations, Messages, Images Moderated, AI Warnings).
   - Bảng danh sách phiên hoạt động (Active Sessions Table) kèm nút Ngắt kết nối (Disconnect).
   - Bảng phân tích AI Moderation với thanh tiến trình tỉ lệ trực quan (CLEAR / WARNING / BLOCKED / UNAVAILABLE).
   - Khu vực Server Logs có thể lọc theo ALL / INFO / WARN / ERROR, tìm kiếm từ khóa, xuất log ra file text.
   - Các hành động quản trị nhanh: Dừng Server an toàn, Làm mới dữ liệu, Mở thư mục chứa Data/Media.
   - Đảm bảo 100% tương thích chế độ headless/`--no-ui`.
