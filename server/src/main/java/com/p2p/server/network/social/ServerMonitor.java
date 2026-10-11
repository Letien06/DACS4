package com.p2p.server.network.social;

import com.formdev.flatlaf.FlatDarkLaf;
import com.p2p.server.social.SocialServer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Modern SafeChat Server Administration Dashboard.
 * Built with Java Swing + FlatLaf for real-time monitoring and management.
 */
public final class ServerMonitor {
    private static final Color BG_APP = new Color(14, 18, 24);
    private static final Color BG_SURFACE = new Color(22, 27, 36);
    private static final Color BG_CARD = new Color(30, 36, 48);
    private static final Color BORDER = new Color(44, 52, 68);
    private static final Color PRIMARY = new Color(99, 102, 241);
    private static final Color TEXT_MAIN = new Color(244, 246, 251);
    private static final Color TEXT_MUTED = new Color(152, 162, 179);
    private static final Color GREEN = new Color(52, 211, 153);
    private static final Color YELLOW = new Color(245, 158, 11);
    private static final Color RED = new Color(239, 68, 68);
    private static final Color BLUE = new Color(59, 130, 246);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ServerMonitor() { }

    public static void open(SocialServer server, SocialServer.Config config) {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();
            UIManager.put("Component.arc", 12);
            UIManager.put("Button.arc", 10);
            UIManager.put("Table.arc", 8);

            JFrame frame = new JFrame("SafeChat Server · Bảng điều khiển quản trị");
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setSize(1080, 720);
            frame.setMinimumSize(new Dimension(920, 600));
            frame.setLocationRelativeTo(null);

            JPanel root = new JPanel(new BorderLayout(0, 14));
            root.setBackground(BG_APP);
            root.setBorder(new EmptyBorder(16, 20, 16, 20));

            // ==========================================
            // 1. HEADER PANEL
            // ==========================================
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(BG_SURFACE);
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER, 1),
                    new EmptyBorder(14, 18, 14, 18)
            ));

            JPanel brandBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            brandBox.setOpaque(false);
            JLabel logoIcon = new JLabel("🛡️");
            logoIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
            JLabel title = new JLabel("SafeChat Server");
            title.setFont(new Font("Segoe UI", Font.BOLD, 20));
            title.setForeground(TEXT_MAIN);

            JLabel statusPill = new JLabel("● ĐANG HOẠT ĐỘNG");
            statusPill.setFont(new Font("Segoe UI", Font.BOLD, 12));
            statusPill.setForeground(GREEN);
            statusPill.setBorder(new EmptyBorder(4, 10, 4, 10));

            brandBox.add(logoIcon);
            brandBox.add(title);
            brandBox.add(statusPill);

            JPanel netInfoBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
            netInfoBox.setOpaque(false);
            JLabel portsLabel = new JLabel("TCP/TLS: " + server.tcpPort() + "  |  UDP Discovery: " + server.discoveryPort() + "  |  Typing: " + server.typingPort());
            portsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            portsLabel.setForeground(TEXT_MUTED);
            netInfoBox.add(portsLabel);

            header.add(brandBox, BorderLayout.WEST);
            header.add(netInfoBox, BorderLayout.EAST);
            root.add(header, BorderLayout.NORTH);

            // ==========================================
            // 2. METRIC CARDS (6 CARDS)
            // ==========================================
            JPanel metricsGrid = new JPanel(new GridLayout(1, 6, 10, 0));
            metricsGrid.setOpaque(false);

            JLabel connsVal = new JLabel("0 / " + config.maxConnections());
            JLabel onlineVal = new JLabel("0");
            JLabel convsVal = new JLabel("0");
            JLabel msgsVal = new JLabel("0");
            JLabel modVal = new JLabel("0");
            JLabel warnsVal = new JLabel("0");

            metricsGrid.add(createMetricCard("KẾT NỐI CLIENT", connsVal, BLUE));
            metricsGrid.add(createMetricCard("ONLINE", onlineVal, GREEN));
            metricsGrid.add(createMetricCard("CUỘC TRÒ CHUYỆN", convsVal, PRIMARY));
            metricsGrid.add(createMetricCard("TIN NHẮN", msgsVal, TEXT_MAIN));
            metricsGrid.add(createMetricCard("ẢNH ĐÃ KIỂM TRA", modVal, YELLOW));
            metricsGrid.add(createMetricCard("CẢNH BÁO AI", warnsVal, RED));

            // ==========================================
            // 3. CENTER CONTENT (TABS: CLIENTS, MODERATION, LOGS)
            // ==========================================
            JTabbedPane centerTabs = new JTabbedPane();
            centerTabs.setBackground(BG_SURFACE);
            centerTabs.setForeground(TEXT_MAIN);

            // Tab 1: Clients / Sessions Table
            String[] colNames = {"Mã phiên", "Tên đăng nhập", "Địa chỉ kết nối", "Trạng thái", "Hành động"};
            DefaultTableModel sessionModel = new DefaultTableModel(colNames, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            JTable sessionTable = new JTable(sessionModel);
            sessionTable.setBackground(BG_SURFACE);
            sessionTable.setForeground(TEXT_MAIN);
            sessionTable.setRowHeight(32);
            sessionTable.setShowGrid(true);
            sessionTable.setGridColor(BORDER);
            sessionTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            JPanel sessionsTab = new JPanel(new BorderLayout(0, 8));
            sessionsTab.setBackground(BG_SURFACE);
            sessionsTab.setBorder(new EmptyBorder(12, 12, 12, 12));
            sessionsTab.add(new JScrollPane(sessionTable), BorderLayout.CENTER);

            JPanel sessionActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            sessionActions.setOpaque(false);
            JButton kickBtn = new JButton("Ngắt phiên đã chọn");
            kickBtn.setForeground(Color.WHITE);
            kickBtn.setBackground(RED);
            kickBtn.addActionListener(e -> {
                int row = sessionTable.getSelectedRow();
                if (row >= 0) {
                    String connId = String.valueOf(sessionModel.getValueAt(row, 0));
                    String uname = String.valueOf(sessionModel.getValueAt(row, 1));
                    if (JOptionPane.showConfirmDialog(frame, "Ngắt kết nối của " + uname + "?", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        server.disconnectSession(connId);
                    }
                } else {
                    JOptionPane.showMessageDialog(frame, "Vui lòng chọn một phiên kết nối từ bảng.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                }
            });
            sessionActions.add(kickBtn);
            sessionsTab.add(sessionActions, BorderLayout.SOUTH);
            centerTabs.addTab("👥 Phiên kết nối (" + server.connectionCount() + ")", sessionsTab);

            // Tab 2: AI Moderation Analytics
            JPanel modTab = new JPanel(new BorderLayout(14, 14));
            modTab.setBackground(BG_SURFACE);
            modTab.setBorder(new EmptyBorder(18, 22, 18, 22));

            JPanel modStatsBox = new JPanel(new GridLayout(4, 1, 10, 10));
            modStatsBox.setOpaque(false);

            JProgressBar clearBar = new JProgressBar(0, 100);
            clearBar.setForeground(GREEN);
            clearBar.setBackground(BG_CARD);
            clearBar.setStringPainted(true);

            JProgressBar warnBar = new JProgressBar(0, 100);
            warnBar.setForeground(YELLOW);
            warnBar.setBackground(BG_CARD);
            warnBar.setStringPainted(true);

            JProgressBar blockBar = new JProgressBar(0, 100);
            blockBar.setForeground(RED);
            blockBar.setBackground(BG_CARD);
            blockBar.setStringPainted(true);

            JProgressBar unavailBar = new JProgressBar(0, 100);
            unavailBar.setForeground(TEXT_MUTED);
            unavailBar.setBackground(BG_CARD);
            unavailBar.setStringPainted(true);

            modStatsBox.add(createProgressBarPanel("✓ Ảnh CLEAR (An toàn):", clearBar));
            modStatsBox.add(createProgressBarPanel("⚠️ Ảnh WARNING (Cảnh báo bạo lực):", warnBar));
            modStatsBox.add(createProgressBarPanel("⊘ Ảnh BLOCKED (Bị chặn chính sách):", blockBar));
            modStatsBox.add(createProgressBarPanel("? Ảnh UNAVAILABLE (Lỗi dịch vụ/chưa rõ):", unavailBar));

            JLabel modSummary = new JLabel("Chính sách AI: Server gọi mô hình OpenAI Moderation qua HTTPS. Không có API key lưu ở client.");
            modSummary.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            modSummary.setForeground(TEXT_MUTED);

            modTab.add(modStatsBox, BorderLayout.CENTER);
            modTab.add(modSummary, BorderLayout.SOUTH);
            centerTabs.addTab("🛡️ Kiểm duyệt AI", modTab);

            // Tab 3: Server Logs
            JPanel logsTab = new JPanel(new BorderLayout(0, 8));
            logsTab.setBackground(BG_SURFACE);
            logsTab.setBorder(new EmptyBorder(12, 12, 12, 12));

            DefaultListModel<String> logListModel = new DefaultListModel<>();
            List<LogEntry> fullLogs = new ArrayList<>();
            JList<String> logList = new JList<>(logListModel);
            logList.setBackground(new Color(16, 20, 28));
            logList.setForeground(new Color(220, 226, 240));
            logList.setFont(new Font("Consolas", Font.PLAIN, 12));

            JPanel logFilterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            logFilterBar.setOpaque(false);
            JComboBox<String> levelFilter = new JComboBox<>(new String[]{"TẤT CẢ (ALL)", "INFO", "WARN", "ERROR"});
            JTextField searchLog = new JTextField(18);
            searchLog.putClientProperty("JTextField.placeholderText", "Tìm kiếm log...");

            Runnable renderLogs = () -> {
                String lvl = String.valueOf(levelFilter.getSelectedItem());
                String q = searchLog.getText().trim().toLowerCase();
                logListModel.clear();
                for (LogEntry e : fullLogs) {
                    if (!lvl.startsWith("TẤT CẢ") && !e.level.equalsIgnoreCase(lvl)) continue;
                    if (!q.isBlank() && !e.message.toLowerCase().contains(q)) continue;
                    logListModel.addElement("[" + e.time + " " + e.level + "] " + e.message);
                }
            };
            levelFilter.addActionListener(e -> renderLogs.run());
            searchLog.addActionListener(e -> renderLogs.run());

            logFilterBar.add(new JLabel("Lọc cấp độ:"));
            logFilterBar.add(levelFilter);
            logFilterBar.add(new JLabel("Tìm kiếm:"));
            logFilterBar.add(searchLog);

            logsTab.add(logFilterBar, BorderLayout.NORTH);
            logsTab.add(new JScrollPane(logList), BorderLayout.CENTER);
            centerTabs.addTab("📜 Nhật ký Server", logsTab);

            // Gộp Metrics + Tabs vào Center
            JPanel centerPanel = new JPanel(new BorderLayout(0, 12));
            centerPanel.setOpaque(false);
            centerPanel.add(metricsGrid, BorderLayout.NORTH);
            centerPanel.add(centerTabs, BorderLayout.CENTER);
            root.add(centerPanel, BorderLayout.CENTER);

            // ==========================================
            // 4. FOOTER CONTROLS
            // ==========================================
            JPanel footer = new JPanel(new BorderLayout());
            footer.setBackground(BG_SURFACE);
            footer.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER, 1),
                    new EmptyBorder(10, 16, 10, 16)
            ));

            JLabel pathLabel = new JLabel("Database: " + config.databasePath().toAbsolutePath());
            pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            pathLabel.setForeground(TEXT_MUTED);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.setOpaque(false);

            JButton refreshBtn = new JButton("🔄 Làm mới");
            refreshBtn.addActionListener(e -> {
                // Sẽ được cập nhật ngay trong timer
            });

            JButton dataDirBtn = new JButton("📁 Mở thư mục dữ liệu");
            dataDirBtn.addActionListener(e -> {
                try {
                    Path dataDir = config.databasePath().getParent();
                    if (dataDir != null && Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(dataDir.toFile());
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Không thể mở thư mục: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });

            JButton exportLogBtn = new JButton("💾 Xuất log");
            exportLogBtn.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File("safechat-server-" + System.currentTimeMillis() + ".log"));
                if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    try (PrintWriter pw = new PrintWriter(chooser.getSelectedFile())) {
                        for (LogEntry entry : fullLogs) {
                            pw.println("[" + entry.time + " " + entry.level + "] " + entry.message);
                        }
                        JOptionPane.showMessageDialog(frame, "Đã xuất log thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(frame, "Lỗi khi lưu file: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            JButton stopBtn = new JButton("🛑 Dừng Server");
            stopBtn.setForeground(Color.WHITE);
            stopBtn.setBackground(RED);
            stopBtn.addActionListener(e -> {
                if (JOptionPane.showConfirmDialog(frame, "Bạn có chắc chắn muốn dừng SafeChat Server an toàn?", "Xác nhận dừng server", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                    stopBtn.setEnabled(false);
                    new Thread(() -> {
                        server.close();
                        SwingUtilities.invokeLater(frame::dispose);
                    }, "server-stop-thread").start();
                }
            });

            actions.add(refreshBtn);
            actions.add(dataDirBtn);
            actions.add(exportLogBtn);
            actions.add(stopBtn);

            footer.add(pathLabel, BorderLayout.WEST);
            footer.add(actions, BorderLayout.EAST);
            root.add(footer, BorderLayout.SOUTH);

            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) {
                    stopBtn.doClick();
                }
            });

            frame.setContentPane(root);
            frame.setVisible(true);

            // Log ban đầu
            fullLogs.add(new LogEntry(LocalDateTime.now().format(TIME_FMT), "INFO", "SafeChat Server khởi động trên cổng TCP " + server.tcpPort()));
            fullLogs.add(new LogEntry(LocalDateTime.now().format(TIME_FMT), "INFO", "UDP Discovery listening on " + server.discoveryPort() + ", Typing on " + server.typingPort()));
            fullLogs.add(new LogEntry(LocalDateTime.now().format(TIME_FMT), "INFO", "Cơ sở dữ liệu SQLite: " + config.databasePath().toAbsolutePath()));
            renderLogs.run();

            // Timer cập nhật số liệu thời gian thực
            javax.swing.Timer timer = new javax.swing.Timer(1000, e -> {
                if (server.isClosed()) {
                    ((javax.swing.Timer) e.getSource()).stop();
                    frame.dispose();
                    return;
                }

                int conns = server.connectionCount();
                int online = server.authenticatedCount();
                connsVal.setText(conns + " / " + config.maxConnections());
                onlineVal.setText(String.valueOf(online));

                // Cập nhật tab name
                centerTabs.setTitleAt(0, "👥 Phiên kết nối (" + conns + ")");

                // Query DB metrics
                Map<String, Object> metrics = server.queryMetrics();
                convsVal.setText(String.valueOf(metrics.getOrDefault("totalConversations", 0L)));
                msgsVal.setText(String.valueOf(metrics.getOrDefault("totalMessages", 0L)));

                long totalMod = ((Number) metrics.getOrDefault("totalModerated", 0L)).longValue();
                long clear = ((Number) metrics.getOrDefault("modClear", 0L)).longValue();
                long warning = ((Number) metrics.getOrDefault("modWarning", 0L)).longValue();
                long blocked = ((Number) metrics.getOrDefault("modBlocked", 0L)).longValue();
                long unavailable = ((Number) metrics.getOrDefault("modUnavailable", 0L)).longValue();

                modVal.setText(String.valueOf(totalMod));
                warnsVal.setText(String.valueOf(warning + blocked));

                if (totalMod > 0) {
                    clearBar.setValue((int) (clear * 100 / totalMod));
                    clearBar.setString(clear + " ảnh (" + (clear * 100 / totalMod) + "%)");
                    warnBar.setValue((int) (warning * 100 / totalMod));
                    warnBar.setString(warning + " ảnh (" + (warning * 100 / totalMod) + "%)");
                    blockBar.setValue((int) (blocked * 100 / totalMod));
                    blockBar.setString(blocked + " ảnh (" + (blocked * 100 / totalMod) + "%)");
                    unavailBar.setValue((int) (unavailable * 100 / totalMod));
                    unavailBar.setString(unavailable + " lần (" + (unavailable * 100 / totalMod) + "%)");
                } else {
                    clearBar.setValue(0); clearBar.setString("0 ảnh (0%)");
                    warnBar.setValue(0); warnBar.setString("0 ảnh (0%)");
                    blockBar.setValue(0); blockBar.setString("0 ảnh (0%)");
                    unavailBar.setValue(0); unavailBar.setString("0 lần (0%)");
                }

                // Cập nhật danh sách sessions
                List<SocialServer.ClientSessionInfo> sessions = server.activeSessions();
                sessionModel.setRowCount(0);
                for (SocialServer.ClientSessionInfo s : sessions) {
                    sessionModel.addRow(new Object[]{
                            s.connectionId().substring(0, Math.min(8, s.connectionId().length())) + "...",
                            s.username().isBlank() ? "(Chưa đăng nhập)" : s.username(),
                            s.remoteAddress(),
                            s.authenticated() ? "✓ Đã đăng nhập" : "Đang xác thực",
                            "Ngắt phiên"
                    });
                }
            });
            timer.start();
        });
    }

    private static JPanel createMetricCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, accentColor),
                new EmptyBorder(12, 12, 12, 12)
        ));

        JLabel t = new JLabel(title);
        t.setFont(new Font("Segoe UI", Font.BOLD, 10));
        t.setForeground(TEXT_MUTED);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        valueLabel.setForeground(TEXT_MAIN);

        card.add(t, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private static JPanel createProgressBarPanel(String title, JProgressBar bar) {
        JPanel p = new JPanel(new BorderLayout(8, 4));
        p.setOpaque(false);
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(TEXT_MAIN);
        p.add(lbl, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        return p;
    }

    private static final class LogEntry {
        final String time, level, message;
        LogEntry(String time, String level, String message) {
            this.time = time;
            this.level = level;
            this.message = message;
        }
    }
}
