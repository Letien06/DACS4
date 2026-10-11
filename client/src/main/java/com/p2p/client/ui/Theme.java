package com.p2p.client.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public final class Theme {
    /** SafeChat UI tokens. Modern dark palette optimized for desktop chat. */
    public static final Color PRIMARY = new Color(92, 102, 235);          // #5C66EB
    public static final Color PRIMARY_DARK = new Color(72, 82, 205);
    public static final Color PRIMARY_LIGHT = new Color(129, 140, 248);
    public static final Color PRIMARY_SOFT = new Color(55, 58, 93);
    public static final Color APP_BACKGROUND = new Color(14, 18, 24);     // #0E1218
    public static final Color SURFACE = new Color(22, 27, 36);            // #161B24
    public static final Color SURFACE_HOVER = new Color(32, 38, 50);
    public static final Color NAV_BACKGROUND = new Color(14, 18, 24);     // #0E1218
    public static final Color CHAT_BACKGROUND = new Color(18, 22, 30);    // #12161E
    public static final Color COMPOSER_BACKGROUND = new Color(26, 32, 44);
    public static final Color FILE_CARD = new Color(28, 34, 46);
    public static final Color BUBBLE_SELF = new Color(79, 70, 229);       // #4F46E5 Indigo
    public static final Color BUBBLE_PEER = new Color(30, 36, 48);        // #1E2430 Dark slate
    public static final Color BORDER = new Color(44, 52, 68);
    public static final Color TEXT = new Color(244, 246, 251);            // #F4F6FB
    public static final Color MUTED = new Color(152, 162, 179);           // #98A2B3
    public static final Color ONLINE = new Color(52, 211, 153);           // #34D399
    public static final Color WARNING = new Color(245, 158, 11);          // #F59E0B
    public static final Color DANGER = new Color(239, 68, 68);            // #EF4444
    public static final Color INFO = new Color(59, 130, 246);             // #3B82F6

    private Theme() { }

    public static Font font(int style, float size) {
        return new Font("Segoe UI", style, Math.round(size));
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }

    public static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(font(Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(PRIMARY);
        button.setBorder(padding(9, 18, 9, 18));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (button.isEnabled()) button.setBackground(PRIMARY_DARK); }
            @Override public void mouseExited(MouseEvent e) { if (button.isEnabled()) button.setBackground(PRIMARY); }
        });
        return button;
    }

    public static JButton ghostButton(String text) {
        JButton button = new JButton(text);
        button.setFont(font(Font.BOLD, 13));
        button.setForeground(TEXT);
        button.setBackground(SURFACE_HOVER);
        button.setBorder(padding(8, 14, 8, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (button.isEnabled()) button.setBackground(PRIMARY_SOFT); }
            @Override public void mouseExited(MouseEvent e) { if (button.isEnabled()) button.setBackground(SURFACE_HOVER); }
        });
        return button;
    }

    public static JButton iconButton(Icon icon, String tooltip, boolean primary) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(38, 38));
        button.setMinimumSize(new Dimension(38, 38));
        button.setMaximumSize(new Dimension(38, 38));
        button.setFocusable(false);
        button.setForeground(primary ? Color.WHITE : TEXT);
        button.setBackground(primary ? PRIMARY : COMPOSER_BACKGROUND);
        button.setBorder(padding(6, 6, 6, 6));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) button.setBackground(primary ? PRIMARY_DARK : PRIMARY_SOFT);
            }
            @Override public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) button.setBackground(primary ? PRIMARY : COMPOSER_BACKGROUND);
            }
        });
        return button;
    }

    public static JButton navButton(Icon icon, String tooltip, boolean selected) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(46, 46));
        button.setMinimumSize(new Dimension(46, 46));
        button.setFocusable(false);
        button.setForeground(selected ? Color.WHITE : MUTED);
        button.setBackground(selected ? PRIMARY_SOFT : NAV_BACKGROUND);
        button.setBorder(padding(11, 11, 11, 11));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (!selected) button.setBackground(SURFACE_HOVER);
            }
            @Override public void mouseExited(MouseEvent e) {
                if (!selected) button.setBackground(NAV_BACKGROUND);
            }
        });
        return button;
    }

    public static JButton actionButton(String text, Icon icon, boolean primary) {
        JButton button = primary ? primaryButton(text) : ghostButton(text);
        button.setIcon(icon);
        button.setIconTextGap(7);
        button.setFocusable(false);
        return button;
    }

    public static JPanel roundedPanel(Color background, int arc) {
        return new RoundedPanel(background, arc);
    }

    public static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final int arc;

        private RoundedPanel(Color fill, int arc) {
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    /** Modern rounded message bubble supporting tailored left/right bubble styles. */
    public static final class BubblePanel extends JPanel {
        private final Color fill;
        private final boolean isSelf;
        private final int arc;

        public BubblePanel(Color fill, boolean isSelf, int arc) {
            this.fill = fill;
            this.isSelf = isSelf;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    /** Pill badge for unread count, tags and moderation badges. */
    public static JComponent pill(String text, Color bg, Color fg) {
        JLabel label = new JLabel(text);
        label.setFont(font(Font.BOLD, 11));
        label.setForeground(fg);
        label.setBorder(padding(2, 8, 2, 8));
        JPanel pill = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(bg);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        pill.setOpaque(false);
        pill.add(label);
        return pill;
    }

    public static final class Avatar extends JComponent {
        private String name;
        private final int size;
        private boolean online;
        private boolean isGroup;

        public Avatar(String name, int size, boolean online) {
            this(name, size, online, false);
        }

        public Avatar(String name, int size, boolean online, boolean isGroup) {
            this.name = name == null ? "?" : name;
            this.size = size;
            this.online = online;
            this.isGroup = isGroup;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        public void setOnline(boolean online) {
            this.online = online;
            repaint();
        }

        public void setName(String name) {
            this.name = name == null ? "?" : name;
            repaint();
        }

        public void setGroup(boolean isGroup) {
            this.isGroup = isGroup;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(isGroup ? new Color(79, 70, 229) : colorFor(name));
            g.fill(new Ellipse2D.Float(1, 1, size - 2, size - 2));

            if (isGroup) {
                // Group icon
                AppIcon groupIcon = new AppIcon(AppIcon.Type.GROUP, Color.WHITE, Math.round(size * 0.55f));
                int offset = (size - groupIcon.getIconWidth()) / 2;
                groupIcon.paintIcon(this, g, offset, offset);
            } else {
                g.setFont(font(Font.BOLD, size * 0.40f));
                g.setColor(Color.WHITE);
                String initial = name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
                FontMetrics metrics = g.getFontMetrics();
                int x = (size - metrics.stringWidth(initial)) / 2;
                int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(initial, x, y);
            }

            if (online) {
                int dot = Math.max(9, size / 4);
                int dx = size - dot - 1;
                int dy = size - dot - 1;
                g.setColor(APP_BACKGROUND);
                g.fillOval(dx - 2, dy - 2, dot + 4, dot + 4);
                g.setColor(ONLINE);
                g.fillOval(dx, dy, dot, dot);
            }
            g.dispose();
        }

        private static Color colorFor(String value) {
            Color[] colors = {
                    new Color(99, 102, 241), new Color(139, 92, 246),
                    new Color(14, 165, 233), new Color(236, 72, 153),
                    new Color(16, 185, 129), new Color(245, 158, 11),
                    new Color(239, 68, 68), new Color(20, 184, 166)
            };
            return colors[Math.floorMod(value.hashCode(), colors.length)];
        }
    }

    /** Modern rounded photo bubble with real-time blur for violent / sensitive media. */
    public static final class PhotoBubble extends JComponent {
        private Image displayImage;
        private Image blurredImage;
        private final boolean isWarning;
        private boolean isRevealed;
        private final int arc;
        private Runnable clickAction;
        private int targetW = 260;
        private int targetH = 180;

        public PhotoBubble(boolean isWarning, int arc) {
            this.isWarning = isWarning;
            this.arc = arc;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(targetW, targetH));
            setMinimumSize(new Dimension(targetW, targetH));
            setMaximumSize(new Dimension(targetW, targetH));
            setToolTipText(isWarning ? "Ảnh bị làm mờ do cảnh báo bạo lực. Nhấn để mở ảnh." : "Nhấn để xem ảnh phóng to");

            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (isWarning && !isRevealed) {
                        isRevealed = true;
                        setToolTipText("Nhấn để xem ảnh phóng to");
                        repaint();
                    } else if (clickAction != null) {
                        clickAction.run();
                    }
                }
            });
        }

        public void setClickAction(Runnable clickAction) {
            this.clickAction = clickAction;
        }

        public void setImage(Image img) {
            if (img == null) return;
            this.displayImage = img;
            int iw = img.getWidth(null);
            int ih = img.getHeight(null);
            if (iw > 0 && ih > 0) {
                if (iw >= ih) {
                    targetW = 280;
                    targetH = Math.max(130, Math.min(260, (int)(280.0 * ih / iw)));
                } else {
                    targetH = 320;
                    targetW = Math.max(160, Math.min(240, (int)(320.0 * iw / ih)));
                }
                setPreferredSize(new Dimension(targetW, targetH));
                setMinimumSize(new Dimension(targetW, targetH));
                setMaximumSize(new Dimension(targetW, targetH));
            }
            if (isWarning) {
                this.blurredImage = createBlurred(img, targetW, targetH);
            }
            repaint();
        }

        private static Image createBlurred(Image src, int w, int h) {
            if (src == null || w <= 0 || h <= 0) return null;
            int sw = Math.max(8, w / 12);
            int sh = Math.max(8, h / 12);
            BufferedImage tiny = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g1 = tiny.createGraphics();
            g1.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g1.drawImage(src, 0, 0, sw, sh, null);
            g1.dispose();

            BufferedImage blurred = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = blurred.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(tiny, 0, 0, w, h, null);

            // Dark translucent frosted overlay
            g2.setColor(new Color(15, 23, 42, 160));
            g2.fillRect(0, 0, w, h);
            g2.dispose();
            return blurred;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D clip = new RoundRectangle2D.Float(0, 0, w, h, arc, arc);
            g.setClip(clip);

            Image toDraw = (isWarning && !isRevealed) ? (blurredImage != null ? blurredImage : displayImage) : displayImage;
            if (toDraw != null) {
                g.drawImage(toDraw, 0, 0, w, h, null);
            } else {
                g.setColor(new Color(30, 36, 48));
                g.fillRect(0, 0, w, h);
                g.setColor(Theme.MUTED);
                g.setFont(Theme.font(Font.PLAIN, 12));
                String loadingText = "Đang tải ảnh...";
                FontMetrics fm = g.getFontMetrics();
                g.drawString(loadingText, (w - fm.stringWidth(loadingText)) / 2, (h + fm.getAscent()) / 2);
            }

            if (isWarning && !isRevealed) {
                // If warning and blurred: draw frosted center badge
                int badgeW = Math.min(w - 24, 210);
                int badgeH = 74;
                int bx = (w - badgeW) / 2;
                int by = (h - badgeH) / 2;

                g.setColor(new Color(0, 0, 0, 190));
                g.fillRoundRect(bx, by, badgeW, badgeH, 16, 16);
                g.setColor(new Color(245, 158, 11, 200));
                g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(bx, by, badgeW, badgeH, 16, 16);

                AppIcon eye = new AppIcon(AppIcon.Type.EYE, Theme.WARNING, 22);
                eye.paintIcon(this, g, bx + (badgeW - 22) / 2, by + 10);

                g.setFont(Theme.font(Font.BOLD, 11));
                g.setColor(Theme.WARNING);
                String title = "CẢNH BÁO: ẢNH BẠO LỰC";
                FontMetrics tfm = g.getFontMetrics();
                g.drawString(title, bx + (badgeW - tfm.stringWidth(title)) / 2, by + 46);

                g.setFont(Theme.font(Font.PLAIN, 10));
                g.setColor(new Color(241, 245, 249));
                String sub = "Nhấn để xem ảnh";
                FontMetrics sfm = g.getFontMetrics();
                g.drawString(sub, bx + (badgeW - sfm.stringWidth(sub)) / 2, by + 62);
            } else {
                // Subtle border
                g.setColor(new Color(255, 255, 255, 20));
                g.setStroke(new BasicStroke(1.0f));
                g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

                if (isWarning && isRevealed) {
                    // Small unblurred indicator badge
                    int pillW = 86;
                    int pillH = 20;
                    g.setColor(new Color(0, 0, 0, 180));
                    g.fillRoundRect(8, 8, pillW, pillH, 10, 10);
                    g.setColor(Theme.WARNING);
                    g.setFont(Theme.font(Font.BOLD, 10));
                    g.drawString("⚠ Đã mở ảnh", 14, 22);
                }
            }

            g.dispose();
        }
    }
}
