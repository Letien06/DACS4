package com.p2p.client.ui.social;

import com.p2p.client.network.social.*;
import com.p2p.common.social.*;
import com.p2p.client.ui.Theme;
import com.p2p.client.ui.AppIcon;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import static com.p2p.client.ui.social.Values.*;

/**
 * Modern SafeChat Desktop Client UI.
 * High-performance, asynchronous, responsive messaging shell styled with FlatLaf.
 */
public final class SocialWindow extends JFrame {
    private final ExecutorService workers = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "social-ui-worker");
        t.setDaemon(true);
        return t;
    });
    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel body = new JPanel(new BorderLayout());
    private final JLabel connection = new JLabel("Chưa kết nối");
    private final JLabel identity = new JLabel();
    private JPanel ownAvatarHolder;
    private JButton notificationNav;
    private JButton currentActionSource;
    private volatile SocialClient client;
    private javax.net.ssl.SSLContext connectionTlsContext;
    private volatile long reconnectToken;
    private long pendingOwnerId;
    private long udpExpiresAt;
    private final javax.swing.Timer udpRefresh = new javax.swing.Timer(60000, e -> refreshUdp());
    private UdpClient udp;
    private MediaTransfers media = new MediaTransfers(() -> client, this::showError);
    private final ThumbnailCache thumbnails = new ThumbnailCache();
    private Map<String, Object> currentUser = new LinkedHashMap<>();
    private String host = "localhost";
    private int port = 5000;
    private String truststore = System.getProperty("social.truststore", "config/tls/truststore.p12");
    private String view = "";
    private Runnable refreshCurrent = () -> {};
    private boolean authenticated;
    private long generation;
    private JPanel chatMessages;
    private JScrollPane chatScroll;
    private long activeConversation;
    private long earliestSeq, latestSeq;

    /** Persistent conversation rail shown beside the chat content on the desktop shell. */
    private JPanel conversationRail;
    private JPanel conversationItems;
    private JTextField conversationSearch;
    private List<Map<String, Object>> conversationCache = new ArrayList<>();
    private long conversationRailGeneration;
    private String conversationFilter = "ALL";
    private String conversationCursor;
    private JButton conversationLoadMore;
    private javax.swing.Timer conversationRefreshTimer;
    private JPanel navigationRail;
    private final Map<String, String> compactNavLabels = Map.of(
            "Tin nhắn & nhóm", "CHAT",
            "Bạn bè & tìm người", "PEOPLE",
            "Cảnh báo ảnh", "ALERT",
            "Thông báo", "BELL",
            "Hồ sơ & cài đặt", "SETTINGS",
            "Quản trị", "ADMIN",
            "Tiếp tục tải tệp", "DOWNLOAD",
            "Đăng xuất", "LOGOUT"
    );
    private Map<String, Object> activeConversationData = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Map<String, Object>> messages = new LinkedHashMap<>();
    private final LinkedHashMap<String, PendingMessage> pendingMessages = new LinkedHashMap<>();
    private JLabel typingLabel;
    private JTextArea chatInput;
    private JButton sendButton;
    private final javax.swing.Timer typingExpiry = new javax.swing.Timer(5000, e -> {
        if (typingLabel != null) typingLabel.setText(" ");
    });
    private final javax.swing.Timer countRefresh = new javax.swing.Timer(20000, e -> {
        if (authenticated) refreshCount();
    });
    private final javax.swing.Timer eventRefresh = new javax.swing.Timer(400, e -> {
        if (authenticated) refreshCurrent.run();
    });

    public SocialWindow() {
        super("SafeChat · Nhắn tin bảo mật & Cảnh báo ảnh AI");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1120, 780);
        setMinimumSize(new Dimension(900, 620));
        setLocationRelativeTo(null);
        root.setBackground(Theme.APP_BACKGROUND);
        body.setBackground(Theme.CHAT_BACKGROUND);
        root.setBorder(new EmptyBorder(0, 0, 0, 0));
        setContentPane(root);
        typingExpiry.setRepeats(false);
        eventRefresh.setRepeats(false);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                shutdown();
                dispose();
            }
        });
        conversationRefreshTimer = new javax.swing.Timer(350, e -> refreshConversationRail());
        conversationRefreshTimer.setRepeats(false);
        root.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                adaptShell();
            }
        });
        showAuth(false);
        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowGainedFocus(WindowEvent e) {
                if (authenticated && activeConversation > 0) ackMessages();
            }
        });
    }

    private void shutdown() {
        reconnectToken++;
        authenticated = false;
        SocialClient closing = client;
        client = null;
        countRefresh.stop();
        udpRefresh.stop();
        eventRefresh.stop();
        typingExpiry.stop();
        if (udp != null) udp.close();
        thumbnails.close();
        media.close();
        if (closing != null) closing.close();
        workers.shutdownNow();
    }

    private JButton button(String text, Runnable action) {
        JButton b = Theme.ghostButton(text);
        b.putClientProperty("html.disable", true);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.addActionListener(e -> runAction(b, action));
        return b;
    }

    private JButton primary(String text, Runnable action) {
        JButton b = Theme.primaryButton(text);
        b.addActionListener(e -> runAction(b, action));
        return b;
    }

    private void runAction(JButton source, Runnable action) {
        JButton previous = currentActionSource;
        currentActionSource = source;
        try {
            action.run();
        } finally {
            currentActionSource = previous;
        }
    }

    private JPanel horizontal(Component... components) {
        JPanel p = new JPanel(new WrapLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Component c : components) p.add(c);
        return p;
    }

    private JPanel vertical() {
        return new StackPanel();
    }

    private JPanel card() {
        JPanel p = new CardPanel();
        p.setBackground(Theme.SURFACE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(16, 18, 16, 18)
        ));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static final class CardPanel extends JPanel {
        CardPanel() {
            super(new BorderLayout(12, 12));
        }
        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private JLabel label(String text, boolean heading) {
        JLabel l = new JLabel(text);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 0, 4));
        l.putClientProperty("html.disable", true);
        l.setFont(Theme.font(heading ? Font.BOLD : Font.PLAIN, heading ? 20 : 13));
        l.setForeground(heading ? Theme.TEXT : Theme.TEXT);
        return l;
    }

    private JTextArea text(String value, int rows) {
        JTextArea a = new JTextArea(value, rows, 35);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(Theme.font(Font.PLAIN, 13));
        a.setBackground(Theme.COMPOSER_BACKGROUND);
        a.setForeground(Theme.TEXT);
        a.setCaretColor(Theme.TEXT);
        a.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(8, 10, 8, 10)
        ));
        return a;
    }

    private JTextArea readable(String value) {
        JTextArea a = text(value, Math.min(8, Math.max(1, value.length() / 70 + 1)));
        a.setEditable(false);
        a.setOpaque(false);
        a.setBorder(null);
        a.setForeground(Theme.TEXT);
        return a;
    }

    private JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(null);
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.getVerticalScrollBar().setUnitIncrement(20);
        return s;
    }

    private static final class StackPanel extends JPanel implements Scrollable {
        StackPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 20; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return Math.max(20, r.height - 30); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
        @Override protected void addImpl(Component c, Object constraints, int index) {
            if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            super.addImpl(c, constraints, index);
        }
    }

    private static final class WrapLayout extends FlowLayout {
        WrapLayout() { super(FlowLayout.LEFT, 8, 4); }
        @Override public Dimension preferredLayoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                int width = target.getWidth();
                if (width <= 0 && target.getParent() != null) width = target.getParent().getWidth();
                if (width <= 0) width = 620;
                Insets in = target.getInsets();
                int available = Math.max(1, width - in.left - in.right - 2 * getHgap());
                int x = 0, y = 0, rowHeight = 0, max = 0;
                for (Component c : target.getComponents()) {
                    if (c.isVisible()) {
                        Dimension d = c.getPreferredSize();
                        if (x > 0 && x + getHgap() + d.width > available) {
                            max = Math.max(max, x);
                            y += rowHeight + getVgap();
                            x = 0;
                            rowHeight = 0;
                        }
                        if (x > 0) x += getHgap();
                        x += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }
                return new Dimension(Math.max(max, x) + in.left + in.right + 2 * getHgap(),
                        y + rowHeight + in.top + in.bottom + 2 * getVgap());
            }
        }
    }

    private JComponent avatar(Map<String, Object> user, int size) {
        boolean isGroup = "GROUP".equalsIgnoreCase(str(user, "kind", "type"));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(size, size));
        panel.setMaximumSize(new Dimension(size, size));
        panel.add(new Theme.Avatar(name(user), size, bool(user, "online"), isGroup));
        String mid = str(user, "avatarMediaId");
        if (!mid.isBlank() && !isGroup) {
            thumbnails.load(mid, size, icon -> {
                panel.removeAll();
                JLabel image = new JLabel(icon);
                image.setHorizontalAlignment(SwingConstants.CENTER);
                panel.add(image);
                panel.revalidate();
                panel.repaint();
            });
        }
        return panel;
    }

    private JButton photo(String mediaId, String fileName) {
        JButton b = button(fileName.isBlank() ? "Xem ảnh" : "Xem ảnh · " + fileName, () -> media.preview(this, mediaId));
        b.setPreferredSize(new Dimension(205, 205));
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        thumbnails.load(mediaId, 180, icon -> {
            b.setIcon(icon);
            b.setText("Xem ảnh");
            b.revalidate();
            b.repaint();
        });
        return b;
    }

    private JPanel titled(String title, Component... actions) {
        JPanel p = new JPanel(new BorderLayout(12, 12));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(20, 24, 12, 24));
        p.add(label(title, true), BorderLayout.NORTH);
        if (actions.length > 0) p.add(horizontal(actions), BorderLayout.CENTER);
        return p;
    }

    private void setView(String name, JPanel panel, Runnable refresh) {
        generation++;
        view = name;
        activeConversation = 0;
        messages.clear();
        body.removeAll();
        body.add(panel, BorderLayout.CENTER);
        refreshCurrent = refresh;
        body.revalidate();
        body.repaint();
    }

    private void invoke(Runnable r) { SwingUtilities.invokeLater(r); }

    private Map<String, Object> blocking(String type, Map<String, Object> payload, String key) {
        SocialClient c = client;
        if (c == null) throw new IllegalStateException("Chưa kết nối server");
        return (key == null ? c.request(type, payload) : c.request(type, payload, key)).join();
    }

    private Map<String, Object> blocking(SocialClient c, String type, Map<String, Object> payload, String key) {
        if (c == null) throw new IllegalStateException("Chưa kết nối server");
        return (key == null ? c.request(type, payload) : c.request(type, payload, key)).join();
    }

    private void query(String type, Map<String, Object> payload, Consumer<Map<String, Object>> success) {
        command(type, payload, null, null, success, false);
    }

    private void mutate(String type, Map<String, Object> payload, JButton source, Consumer<Map<String, Object>> success) {
        command(type, payload, UUID.randomUUID().toString(), source == null ? currentActionSource : source, success, true);
    }

    private void command(String type, Map<String, Object> payload, String key, JButton source, Consumer<Map<String, Object>> success, boolean retry) {
        if (source != null) source.setEnabled(false);
        SocialClient expected = client;
        CompletableFuture.supplyAsync(() -> blocking(expected, type, payload, key), workers).whenComplete((result, error) -> invoke(() -> {
            if (source != null) source.setEnabled(authenticated || type.startsWith("AUTH_"));
            if (client != expected) return;
            if (error != null) {
                Throwable cause = unwrap(error);
                if (retry && (cause instanceof java.io.IOException || cause instanceof TimeoutException)) {
                    Object[] choices = {"Thử lại", "Đóng"};
                    int answer = JOptionPane.showOptionDialog(this,
                            "Chưa nhận được phản hồi từ server. Thử lại ngay?\n" + friendly(cause),
                            "Kết nối gián đoạn", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[0]);
                    if (answer == 0) command(type, payload, key, source, success, true);
                    else showError(cause);
                } else showError(cause);
                return;
            }
            success.accept(result);
        }));
    }

    private Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) t = t.getCause();
        return t;
    }

    private String friendly(Throwable error) {
        Throwable e = unwrap(error);
        if (e instanceof RemoteException remote) {
            String code = remote.getCode();
            return switch (code) {
                case "INVALID_CREDENTIALS" -> "Tên đăng nhập hoặc mật khẩu không chính xác.";
                case "ALREADY_SIGNED_IN" -> "Tài khoản đang đăng nhập trên thiết bị khác.";
                case "VERSION_CONFLICT" -> "Dữ liệu đã thay đổi trên server. Vui lòng làm mới lại.";
                case "NOT_FOUND" -> "Nội dung không tồn tại hoặc bạn không có quyền xem.";
                case "FRIENDSHIP_REQUIRED" -> "Cần kết bạn trước khi thực hiện thao tác này.";
                case "UNAUTHENTICATED" -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
                case "RATE_LIMITED" -> "Thao tác quá nhanh. Vui lòng chờ vài giây rồi thử lại.";
                case "USERNAME_TAKEN" -> "Tên đăng nhập này đã có người sử dụng.";
                case "ACCOUNT_SUSPENDED" -> "Tài khoản hiện đang bị tạm khóa bởi quản trị viên.";
                case "ACTION_FORBIDDEN" -> "Bạn không có quyền thực hiện thao tác này.";
                case "INVALID_STATE" -> "Trạng thái không hợp lệ. Vui lòng làm mới danh sách.";
                case "INVALID_REQUEST" -> "Dữ liệu gửi lên không hợp lệ. Kiểm tra lại thông tin.";
                case "INCOMING_REQUEST_EXISTS" -> "Người này đã gửi lời mời cho bạn. Hãy chấp nhận lời mời.";
                case "ALREADY_FRIENDS" -> "Hai bạn đã là bạn bè của nhau.";
                case "QUOTA_EXCEEDED" -> "Đã đạt giới hạn dung lượng lưu trữ.";
                case "FILE_TOO_LARGE" -> "Tệp vượt quá kích thước cho phép (tối đa 25MB cho tệp chat, 5MB cho ảnh).";
                case "HASH_MISMATCH" -> "Kiểm tra toàn vẹn tệp thất bại. Vui lòng thử tải lại.";
                case "SERVER_BUSY" -> "Máy chủ đang bận xử lý. Vui lòng thử lại sau giây lát.";
                default -> Objects.toString(e.getMessage(), "Không thể thực hiện thao tác.");
            };
        }
        if (e instanceof javax.net.ssl.SSLException) return "Không thể xác minh chứng chỉ bảo mật TLS của máy chủ.";
        return Objects.toString(e.getMessage(), "Không thể kết nối tới máy chủ. Vui lòng kiểm tra mạng.");
    }

    private void showError(Throwable e) {
        if (!SwingUtilities.isEventDispatchThread()) {
            invoke(() -> showError(e));
            return;
        }
        JOptionPane.showMessageDialog(this, friendly(e), "Thông báo", JOptionPane.WARNING_MESSAGE);
    }

    private void info(String s) {
        JOptionPane.showMessageDialog(this, s, "SafeChat", JOptionPane.INFORMATION_MESSAGE);
    }

    private boolean confirm(String s) {
        return JOptionPane.showConfirmDialog(this, s, "Xác nhận", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    // ==========================================
    // 1. MÀN HÌNH ĐĂNG NHẬP / ĐĂNG KÝ (AUTH VIEW)
    // ==========================================
    private void showAuth(boolean register) {
        reconnectToken++;
        authenticated = false;
        generation++;
        activeConversation = 0;
        messages.clear();
        currentUser.clear();
        resetConversationRail();
        thumbnails.bind(null);
        countRefresh.stop();
        udpRefresh.stop();
        for (Window window : getOwnedWindows()) window.dispose();
        root.removeAll();

        // Nền tối thanh lịch
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Theme.APP_BACKGROUND);
        outer.setBorder(new EmptyBorder(24, 24, 24, 24));

        // Card đăng nhập sang trọng căn giữa
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(Theme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(32, 36, 28, 36)
        ));
        card.setPreferredSize(new Dimension(460, register ? 580 : 490));

        // Header: Logo SafeChat + Icon Shield
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        brandRow.setOpaque(false);
        JLabel logoIcon = new JLabel(new AppIcon(AppIcon.Type.SHIELD, Theme.PRIMARY, 32));
        JLabel brand = new JLabel("SafeChat");
        brand.setFont(Theme.font(Font.BOLD, 28));
        brand.setForeground(Theme.TEXT);
        brandRow.add(logoIcon);
        brandRow.add(brand);

        JLabel subtitle = new JLabel(register ? "Tạo tài khoản mới để bắt đầu trò chuyện" : "Đăng nhập để kết nối và trò chuyện bảo mật");
        subtitle.setFont(Theme.font(Font.PLAIN, 13));
        subtitle.setForeground(Theme.MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        brandRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(brandRow);
        headerPanel.add(Box.createVerticalStrut(6));
        headerPanel.add(subtitle);
        card.add(headerPanel, BorderLayout.NORTH);

        // Fields Panel
        JPanel fields = vertical();
        fields.setOpaque(false);

        JTextField hostField = new JTextField(host);
        JTextField portField = new JTextField(String.valueOf(port));
        JTextField trustField = new JTextField(truststore);
        JPasswordField trustPassword = new JPasswordField(Objects.toString(System.getenv("SOCIAL_TRUSTSTORE_PASSWORD"),
                Objects.toString(System.getenv("SOCIAL_TLS_PASSWORD"), "changeit")));

        JTextField user = new JTextField();
        JTextField display = new JTextField();
        JPasswordField password = new JPasswordField();
        JPasswordField confirmPassword = new JPasswordField();

        String[] offeredServerId = {null};
        String[] offeredHost = {null};
        int[] offeredPort = {0};

        if (register) {
            addField(fields, "Tên đăng nhập", user);
            addField(fields, "Tên hiển thị (biệt danh)", display);
            addField(fields, "Mật khẩu (10 - 128 ký tự)", password);
            addField(fields, "Xác nhận mật khẩu", confirmPassword);
        } else {
            addField(fields, "Tên đăng nhập", user);
            addField(fields, "Mật khẩu", password);
        }

        // Cài đặt nâng cao (ẩn theo mặc định)
        JPanel advancedBox = vertical();
        advancedBox.setOpaque(false);
        advancedBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(10, 0, 8, 0)
        ));
        advancedBox.setVisible(false);

        JPanel serverRow = new JPanel(new BorderLayout(8, 0));
        serverRow.setOpaque(false);
        hostField.setPreferredSize(new Dimension(180, 34));
        portField.setPreferredSize(new Dimension(70, 34));
        serverRow.add(hostField, BorderLayout.CENTER);
        serverRow.add(portField, BorderLayout.EAST);
        addField(advancedBox, "Địa chỉ máy chủ (Host / Port)", serverRow);

        JPanel trustRow = new JPanel(new BorderLayout(6, 0));
        trustRow.setOpaque(false);
        JButton pickTrust = Theme.ghostButton("Chọn");
        pickTrust.addActionListener(e -> {
            JFileChooser c = new JFileChooser();
            if (c.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                trustField.setText(c.getSelectedFile().getAbsolutePath());
            }
        });
        trustRow.add(trustField, BorderLayout.CENTER);
        trustRow.add(pickTrust, BorderLayout.EAST);
        addField(advancedBox, "Truststore PKCS12", trustRow);
        addField(advancedBox, "Mật khẩu Truststore", trustPassword);

        JButton lanBtn = Theme.ghostButton("🔍 Tìm máy chủ trong mạng LAN");
        lanBtn.addActionListener(e -> {
            connection.setText("Đang tìm máy chủ trong mạng LAN...");
            UdpClient.discover(null, 5001).whenComplete((offers, error) -> invoke(() -> {
                if (error != null) { showError(error); return; }
                if (offers.isEmpty()) { info("Không tìm thấy máy chủ trong mạng LAN. Bạn có thể nhập IP/Port thủ công."); return; }
                String[] options = offers.stream().map(o -> o.serverName() + " · " + o.host() + ":" + o.tcpPort()).toArray(String[]::new);
                String selected = (String) JOptionPane.showInputDialog(this, "Chọn máy chủ trong mạng LAN:", "Máy chủ LAN", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                if (selected != null) {
                    int i = Arrays.asList(options).indexOf(selected);
                    hostField.setText(offers.get(i).host());
                    portField.setText("" + offers.get(i).tcpPort());
                    offeredServerId[0] = offers.get(i).serverId();
                    offeredHost[0] = offers.get(i).host();
                    offeredPort[0] = offers.get(i).tcpPort();
                }
            }));
        });
        advancedBox.add(Box.createVerticalStrut(4));
        advancedBox.add(lanBtn);

        JButton toggleAdvanced = Theme.ghostButton("⚙️ Cài đặt kết nối nâng cao ▼");
        toggleAdvanced.setFont(Theme.font(Font.PLAIN, 12));
        toggleAdvanced.setForeground(Theme.MUTED);
        toggleAdvanced.addActionListener(e -> {
            boolean show = !advancedBox.isVisible();
            advancedBox.setVisible(show);
            toggleAdvanced.setText(show ? "⚙️ Thu gọn cài đặt kết nối ▲" : "⚙️ Cài đặt kết nối nâng cao ▼");
            card.setPreferredSize(new Dimension(460, show ? (register ? 660 : 620) : (register ? 580 : 490)));
            card.revalidate();
            card.repaint();
        });

        fields.add(Box.createVerticalStrut(8));
        fields.add(toggleAdvanced);
        fields.add(advancedBox);
        card.add(fields, BorderLayout.CENTER);

        // Footer Actions
        JPanel footer = vertical();
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(8, 0, 0, 0));

        JLabel stateLabel = new JLabel("🛡️ Kết nối được mã hóa TLS · Mật khẩu không lưu");
        stateLabel.setFont(Theme.font(Font.PLAIN, 11));
        stateLabel.setForeground(Theme.MUTED);
        stateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton submit = Theme.primaryButton(register ? "Đăng ký tài khoản" : "Đăng nhập SafeChat");
        submit.setFont(Theme.font(Font.BOLD, 14));
        submit.setPreferredSize(new Dimension(Integer.MAX_VALUE, 42));
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        submit.setAlignmentX(Component.CENTER_ALIGNMENT);

        submit.addActionListener(e -> {
            char[] secret = password.getPassword(), repeat = confirmPassword.getPassword(), trust = trustPassword.getPassword();
            if (user.getText().trim().isEmpty() || secret.length < 10 || secret.length > 128) {
                Arrays.fill(secret, '\0'); Arrays.fill(repeat, '\0'); Arrays.fill(trust, '\0');
                info("Vui lòng nhập tên đăng nhập và mật khẩu từ 10 - 128 ký tự.");
                return;
            }
            if (register && (!Arrays.equals(secret, repeat) || display.getText().isBlank())) {
                Arrays.fill(secret, '\0'); Arrays.fill(repeat, '\0'); Arrays.fill(trust, '\0');
                info("Vui lòng nhập tên hiển thị và mật khẩu xác nhận phải trùng khớp.");
                return;
            }
            Arrays.fill(repeat, '\0');
            String username = user.getText().trim();
            String name = display.getText().trim();
            String serverHost = hostField.getText().trim();
            String store = trustField.getText().trim();
            int serverPort;
            try {
                serverPort = Integer.parseInt(portField.getText());
                if (serverPort < 1 || serverPort > 65535) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                Arrays.fill(secret, '\0'); Arrays.fill(trust, '\0');
                info("Cổng kết nối Port phải là số từ 1 đến 65535.");
                return;
            }

            submit.setEnabled(false);
            stateLabel.setText("⏳ Đang kết nối TLS và xác thực tài khoản...");
            reconnectToken++;
            String expectedServer = serverHost.equals(offeredHost[0]) && serverPort == offeredPort[0] ? offeredServerId[0] : null;

            CompletableFuture.supplyAsync(() -> {
                SocialClient c = null;
                try {
                    if (client != null) client.close();
                    connectionTlsContext = TlsContexts.client(Path.of(store), trust);
                    c = new SocialClient(serverHost, serverPort, connectionTlsContext);
                    Map<String, Object> hello = c.connect().join();
                    if (expectedServer != null && !expectedServer.equals(str(hello, "serverId"))) {
                        throw new SecurityException("ServerId TLS không khớp máy chủ LAN đã chọn.");
                    }
                    Map<String, Object> login = c.request(register ? "AUTH_REGISTER" : "AUTH_LOGIN",
                            of("username", username, "password", new String(secret), "displayName", register ? name : null)).join();
                    client = c;
                    return login;
                } catch (Throwable ex) {
                    if (c != null) c.close();
                    throw new CompletionException(ex);
                } finally {
                    Arrays.fill(secret, '\0'); Arrays.fill(trust, '\0');
                }
            }, workers).whenComplete((login, error) -> invoke(() -> {
                password.setText(""); confirmPassword.setText(""); submit.setEnabled(true);
                host = serverHost; port = serverPort; truststore = store;
                if (error != null) {
                    stateLabel.setText("❌ Không thể kết nối · Hãy kiểm tra lại");
                    showError(error);
                    return;
                }
                if (register) {
                    info("Tạo tài khoản thành công! Bạn có thể đăng nhập ngay bây giờ.");
                    client.close(); client = null;
                    showAuth(false);
                    return;
                }
                currentUser = map(login.get("currentUser"));
                if (currentUser.isEmpty()) currentUser = map(login.get("user"));
                if (pendingOwnerId != 0 && pendingOwnerId != id(currentUser, "user")) {
                    pendingMessages.clear();
                    media.close();
                    media = new MediaTransfers(() -> client, this::showError);
                }
                pendingOwnerId = id(currentUser, "user");
                media.bind(client);
                thumbnails.bind(client);
                authenticated = true;
                SocialClient logged = client;
                logged.onEvent(event -> {
                    media.event(event);
                    invoke(() -> { if (client == logged) onEvent(event); });
                });
                logged.onDisconnect(errorCause -> invoke(() -> {
                    if (client == logged && authenticated) disconnected(errorCause);
                }));
                setupUdp(login);
                showMain();
                query("STATE_SYNC", of("limit", 20), r -> refreshCount());
                showConversations();
            }));
        });

        JButton switchMode = Theme.ghostButton(register ? "Đã có tài khoản? Đăng nhập ngay" : "Chưa có tài khoản? Tạo tài khoản mới");
        switchMode.setFont(Theme.font(Font.PLAIN, 13));
        switchMode.setPreferredSize(new Dimension(Integer.MAX_VALUE, 36));
        switchMode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        switchMode.setAlignmentX(Component.CENTER_ALIGNMENT);
        switchMode.addActionListener(e -> showAuth(!register));

        footer.add(submit);
        footer.add(Box.createVerticalStrut(8));
        footer.add(switchMode);
        footer.add(Box.createVerticalStrut(8));
        footer.add(stateLabel);
        card.add(footer, BorderLayout.SOUTH);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0;
        gc.anchor = GridBagConstraints.CENTER;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 1; gc.weighty = 1;
        outer.add(card, gc);

        root.add(outer, BorderLayout.CENTER);
        root.revalidate();
        root.repaint();
        getRootPane().setDefaultButton(submit);
    }

    private void addField(JPanel p, String name, Component input) {
        if (input instanceof JTextField || input instanceof JComboBox<?>) {
            input.setPreferredSize(new Dimension(input.getPreferredSize().width, 36));
            input.setFont(Theme.font(Font.PLAIN, 13));
            if (input instanceof JTextField tf) {
                tf.setBackground(Theme.COMPOSER_BACKGROUND);
                tf.setForeground(Theme.TEXT);
                tf.setCaretColor(Theme.TEXT);
                tf.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.BORDER, 1),
                        new EmptyBorder(6, 10, 6, 10)
                ));
            }
        }
        JPanel row = new JPanel(new BorderLayout(4, 4));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(3, 0, 5, 0));
        JLabel lbl = label(name, false);
        lbl.setFont(Theme.font(Font.BOLD, 12));
        lbl.setForeground(Theme.TEXT);
        row.add(lbl, BorderLayout.NORTH);
        row.add(input, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        p.add(row);
    }

    private void setupUdp(Map<String, Object> login) {
        if (udp != null) udp.close();
        Map<String, Object> config = map(login.get("udp"));
        if (config.isEmpty()) config = new LinkedHashMap<>(login);
        if (!config.containsKey("sessionId")) config.put("sessionId", login.get("sessionId"));
        udpExpiresAt = num(config, "keyExpiresAt");
        SocialClient expected = client;
        Map<String, Object> finalConfig = config;
        workers.execute(() -> {
            try {
                UdpClient created = new UdpClient(host, (int) (num(finalConfig, "serverUdpPort") == 0 ? 5002 : num(finalConfig, "serverUdpPort")));
                created.onTyping(p -> invoke(() -> {
                    if (!authenticated || client != expected || udp != created) return;
                    if (activeConversation == num(p, "conversationId") && typingLabel != null) {
                        long remaining = num(p, "expiresAt") - expected.serverTime();
                        if (bool(p, "typing") && remaining > 0) {
                            typingLabel.setText("✍️ Bạn cùng trò chuyện đang nhập...");
                            typingExpiry.setInitialDelay((int) Math.min(5000, remaining));
                            typingExpiry.restart();
                        } else {
                            typingLabel.setText(" ");
                            typingExpiry.stop();
                        }
                    }
                }));
                created.configure(finalConfig, expected.serverTime());
                invoke(() -> {
                    if (client == expected && authenticated) {
                        udp = created;
                        udpRefresh.start();
                    } else created.close();
                });
            } catch (Exception ignored) {
                /* UDP loss never prevents reliable TLS chat. */
            }
        });
    }

    private void refreshUdp() {
        if (!authenticated || udp == null || client == null || client.serverTime() < udpExpiresAt - 120000) return;
        query("UDP_SESSION_REFRESH", of(), p -> setupUdp(p));
    }

    // ==========================================
    // 2. MÀN HÌNH CHÍNH (MAIN DESKTOP SHELL)
    // ==========================================
    private void showMain() {
        root.removeAll();

        // 2.1. Thanh điều hướng bên trái (Navigation Rail)
        JPanel sidebar = vertical();
        navigationRail = sidebar;
        sidebar.setPreferredSize(new Dimension(210, 600));
        sidebar.setBackground(Theme.NAV_BACKGROUND);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
                new EmptyBorder(22, 14, 18, 14)
        ));

        // Brand & User Profile
        JPanel brandBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brandBadge.setOpaque(false);
        brandBadge.add(new JLabel(new AppIcon(AppIcon.Type.SHIELD, Theme.PRIMARY, 22)));
        JLabel brandName = new JLabel("SafeChat");
        brandName.setFont(Theme.font(Font.BOLD, 17));
        brandName.setForeground(Theme.TEXT);
        brandBadge.add(brandName);
        sidebar.add(brandBadge);
        sidebar.add(Box.createVerticalStrut(16));

        ownAvatarHolder = (JPanel) avatar(currentUser, 44);
        JPanel userRow = new JPanel(new BorderLayout(10, 0));
        userRow.setOpaque(false);
        userRow.add(ownAvatarHolder, BorderLayout.WEST);

        JPanel identityBox = new JPanel();
        identityBox.setOpaque(false);
        identityBox.setLayout(new BoxLayout(identityBox, BoxLayout.Y_AXIS));
        identity.setText(name(currentUser));
        identity.setFont(Theme.font(Font.BOLD, 13));
        identity.setForeground(Theme.TEXT);
        identity.putClientProperty("html.disable", true);
        JLabel roleLabel = new JLabel("ONLINE");
        roleLabel.setFont(Theme.font(Font.BOLD, 10));
        roleLabel.setForeground(Theme.ONLINE);
        identityBox.add(identity);
        identityBox.add(Box.createVerticalStrut(2));
        identityBox.add(roleLabel);
        userRow.add(identityBox, BorderLayout.CENTER);
        sidebar.add(userRow);
        sidebar.add(Box.createVerticalStrut(20));

        // Navigation Items
        sidebar.add(createNavBtn("Tin nhắn & nhóm", AppIcon.Type.CHAT, this::showConversations));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(createNavBtn("Bạn bè & tìm người", AppIcon.Type.GROUP, this::showPeople));
        sidebar.add(Box.createVerticalStrut(6));
        notificationNav = createNavBtn("Thông báo", AppIcon.Type.BELL, this::showNotifications);
        sidebar.add(notificationNav);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(createNavBtn("Cảnh báo ảnh", AppIcon.Type.WARNING, () -> info(
                "Chính sách kiểm duyệt hình ảnh SafeChat:\n\n" +
                "• Mọi hình ảnh gửi qua hệ thống được kiểm tra an toàn bằng AI trên máy chủ.\n" +
                "• Ảnh có dấu hiệu bạo lực (WARNING) yêu cầu xác nhận trước khi xem/tải.\n" +
                "• Ảnh bạo lực nghiêm trọng (BLOCKED) sẽ bị chặn hoàn toàn để bảo vệ người dùng.\n" +
                "• Tin nhắn văn bản và tệp thông thường vẫn hoạt động bình thường khi AI bận.")));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(createNavBtn("Hồ sơ & cài đặt", AppIcon.Type.SETTINGS, this::showSettings));

        if ("ADMIN".equals(str(currentUser, "role"))) {
            sidebar.add(Box.createVerticalStrut(6));
            sidebar.add(createNavBtn("Quản trị", AppIcon.Type.SERVER, this::showAdmin));
        }

        sidebar.add(Box.createVerticalGlue());
        sidebar.add(createNavBtn("Tiếp tục tải tệp", AppIcon.Type.DOWNLOAD, () -> media.resumeUploads(this)));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(createNavBtn("Đăng xuất", AppIcon.Type.LOGOUT, this::logout));

        // Footer Bar
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setBackground(Theme.SURFACE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(8, 16, 8, 16)
        ));
        connection.setText("● Đã kết nối an toàn TLS · " + host + ":" + port);
        connection.setFont(Theme.font(Font.PLAIN, 12));
        connection.setForeground(Theme.ONLINE);

        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        footerActions.setOpaque(false);
        JButton syncBtn = Theme.ghostButton("Đồng bộ");
        syncBtn.setFont(Theme.font(Font.PLAIN, 12));
        syncBtn.addActionListener(e -> query("STATE_SYNC", of("limit", 20), r -> {
            refreshConversationRail();
            refreshCurrent.run();
            refreshCount();
        }));
        JButton reconnectBtn = Theme.ghostButton("Đăng nhập lại");
        reconnectBtn.setFont(Theme.font(Font.PLAIN, 12));
        reconnectBtn.addActionListener(e -> {
            reconnectToken++;
            if (client != null) client.close();
            showAuth(false);
        });
        footerActions.add(syncBtn);
        footerActions.add(reconnectBtn);
        footer.add(connection, BorderLayout.WEST);
        footer.add(footerActions, BorderLayout.EAST);

        // Center: Conversation Rail (West) + Chat Body (Center)
        conversationRail = buildConversationRail();
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(conversationRail, BorderLayout.WEST);
        center.add(body, BorderLayout.CENTER);

        root.add(sidebar, BorderLayout.WEST);
        root.add(center, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        root.revalidate();
        root.repaint();
        getRootPane().setDefaultButton(null);
        countRefresh.start();
        refreshCount();
        refreshConversationRail();
        adaptShell();
    }

    private JButton createNavBtn(String text, AppIcon.Type iconType, Runnable action) {
        JButton b = new JButton(text, new AppIcon(iconType, Theme.MUTED, 18));
        b.setFont(Theme.font(Font.PLAIN, 13));
        b.setForeground(Theme.TEXT);
        b.setBackground(Theme.NAV_BACKGROUND);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setIconTextGap(10);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(9, 12, 9, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(Theme.SURFACE_HOVER); }
            @Override public void mouseExited(MouseEvent e) { b.setBackground(Theme.NAV_BACKGROUND); }
        });
        b.addActionListener(e -> runAction(b, action));
        return b;
    }

    private void adaptShell() {
        if (navigationRail == null) return;
        boolean compact = getWidth() < 1100;
        navigationRail.setPreferredSize(new Dimension(compact ? 72 : 210, 600));
        navigationRail.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
                new EmptyBorder(20, compact ? 8 : 14, 16, compact ? 8 : 14)
        ));

        for (Component c : navigationRail.getComponents()) {
            if (c instanceof JButton b) {
                String label = b.getText();
                Object stored = b.getClientProperty("fullLabel");
                String raw = stored != null ? String.valueOf(stored) : label;
                String key = raw.startsWith("Thông báo") ? "Thông báo" : raw;
                if (compactNavLabels.containsKey(key)) {
                    if (compact) {
                        if (stored == null) b.putClientProperty("fullLabel", label);
                        b.setToolTipText(key);
                        b.setText("");
                        b.setIcon(navIcon(compactNavLabels.get(key)));
                        b.setHorizontalAlignment(SwingConstants.CENTER);
                    } else {
                        Object full = b.getClientProperty("fullLabel");
                        if (full != null) {
                            b.setText(String.valueOf(full));
                            b.putClientProperty("fullLabel", null);
                        }
                        b.setIcon(navIcon(compactNavLabels.get(key)));
                        b.setToolTipText(null);
                        b.setHorizontalAlignment(SwingConstants.LEFT);
                    }
                    b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
                }
            }
        }
        root.revalidate();
    }

    private Icon navIcon(String key) {
        return switch (key) {
            case "CHAT" -> new AppIcon(AppIcon.Type.CHAT, Theme.TEXT, 20);
            case "PEOPLE" -> new AppIcon(AppIcon.Type.GROUP, Theme.TEXT, 20);
            case "ALERT" -> new AppIcon(AppIcon.Type.WARNING, Theme.WARNING, 20);
            case "BELL" -> new AppIcon(AppIcon.Type.BELL, Theme.TEXT, 20);
            case "SETTINGS" -> new AppIcon(AppIcon.Type.SETTINGS, Theme.TEXT, 20);
            case "ADMIN" -> new AppIcon(AppIcon.Type.SERVER, Theme.TEXT, 20);
            case "DOWNLOAD" -> new AppIcon(AppIcon.Type.DOWNLOAD, Theme.TEXT, 20);
            case "LOGOUT" -> new AppIcon(AppIcon.Type.LOGOUT, Theme.DANGER, 20);
            default -> new AppIcon(AppIcon.Type.FILE, Theme.TEXT, 20);
        };
    }

    // ==========================================
    // 3. DANH SÁCH CUỘC TRÒ CHUYỆN (CONVERSATION RAIL)
    // ==========================================
    private JPanel buildConversationRail() {
        JPanel rail = new JPanel(new BorderLayout(0, 10));
        rail.setPreferredSize(new Dimension(310, 600));
        rail.setBackground(Theme.SURFACE);
        rail.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
                new EmptyBorder(16, 14, 12, 14)
        ));

        // Header
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setOpaque(false);
        JLabel title = new JLabel("Cuộc trò chuyện");
        title.setFont(Theme.font(Font.BOLD, 18));
        title.setForeground(Theme.TEXT);
        header.add(title, BorderLayout.WEST);

        JButton create = Theme.ghostButton("＋ Nhóm");
        create.setFont(Theme.font(Font.BOLD, 12));
        create.addActionListener(e -> createGroup());
        header.add(create, BorderLayout.EAST);

        // Ô tìm kiếm hiện đại
        conversationSearch = new JTextField();
        conversationSearch.putClientProperty("JTextField.placeholderText", "Tìm cuộc trò chuyện...");
        conversationSearch.setBackground(Theme.COMPOSER_BACKGROUND);
        conversationSearch.setForeground(Theme.TEXT);
        conversationSearch.setCaretColor(Theme.TEXT);
        conversationSearch.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
        conversationSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { renderConversationRail(); }
            public void removeUpdate(DocumentEvent e) { renderConversationRail(); }
            public void changedUpdate(DocumentEvent e) { renderConversationRail(); }
        });

        // Filter Bar dạng segment buttons
        JPanel filterBar = new JPanel(new GridLayout(1, 3, 4, 0));
        filterBar.setOpaque(false);
        JButton allBtn = Theme.ghostButton("Tất cả");
        JButton directBtn = Theme.ghostButton("Cá nhân");
        JButton groupBtn = Theme.ghostButton("Nhóm");

        Runnable updateFilterStyle = () -> {
            allBtn.setBackground("ALL".equals(conversationFilter) ? Theme.PRIMARY_SOFT : Theme.SURFACE_HOVER);
            directBtn.setBackground("DIRECT".equals(conversationFilter) ? Theme.PRIMARY_SOFT : Theme.SURFACE_HOVER);
            groupBtn.setBackground("GROUP".equals(conversationFilter) ? Theme.PRIMARY_SOFT : Theme.SURFACE_HOVER);
            renderConversationRail();
        };

        allBtn.addActionListener(e -> { conversationFilter = "ALL"; updateFilterStyle.run(); });
        directBtn.addActionListener(e -> { conversationFilter = "DIRECT"; updateFilterStyle.run(); });
        groupBtn.addActionListener(e -> { conversationFilter = "GROUP"; updateFilterStyle.run(); });
        allBtn.setBackground(Theme.PRIMARY_SOFT);

        filterBar.add(allBtn);
        filterBar.add(directBtn);
        filterBar.add(groupBtn);

        JPanel topBox = vertical();
        topBox.setOpaque(false);
        topBox.add(header);
        topBox.add(Box.createVerticalStrut(10));
        topBox.add(conversationSearch);
        topBox.add(Box.createVerticalStrut(8));
        topBox.add(filterBar);
        rail.add(topBox, BorderLayout.NORTH);

        conversationItems = vertical();
        conversationItems.setOpaque(false);
        conversationItems.setBorder(new EmptyBorder(6, 0, 6, 0));

        JPanel listWrap = new JPanel(new BorderLayout());
        listWrap.setOpaque(false);
        listWrap.add(scroll(conversationItems), BorderLayout.CENTER);

        conversationLoadMore = Theme.ghostButton("Tải thêm cuộc trò chuyện");
        conversationLoadMore.setVisible(false);
        conversationLoadMore.addActionListener(e -> loadConversationRailMore());
        listWrap.add(conversationLoadMore, BorderLayout.SOUTH);
        rail.add(listWrap, BorderLayout.CENTER);

        return rail;
    }

    private void refreshConversationRail() {
        if (conversationRail == null || !authenticated) return;
        long token = ++conversationRailGeneration;
        conversationCursor = null;
        conversationCache.clear();
        Map<String, Object> request = of("limit", 50);
        query("CONVERSATION_LIST", request, r -> {
            if (token != conversationRailGeneration) return;
            conversationCache.addAll(rows(r.get("items")));
            conversationCursor = str(r, "nextCursor", "cursor");
            if (conversationLoadMore != null) conversationLoadMore.setVisible(!conversationCursor.isBlank());
            renderConversationRail();
        });
    }

    private void loadConversationRailMore() {
        if (conversationRail == null || !authenticated || conversationCursor == null || conversationCursor.isBlank()) return;
        long token = ++conversationRailGeneration;
        String cursor = conversationCursor;
        query("CONVERSATION_LIST", of("limit", 50, "cursor", cursor), r -> {
            if (token != conversationRailGeneration) return;
            conversationCache.addAll(rows(r.get("items")));
            conversationCursor = str(r, "nextCursor", "cursor");
            if (conversationLoadMore != null) conversationLoadMore.setVisible(!conversationCursor.isBlank());
            renderConversationRail();
        });
    }

    private void invalidateConversationRail() {
        conversationRailGeneration++;
        conversationCursor = null;
        conversationCache.clear();
        if (conversationItems != null) {
            conversationItems.removeAll();
            conversationItems.add(label("Đang cập nhật cuộc trò chuyện…", false));
            conversationItems.revalidate();
            conversationItems.repaint();
        }
        if (conversationRefreshTimer != null) conversationRefreshTimer.restart();
    }

    private void resetConversationRail() {
        conversationRailGeneration++;
        conversationCursor = null;
        conversationCache.clear();
        conversationFilter = "ALL";
        if (conversationRefreshTimer != null) conversationRefreshTimer.stop();
        conversationRail = null;
        conversationItems = null;
        conversationSearch = null;
        conversationLoadMore = null;
        navigationRail = null;
    }

    private void renderConversationRail() {
        if (conversationItems == null) return;
        conversationItems.removeAll();
        String q = conversationSearch == null ? "" : conversationSearch.getText().trim().toLowerCase();
        int visible = 0;

        for (Map<String, Object> conv : conversationCache) {
            String kind = str(conv, "kind", "type");
            if (!"ALL".equals(conversationFilter) && !conversationFilter.equals(kind)) continue;
            String title = name(conv);
            if (title.isBlank()) title = str(conv, "title", "Cuộc trò chuyện #" + id(conv, "conversation"));
            String preview = str(conv, "latestPreview", "preview", "latest_content");
            Map<String, Object> latest = map(conv.get("latestMessage"));
            if (latest.isEmpty()) latest = map(conv.get("latest"));
            if (preview.isBlank()) preview = str(latest, "content", "body");

            if (!q.isBlank() && !((title + " " + preview).toLowerCase().contains(q))) continue;
            visible++;

            long cid = id(conv, "conversation");
            long unread = num(conv, "unreadCount", "unread");
            String shownTitle = ellipsizeRail(title, 22);
            String shownPreview = ellipsizeRail(preview.isBlank() ? "Chưa có tin nhắn" : preview, 32);
            String timestamp = str(conv, "lastActivityAt", "updatedAt", "latestAt");

            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(true);
            boolean isSelected = cid == activeConversation;
            row.setBackground(isSelected ? Theme.PRIMARY_SOFT : Theme.SURFACE);
            row.setBorder(BorderFactory.createCompoundBorder(
                    isSelected ? BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.PRIMARY) : BorderFactory.createEmptyBorder(0, 3, 0, 0),
                    new EmptyBorder(8, 8, 8, 8)
            ));

            row.add(avatar(conv, 42), BorderLayout.WEST);

            JPanel copy = new JPanel();
            copy.setOpaque(false);
            copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

            // Top row: Title + Time
            JPanel topRow = new JPanel(new BorderLayout());
            topRow.setOpaque(false);
            JLabel n = new JLabel(shownTitle);
            n.setToolTipText(title);
            n.setFont(Theme.font(Font.BOLD, 13));
            n.setForeground(Theme.TEXT);
            topRow.add(n, BorderLayout.WEST);

            if (!timestamp.isBlank()) {
                JLabel t = new JLabel(formatRelativeTime(timestamp));
                t.setForeground(Theme.MUTED);
                t.setFont(Theme.font(Font.PLAIN, 10));
                topRow.add(t, BorderLayout.EAST);
            }
            copy.add(topRow);
            copy.add(Box.createVerticalStrut(3));

            // Bottom row: Preview + Unread pill badge
            JPanel btmRow = new JPanel(new BorderLayout());
            btmRow.setOpaque(false);
            JLabel p = new JLabel(shownPreview);
            p.setToolTipText(preview);
            p.setForeground(Theme.MUTED);
            p.setFont(Theme.font(Font.PLAIN, 12));
            btmRow.add(p, BorderLayout.CENTER);

            if (unread > 0) {
                JComponent unreadBadge = Theme.pill(String.valueOf(unread), Theme.PRIMARY, Color.WHITE);
                btmRow.add(unreadBadge, BorderLayout.EAST);
            }
            copy.add(btmRow);
            row.add(copy, BorderLayout.CENTER);

            row.setFocusable(true);
            row.setToolTipText(title);
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open-conversation");
            row.getActionMap().put("open-conversation", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { openChat(conv); }
            });

            MouseAdapter click = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) openChat(conv);
                }
                @Override public void mouseEntered(MouseEvent e) {
                    if (cid != activeConversation) row.setBackground(Theme.SURFACE_HOVER);
                }
                @Override public void mouseExited(MouseEvent e) {
                    if (cid != activeConversation) row.setBackground(Theme.SURFACE);
                }
            };
            row.addMouseListener(click);
            for (Component child : new Component[]{row.getComponent(0), copy, n, p}) child.addMouseListener(click);

            conversationItems.add(row);
            conversationItems.add(Box.createVerticalStrut(4));
        }

        if (visible == 0) {
            JPanel emptyBox = vertical();
            emptyBox.setOpaque(false);
            emptyBox.setBorder(new EmptyBorder(30, 10, 20, 10));
            JLabel emptyIcon = new JLabel(new AppIcon(AppIcon.Type.CHAT, Theme.MUTED, 36));
            emptyIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel emptyText = new JLabel(q.isBlank() ? "Chưa có cuộc trò chuyện" : "Không tìm thấy kết quả");
            emptyText.setFont(Theme.font(Font.PLAIN, 13));
            emptyText.setForeground(Theme.MUTED);
            emptyText.setAlignmentX(Component.CENTER_ALIGNMENT);
            emptyBox.add(emptyIcon);
            emptyBox.add(Box.createVerticalStrut(8));
            emptyBox.add(emptyText);
            conversationItems.add(emptyBox);
        }
        conversationItems.revalidate();
        conversationItems.repaint();
    }

    private static String ellipsizeRail(String value, int max) {
        if (value == null) return "";
        value = value.replaceAll("\\s+", " ").trim();
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static String formatRelativeTime(String time) {
        if (time == null || time.isBlank()) return "";
        if (time.length() > 16) {
            return time.substring(11, 16); // Chỉ lấy HH:mm nếu có chuỗi timestamp đầy đủ
        }
        return time;
    }

    private void logout() {
        if (!confirm("Bạn có chắc chắn muốn đăng xuất khỏi SafeChat?")) return;
        query("AUTH_LOGOUT", of(), r -> {
            clearSession();
            showAuth(false);
        });
    }

    private void clearSession() {
        reconnectToken++;
        authenticated = false;
        resetConversationRail();
        thumbnails.bind(null);
        udpRefresh.stop();
        for (Window window : getOwnedWindows()) window.dispose();
        if (udp != null) { udp.close(); udp = null; }
        if (client != null) { client.close(); client = null; }
        media.close();
        media = new MediaTransfers(() -> client, this::showError);
        messages.clear();
        pendingMessages.clear();
        pendingOwnerId = 0;
        currentUser.clear();
        body.removeAll();
    }

    private void disconnected(Throwable error) {
        authenticated = false;
        connection.setText("● Mất kết nối · Đang thử kết nối lại...");
        connection.setForeground(Theme.WARNING);
        if (udp != null) udp.close();
        udpRefresh.stop();
        media.disconnected();
        if (chatInput != null) chatInput.setEnabled(false);
        if (sendButton != null) sendButton.setEnabled(false);
        if (typingLabel != null) typingLabel.setText(" ");
        countRefresh.stop();
        disableButtons(body);
        long token = ++reconnectToken;
        if (error == null) return;
        workers.execute(() -> {
            for (int attempt = 0; attempt < 6 && reconnectToken == token; attempt++) {
                try {
                    Thread.sleep(Math.min(30000, 1000L << attempt) + ThreadLocalRandom.current().nextInt(250));
                    if (reconnectToken != token) return;
                    SocialClient candidate = new SocialClient(host, port, connectionTlsContext);
                    try {
                        candidate.connect().join();
                        if (reconnectToken != token) { candidate.close(); return; }
                        client = candidate;
                        invoke(() -> {
                            if (reconnectToken == token) {
                                showAuth(false);
                                info("Đã kết nối lại TLS thành công. Vui lòng đăng nhập lại.");
                            }
                        });
                        return;
                    } catch (Throwable e) {
                        candidate.close();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            invoke(() -> {
                if (reconnectToken == token) {
                    connection.setText("● Mất kết nối · Bấm Đăng nhập lại");
                    connection.setForeground(Theme.DANGER);
                }
            });
        });
    }

    private void disableButtons(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof JButton b) b.setEnabled(false);
            if (child instanceof Container nested) disableButtons(nested);
        }
    }

    private void refreshCount() {
        if (!authenticated) return;
        query("NOTIFICATION_COUNT", of(), r -> {
            if (notificationNav != null) {
                long unread = num(r, "unreadCount", "count");
                notificationNav.setText("Thông báo" + (unread > 0 ? " (" + unread + ")" : ""));
                adaptShell();
            }
        });
    }

    private void onEvent(WireMessage event) {
        if (!authenticated) return;
        String type = event.getType();
        Map<String, Object> p = event.getPayload();
        if (Set.of("PROFILE_INVALIDATED", "RELATION_CHANGED", "POST_INVALIDATED", "CONVERSATION_INVALIDATED").contains(type)) {
            thumbnails.clear();
        }
        if ("PROFILE_INVALIDATED".equals(type) && num(p, "userId") == id(currentUser, "user") && authenticated) {
            query("PROFILE_GET", of("targetUserId", id(currentUser, "user")), this::updateSelf);
        }
        if ("SESSION_REVOKED".equals(type) || "SERVER_STOPPING".equals(type)) {
            disconnected(null);
            return;
        }
        if ("NOTIFICATION_CHANGED".equals(type)) refreshCount();
        if ("PRESENCE_CHANGED".equals(type) && "people".equals(view)) {
            updatePresence(body, num(p, "userId"), bool(p, "online"), num(p, "asOf"));
            return;
        }
        if ("PRESENCE_CHANGED".equals(type) || "RELATION_CHANGED".equals(type) || "CONVERSATION_INVALIDATED".equals(type) || "GROUP_INVITE_CHANGED".equals(type)) {
            if ("RELATION_CHANGED".equals(type) || "CONVERSATION_INVALIDATED".equals(type)) invalidateConversationRail();
            else if (conversationRefreshTimer != null) conversationRefreshTimer.restart();
        }
        if ("MESSAGE_CREATED".equals(type)) {
            Map<String, Object> m = map(p.get("message"));
            if (m.isEmpty()) m = p;
            if (activeConversation == num(m, "conversationId")) {
                upsertMessage(m);
                renderMessages(false);
                ackMessages();
            }
            if (conversationRefreshTimer != null) conversationRefreshTimer.restart();
            return;
        }
        if ("RECEIPT_UPDATED".equals(type) && activeConversation == num(p, "conversationId")) {
            List<Map<String, Object>> receipts = rows(p.get("receipts"));
            if (receipts.isEmpty()) receipts = List.of(p);
            for (Map<String, Object> m : messages.values()) {
                if (num(m, "senderId") != id(currentUser, "user")) continue;
                long seq = num(m, "conversationSeq", "seq"), read = 0, delivered = 0;
                for (Map<String, Object> receipt : receipts) {
                    if (num(receipt, "actorId") == id(currentUser, "user")) continue;
                    if (seq <= num(receipt, "readThroughSeq", "readSeq")) read++;
                    if (seq <= num(receipt, "deliveredThroughSeq", "deliveredSeq")) delivered++;
                }
                m.put("readByCount", read);
                m.put("deliveredByCount", delivered);
                if (read > 0) m.put("status", "READ");
                else if (delivered > 0) m.put("status", "DELIVERED");
            }
            renderMessages(false);
            if (conversationRefreshTimer != null) conversationRefreshTimer.restart();
            return;
        }
        if (Set.of("RELATION_CHANGED", "POST_INVALIDATED", "COMMENT_INVALIDATED", "LIKE_INVALIDATED", "PROFILE_INVALIDATED", "CONVERSATION_INVALIDATED", "GROUP_INVITE_CHANGED", "PRESENCE_CHANGED", "NOTIFICATION_CHANGED").contains(type)) {
            if ("chat".equals(view) && ("RELATION_CHANGED".equals(type) || "CONVERSATION_INVALIDATED".equals(type))) {
                long cid = activeConversation;
                messages.clear();
                chatMessages.removeAll();
                chatMessages.add(label("Đang kiểm tra quyền truy cập cuộc trò chuyện…", false));
                chatMessages.revalidate();
                chatMessages.repaint();
                chatInput.setEnabled(false);
                if (sendButton != null) sendButton.setEnabled(false);
                recheckConversationAfterAcl(cid, null, 0, generation, client);
                return;
            }
            eventRefresh.restart();
        }
    }

    private void recheckConversationAfterAcl(long cid, String cursor, int page, long session, SocialClient expected) {
        if (!authenticated || client != expected || generation != session || !"chat".equals(view) || activeConversation != cid) return;
        if (page >= 40) { showConversations(); return; }
        Map<String, Object> request = of("limit", 50);
        if (cursor != null && !cursor.isBlank()) request.put("cursor", cursor);
        query("CONVERSATION_LIST", request, result -> {
            if (!authenticated || client != expected || generation != session || !"chat".equals(view) || activeConversation != cid) return;
            Map<String, Object> found = rows(result.get("items")).stream().filter(c -> id(c, "conversation") == cid).findFirst().orElse(null);
            if (found != null) openChat(found);
            else {
                String next = str(result, "nextCursor", "cursor");
                if (next.isBlank()) showConversations();
                else recheckConversationAfterAcl(cid, next, page + 1, session, expected);
            }
        });
    }

    private void updateSelf(Map<String, Object> profile) {
        currentUser.putAll(profile);
        identity.setText(name(currentUser));
        if (ownAvatarHolder != null) {
            ownAvatarHolder.removeAll();
            ownAvatarHolder.add(avatar(currentUser, 44));
            ownAvatarHolder.revalidate();
            ownAvatarHolder.repaint();
        }
    }

    // ==========================================
    // 4. KHUNG CHAT (CHAT VIEW & COMPOSER)
    // ==========================================
    private void showConversations() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.CHAT_BACKGROUND);

        JPanel welcome = vertical();
        welcome.setBorder(new EmptyBorder(60, 40, 60, 40));
        welcome.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel chatBigIcon = new JLabel(new AppIcon(AppIcon.Type.CHAT, Theme.PRIMARY_LIGHT, 56));
        chatBigIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel welcomeTitle = new JLabel("Chào mừng đến với SafeChat");
        welcomeTitle.setFont(Theme.font(Font.BOLD, 22));
        welcomeTitle.setForeground(Theme.TEXT);
        welcomeTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel welcomeDesc = new JLabel("Chọn một cuộc trò chuyện từ danh sách bên trái hoặc tạo nhóm mới để bắt đầu nhắn tin bảo mật.");
        welcomeDesc.setFont(Theme.font(Font.PLAIN, 13));
        welcomeDesc.setForeground(Theme.MUTED);
        welcomeDesc.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton newGroupBtn = Theme.primaryButton("＋ Tạo nhóm trò chuyện mới");
        newGroupBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        newGroupBtn.addActionListener(e -> createGroup());

        welcome.add(chatBigIcon);
        welcome.add(Box.createVerticalStrut(16));
        welcome.add(welcomeTitle);
        welcome.add(Box.createVerticalStrut(8));
        welcome.add(welcomeDesc);
        welcome.add(Box.createVerticalStrut(24));
        welcome.add(newGroupBtn);

        Page invites = new Page("Lời mời vào nhóm", "GROUP_INVITE_LIST", of(), invite -> {
            JPanel c = card();
            c.add(label(str(invite, "title", "groupTitle") + " · #" + num(invite, "conversationId") + " · " + str(invite, "status"), false));
            JPanel actions = horizontal();
            long inviteId = id(invite, "invite");
            if (num(invite, "inviteeId") != id(currentUser, "user")) {
                actions.add(button("Hủy", () -> mutate("GROUP_INVITE_CANCEL", of("inviteId", inviteId), null, r -> showConversations())));
            } else {
                actions.add(button("Tham gia", () -> mutate("GROUP_INVITE_ACCEPT", of("inviteId", inviteId), null, r -> {
                    info("Bạn đã tham gia nhóm!");
                    showConversations();
                })));
                actions.add(button("Từ chối", () -> mutate("GROUP_INVITE_REJECT", of("inviteId", inviteId), null, r -> showConversations())));
            }
            if (!"PENDING".equals(str(invite, "status"))) disableButtons(actions);
            c.add(actions, BorderLayout.SOUTH);
            return c;
        });

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Trò chuyện", welcome);
        tabs.addTab("Lời mời vào nhóm", invites.panel);
        panel.add(tabs, BorderLayout.CENTER);

        setView("conversations", panel, this::showConversations);
        invites.load(true);
        refreshConversationRail();
    }

    private void createGroup() {
        String title = JOptionPane.showInputDialog(this, "Nhập tên nhóm trò chuyện mới:", "Tạo nhóm SafeChat", JOptionPane.QUESTION_MESSAGE);
        if (title == null || title.isBlank()) return;
        mutate("GROUP_CREATE", of("title", title.trim()), null, r -> {
            Map<String, Object> c = map(r.get("conversation"));
            if (c.isEmpty()) c = new LinkedHashMap<>(r);
            c.putIfAbsent("kind", "GROUP");
            c.putIfAbsent("ownerId", id(currentUser, "user"));
            openChat(c);
        });
    }

    private void openChat(Map<String, Object> conv) {
        long cid = id(conv, "conversation");
        if (cid == 0) {
            showError(new IllegalArgumentException("Máy chủ chưa trả về conversationId"));
            return;
        }

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.CHAT_BACKGROUND);

        // 4.1. Header cuộc trò chuyện
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(Theme.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(12, 20, 12, 20)
        ));

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerLeft.setOpaque(false);
        headerLeft.add(avatar(conv, 40));

        JPanel headerInfo = new JPanel();
        headerInfo.setOpaque(false);
        headerInfo.setLayout(new BoxLayout(headerInfo, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(name(conv));
        titleLabel.setFont(Theme.font(Font.BOLD, 15));
        titleLabel.setForeground(Theme.TEXT);

        boolean isGroup = "GROUP".equalsIgnoreCase(str(conv, "type", "kind"));
        JLabel subtitleLabel = new JLabel(isGroup ? "Nhóm trò chuyện" : (bool(conv, "online") ? "● Đang hoạt động" : "○ Không hoạt động"));
        subtitleLabel.setFont(Theme.font(Font.PLAIN, 11));
        subtitleLabel.setForeground(isGroup ? Theme.PRIMARY_LIGHT : (bool(conv, "online") ? Theme.ONLINE : Theme.MUTED));

        headerInfo.add(titleLabel);
        headerInfo.add(Box.createVerticalStrut(2));
        headerInfo.add(subtitleLabel);
        headerLeft.add(headerInfo);
        header.add(headerLeft, BorderLayout.WEST);

        // 4.2. Info Drawer (Side panel bên phải)
        JPanel infoPanel = new JPanel(new BorderLayout(8, 8));
        infoPanel.setBorder(new EmptyBorder(20, 16, 18, 16));
        infoPanel.setPreferredSize(new Dimension(270, 0));
        infoPanel.setBackground(Theme.SURFACE);
        infoPanel.setVisible(false);

        JPanel infoContent = vertical();
        infoContent.setOpaque(false);
        JLabel infoTitle = label("Thông tin cuộc trò chuyện", true);
        infoTitle.setFont(Theme.font(Font.BOLD, 16));
        infoContent.add(infoTitle);
        infoContent.add(Box.createVerticalStrut(14));

        JPanel avatarCenter = new JPanel(new FlowLayout(FlowLayout.CENTER));
        avatarCenter.setOpaque(false);
        avatarCenter.add(avatar(conv, 64));
        infoContent.add(avatarCenter);
        infoContent.add(Box.createVerticalStrut(8));

        JLabel infoName = new JLabel(name(conv), SwingConstants.CENTER);
        infoName.setFont(Theme.font(Font.BOLD, 15));
        infoName.setForeground(Theme.TEXT);
        infoName.setAlignmentX(Component.CENTER_ALIGNMENT);
        infoContent.add(infoName);
        infoContent.add(Box.createVerticalStrut(14));

        infoContent.add(label("Loại: " + (isGroup ? "Nhóm chat" : "Trò chuyện trực tiếp"), false));
        infoContent.add(Box.createVerticalStrut(4));
        infoContent.add(label("Mã hội thoại: #" + cid, false));
        infoContent.add(Box.createVerticalStrut(12));

        if (isGroup) {
            JButton groupMgmt = Theme.ghostButton("👥 Quản lý thành viên nhóm");
            groupMgmt.setAlignmentX(Component.LEFT_ALIGNMENT);
            groupMgmt.addActionListener(e -> groupInfo(conv));
            infoContent.add(groupMgmt);
        }

        JButton closeInfo = Theme.ghostButton("✕ Đóng");
        closeInfo.addActionListener(e -> {
            infoPanel.setVisible(false);
            panel.revalidate();
        });
        infoPanel.add(infoContent, BorderLayout.CENTER);
        infoPanel.add(closeInfo, BorderLayout.SOUTH);

        // Header Actions
        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        headerActions.setOpaque(false);
        JButton historyBtn = Theme.ghostButton("Tải lịch sử cũ");
        historyBtn.setToolTipText("Tải thêm tin nhắn cũ");
        historyBtn.addActionListener(e -> loadHistory(true));

        JButton infoToggle = Theme.iconButton(new AppIcon(AppIcon.Type.INFO, Theme.TEXT, 18), "Thông tin cuộc trò chuyện", false);
        infoToggle.addActionListener(e -> {
            infoPanel.setVisible(!infoPanel.isVisible());
            panel.revalidate();
            panel.repaint();
        });

        headerActions.add(historyBtn);
        if (isGroup) {
            JButton grpBtn = Theme.ghostButton("Thành viên");
            grpBtn.addActionListener(e -> groupInfo(conv));
            headerActions.add(grpBtn);
        }
        headerActions.add(infoToggle);
        header.add(headerActions, BorderLayout.EAST);

        // Typing Label Indicator
        typingLabel = new JLabel(" ");
        typingLabel.setFont(Theme.font(Font.ITALIC, 11));
        typingLabel.setForeground(Theme.PRIMARY_LIGHT);
        typingLabel.setBorder(new EmptyBorder(2, 24, 4, 24));

        JPanel headerWrap = new JPanel(new BorderLayout());
        headerWrap.setOpaque(false);
        headerWrap.add(header, BorderLayout.CENTER);
        headerWrap.add(typingLabel, BorderLayout.SOUTH);

        // 4.3. Message List
        chatMessages = vertical();
        chatMessages.setOpaque(false);
        chatMessages.setBorder(new EmptyBorder(16, 20, 16, 20));
        chatScroll = scroll(chatMessages);
        chatScroll.setBackground(Theme.CHAT_BACKGROUND);
        chatScroll.getViewport().setBackground(Theme.CHAT_BACKGROUND);

        // 4.4. Composer
        chatInput = new JTextArea("", 2, 35);
        chatInput.setLineWrap(true);
        chatInput.setWrapStyleWord(true);
        chatInput.setFont(Theme.font(Font.PLAIN, 13));
        chatInput.setBackground(Theme.COMPOSER_BACKGROUND);
        chatInput.setForeground(Theme.TEXT);
        chatInput.setCaretColor(Theme.TEXT);
        chatInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
        chatInput.putClientProperty("JTextField.placeholderText", "Nhập tin nhắn... (Enter gửi, Shift+Enter xuống dòng)");

        sendButton = Theme.primaryButton("Gửi");
        sendButton.setIcon(new AppIcon(AppIcon.Type.SEND, Color.WHITE, 16));
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage(cid, null, chatInput.getText()));

        // Tự động vô hiệu hóa nút gửi khi text rỗng
        chatInput.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { checkText(); typing(); }
            public void removeUpdate(DocumentEvent e) { checkText(); typing(); }
            public void changedUpdate(DocumentEvent e) { checkText(); typing(); }
            private void checkText() {
                sendButton.setEnabled(!chatInput.getText().trim().isEmpty());
            }
            private void typing() {
                if (udp != null && activeConversation == cid) {
                    udp.typing(cid, !chatInput.getText().isBlank());
                }
            }
        });

        // Enter gửi, Shift+Enter xuống dòng
        chatInput.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send-message");
        chatInput.getActionMap().put("send-message", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (sendButton.isEnabled()) sendButton.doClick();
            }
        });
        chatInput.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-break");

        // Nút Emoji Popup
        JButton emojiBtn = Theme.iconButton(new AppIcon(AppIcon.Type.EMOJI, Theme.TEXT, 20), "Chèn biểu cảm Emoji", false);
        emojiBtn.addActionListener(e -> showEmojiMenu(emojiBtn, chatInput));

        // Nút Đính kèm ảnh / tệp
        JButton attachBtn = Theme.iconButton(new AppIcon(AppIcon.Type.ATTACHMENT, Theme.TEXT, 20), "Gửi ảnh hoặc tệp tin", false);
        attachBtn.addActionListener(e -> {
            JFileChooser c = new JFileChooser();
            if (c.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            String caption = chatInput.getText();
            String filename = c.getSelectedFile().getName();
            media.upload(this, c.getSelectedFile().toPath(), "CHAT_FILE", mid ->
                    sendMessage(cid, mid, caption.isBlank() ? filename : caption));
        });

        JPanel composerTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        composerTools.setOpaque(false);
        composerTools.add(emojiBtn);
        composerTools.add(attachBtn);

        JPanel composerBottom = new JPanel(new BorderLayout());
        composerBottom.setOpaque(false);
        composerBottom.add(composerTools, BorderLayout.WEST);
        composerBottom.add(sendButton, BorderLayout.EAST);

        JPanel composer = new JPanel(new BorderLayout(0, 8));
        composer.setBackground(Theme.SURFACE);
        composer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(12, 20, 14, 20)
        ));
        composer.add(chatInput, BorderLayout.CENTER);
        composer.add(composerBottom, BorderLayout.SOUTH);

        if (Boolean.FALSE.equals(conv.get("canSend")) || Boolean.FALSE.equals(conv.get("active"))) {
            chatInput.setEnabled(false);
            sendButton.setEnabled(false);
            attachBtn.setEnabled(false);
            emojiBtn.setEnabled(false);
            typingLabel.setText("Chỉ đọc lịch sử · Bạn không còn quyền gửi tin nhắn vào cuộc trò chuyện này");
        }

        JPanel chatColumn = new JPanel(new BorderLayout());
        chatColumn.setOpaque(false);
        chatColumn.add(headerWrap, BorderLayout.NORTH);
        chatColumn.add(chatScroll, BorderLayout.CENTER);
        chatColumn.add(composer, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatColumn, infoPanel);
        split.setResizeWeight(1.0);
        split.setDividerSize(4);
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setOpaque(false);
        panel.add(split, BorderLayout.CENTER);

        setView("chat", panel, () -> syncChat(cid));
        activeConversation = cid;
        activeConversationData = new LinkedHashMap<>(conv);
        earliestSeq = 0;
        latestSeq = 0;
        renderConversationRail();
        loadHistory(false);
    }

    private void showEmojiMenu(Component invoker, JTextArea target) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.SURFACE);
        menu.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        String[] emojis = {"😊", "😂", "❤️", "👍", "🔥", "🎉", "👋", "✨", "🚀", "👏", "💡", "🤝", "💯", "🎯", "🌟", "💬"};
        JPanel grid = new JPanel(new GridLayout(2, 8, 4, 4));
        grid.setBackground(Theme.SURFACE);
        grid.setBorder(new EmptyBorder(6, 6, 6, 6));
        for (String emo : emojis) {
            JButton b = new JButton(emo);
            b.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
            b.setFocusPainted(false);
            b.setBackground(Theme.COMPOSER_BACKGROUND);
            b.setBorder(new EmptyBorder(4, 6, 4, 6));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(ev -> {
                target.insert(emo, target.getCaretPosition());
                target.requestFocusInWindow();
                menu.setVisible(false);
            });
            grid.add(b);
        }
        menu.add(grid);
        menu.show(invoker, 0, -grid.getPreferredSize().height - 5);
    }

    private void loadHistory(boolean older) {
        long cid = activeConversation;
        if (cid == 0) return;
        Map<String, Object> p = of("conversationId", cid, "limit", 50);
        if (older && earliestSeq > 0) p.put("beforeSeq", earliestSeq);
        query("MESSAGE_HISTORY", p, r -> {
            if (activeConversation != cid) return;
            for (Map<String, Object> m : rows(r.get("items"))) upsertMessage(m);
            latestSeq = Math.max(latestSeq, num(r, "latestSeq"));
            renderMessages(!older);
            ackMessages();
        });
    }

    private void syncChat(long cid) {
        if (cid != activeConversation) return;
        query("MESSAGE_HISTORY", of("conversationId", cid, "afterSeq", latestSeq, "limit", 100), r -> {
            if (cid != activeConversation) return;
            for (Map<String, Object> m : rows(r.get("items"))) upsertMessage(m);
            renderMessages(false);
            ackMessages();
        });
    }

    private void upsertMessage(Map<String, Object> m) {
        long seq = num(m, "conversationSeq", "seq"), mid = id(m, "message");
        if (mid == 0) mid = seq;
        if (mid == 0) return;
        Map<String, Object> merged = new LinkedHashMap<>(messages.getOrDefault(mid, Map.of()));
        String previousStatus = str(merged, "status");
        merged.putAll(m);
        if (statusRank(previousStatus) > statusRank(str(merged, "status"))) merged.put("status", previousStatus);
        messages.put(mid, merged);
        if (earliestSeq == 0 || seq < earliestSeq) earliestSeq = seq;
        latestSeq = Math.max(latestSeq, seq);
        String clientId = str(m, "clientMessageId");
        pendingMessages.remove(clientId);
        while (messages.size() > 200) messages.remove(messages.keySet().iterator().next());
    }

    private int statusRank(String s) {
        return switch (s) {
            case "READ" -> 3;
            case "DELIVERED" -> 2;
            case "STORED" -> 1;
            default -> 0;
        };
    }

    private void ackMessages() {
        if (!authenticated || latestSeq <= 0 || activeConversation <= 0) return;
        long cid = activeConversation;
        long seq = messages.values().stream().mapToLong(m -> num(m, "conversationSeq", "seq")).max().orElse(0);
        if (seq == 0) return;
        mutate("MESSAGE_DELIVERED", of("conversationId", cid, "throughSeq", seq), null, r -> {});
        if (isActive()) mutate("MESSAGE_READ", of("conversationId", cid, "throughSeq", seq), null, r -> {});
    }

    private void renderMessages(boolean bottom) {
        if (chatMessages == null) return;
        JScrollBar bar = chatScroll.getVerticalScrollBar();
        int old = bar.getValue(), height = bar.getMaximum();
        boolean nearBottom = old + bar.getVisibleAmount() >= height - 40;

        chatMessages.removeAll();
        List<Map<String, Object>> sorted = new ArrayList<>(messages.values());
        sorted.sort(Comparator.comparingLong(m -> num(m, "conversationSeq", "seq")));

        for (Map<String, Object> m : sorted) {
            boolean mine = num(m, "senderId") == id(currentUser, "user");
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(3, 4, 3, 4));

            String sender = str(m, "senderName", "displayName");
            if (sender.isBlank()) sender = mine ? "Bạn" : "Người gửi #" + num(m, "senderId");
            String statusStr = str(m, "status");
            String timeStr = time(m);

            // Xử lý tệp đính kèm và kiểm duyệt AI
            Map<String, Object> attachment = map(m.get("media"));
            if (attachment.isEmpty()) attachment = map(m.get("attachment"));
            String mid = str(m, "mediaId");
            if (mid.isBlank()) mid = str(attachment, "mediaId", "id");
            String mediaId = mid;
            String fileName = str(attachment, "fileName", "originalName");
            if (fileName.isBlank()) fileName = str(m, "fileName");
            String suggestedName = fileName;
            Map<String, Object> review = map(attachment.get("moderation"));
            boolean isImage = str(attachment, "mimeType").startsWith("image/");
            String modStatus = str(review, "status");

            String rawContent = str(m, "content");
            boolean hasText = !rawContent.isBlank() && !rawContent.equals(suggestedName);

            // Container nội dung tin nhắn
            JPanel bubbleBox = new JPanel();
            bubbleBox.setOpaque(false);
            bubbleBox.setLayout(new BoxLayout(bubbleBox, BoxLayout.Y_AXIS));

            if (!mine) {
                JLabel nameLabel = new JLabel(sender);
                nameLabel.setFont(Theme.font(Font.BOLD, 12));
                nameLabel.setForeground(Theme.PRIMARY_LIGHT);
                nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                bubbleBox.add(nameLabel);
                bubbleBox.add(Box.createVerticalStrut(4));
            }

            // 1. Nếu là Hình ảnh: Hiển thị trực tiếp (CLEAR) hoặc Làm mờ (WARNING)
            if (isImage && !mediaId.isBlank()) {
                if ("BLOCKED".equals(modStatus)) {
                    // Thẻ BỊ CHẶN do vi phạm chính sách
                    JPanel blockedCard = Theme.roundedPanel(new Color(239, 68, 68, 35), 10);
                    blockedCard.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Theme.DANGER, 1),
                            new EmptyBorder(8, 10, 8, 10)
                    ));
                    blockedCard.setLayout(new BorderLayout(6, 4));
                    JLabel blockedLabel = new JLabel("⊘ Ảnh bị chặn do vi phạm chính sách an toàn");
                    blockedLabel.setFont(Theme.font(Font.BOLD, 12));
                    blockedLabel.setForeground(Theme.DANGER);
                    blockedCard.add(blockedLabel, BorderLayout.CENTER);
                    blockedCard.setAlignmentX(Component.LEFT_ALIGNMENT);
                    bubbleBox.add(blockedCard);
                } else {
                    // ẢNH BÌNH THƯỜNG (Hiện trực tiếp trong chat) HOẶC ẢNH BẠO LỰC (Làm mờ)
                    boolean isWarning = "WARNING".equals(modStatus);
                    Theme.PhotoBubble photoBubble = new Theme.PhotoBubble(isWarning, 16);
                    photoBubble.setClickAction(() -> media.preview(this, mediaId));
                    photoBubble.setAlignmentX(Component.LEFT_ALIGNMENT);

                    Object directIcon = attachment.get("thumbnailIcon");
                    if (directIcon instanceof ImageIcon icon) {
                        photoBubble.setImage(icon.getImage());
                    } else {
                        thumbnails.load(mediaId, 320, icon -> {
                            if (icon != null) {
                                photoBubble.setImage(icon.getImage());
                                chatMessages.revalidate();
                                chatMessages.repaint();
                            }
                        });
                    }
                    bubbleBox.add(photoBubble);
                }
            }

            // 2. Nếu có văn bản (caption hoặc tin nhắn văn bản thông thường)
            if (hasText) {
                if (isImage) bubbleBox.add(Box.createVerticalStrut(6));
                JTextArea bodyText = readable(rawContent);
                bodyText.setForeground(Theme.TEXT);
                bodyText.setAlignmentX(Component.LEFT_ALIGNMENT);
                bubbleBox.add(bodyText);
            }

            // 3. Nếu là Tệp đính kèm thông thường (PDF, zip, docx...)
            if (!mid.isBlank() && !isImage) {
                JPanel attachCard = Theme.roundedPanel(Theme.FILE_CARD, 10);
                attachCard.setBorder(new EmptyBorder(8, 10, 8, 10));
                attachCard.setLayout(new BorderLayout(8, 0));
                JLabel fileIcon = new JLabel(new AppIcon(AppIcon.Type.FILE, Theme.PRIMARY_LIGHT, 24));
                attachCard.add(fileIcon, BorderLayout.WEST);

                JButton dlBtn = Theme.primaryButton("Tải tệp · " + (suggestedName.isBlank() ? "#" + mediaId : suggestedName));
                dlBtn.addActionListener(e -> media.download(this, mediaId, suggestedName));
                attachCard.add(dlBtn, BorderLayout.CENTER);
                attachCard.setAlignmentX(Component.LEFT_ALIGNMENT);
                bubbleBox.add(attachCard);
            }

            // 4. Thời gian gửi & Trạng thái đã nhận / đã đọc
            JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            metaRow.setOpaque(false);
            metaRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel timeLabel = new JLabel(formatRelativeTime(timeStr));
            timeLabel.setFont(Theme.font(Font.PLAIN, 10));
            timeLabel.setForeground(mine ? new Color(210, 220, 255) : Theme.MUTED);
            metaRow.add(timeLabel);

            if (mine) {
                JLabel statusIcon = new JLabel(switch (statusStr) {
                    case "READ" -> new AppIcon(AppIcon.Type.DOUBLE_CHECK, new Color(52, 211, 153), 13);
                    case "DELIVERED" -> new AppIcon(AppIcon.Type.DOUBLE_CHECK, new Color(200, 210, 230), 13);
                    default -> new AppIcon(AppIcon.Type.CHECK, new Color(180, 190, 220), 13);
                });
                statusIcon.setToolTipText(messageStatus(statusStr));
                metaRow.add(statusIcon);
            }
            bubbleBox.add(Box.createVerticalStrut(2));
            bubbleBox.add(metaRow);

            // Modern Bubble Panel
            boolean isPurePhoto = isImage && !hasText && !"BLOCKED".equals(modStatus);
            Theme.BubblePanel bubble = new Theme.BubblePanel(
                    isPurePhoto ? new Color(0, 0, 0, 0) : (mine ? Theme.BUBBLE_SELF : Theme.BUBBLE_PEER),
                    mine, 16
            );
            bubble.setLayout(new BorderLayout());
            bubble.setBorder(new EmptyBorder(isPurePhoto ? 0 : 8, isPurePhoto ? 0 : 12, isPurePhoto ? 0 : 8, isPurePhoto ? 0 : 12));
            bubble.add(bubbleBox, BorderLayout.CENTER);

            if (!mine) {
                JPanel peerRow = new JPanel(new BorderLayout(8, 0));
                peerRow.setOpaque(false);
                JPanel avBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                avBox.setOpaque(false);
                avBox.add(new Theme.Avatar(sender, 28, false, false));
                peerRow.add(avBox, BorderLayout.WEST);
                peerRow.add(bubble, BorderLayout.CENTER);
                row.add(peerRow, BorderLayout.WEST);
            } else {
                row.add(bubble, BorderLayout.EAST);
            }

            chatMessages.add(row);
            chatMessages.add(Box.createVerticalStrut(6));
        }

        // Pending messages
        for (PendingMessage pending : pendingMessages.values()) {
            if (pending.conversationId == activeConversation) {
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false);
                row.setBorder(new EmptyBorder(3, 4, 3, 4));

                Theme.BubblePanel bubble = new Theme.BubblePanel(new Color(60, 64, 80), true, 14);
                bubble.setLayout(new BorderLayout(6, 6));
                bubble.setBorder(new EmptyBorder(8, 14, 8, 14));

                JLabel pendingMeta = new JLabel("Bạn · " + pending.status, new AppIcon(AppIcon.Type.CLOCK, Theme.MUTED, 12), SwingConstants.LEFT);
                pendingMeta.setFont(Theme.font(Font.PLAIN, 11));
                pendingMeta.setForeground(Theme.MUTED);
                bubble.add(pendingMeta, BorderLayout.NORTH);

                JTextArea bodyText = readable(pending.content);
                bodyText.setForeground(Theme.TEXT);
                bubble.add(bodyText, BorderLayout.CENTER);

                if (!"Đang gửi…".equals(pending.status)) {
                    JButton retry = Theme.ghostButton("Thử lại tin nhắn này");
                    retry.addActionListener(e -> retryMessage(pending));
                    bubble.add(retry, BorderLayout.SOUTH);
                }
                row.add(bubble, BorderLayout.EAST);
                chatMessages.add(row);
            }
        }

        if (chatMessages.getComponentCount() == 0) {
            JPanel emptyChat = vertical();
            emptyChat.setOpaque(false);
            emptyChat.setBorder(new EmptyBorder(40, 20, 20, 20));
            JLabel emptyIcon = new JLabel(new AppIcon(AppIcon.Type.CHAT, Theme.MUTED, 40));
            emptyIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel emptyLabel = new JLabel("Chưa có tin nhắn trong cuộc trò chuyện này");
            emptyLabel.setFont(Theme.font(Font.PLAIN, 14));
            emptyLabel.setForeground(Theme.MUTED);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel emptySub = new JLabel("Hãy gửi lời chào đầu tiên! 👋");
            emptySub.setFont(Theme.font(Font.PLAIN, 12));
            emptySub.setForeground(Theme.PRIMARY_LIGHT);
            emptySub.setAlignmentX(Component.CENTER_ALIGNMENT);

            emptyChat.add(emptyIcon);
            emptyChat.add(Box.createVerticalStrut(8));
            emptyChat.add(emptyLabel);
            emptyChat.add(Box.createVerticalStrut(4));
            emptyChat.add(emptySub);
            chatMessages.add(emptyChat);
        }

        chatMessages.revalidate();
        chatMessages.repaint();
        invoke(() -> {
            if (bottom || nearBottom) bar.setValue(bar.getMaximum());
            else bar.setValue(old + Math.max(0, bar.getMaximum() - height));
        });
    }

    private String messageStatus(String status) {
        return switch (status) {
            case "READ" -> "Đã đọc";
            case "DELIVERED" -> "Đã nhận";
            case "STORED" -> "Đã lưu trên máy chủ";
            default -> status.isBlank() ? "Đã lưu trên máy chủ" : status;
        };
    }

    private void sendMessage(long cid, String mediaId, String content) {
        if (content.length() > 4000) {
            info("Tin nhắn tối đa 4.000 ký tự.");
            return;
        }
        if (content.isBlank() && mediaId == null) return;
        if (!authenticated) {
            info("Phiên đã kết thúc. Vui lòng đăng nhập lại.");
            return;
        }
        if (pendingMessages.size() >= 50) {
            info("Có 50 tin chưa xác định kết quả. Vui lòng thử lại các tin đó trước.");
            return;
        }
        String stableId = UUID.randomUUID().toString();
        PendingMessage p = new PendingMessage(cid, stableId, UUID.randomUUID().toString(), content, mediaId);
        pendingMessages.put(stableId, p);
        if (activeConversation == cid) {
            chatInput.setText("");
            renderMessages(true);
        }
        submitMessage(p);
    }

    private void submitMessage(PendingMessage p) {
        p.status = "Đang gửi…";
        SocialClient requestedClient = client;
        CompletableFuture.supplyAsync(() -> blocking(requestedClient, "MESSAGE_SEND",
                of("conversationId", p.conversationId, "clientMessageId", p.clientId, "content", p.content, "mediaId", p.mediaId), p.key), workers).whenComplete((ack, error) -> invoke(() -> {
            if (client != requestedClient) return;
            if (error != null) {
                p.status = (unwrap(error) instanceof RemoteException ? "Không gửi được · " : "Chưa xác định · ") + friendly(error);
                if (activeConversation == p.conversationId) renderMessages(false);
                return;
            }
            Map<String, Object> msg = map(ack.get("message"));
            if (msg.isEmpty()) msg = new LinkedHashMap<>(ack);
            msg.putIfAbsent("content", p.content);
            msg.putIfAbsent("senderId", id(currentUser, "user"));
            msg.putIfAbsent("conversationId", p.conversationId);
            msg.putIfAbsent("clientMessageId", p.clientId);
            if (p.mediaId != null) msg.putIfAbsent("mediaId", p.mediaId);
            msg.putIfAbsent("status", "STORED");
            pendingMessages.remove(p.clientId);
            if (activeConversation == p.conversationId) {
                upsertMessage(msg);
                renderMessages(false);
            }
        }));
    }

    private void retryMessage(PendingMessage p) {
        SocialClient requestedClient = client;
        CompletableFuture.supplyAsync(() -> blocking(requestedClient, "MESSAGE_LOOKUP", of("clientMessageId", p.clientId), null), workers).whenComplete((found, error) -> invoke(() -> {
            if (client != requestedClient) return;
            if (error == null) {
                Map<String, Object> m = map(found.get("message"));
                if (m.isEmpty()) m = found;
                pendingMessages.remove(p.clientId);
                if (activeConversation == p.conversationId) {
                    upsertMessage(m);
                    renderMessages(false);
                }
            } else if (unwrap(error) instanceof RemoteException r && "NOT_FOUND".equals(r.getCode())) {
                submitMessage(p);
            } else {
                p.status = "Tra cứu thất bại · " + friendly(error);
                renderMessages(false);
            }
        }));
    }

    private static final class PendingMessage {
        final long conversationId;
        final String clientId, key, content;
        final String mediaId;
        String status = "Đang gửi…";
        PendingMessage(long c, String id, String key, String content, String mediaId) {
            conversationId = c;
            clientId = id;
            this.key = key;
            this.content = content;
            this.mediaId = mediaId;
        }
    }

    // ==========================================
    // 5. CÁC PANEL PHỤ: BẠN BÈ, NHÓM, CÀI ĐẶT
    // ==========================================
    private void groupInfo(Map<String, Object> original) {
        long cid = id(original, "conversation");
        if (!"GROUP".equals(str(original, "type", "kind"))) {
            info("Đây là cuộc trò chuyện cá nhân.");
            return;
        }
        boolean owner = num(original, "ownerId") == id(currentUser, "user") || "OWNER".equals(str(original, "role"));
        JDialog d = dialog("Thông tin nhóm · " + name(original), 680, 560);
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(Theme.SURFACE);

        JPanel header = vertical();
        header.setOpaque(false);
        header.add(label(name(original), true));
        header.add(readable("Thành viên nhóm có thể xem lịch sử từ thời điểm tham gia."));

        JTextField titleField = new JTextField(name(original));
        if (owner) {
            JButton renameBtn = Theme.primaryButton("Đổi tên nhóm");
            renameBtn.addActionListener(e -> mutate("GROUP_RENAME", of("conversationId", cid, "title", titleField.getText().trim(), "expectedVersion", num(original, "version")), null, r -> {
                d.dispose();
                showConversations();
            }));
            header.add(horizontal(titleField, renameBtn));
        }

        JPanel groupActions = horizontal();
        if (owner) {
            groupActions.add(button("Mời bạn bè", () -> selectFriend(uid -> mutate("GROUP_INVITE", of("conversationId", cid, "targetUserId", uid), null, r -> info("Đã gửi lời mời vào nhóm!")))));
        }
        groupActions.add(button("Rời nhóm", () -> {
            if (confirm(owner ? "Chủ nhóm cần chuyển quyền trước khi rời (trừ khi là thành viên duy nhất). Bạn muốn tiếp tục?" : "Rời khỏi nhóm này? Bạn sẽ không thể xem lịch sử sau này.")) {
                mutate("GROUP_LEAVE", of("conversationId", cid, "expectedVersion", num(original, "version")), null, r -> {
                    d.dispose();
                    showConversations();
                });
            }
        }));
        if (owner) {
            groupActions.add(button("Giải tán nhóm", () -> {
                if (confirm("Giải tán nhóm này cho tất cả thành viên?")) {
                    mutate("GROUP_DISBAND", of("conversationId", cid, "expectedVersion", num(original, "version")), null, r -> {
                        d.dispose();
                        showConversations();
                    });
                }
            }));
        }
        header.add(groupActions);
        p.add(header, BorderLayout.NORTH);

        JPanel members = vertical();
        p.add(scroll(members), BorderLayout.CENTER);
        JLabel status = label("Đang tải danh sách thành viên…", false);
        String[] cursor = {null};
        JButton more = Theme.ghostButton("Tải thêm");

        Runnable load = () -> {
            Map<String, Object> request = of("conversationId", cid, "limit", 20, "cursor", cursor[0]);
            query("GROUP_MEMBERS_GET", request, r -> {
                if (members.getComponentCount() >= 60) members.removeAll();
                for (Map<String, Object> member : rows(r.get("items"))) {
                    Map<String, Object> user = map(member.get("user"));
                    if (user.isEmpty()) user = member;
                    long uid = id(user, "user");
                    JPanel row = card();
                    row.add(label(name(user) + " · " + str(member, "role"), false), BorderLayout.WEST);
                    JPanel actions = horizontal();
                    if (owner && uid != id(currentUser, "user")) {
                        actions.add(button("Chuyển quyền chủ", () -> {
                            if (confirm("Chuyển quyền chủ nhóm cho người này?")) {
                                mutate("GROUP_OWNER_TRANSFER", of("conversationId", cid, "userId", uid, "expectedVersion", num(original, "version")), null, x -> {
                                    d.dispose();
                                    showConversations();
                                });
                            }
                        }));
                        actions.add(button("Mời ra khỏi nhóm", () -> {
                            if (confirm("Mời thành viên này ra khỏi nhóm?")) {
                                mutate("GROUP_KICK", of("conversationId", cid, "userId", uid, "expectedVersion", num(original, "version")), null, x -> {
                                    d.dispose();
                                    showConversations();
                                });
                            }
                        }));
                    }
                    row.add(actions, BorderLayout.EAST);
                    members.add(row);
                    members.add(Box.createVerticalStrut(6));
                }
                cursor[0] = str(r, "nextCursor");
                more.setEnabled(bool(r, "hasMore") || !cursor[0].isBlank());
                status.setText("Chỉ chủ nhóm có quyền đổi tên và quản lý thành viên.");
                members.revalidate();
                members.repaint();
            });
        };
        more.addActionListener(e -> load.run());
        p.add(horizontal(status, more), BorderLayout.SOUTH);
        d.add(p);
        d.setVisible(true);
        load.run();
    }

    private void selectFriend(LongConsumer selection) {
        JDialog d = dialog("Chọn bạn bè", 520, 480);
        Page page = new Page("Mời bạn bè vào nhóm", "RELATION_LIST", of("kind", "FRIENDS"), rel -> {
            Map<String, Object> u = relationUser(rel);
            long uid = id(u, "user");
            JPanel row = card();
            row.add(button(name(u), () -> {
                d.dispose();
                selection.accept(uid);
            }), BorderLayout.CENTER);
            return row;
        });
        d.add(page.panel);
        d.setVisible(true);
        page.load(true);
    }

    private void showPeople() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.CHAT_BACKGROUND);
        JTabbedPane tabs = new JTabbedPane();
        panel.add(titled("Bạn bè & tìm kiếm người dùng", button("Làm mới", this::showPeople)), BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);

        List<Page> pages = new ArrayList<>();
        for (String kind : List.of("FRIENDS", "INCOMING", "OUTGOING")) {
            Page page = new Page(switch (kind) {
                case "FRIENDS" -> "Bạn bè";
                case "INCOMING" -> "Lời mời nhận được";
                default -> "Lời mời đã gửi";
            }, "RELATION_LIST", of("kind", kind), r -> relationCard(r, kind));
            pages.add(page);
            tabs.addTab(switch (kind) {
                case "FRIENDS" -> "Bạn bè";
                case "INCOMING" -> "Đã nhận";
                default -> "Đã gửi";
            }, page.panel);
        }

        JTextField search = new JTextField(30);
        Page results = new Page("Kết quả tìm kiếm", "USER_SEARCH", of("query", ""), this::userCard);
        results.status.setText("Nhập ít nhất 2 ký tự để tìm kiếm người dùng.");
        results.more.setEnabled(false);

        JPanel find = new JPanel(new BorderLayout(8, 8));
        find.setBackground(Theme.CHAT_BACKGROUND);
        find.setBorder(new EmptyBorder(12, 20, 8, 20));
        find.add(horizontal(search, primary("Tìm kiếm", () -> {
            if (search.getText().trim().length() < 2) {
                info("Vui lòng nhập ít nhất 2 ký tự.");
                return;
            }
            results.base.put("query", search.getText().trim());
            results.load(true);
        })), BorderLayout.NORTH);
        find.add(results.panel, BorderLayout.CENTER);
        tabs.addTab("Tìm người", find);

        setView("people", panel, this::showPeople);
        for (Page page : pages) page.load(true);
    }

    private Map<String, Object> relationUser(Map<String, Object> r) {
        Map<String, Object> u = map(r.get("user"));
        if (u.isEmpty()) u = map(r.get("otherUser"));
        if (u.isEmpty()) u = map(r.get("targetUser"));
        if (u.isEmpty()) u = r;
        return u;
    }

    private JComponent relationCard(Map<String, Object> r, String kind) {
        JPanel c = card();
        Map<String, Object> u = relationUser(r);
        long uid = num(u, "userId", "id", "otherUserId", "targetUserId");
        if (uid == 0) {
            long requester = num(r, "requesterId"), addressee = num(r, "addresseeId");
            uid = requester == id(currentUser, "user") ? addressee : requester;
        }
        long target = uid;
        JLabel presence = label("FRIENDS".equals(kind) ? "Đang cập nhật..." : "Lời mời kết bạn", false);
        presence.putClientProperty("presenceUserId", target);
        c.add(horizontal(avatar(u, 42), button(name(u), () -> showProfile(target)), presence), BorderLayout.NORTH);

        long relation = id(r, "relation");
        JPanel actions = horizontal();
        if ("INCOMING".equals(kind)) {
            actions.add(primary("Chấp nhận", () -> mutate("FRIEND_ACCEPT", of("relationId", relation, "expectedVersion", num(r, "version")), null, x -> showPeople())));
            actions.add(button("Từ chối", () -> mutate("FRIEND_REJECT", of("relationId", relation, "expectedVersion", num(r, "version")), null, x -> showPeople())));
        } else if ("OUTGOING".equals(kind)) {
            actions.add(button("Hủy lời mời", () -> mutate("FRIEND_CANCEL", of("relationId", relation, "expectedVersion", num(r, "version")), null, x -> showPeople())));
        } else {
            actions.add(primary("Nhắn tin", () -> openDirect(target)));
            actions.add(button("Hủy kết bạn", () -> {
                if (confirm("Hủy kết bạn với người này?")) {
                    mutate("FRIEND_REMOVE", of("relationId", relation), null, x -> showPeople());
                }
            }));
        }
        actions.add(button("Chặn", () -> block(target, true)));
        c.add(actions, BorderLayout.SOUTH);
        return c;
    }

    private void updatePresence(Container parent, long userId, boolean online, long asOf) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JLabel lbl && Objects.equals(lbl.getClientProperty("presenceUserId"), userId)) {
                lbl.setText(online ? "● Đang online" : "○ Offline");
                lbl.setForeground(online ? Theme.ONLINE : Theme.MUTED);
            } else if (c instanceof Container child) {
                updatePresence(child, userId, online, asOf);
            }
        }
    }

    private JComponent userCard(Map<String, Object> user) {
        JPanel c = card();
        long uid = id(user, "user");
        c.add(horizontal(avatar(user, 40), button(name(user), () -> showProfile(uid)), label("@" + str(user, "username"), false)), BorderLayout.NORTH);
        c.add(readable(str(user, "bio")), BorderLayout.CENTER);
        c.add(horizontal(button("Kết bạn", () -> mutate("FRIEND_REQUEST", of("targetUserId", uid), null, r -> info("Đã gửi lời mời kết bạn!"))),
                primary("Nhắn tin", () -> openDirect(uid))), BorderLayout.SOUTH);
        return c;
    }

    private void block(long uid, boolean blocked) {
        if (blocked && !confirm("Chặn người dùng này? Bạn sẽ không nhận tin nhắn trực tiếp từ họ.")) return;
        mutate("BLOCK_SET", of("targetUserId", uid, "blocked", blocked), null, r -> showPeople());
    }

    private void showProfile(long uid) {
        Page timeline = new Page("Bài viết của người dùng", "PROFILE_POSTS_GET", of("authorId", uid), this::postCard);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.CHAT_BACKGROUND);
        JPanel profile = vertical();
        profile.add(label("Đang tải hồ sơ…", false));
        panel.add(profile, BorderLayout.NORTH);
        panel.add(timeline.panel, BorderLayout.CENTER);
        setView("profile", panel, () -> showProfile(uid));
        long token = generation;
        query("PROFILE_GET", of("targetUserId", uid), r -> {
            if (token != generation) return;
            Map<String, Object> u = map(r.get("profile"));
            if (u.isEmpty()) u = r;
            profile.removeAll();
            JPanel header = card();
            String avatarMediaId = str(u, "avatarMediaId");
            header.add(horizontal(avatar(u, 64), label(name(u), true), label("@" + str(u, "username"), false)), BorderLayout.NORTH);
            header.add(readable(str(u, "bio")), BorderLayout.CENTER);
            JPanel actions = horizontal();
            if (!avatarMediaId.isBlank()) actions.add(button("Xem avatar", () -> media.preview(this, avatarMediaId)));
            if (uid != id(currentUser, "user")) {
                actions.add(button("Kết bạn", () -> mutate("FRIEND_REQUEST", of("targetUserId", uid), null, x -> showPeople())));
                actions.add(primary("Nhắn tin", () -> openDirect(uid)));
                actions.add(button("Chặn", () -> block(uid, true)));
                actions.add(button("Báo cáo", () -> report("USER", uid)));
            } else {
                actions.add(primary("Chỉnh sửa hồ sơ", this::showSettings));
            }
            header.add(actions, BorderLayout.SOUTH);
            profile.add(header);
            profile.revalidate();
            profile.repaint();
        });
        timeline.load(true);
    }

    private void showSettings() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.CHAT_BACKGROUND);
        panel.add(titled("Hồ sơ & cài đặt"), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        panel.add(tabs, BorderLayout.CENTER);

        JPanel settings = vertical();
        settings.setBorder(new EmptyBorder(16, 24, 20, 24));
        JTextField nameField = new JTextField(name(currentUser));
        JTextArea bio = text(str(currentUser, "bio"), 4);
        addField(settings, "Tên hiển thị", nameField);
        addField(settings, "Tiểu sử", scroll(bio));
        JLabel version = label("Đang tải hồ sơ…", false);
        settings.add(version);
        Map<String, Object> profile = new LinkedHashMap<>(currentUser);
        nameField.setEnabled(false);
        bio.setEnabled(false);

        JButton save = Theme.primaryButton("Lưu hồ sơ");
        save.addActionListener(e -> mutate("PROFILE_UPDATE", of("displayName", nameField.getText(), "bio", bio.getText(), "expectedVersion", num(profile, "version")), save, r -> {
            Map<String, Object> u = map(r.get("profile"));
            if (u.isEmpty()) u = r;
            updateSelf(u);
            showSettings();
        }));

        settings.add(horizontal(save, button("Đổi ảnh đại diện", () -> {
            JFileChooser c = new JFileChooser();
            if (c.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                media.upload(this, c.getSelectedFile().toPath(), "AVATAR", mid ->
                        mutate("AVATAR_SET", of("mediaId", mid, "expectedVersion", num(profile, "version")), null, r -> showSettings()));
            }
        }), button("Xem hồ sơ của tôi", () -> showProfile(id(currentUser, "user")))));

        settings.add(Box.createVerticalStrut(24));
        settings.add(label("Đổi mật khẩu", true));
        JPasswordField old = new JPasswordField(), next = new JPasswordField(), repeat = new JPasswordField();
        addField(settings, "Mật khẩu hiện tại", old);
        addField(settings, "Mật khẩu mới (10 - 128 ký tự)", next);
        addField(settings, "Xác nhận mật khẩu mới", repeat);

        JButton change = Theme.primaryButton("Đổi mật khẩu");
        change.addActionListener(e -> {
            char[] a = old.getPassword(), b = next.getPassword(), c = repeat.getPassword();
            if (b.length < 10 || b.length > 128 || !Arrays.equals(b, c)) {
                Arrays.fill(a, '\0'); Arrays.fill(b, '\0'); Arrays.fill(c, '\0');
                info("Mật khẩu mới từ 10 - 128 ký tự và phải khớp với xác nhận.");
                return;
            }
            Map<String, Object> payload = of("currentPassword", new String(a), "newPassword", new String(b));
            Arrays.fill(a, '\0'); Arrays.fill(b, '\0'); Arrays.fill(c, '\0');
            old.setText(""); next.setText(""); repeat.setText("");
            command("AUTH_PASSWORD_CHANGE", payload, null, change, r -> {
                clearSession();
                showAuth(false);
                info("Đã đổi mật khẩu thành công! Vui lòng đăng nhập lại.");
            }, false);
        });
        settings.add(horizontal(change));
        tabs.addTab("Hồ sơ & bảo mật", scroll(settings));

        Page blocked = new Page("Người đã chặn", "BLOCK_LIST", of(), u -> {
            JPanel c = card();
            c.add(label(name(u), false), BorderLayout.WEST);
            c.add(button("Bỏ chặn", () -> block(id(u, "user"), false)), BorderLayout.EAST);
            return c;
        });
        tabs.addTab("Đã chặn", blocked.panel);

        setView("settings", panel, this::showSettings);
        long token = generation;
        query("PROFILE_GET", of("targetUserId", id(currentUser, "user")), r -> {
            if (token != generation) return;
            Map<String, Object> u = map(r.get("profile"));
            if (u.isEmpty()) u = r;
            profile.clear();
            profile.putAll(u);
            updateSelf(u);
            nameField.setText(name(u));
            bio.setText(str(u, "bio"));
            version.setText("Phiên bản hồ sơ " + num(u, "version"));
            nameField.setEnabled(true);
            bio.setEnabled(true);
        });
        blocked.load(true);
    }

    private void openDirect(long target) {
        mutate("CONVERSATION_DIRECT_GET_OR_CREATE", of("targetUserId", target), null, r -> {
            Map<String, Object> conv = map(r.get("conversation"));
            if (conv.isEmpty()) conv = new LinkedHashMap<>(r);
            conv.putIfAbsent("title", "Trò chuyện với người dùng #" + target);
            conv.putIfAbsent("kind", "DIRECT");
            conv.putIfAbsent("otherUserId", target);
            openChat(conv);
        });
    }

    private void showNotifications() {
        Page page = new Page("Thông báo", "NOTIFICATION_LIST", of("unreadOnly", false), n -> {
            JPanel c = card();
            long nid = id(n, "notification");
            boolean available = !Boolean.FALSE.equals(n.get("available"));
            boolean read = bool(n, "read") || bool(n, "isRead") || num(n, "isRead") > 0;
            c.add(label((read ? "" : "● ") + notificationTitle(str(n, "kind", "title", "type")) + " · " + time(n), false), BorderLayout.NORTH);
            c.add(readable(available ? str(n, "content", "body", "preview") : "Nội dung không còn khả dụng."), BorderLayout.CENTER);
            JPanel actions = horizontal(button("Đánh dấu đã đọc", () -> mutate("NOTIFICATION_READ", of("notificationIds", List.of(nid)), null, r -> {
                refreshCount();
                showNotifications();
            })));
            if (available) {
                actions.add(primary("Mở nội dung", () -> {
                    mutate("NOTIFICATION_READ", of("notificationIds", List.of(nid)), null, r -> refreshCount());
                    Map<String, Object> target = map(n.get("target"));
                    String type = str(n, "targetType");
                    if (type.isBlank()) type = str(target, "type", "targetType");
                    long id = num(n, "targetId");
                    if (id == 0) id = num(target, "id", "targetId");
                    switch (type) {
                        case "POST", "COMMENT" -> showPost(num(n, "postId") > 0 ? num(n, "postId") : id);
                        case "USER", "PROFILE" -> showProfile(id);
                        case "CONVERSATION", "MESSAGE", "GROUP" -> {
                            long cid = num(n, "conversationId");
                            if (cid == 0) cid = id;
                            openChat(of("conversationId", cid, "title", "Cuộc trò chuyện #" + cid));
                        }
                        case "RELATION", "FRIEND_REQUEST" -> showPeople();
                        default -> showConversations();
                    }
                }));
            }
            c.add(actions, BorderLayout.SOUTH);
            return c;
        });
        setView("notifications", page.panel, this::showNotifications);
        page.load(true);
        refreshCount();
    }

    private String notificationTitle(String type) {
        return switch (type) {
            case "FRIEND_REQUEST" -> "Lời mời kết bạn";
            case "FRIEND_ACCEPT" -> "Lời mời kết bạn được chấp nhận";
            case "LIKE" -> "Có người thích bài viết";
            case "COMMENT" -> "Bình luận mới";
            case "MESSAGE" -> "Tin nhắn mới";
            case "GROUP_INVITE" -> "Lời mời vào nhóm";
            default -> type;
        };
    }

    private void report(String type, long target) {
        JDialog d = dialog("Báo cáo nội dung", 510, 370);
        JPanel p = vertical();
        JComboBox<String> reason = new JComboBox<>(new String[]{"SPAM", "HARASSMENT", "INAPPROPRIATE", "OTHER"});
        JTextArea description = text("", 5);
        p.add(label("Báo cáo " + type + " #" + target, true));
        addField(p, "Lý do", reason);
        addField(p, "Mô tả chi tiết", scroll(description));
        JButton submit = Theme.primaryButton("Gửi báo cáo");
        submit.addActionListener(e -> mutate("REPORT_CREATE", of("targetType", type, "targetId", target, "reason", reason.getSelectedItem(), "description", description.getText()), submit, r -> {
            d.dispose();
            info("Đã ghi nhận báo cáo của bạn. Quản trị viên sẽ xem xét.");
        }));
        p.add(horizontal(submit, button("Đóng", d::dispose)));
        d.add(p);
        d.setVisible(true);
    }

    private void showAdmin() {
        Page reports = new Page("Hàng đợi báo cáo", "ADMIN_REPORT_LIST", of("filter", ""), r -> {
            JPanel c = card();
            c.add(label("#" + id(r, "report") + " · " + str(r, "targetType") + " · " + str(r, "status"), false), BorderLayout.NORTH);
            c.add(readable(str(r, "reason") + "\n" + str(r, "description")), BorderLayout.CENTER);
            c.add(primary("Xem & xử lý", () -> reviewReport(id(r, "report"))), BorderLayout.SOUTH);
            return c;
        });
        Page audit = new Page("Nhật ký quản trị", "ADMIN_AUDIT_LIST", of(), r -> {
            JPanel c = card();
            c.add(label("#" + id(r, "audit") + " · " + str(r, "action") + " · " + time(r), false), BorderLayout.NORTH);
            c.add(readable(str(r, "reason", "summary")), BorderLayout.CENTER);
            c.add(button("Chi tiết", () -> query("ADMIN_AUDIT_GET", of("auditId", id(r, "audit")), data -> showRecord("Nhật ký quản trị", data))), BorderLayout.SOUTH);
            return c;
        });
        JPanel controls = vertical();
        controls.setBorder(new EmptyBorder(25, 25, 25, 25));
        controls.add(label("Khôi phục tài khoản / nhóm", true));
        JTextField user = new JTextField(), conversation = new JTextField(), newOwner = new JTextField(), version = new JTextField("1");
        JTextArea reason = text("", 3);
        addField(controls, "User ID cần mở khóa", user);
        addField(controls, "Conversation ID", conversation);
        addField(controls, "User ID chủ nhóm mới", newOwner);
        addField(controls, "Phiên bản nhóm hiện tại", version);
        addField(controls, "Lý do (bắt buộc)", scroll(reason));
        controls.add(horizontal(
                button("Mở khóa tài khoản", () -> {
                    try {
                        mutate("ADMIN_USER_UNSUSPEND", of("targetUserId", Long.parseLong(user.getText()), "reason", reason.getText()), null, r -> info("Đã mở khóa tài khoản."));
                    } catch (NumberFormatException ex) { info("Nhập ID hợp lệ."); }
                }),
                button("Chuyển chủ nhóm", () -> {
                    try {
                        mutate("ADMIN_GROUP_OWNER_TRANSFER", of("conversationId", Long.parseLong(conversation.getText()), "targetUserId", Long.parseLong(newOwner.getText()), "expectedVersion", Long.parseLong(version.getText()), "reason", reason.getText()), null, r -> info("Đã chuyển quyền chủ nhóm."));
                    } catch (NumberFormatException ex) { info("Nhập ID hợp lệ."); }
                }),
                button("Đóng nhóm", () -> {
                    try {
                        if (confirm("Đóng nhóm này bằng quyền quản trị?")) {
                            mutate("ADMIN_GROUP_CLOSE", of("conversationId", Long.parseLong(conversation.getText()), "expectedVersion", Long.parseLong(version.getText()), "reason", reason.getText()), null, r -> info("Đã đóng nhóm."));
                        }
                    } catch (NumberFormatException ex) { info("Nhập ID hợp lệ."); }
                })
        ));
        JPanel panel = new JPanel(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Báo cáo", reports.panel);
        tabs.addTab("Nhật ký", audit.panel);
        tabs.addTab("Khôi phục", scroll(controls));
        panel.add(tabs);
        setView("admin", panel, this::showAdmin);
        reports.load(true);
        audit.load(true);
    }

    private void reviewReport(long reportId) {
        query("ADMIN_REPORT_GET", of("reportId", reportId), data -> {
            Map<String, Object> report = map(data.get("report"));
            if (report.isEmpty()) report = data;
            Map<String, Object> r = report;
            JDialog d = dialog("Báo cáo #" + reportId, 650, 620);
            JPanel p = vertical();
            p.add(label(str(r, "targetType") + " #" + num(r, "targetId") + " · " + str(r, "status"), true));
            p.add(scroll(readable(recordText(data))));
            JComboBox<String> action = new JComboBox<>(new String[]{"NO_ACTION", "HIDE_POST", "HIDE_COMMENT", "SUSPEND_USER"});
            JTextArea reason = text("", 3);
            addField(p, "Hành động xử lý", action);
            addField(p, "Lý do quyết định", scroll(reason));
            JButton claim = Theme.primaryButton("Nhận xử lý");
            JButton resolve = Theme.primaryButton("Xác nhận xử lý");
            claim.setEnabled("OPEN".equals(str(r, "status")));
            resolve.setEnabled("IN_REVIEW".equals(str(r, "status")) && num(r, "assignedAdminId") == id(currentUser, "user"));
            claim.addActionListener(e -> mutate("ADMIN_REPORT_CLAIM", of("reportId", reportId, "expectedVersion", num(r, "version")), claim, updated -> {
                d.dispose();
                reviewReport(reportId);
            }));
            resolve.addActionListener(e -> {
                if (reason.getText().isBlank()) { info("Vui lòng nhập lý do quyết định."); return; }
                if (confirm("Áp dụng quyết định quản trị và ghi nhật ký?")) {
                    mutate("ADMIN_REPORT_RESOLVE", of("reportId", reportId, "action", action.getSelectedItem(), "reason", reason.getText(), "expectedVersion", num(r, "version")), resolve, updated -> {
                        d.dispose();
                        showAdmin();
                    });
                }
            });
            p.add(horizontal(claim, resolve, button("Đóng", d::dispose)));
            d.add(p);
            d.setVisible(true);
        });
    }

    private void showRecord(String title, Map<String, Object> record) {
        JDialog d = dialog(title, 650, 500);
        d.add(scroll(readable(recordText(record))));
        d.setVisible(true);
    }

    private String recordText(Map<String, Object> data) {
        StringBuilder b = new StringBuilder();
        data.forEach((k, v) -> {
            if (!Set.of("udpKey", "password", "passwordHash").contains(k)) b.append(k).append(": ").append(v).append('\n');
        });
        return b.toString();
    }

    // ==========================================
    // 6. CÁC TIỆN ÍCH PAGE, POST & DIALOG
    // ==========================================
    private JDialog dialog(String title, int width, int height) {
        JDialog d = new JDialog(this, title, Dialog.ModalityType.MODELESS);
        d.setSize(width, height);
        d.setLocationRelativeTo(this);
        ((JComponent) d.getContentPane()).setBorder(new EmptyBorder(18, 20, 18, 20));
        d.getContentPane().setBackground(Theme.SURFACE);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return d;
    }

    private final class Page {
        final JPanel panel = new JPanel(new BorderLayout(12, 12));
        final JPanel list = vertical();
        final JLabel status = label("Đang tải…", false);
        final JButton more;
        final String type;
        final Map<String, Object> base;
        final Function<Map<String, Object>, JComponent> render;
        String cursor;
        boolean loading;
        int epoch;

        Page(String title, String type, Map<String, Object> base, Function<Map<String, Object>, JComponent> render, Component... actions) {
            this.type = type;
            this.base = base;
            this.render = render;
            panel.setBackground(Theme.CHAT_BACKGROUND);
            panel.add(titled(title, actions), BorderLayout.NORTH);
            list.setBorder(new EmptyBorder(4, 24, 16, 24));
            panel.add(scroll(list), BorderLayout.CENTER);
            more = Theme.ghostButton("Tải thêm");
            more.addActionListener(e -> load(false));
            panel.add(horizontal(status, button("Làm mới", () -> load(true)), more), BorderLayout.SOUTH);
        }

        void load(boolean reset) {
            if (loading) return;
            if ("USER_SEARCH".equals(type) && str(base, "query").trim().length() < 2) {
                status.setText("Nhập ít nhất 2 ký tự để tìm người.");
                return;
            }
            loading = true;
            more.setEnabled(false);
            int token = ++epoch;
            long session = generation;
            if (reset) {
                cursor = null;
                list.removeAll();
                status.setText("Đang tải…");
            }
            Map<String, Object> request = new LinkedHashMap<>(base);
            request.put("limit", 20);
            if (cursor != null && !cursor.isBlank()) request.put("cursor", cursor);
            SocialClient requestedClient = client;
            CompletableFuture.supplyAsync(() -> blocking(requestedClient, type, request, null), workers).whenComplete((result, error) -> invoke(() -> {
                if (token != epoch || session != generation) return;
                loading = false;
                if (error != null) {
                    status.setText("Không tải được · bấm Làm mới");
                    more.setEnabled(true);
                    showError(error);
                    return;
                }
                if (list.getComponentCount() >= 120) list.removeAll();
                List<Map<String, Object>> items = rows(result.get("items"));
                for (Map<String, Object> row : items) {
                    JComponent rendered = render.apply(row);
                    rendered.setAlignmentX(Component.LEFT_ALIGNMENT);
                    rendered.setMaximumSize(new Dimension(Integer.MAX_VALUE, rendered.getPreferredSize().height));
                    list.add(rendered);
                    list.add(Box.createVerticalStrut(10));
                }
                if ("RELATION_LIST".equals(type) && "FRIENDS".equals(base.get("kind")) && !items.isEmpty()) {
                    List<Long> ids = items.stream().map(r -> num(relationUser(r), "userId", "otherUserId", "targetUserId")).filter(id -> id > 0).toList();
                    query("PRESENCE_GET", of("userIds", ids), presence -> {
                        if (session != generation) return;
                        for (Map<String, Object> state : rows(presence.get("items"))) {
                            updatePresence(list, num(state, "userId"), bool(state, "online"), num(state, "asOf"));
                        }
                    });
                }
                cursor = str(result, "nextCursor");
                more.setEnabled(bool(result, "hasMore") || !cursor.isBlank());
                status.setText(items.isEmpty() && list.getComponentCount() == 0 ? "Chưa có nội dung." : "Đã tải " + items.size() + " mục");
                list.revalidate();
                list.repaint();
            }));
        }
    }

    private void showPost(long postId) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.CHAT_BACKGROUND);
        JPanel top = vertical();
        top.add(label("Đang tải bài viết…", false));
        panel.add(top, BorderLayout.NORTH);

        long[] postOwner = {0};
        Page comments = new Page("Bình luận", "COMMENT_LIST", of("postId", postId), comment -> {
            JPanel c = card();
            c.add(label(str(comment, "authorName", "displayName") + " · " + time(comment), false), BorderLayout.NORTH);
            c.add(readable(bool(comment, "tombstone") ? "Nội dung không khả dụng" : str(comment, "content")), BorderLayout.CENTER);
            if (bool(comment, "tombstone")) return c;
            JPanel actions = horizontal(button("Báo cáo", () -> report("COMMENT", id(comment, "comment"))));
            if (num(comment, "authorId") == id(currentUser, "user") || postOwner[0] == id(currentUser, "user")) {
                actions.add(button("Xóa", () -> {
                    if (confirm("Xóa bình luận này?")) {
                        mutate("COMMENT_DELETE", of("commentId", id(comment, "comment")), null, r -> showPost(postId));
                    }
                }));
            }
            c.add(actions, BorderLayout.SOUTH);
            return c;
        });

        panel.add(comments.panel, BorderLayout.CENTER);
        JTextArea input = text("", 2);
        JButton send = Theme.primaryButton("Bình luận");
        send.addActionListener(e -> {
            if (!input.getText().isBlank()) {
                mutate("COMMENT_CREATE", of("postId", postId, "content", input.getText()), send, r -> {
                    input.setText("");
                    showPost(postId);
                });
            }
        });
        panel.add(horizontal(scroll(input), send), BorderLayout.SOUTH);
        setView("post", panel, () -> showPost(postId));
        long token = generation;
        query("POST_GET", of("postId", postId), r -> {
            if (token != generation) return;
            Map<String, Object> post = map(r.get("post"));
            if (post.isEmpty()) post = r;
            postOwner[0] = num(post, "authorId");
            top.removeAll();
            top.add(postCard(post));
            top.revalidate();
            top.repaint();
            if (bool(post, "unavailable") || "HIDDEN".equals(str(post, "status"))) {
                input.setEnabled(false);
                send.setEnabled(false);
                panel.remove(comments.panel);
                panel.add(label("Bình luận và tương tác không khả dụng.", false));
                panel.revalidate();
            } else comments.load(true);
        });
    }

    private JComponent postCard(Map<String, Object> post) {
        JPanel c = card();
        long postId = id(post, "post");
        c.putClientProperty("postId", postId);
        Map<String, Object> author = map(post.get("author"));
        if (author.isEmpty()) author = of("userId", num(post, "authorId"), "displayName", str(post, "authorName", "displayName"), "avatarMediaId", str(post, "authorAvatarMediaId"));
        Map<String, Object> profile = author;
        c.add(horizontal(avatar(author, 40), button(name(author), () -> showProfile(id(profile, "user"))), label(time(post), false)), BorderLayout.NORTH);
        if (bool(post, "unavailable") || "HIDDEN".equals(str(post, "status"))) {
            c.add(readable("Bài viết đã bị ẩn.\n" + str(post, "moderationReason")), BorderLayout.CENTER);
            return c;
        }
        JPanel content = vertical();
        content.add(readable(str(post, "content")));
        List<Map<String, Object>> images = rows(post.get("media"));
        if (images.isEmpty()) images = rows(post.get("images"));
        List<Component> imageButtons = new ArrayList<>();
        for (Map<String, Object> image : images) {
            String mid = str(image, "mediaId", "id");
            imageButtons.add(photo(mid, str(image, "fileName")));
        }
        if (imageButtons.isEmpty() && post.get("mediaIds") instanceof Collection<?> ids) {
            for (Object raw : ids) {
                String mid = String.valueOf(raw);
                imageButtons.add(photo(mid, ""));
            }
        }
        if (!imageButtons.isEmpty()) content.add(horizontal(imageButtons.toArray(Component[]::new)));
        c.add(content, BorderLayout.CENTER);

        JButton like = button((bool(post, "liked") ? "♥ Đã thích" : "♡ Thích") + " (" + num(post, "likeCount") + ")", () -> {});
        like.addActionListener(e -> mutate("LIKE_SET", of("postId", postId, "liked", !bool(post, "liked")), like, r -> refreshCurrent.run()));
        JPanel actions = horizontal(like, button("Bình luận (" + num(post, "commentCount") + ")", () -> showPost(postId)), button("Báo cáo", () -> report("POST", postId)));
        if (num(post, "authorId") == id(currentUser, "user") || id(author, "user") == id(currentUser, "user")) {
            actions.add(button("Xóa", () -> {
                if (confirm("Xóa bài viết này?")) {
                    mutate("POST_DELETE", of("postId", postId, "expectedVersion", num(post, "version")), null, r -> refreshCurrent.run());
                }
            }));
        }
        c.add(actions, BorderLayout.SOUTH);
        return c;
    }
}
