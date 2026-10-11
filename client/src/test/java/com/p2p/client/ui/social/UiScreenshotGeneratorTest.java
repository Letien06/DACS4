package com.p2p.client.ui.social;

import com.formdev.flatlaf.FlatDarkLaf;
import com.p2p.client.ui.AppIcon;
import com.p2p.client.ui.Theme;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import static com.p2p.client.ui.social.Values.*;

/**
 * Automates generation of pristine, high-resolution screenshots for UI/UX audit documentation.
 */
public class UiScreenshotGeneratorTest {

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static void saveComponent(Component comp, Path output) throws Exception {
        BufferedImage image = new BufferedImage(comp.getWidth(), comp.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        comp.printAll(g);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    @Test
    void generateAllScreenshots() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        Path outDir = Path.of("..", "docs", "screenshots").toAbsolutePath().normalize();
        Files.createDirectories(outDir);

        edt(() -> {
            FlatDarkLaf.setup();

            // 1. LOGIN SCREENSHOT
            SocialWindow loginWin = new SocialWindow();
            loginWin.setSize(1080, 720);
            loginWin.addNotify();
            loginWin.validate();
            saveComponent(loginWin.getContentPane(), outDir.resolve("login.png"));
            loginWin.dispose();

            // 2. CONVERSATION LIST & 3. CHAT VIEW SCREENSHOT
            SocialWindow chatWin = new SocialWindow();
            chatWin.setSize(1140, 780);
            chatWin.addNotify();

            try {
                // Mock current user
                set(chatWin, "authenticated", true);
                set(chatWin, "currentUser", of("id", 1L, "displayName", "Lê Cao Sơn Tiến", "username", "sontien", "role", "USER"));
                invoke(chatWin, "showMain", new Class<?>[0]);

                // Mock conversation rail items
                List<Map<String, Object>> convList = List.of(
                        of("conversationId", 101L, "title", "Nhóm Học Lập Trình Mạng", "kind", "GROUP", "type", "GROUP",
                                "latestPreview", "Ảnh hiện trường vụ việc cần kiểm tra", "lastActivityAt", "2026-10-10T10:15:00", "unreadCount", 2L),
                        of("conversationId", 102L, "title", "Alice (Nhóm trưởng)", "kind", "DIRECT", "type", "DIRECT", "online", true,
                                "latestPreview", "Nhớ kiểm tra lại tính năng cảnh báo ảnh nhé", "lastActivityAt", "2026-10-10T10:12:00", "unreadCount", 0L),
                        of("conversationId", 103L, "title", "Bob", "kind", "DIRECT", "type", "DIRECT", "online", false,
                                "latestPreview", "Hẹn gặp vào buổi bảo vệ đồ án tuần tới", "lastActivityAt", "2026-10-10T09:40:00", "unreadCount", 0L),
                        of("conversationId", 104L, "title", "Nhóm Đồ Án Cơ Sở 4", "kind", "GROUP", "type", "GROUP",
                                "latestPreview", "Tài liệu thiết kế UI đã hoàn thành.", "lastActivityAt", "2026-10-10T08:30:00", "unreadCount", 0L)
                );
                set(chatWin, "conversationCache", convList);
                invoke(chatWin, "renderConversationRail", new Class<?>[0]);

                // Save conversation list focused view
                chatWin.validate();
                saveComponent(chatWin.getContentPane(), outDir.resolve("conversation-list.png"));

                // Mock active chat
                Map<String, Object> activeConv = convList.get(0);
                invoke(chatWin, "openChat", new Class<?>[]{Map.class}, activeConv);

                // Add mock messages
                invoke(chatWin, "upsertMessage", new Class<?>[]{Map.class}, of(
                        "messageId", 1L, "conversationId", 101L, "conversationSeq", 1L,
                        "senderId", 2L, "senderName", "Alice", "content", "Chào cả nhóm! Mọi người đã chuẩn bị xong demo SafeChat chưa?",
                        "status", "READ", "createdAt", System.currentTimeMillis() - 600000
                ));
                invoke(chatWin, "upsertMessage", new Class<?>[]{Map.class}, of(
                        "messageId", 2L, "conversationId", 101L, "conversationSeq", 2L,
                        "senderId", 3L, "senderName", "Bob", "content", "Mình vừa test xong kiểm duyệt ảnh bạo lực bằng AI trên server, kết quả rất chuẩn!",
                        "status", "READ", "createdAt", System.currentTimeMillis() - 480000
                ));
                invoke(chatWin, "upsertMessage", new Class<?>[]{Map.class}, of(
                        "messageId", 3L, "conversationId", 101L, "conversationSeq", 3L,
                        "senderId", 1L, "senderName", "Bạn", "content", "Giao diện mới được làm lại theo phong cách hiện đại như Telegram Desktop, rất mượt và trực quan!",
                        "status", "READ", "createdAt", System.currentTimeMillis() - 300000
                ));
                invoke(chatWin, "upsertMessage", new Class<?>[]{Map.class}, of(
                        "messageId", 4L, "conversationId", 101L, "conversationSeq", 4L,
                        "senderId", 2L, "senderName", "Alice", "content", "Mình gửi tài liệu báo cáo đính kèm đây nhé:",
                        "fileName", "Bao_cao_SafeChat_DACS4.pdf", "mediaId", "doc-101",
                        "media", of("mediaId", "doc-101", "originalName", "Bao_cao_SafeChat_DACS4.pdf", "mimeType", "application/pdf"),
                        "status", "READ", "createdAt", System.currentTimeMillis() - 120000
                ));
                // 3a. MOCK ẢNH CÓ DẤU HIỆU BẠO LỰC (WARNING -> TỰ ĐỘNG LÀM MỜ VỚI OVERLAY CẢNH BÁO)
                ImageIcon violentPhoto = createSampleViolentPhotoImage(280, 175);
                invoke(chatWin, "upsertMessage", new Class<?>[]{Map.class}, of(
                        "messageId", 5L, "conversationId", 101L, "conversationSeq", 5L,
                        "senderId", 3L, "senderName", "Bob",
                        "content", "Ảnh hiện trường vụ việc cần kiểm tra",
                        "fileName", "evidence-photo.jpg", "mediaId", "warn-photo-102",
                        "media", of("mediaId", "warn-photo-102", "originalName", "evidence-photo.jpg", "mimeType", "image/jpeg",
                                "thumbnailIcon", violentPhoto,
                                "moderation", of("status", "WARNING", "reason", "Phát hiện dấu hiệu bạo lực trực diện (độ tin cậy: 88%)")),
                        "status", "DELIVERED", "createdAt", System.currentTimeMillis() - 60000
                ));

                // 3b. MOCK GỬI ẢNH BÌNH THƯỜNG (CLEAR -> HIỂN THỊ TRỰC TIẾP TRONG CHAT NHƯ MESSENGER/TELEGRAM)
                ImageIcon motorbikePhoto = createSampleMotorbikePhotoImage(200, 310);
                invoke(chatWin, "upsertMessage", new Class<?>[]{Map.class}, of(
                        "messageId", 6L, "conversationId", 101L, "conversationSeq", 6L,
                        "senderId", 1L, "senderName", "Bạn",
                        "content", "Lên đi bạn ơi, trời đang mưa đường ướt nè:",
                        "fileName", "di_duong_troi_mua.jpg", "mediaId", "img-bike-202",
                        "media", of("mediaId", "img-bike-202", "originalName", "di_duong_troi_mua.jpg", "mimeType", "image/jpeg",
                                "thumbnailIcon", motorbikePhoto,
                                "moderation", of("status", "CLEAR", "reason", "Nội dung an toàn, không phát hiện vi phạm")),
                        "status", "READ", "createdAt", System.currentTimeMillis() - 15000
                ));

                // 3c. Phản hồi tiếp theo
                invoke(chatWin, "upsertMessage", new Class<?>[]{Map.class}, of(
                        "messageId", 7L, "conversationId", 101L, "conversationSeq", 7L,
                        "senderId", 2L, "senderName", "Alice",
                        "content", "Trời mưa ướt đường trơn, đi cẩn thận nhé bạn!",
                        "status", "READ", "createdAt", System.currentTimeMillis() - 5000
                ));

                chatWin.setSize(1140, 900);
                invoke(chatWin, "renderMessages", new Class<?>[]{boolean.class}, true);
                chatWin.validate();
                saveComponent(chatWin.getContentPane(), outDir.resolve("chat.png"));
                saveComponent(chatWin.getContentPane(), outDir.resolve("normal-image-chat.png"));
                chatWin.dispose();

                // 3d. ẢNH CHỤP CẬN CẢNH: ẢNH BẠO LỰC BỊ LÀM MỜ TRONG CHAT
                JFrame blurFrame = new JFrame("SafeChat · Trải nghiệm làm mờ ảnh bạo lực");
                blurFrame.setSize(520, 320);
                blurFrame.setLocationRelativeTo(null);
                JPanel blurWrap = Theme.roundedPanel(new Color(18, 22, 30), 16);
                blurWrap.setBorder(new EmptyBorder(20, 24, 20, 24));
                blurWrap.setLayout(new BorderLayout(0, 10));
                JLabel blurHeader = new JLabel("Bong bóng tin nhắn: Ảnh bạo lực bị làm mờ (Tự động)");
                blurHeader.setFont(Theme.font(Font.BOLD, 14));
                blurHeader.setForeground(Theme.TEXT);
                blurWrap.add(blurHeader, BorderLayout.NORTH);

                Theme.PhotoBubble demoBlurBubble = new Theme.PhotoBubble(true, 16);
                demoBlurBubble.setImage(violentPhoto.getImage());
                JPanel bubbleCenter = new JPanel(new FlowLayout(FlowLayout.CENTER));
                bubbleCenter.setOpaque(false);
                bubbleCenter.add(demoBlurBubble);
                blurWrap.add(bubbleCenter, BorderLayout.CENTER);

                blurFrame.setContentPane(blurWrap);
                blurFrame.addNotify();
                blurFrame.validate();
                saveComponent(blurFrame.getContentPane(), outDir.resolve("blurred-violent-image.png"));
                blurFrame.dispose();

                // 4. AI WARNING MODAL SCREENSHOT
                JFrame warningFrame = new JFrame("SafeChat · Cảnh báo nội dung");
                warningFrame.setSize(540, 270);
                warningFrame.setLocationRelativeTo(null);
                JPanel warnCard = Theme.roundedPanel(Theme.SURFACE, 18);
                warnCard.setBorder(Theme.padding(22, 24, 20, 24));
                warnCard.setLayout(new BorderLayout(14, 14));

                JLabel warnIcon = new JLabel(new AppIcon(AppIcon.Type.WARNING, Theme.WARNING, 38));
                warnIcon.setVerticalAlignment(SwingConstants.TOP);

                JPanel textPanel = new JPanel();
                textPanel.setOpaque(false);
                textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

                JLabel badge = new JLabel("CẢNH BÁO NỘI DUNG (WARNING)");
                badge.setFont(Theme.font(Font.BOLD, 12));
                badge.setForeground(Theme.WARNING);

                JLabel heading = new JLabel("Hình ảnh có dấu hiệu bạo lực");
                heading.setFont(Theme.font(Font.BOLD, 17));
                heading.setForeground(Theme.TEXT);

                JTextArea detail = new JTextArea("Mô hình AI kiểm duyệt phát hiện tín hiệu bạo lực trực diện trong bức ảnh này.\nNgười nhận cần xác nhận trước khi xem hoặc tải về máy.");
                detail.setEditable(false);
                detail.setLineWrap(true);
                detail.setWrapStyleWord(true);
                detail.setOpaque(false);
                detail.setForeground(Theme.MUTED);
                detail.setFont(Theme.font(Font.PLAIN, 13));

                textPanel.add(badge);
                textPanel.add(Box.createVerticalStrut(4));
                textPanel.add(heading);
                textPanel.add(Box.createVerticalStrut(8));
                textPanel.add(detail);

                JPanel bodyPanel = new JPanel(new BorderLayout(14, 0));
                bodyPanel.setOpaque(false);
                bodyPanel.add(warnIcon, BorderLayout.WEST);
                bodyPanel.add(textPanel, BorderLayout.CENTER);
                warnCard.add(bodyPanel, BorderLayout.CENTER);

                JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                actionsPanel.setOpaque(false);
                JButton cancelBtn = Theme.ghostButton("Bỏ qua");
                JButton confirmBtn = Theme.primaryButton("Xác nhận & Xem ảnh");
                actionsPanel.add(cancelBtn);
                actionsPanel.add(confirmBtn);
                warnCard.add(actionsPanel, BorderLayout.SOUTH);

                warningFrame.setContentPane(warnCard);
                warningFrame.addNotify();
                warningFrame.validate();
                saveComponent(warningFrame.getContentPane(), outDir.resolve("ai-warning.png"));
                warningFrame.dispose();

                // 5. SERVER DASHBOARD SCREENSHOT
                JFrame dashFrame = new JFrame("SafeChat Server · Bảng điều khiển quản trị");
                dashFrame.setSize(1080, 720);
                dashFrame.setLocationRelativeTo(null);

                JPanel dashRoot = new JPanel(new BorderLayout(0, 14));
                dashRoot.setBackground(new Color(14, 18, 24));
                dashRoot.setBorder(new EmptyBorder(16, 20, 16, 20));

                // Header
                JPanel dHeader = new JPanel(new BorderLayout());
                dHeader.setBackground(new Color(22, 27, 36));
                dHeader.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(44, 52, 68), 1),
                        new EmptyBorder(14, 18, 14, 18)
                ));
                JPanel dBrand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
                dBrand.setOpaque(false);
                JLabel dLogo = new JLabel(new AppIcon(AppIcon.Type.SHIELD, Theme.PRIMARY, 24));
                JLabel dTitle = new JLabel("SafeChat Server");
                dTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
                dTitle.setForeground(Theme.TEXT);
                JLabel dStatus = new JLabel("● ĐANG HOẠT ĐỘNG");
                dStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
                dStatus.setForeground(Theme.ONLINE);
                dBrand.add(dLogo); dBrand.add(dTitle); dBrand.add(dStatus);

                JLabel dPorts = new JLabel("TCP/TLS: 5000  |  UDP Discovery: 5001  |  Typing: 5002");
                dPorts.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                dPorts.setForeground(Theme.MUTED);

                dHeader.add(dBrand, BorderLayout.WEST);
                dHeader.add(dPorts, BorderLayout.EAST);
                dashRoot.add(dHeader, BorderLayout.NORTH);

                // Metric Grid
                JPanel dMetrics = new JPanel(new GridLayout(1, 6, 10, 0));
                dMetrics.setOpaque(false);
                dMetrics.add(createMockMetric("KẾT NỐI CLIENT", "12 / 50", Theme.INFO));
                dMetrics.add(createMockMetric("ONLINE", "8", Theme.ONLINE));
                dMetrics.add(createMockMetric("CUỘC TRÒ CHUYỆN", "15", Theme.PRIMARY));
                dMetrics.add(createMockMetric("TIN NHẮN", "1,420", Theme.TEXT));
                dMetrics.add(createMockMetric("ẢNH ĐÃ KIỂM TRA", "85", Theme.WARNING));
                dMetrics.add(createMockMetric("CẢNH BÁO AI", "8", Theme.DANGER));

                // Tabs
                JTabbedPane dTabs = new JTabbedPane();
                dTabs.setBackground(new Color(22, 27, 36));
                dTabs.setForeground(Theme.TEXT);

                // Sessions table
                String[] cols = {"Mã phiên", "Tên đăng nhập", "Địa chỉ kết nối", "Trạng thái", "Hành động"};
                DefaultTableModel sModel = new DefaultTableModel(cols, 0);
                sModel.addRow(new Object[]{"s-98a21...", "alice", "192.168.1.15:54210", "✓ Đã đăng nhập", "Ngắt phiên"});
                sModel.addRow(new Object[]{"s-43f11...", "bob", "192.168.1.18:54214", "✓ Đã đăng nhập", "Ngắt phiên"});
                sModel.addRow(new Object[]{"s-77c82...", "carol", "127.0.0.1:54220", "✓ Đã đăng nhập", "Ngắt phiên"});
                sModel.addRow(new Object[]{"s-12b04...", "sontien", "192.168.1.10:54201", "✓ Đã đăng nhập", "Ngắt phiên"});

                JTable sTable = new JTable(sModel);
                sTable.setBackground(new Color(22, 27, 36));
                sTable.setForeground(Theme.TEXT);
                sTable.setRowHeight(32);
                sTable.setGridColor(new Color(44, 52, 68));
                JPanel sPanel = new JPanel(new BorderLayout(0, 8));
                sPanel.setBackground(new Color(22, 27, 36));
                sPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
                sPanel.add(new JScrollPane(sTable), BorderLayout.CENTER);
                dTabs.addTab("Phiên kết nối (4)", sPanel);

                // Moderation tab
                JPanel mPanel = new JPanel(new GridLayout(4, 1, 10, 10));
                mPanel.setBackground(new Color(22, 27, 36));
                mPanel.setBorder(new EmptyBorder(18, 22, 18, 22));
                mPanel.add(createMockProgress("Ảnh CLEAR (An toàn):", 89, "76 ảnh (89%)", Theme.ONLINE));
                mPanel.add(createMockProgress("Ảnh WARNING (Cảnh báo bạo lực):", 7, "6 ảnh (7%)", Theme.WARNING));
                mPanel.add(createMockProgress("Ảnh BLOCKED (Bị chặn chính sách):", 2, "2 ảnh (2%)", Theme.DANGER));
                mPanel.add(createMockProgress("Ảnh UNAVAILABLE (Lỗi dịch vụ/chưa rõ):", 2, "1 lần (2%)", Theme.MUTED));
                dTabs.addTab("Kiểm duyệt AI", mPanel);

                // Logs tab
                DefaultListModel<String> lModel = new DefaultListModel<>();
                lModel.addElement("[10:14:02 INFO] Client sontien kết nối thành công từ 192.168.1.10:54201");
                lModel.addElement("[10:14:15 INFO] Gửi tin nhắn vào cuộc trò chuyện #101 bởi Alice");
                lModel.addElement("[10:14:30 INFO] Kiểm duyệt ảnh media-841: CLEAR (OpenAI omni-moderation-latest)");
                lModel.addElement("[10:15:02 WARN] Kiểm duyệt ảnh media-842: WARNING (violence score: 0.84)");
                lModel.addElement("[10:15:20 INFO] Client bob đã nhận gói tin UDP typing từ conversation #101");
                JList<String> lList = new JList<>(lModel);
                lList.setBackground(new Color(16, 20, 28));
                lList.setForeground(new Color(220, 226, 240));
                lList.setFont(new Font("Consolas", Font.PLAIN, 12));
                JPanel lPanel = new JPanel(new BorderLayout());
                lPanel.setBackground(new Color(22, 27, 36));
                lPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
                lPanel.add(new JScrollPane(lList), BorderLayout.CENTER);
                dTabs.addTab("Nhật ký Server", lPanel);

                JPanel centerWrap = new JPanel(new BorderLayout(0, 12));
                centerWrap.setOpaque(false);
                centerWrap.add(dMetrics, BorderLayout.NORTH);
                centerWrap.add(dTabs, BorderLayout.CENTER);
                dashRoot.add(centerWrap, BorderLayout.CENTER);

                // Footer
                JPanel dFooter = new JPanel(new BorderLayout());
                dFooter.setBackground(new Color(22, 27, 36));
                dFooter.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(44, 52, 68), 1),
                        new EmptyBorder(10, 16, 10, 16)
                ));
                JLabel dDb = new JLabel("Database: data/social.db · Media: data/media (Lưu trữ an toàn)");
                dDb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                dDb.setForeground(Theme.MUTED);

                JPanel dActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                dActions.setOpaque(false);
                dActions.add(Theme.ghostButton("Làm mới"));
                dActions.add(Theme.ghostButton("Mở thư mục dữ liệu"));
                dActions.add(Theme.ghostButton("Xuất log"));
                JButton dStop = Theme.primaryButton("Dừng Server");
                dStop.setBackground(Theme.DANGER);
                dActions.add(dStop);

                dFooter.add(dDb, BorderLayout.WEST);
                dFooter.add(dActions, BorderLayout.EAST);
                dashRoot.add(dFooter, BorderLayout.SOUTH);

                dashFrame.setContentPane(dashRoot);
                dashFrame.addNotify();
                dashFrame.validate();
                saveComponent(dashFrame.getContentPane(), outDir.resolve("server-dashboard.png"));
                dashFrame.dispose();

                // 6. PHOTO PREVIEW MODAL SCREENSHOT (Khi người dùng nhấn Xem ảnh)
                JFrame photoPreviewFrame = new JFrame("SafeChat · Xem ảnh · vku_sunset_campus.jpg");
                photoPreviewFrame.setSize(760, 540);
                photoPreviewFrame.setLocationRelativeTo(null);

                JPanel photoRoot = new JPanel(new BorderLayout(0, 12));
                photoRoot.setBackground(new Color(15, 20, 28));
                photoRoot.setBorder(new EmptyBorder(16, 20, 16, 20));

                // Preview Header
                JPanel pHeader = new JPanel(new BorderLayout());
                pHeader.setOpaque(false);
                JLabel pTitle = new JLabel("vku_sunset_campus.jpg · 1920 × 1080 · 1.4 MB");
                pTitle.setFont(Theme.font(Font.BOLD, 14));
                pTitle.setForeground(Theme.TEXT);
                JLabel pAiBadge = new JLabel("✓ KIỂM DUYỆT AN TOÀN (CLEAR)");
                pAiBadge.setFont(Theme.font(Font.BOLD, 12));
                pAiBadge.setForeground(Theme.ONLINE);
                pHeader.add(pTitle, BorderLayout.WEST);
                pHeader.add(pAiBadge, BorderLayout.EAST);
                photoRoot.add(pHeader, BorderLayout.NORTH);

                // Large Image View
                ImageIcon largePhoto = createSamplePhotoImage(700, 410);
                JLabel photoDisplay = new JLabel(largePhoto);
                photoDisplay.setHorizontalAlignment(SwingConstants.CENTER);
                photoDisplay.setBorder(BorderFactory.createLineBorder(new Color(44, 52, 68), 1));
                photoRoot.add(photoDisplay, BorderLayout.CENTER);

                // Preview Footer Actions
                JPanel pFooter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
                pFooter.setOpaque(false);
                JButton pDl = Theme.primaryButton("Lưu ảnh vào máy");
                JButton pClose = Theme.ghostButton("Đóng");
                pFooter.add(pClose);
                pFooter.add(pDl);
                photoRoot.add(pFooter, BorderLayout.SOUTH);

                photoPreviewFrame.setContentPane(photoRoot);
                photoPreviewFrame.addNotify();
                photoPreviewFrame.validate();
                saveComponent(photoPreviewFrame.getContentPane(), outDir.resolve("normal-image-preview.png"));
                photoPreviewFrame.dispose();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return null;
        });
    }

    private static ImageIcon createSamplePhotoImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Sunset sky gradient
        GradientPaint sky = new GradientPaint(0, 0, new Color(49, 46, 129), 0, height * 0.7f, new Color(249, 115, 22));
        g.setPaint(sky);
        g.fillRect(0, 0, width, height);

        // Glowing sun
        g.setPaint(new RadialGradientPaint(
                new Point(width / 2, (int)(height * 0.45)),
                width * 0.28f,
                new float[]{0.0f, 0.4f, 1.0f},
                new Color[]{new Color(255, 255, 220, 255), new Color(254, 215, 170, 180), new Color(249, 115, 22, 0)}
        ));
        g.fillOval(width / 2 - 70, (int)(height * 0.45) - 70, 140, 140);

        // Distant mountains silhouette
        g.setColor(new Color(67, 24, 76, 210));
        Polygon mountains = new Polygon();
        mountains.addPoint(0, height);
        mountains.addPoint(0, (int)(height * 0.58));
        mountains.addPoint((int)(width * 0.25), (int)(height * 0.48));
        mountains.addPoint((int)(width * 0.45), (int)(height * 0.55));
        mountains.addPoint((int)(width * 0.70), (int)(height * 0.42));
        mountains.addPoint((int)(width * 0.88), (int)(height * 0.52));
        mountains.addPoint(width, (int)(height * 0.46));
        mountains.addPoint(width, height);
        g.fillPolygon(mountains);

        // Ground / Lake reflection
        GradientPaint ground = new GradientPaint(0, (int)(height * 0.65), new Color(15, 23, 42), 0, height, new Color(2, 6, 23));
        g.setPaint(ground);
        g.fillRect(0, (int)(height * 0.65), width, (int)(height * 0.35));

        // Campus bridge / trees silhouette
        g.setColor(new Color(30, 41, 59));
        g.fillRoundRect((int)(width * 0.1), (int)(height * 0.72), (int)(width * 0.8), 6, 4, 4);

        // Text watermark badge
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(12, height - 34, 180, 22, 8, 8);
        g.setColor(new Color(241, 245, 249));
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g.drawString("📷 VKU Sunset Campus", 20, height - 19);

        g.dispose();
        return new ImageIcon(img);
    }

    private static ImageIcon createSampleMotorbikePhotoImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Wet asphalt road gradient (dark grey with subtle rainy sheen)
        GradientPaint asphalt = new GradientPaint(0, 0, new Color(135, 145, 160), 0, height * 0.65f, new Color(55, 62, 72));
        g.setPaint(asphalt);
        g.fillRect(0, 0, width, height);

        // Rainy mist gradient overlay
        GradientPaint rainMist = new GradientPaint(0, 0, new Color(200, 210, 225, 90), 0, height * 0.4f, new Color(0, 0, 0, 0));
        g.setPaint(rainMist);
        g.fillRect(0, 0, width, (int)(height * 0.4));

        // White road dashed lane markers with perspective
        g.setColor(new Color(245, 248, 255, 230));
        Polygon lane1 = new Polygon();
        lane1.addPoint((int)(width * 0.38), (int)(height * 0.08));
        lane1.addPoint((int)(width * 0.44), (int)(height * 0.08));
        lane1.addPoint((int)(width * 0.42), (int)(height * 0.16));
        lane1.addPoint((int)(width * 0.35), (int)(height * 0.16));
        g.fillPolygon(lane1);

        Polygon lane2 = new Polygon();
        lane2.addPoint((int)(width * 0.30), (int)(height * 0.22));
        lane2.addPoint((int)(width * 0.38), (int)(height * 0.22));
        lane2.addPoint((int)(width * 0.34), (int)(height * 0.34));
        lane2.addPoint((int)(width * 0.24), (int)(height * 0.34));
        g.fillPolygon(lane2);

        // Water reflection shimmer
        g.setColor(new Color(180, 200, 220, 50));
        g.fillOval((int)(width * 0.15), (int)(height * 0.40), (int)(width * 0.7), 32);

        // Dark motorbike windshield and dashboard at bottom
        int dashY = (int)(height * 0.58);
        GradientPaint dash = new GradientPaint(0, dashY, new Color(30, 35, 42), 0, height, new Color(12, 14, 18));
        g.setPaint(dash);
        g.fillRoundRect(8, dashY, width - 16, height - dashY + 20, 28, 28);

        // Digital speedometer screen
        int scrY = dashY + 36;
        g.setColor(new Color(22, 26, 32));
        g.fillRoundRect((int)(width * 0.20), scrY, (int)(width * 0.60), 65, 12, 12);
        g.setColor(new Color(60, 70, 85));
        g.drawRoundRect((int)(width * 0.20), scrY, (int)(width * 0.60), 65, 12, 12);

        // Phone mount clip in center
        g.setColor(new Color(40, 45, 55));
        g.fillRoundRect((int)(width * 0.42), scrY + 12, (int)(width * 0.16), 40, 8, 8);

        // Raindrop glints on motorbike windshield
        g.setColor(new Color(220, 235, 255, 130));
        g.fillOval(35, dashY + 20, 4, 6);
        g.fillOval(52, dashY + 35, 3, 5);
        g.fillOval(85, dashY + 14, 5, 7);
        g.fillOval((int)(width * 0.75), dashY + 25, 4, 6);
        g.fillOval((int)(width * 0.82), dashY + 45, 3, 4);

        g.dispose();
        return new ImageIcon(img);
    }

    private static ImageIcon createSampleViolentPhotoImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dramatic deep dark red/crimson gradient indicating graphic content
        GradientPaint gp = new GradientPaint(0, 0, new Color(85, 15, 22), width, height, new Color(28, 8, 12));
        g.setPaint(gp);
        g.fillRect(0, 0, width, height);

        g.setColor(new Color(180, 30, 45, 170));
        g.fillOval(20, 15, width - 40, height - 30);
        g.setColor(new Color(220, 60, 75, 120));
        g.fillOval(50, 35, width - 100, height - 70);

        g.dispose();
        return new ImageIcon(img);
    }

    private static JPanel createMockMetric(String title, String val, Color accent) {
        JPanel c = new JPanel(new BorderLayout(0, 6));
        c.setBackground(new Color(30, 36, 48));
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
                new EmptyBorder(12, 12, 12, 12)
        ));
        JLabel t = new JLabel(title);
        t.setFont(new Font("Segoe UI", Font.BOLD, 10));
        t.setForeground(Theme.MUTED);
        JLabel v = new JLabel(val);
        v.setFont(new Font("Segoe UI", Font.BOLD, 18));
        v.setForeground(Theme.TEXT);
        c.add(t, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        return c;
    }

    private static JPanel createMockProgress(String title, int val, String text, Color col) {
        JPanel p = new JPanel(new BorderLayout(8, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(title);
        l.setFont(Theme.font(Font.BOLD, 12));
        l.setForeground(Theme.TEXT);
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(val);
        bar.setString(text);
        bar.setStringPainted(true);
        bar.setForeground(col);
        bar.setBackground(new Color(30, 36, 48));
        p.add(l, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        return p;
    }

    private static void set(Object obj, String name, Object val) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, val);
    }

    private static Object invoke(Object obj, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = obj.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(obj, args);
    }
}
