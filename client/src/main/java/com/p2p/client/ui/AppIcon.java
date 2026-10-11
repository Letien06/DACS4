package com.p2p.client.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Lightweight vector icons for modern desktop messaging interface without image assets. */
public final class AppIcon implements Icon {
    public enum Type {
        SEND, ATTACHMENT, DOWNLOAD, OPEN_FILE, REFRESH, FILE, CHAT, FOLDER, SETTINGS, LOGOUT,
        EMOJI, INFO, GROUP, USER, SEARCH, PLUS, CHECK, DOUBLE_CHECK, WARNING, BLOCKED,
        CLOCK, SERVER, LOGS, SHIELD, BELL, CLOSE, CHEVRON_RIGHT, TRASH, LEAVE, EYE
    }

    private final Type type;
    private final Color color;
    private final int size;

    public AppIcon(Type type, Color color, int size) {
        this.type = type;
        this.color = color;
        this.size = size;
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1.7f, size / 11f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double scale = size / 24d;
        g.scale(scale, scale);
        switch (type) {
            case SEND -> paintSend(g);
            case ATTACHMENT -> paintAttachment(g);
            case DOWNLOAD -> paintDownload(g);
            case OPEN_FILE -> paintOpen(g);
            case REFRESH -> paintRefresh(g);
            case FILE -> paintFile(g);
            case CHAT -> paintChat(g);
            case FOLDER -> paintFolder(g);
            case SETTINGS -> paintSettings(g);
            case LOGOUT -> paintLogout(g);
            case EMOJI -> paintEmoji(g);
            case INFO -> paintInfo(g);
            case GROUP -> paintGroup(g);
            case USER -> paintUser(g);
            case SEARCH -> paintSearch(g);
            case PLUS -> paintPlus(g);
            case CHECK -> paintCheck(g);
            case DOUBLE_CHECK -> paintDoubleCheck(g);
            case WARNING -> paintWarning(g);
            case BLOCKED -> paintBlocked(g);
            case CLOCK -> paintClock(g);
            case SERVER -> paintServer(g);
            case LOGS -> paintLogs(g);
            case SHIELD -> paintShield(g);
            case BELL -> paintBell(g);
            case CLOSE -> paintClose(g);
            case CHEVRON_RIGHT -> paintChevronRight(g);
            case TRASH -> paintTrash(g);
            case LEAVE -> paintLeave(g);
            case EYE -> paintEye(g);
        }
        g.dispose();
    }

    private static void paintSend(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(3, 11.2); path.lineTo(20.5, 3.5); path.lineTo(14.1, 20.5);
        path.lineTo(10.6, 13.5); path.closePath();
        g.draw(path);
        line(g, 10.6, 13.5, 20.5, 3.5);
    }

    private static void paintAttachment(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(8.2, 12.7); path.lineTo(14.4, 6.5);
        path.curveTo(16.2, 4.7, 19.1, 4.7, 20.9, 6.5);
        path.curveTo(22.7, 8.3, 22.7, 11.2, 20.9, 13);
        path.lineTo(11.5, 22.4);
        path.curveTo(8.8, 25.1, 4.5, 25.1, 1.8, 22.4);
        path.curveTo(-0.9, 19.7, -0.9, 15.4, 1.8, 12.7);
        path.lineTo(11.2, 3.3);
        g.draw(path);
    }

    private static void paintDownload(Graphics2D g) {
        line(g, 12, 3, 12, 15);
        line(g, 7.5, 10.7, 12, 15.2);
        line(g, 16.5, 10.7, 12, 15.2);
        line(g, 5, 20, 19, 20);
    }

    private static void paintOpen(Graphics2D g) {
        Path2D folder = new Path2D.Double();
        folder.moveTo(3, 7); folder.lineTo(9, 7); folder.lineTo(11, 9); folder.lineTo(21, 9);
        folder.lineTo(19, 20); folder.lineTo(3, 20); folder.closePath();
        g.draw(folder);
        line(g, 13, 15, 21, 7);
        line(g, 16.5, 7, 21, 7);
        line(g, 21, 7, 21, 11.5);
    }

    private static void paintRefresh(Graphics2D g) {
        g.draw(new Arc2D.Double(4, 4, 16, 16, 35, 285, Arc2D.OPEN));
        line(g, 16.5, 4.5, 20.5, 4.5);
        line(g, 20.5, 4.5, 20.5, 8.5);
    }

    private static void paintFile(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(5, 2.5, 14, 19, 2, 2));
        line(g, 14, 3, 14, 8);
        line(g, 14, 8, 19, 8);
        line(g, 8.5, 13, 15.5, 13);
        line(g, 8.5, 17, 14, 17);
    }

    private static void paintChat(Graphics2D g) {
        Path2D bubble = new Path2D.Double();
        bubble.moveTo(4, 5); bubble.lineTo(20, 5); bubble.lineTo(20, 16);
        bubble.lineTo(14, 16); bubble.lineTo(9, 21); bubble.lineTo(9, 16); bubble.lineTo(4, 16); bubble.closePath();
        g.draw(bubble);
    }

    private static void paintFolder(Graphics2D g) {
        Path2D folder = new Path2D.Double();
        folder.moveTo(3, 7); folder.lineTo(9, 7); folder.lineTo(11, 9); folder.lineTo(21, 9);
        folder.lineTo(20, 19); folder.lineTo(4, 19); folder.closePath();
        g.draw(folder);
    }

    private static void paintSettings(Graphics2D g) {
        g.draw(new Ellipse2D.Double(8, 8, 8, 8));
        g.draw(new Ellipse2D.Double(3, 10.5, 3, 3));
        g.draw(new Ellipse2D.Double(18, 10.5, 3, 3));
        line(g, 6, 12, 8, 12); line(g, 16, 12, 18, 12);
        line(g, 12, 6, 12, 8); line(g, 12, 16, 12, 18);
    }

    private static void paintLogout(Graphics2D g) {
        line(g, 4, 12, 16, 12); line(g, 12, 8, 16, 12); line(g, 12, 16, 16, 12);
        line(g, 5, 5, 5, 19); line(g, 5, 5, 11, 5);
    }

    private static void paintEmoji(Graphics2D g) {
        g.draw(new Ellipse2D.Double(3, 3, 18, 18));
        g.fill(new Ellipse2D.Double(8, 8.5, 2.2, 2.2));
        g.fill(new Ellipse2D.Double(14, 8.5, 2.2, 2.2));
        g.draw(new Arc2D.Double(7, 9.5, 10, 8, 200, 140, Arc2D.OPEN));
    }

    private static void paintInfo(Graphics2D g) {
        g.draw(new Ellipse2D.Double(3, 3, 18, 18));
        line(g, 12, 11, 12, 17);
        g.fill(new Ellipse2D.Double(11, 7, 2, 2));
    }

    private static void paintGroup(Graphics2D g) {
        g.draw(new Ellipse2D.Double(7, 3, 6, 6));
        Path2D body = new Path2D.Double();
        body.moveTo(4, 18); body.curveTo(4, 13.5, 6, 12, 10, 12);
        body.curveTo(14, 12, 16, 13.5, 16, 18);
        g.draw(body);
        g.draw(new Arc2D.Double(14, 5, 5, 5, 270, 180, Arc2D.OPEN));
        Path2D rightBody = new Path2D.Double();
        rightBody.moveTo(17, 13.5); rightBody.curveTo(19.5, 14, 21, 15.5, 21, 18);
        g.draw(rightBody);
    }

    private static void paintUser(Graphics2D g) {
        g.draw(new Ellipse2D.Double(8, 3, 8, 8));
        Path2D body = new Path2D.Double();
        body.moveTo(4, 20); body.curveTo(4, 15, 7, 14, 12, 14);
        body.curveTo(17, 14, 20, 15, 20, 20);
        g.draw(body);
    }

    private static void paintSearch(Graphics2D g) {
        g.draw(new Ellipse2D.Double(4, 4, 11, 11));
        line(g, 13.5, 13.5, 20, 20);
    }

    private static void paintPlus(Graphics2D g) {
        line(g, 12, 5, 12, 19);
        line(g, 5, 12, 19, 12);
    }

    private static void paintCheck(Graphics2D g) {
        line(g, 4, 12.5, 9.5, 18);
        line(g, 9.5, 18, 20, 6.5);
    }

    private static void paintDoubleCheck(Graphics2D g) {
        line(g, 2, 12.5, 7.5, 18);
        line(g, 7.5, 18, 18, 6.5);
        line(g, 8.5, 12.5, 12.5, 16.5);
        line(g, 12.5, 16.5, 22, 6.5);
    }

    private static void paintWarning(Graphics2D g) {
        Path2D tri = new Path2D.Double();
        tri.moveTo(12, 3); tri.lineTo(21.5, 20); tri.lineTo(2.5, 20); tri.closePath();
        g.draw(tri);
        line(g, 12, 8.5, 12, 14.5);
        g.fill(new Ellipse2D.Double(11, 16.5, 2, 2));
    }

    private static void paintBlocked(Graphics2D g) {
        g.draw(new Ellipse2D.Double(3, 3, 18, 18));
        line(g, 6, 6, 18, 18);
    }

    private static void paintClock(Graphics2D g) {
        g.draw(new Ellipse2D.Double(3, 3, 18, 18));
        line(g, 12, 7, 12, 12);
        line(g, 12, 12, 16, 14);
    }

    private static void paintServer(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(3, 4, 18, 6, 3, 3));
        g.draw(new RoundRectangle2D.Double(3, 14, 18, 6, 3, 3));
        g.fill(new Ellipse2D.Double(6, 6.5, 1.8, 1.8));
        g.fill(new Ellipse2D.Double(6, 16.5, 1.8, 1.8));
    }

    private static void paintLogs(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(4, 3, 16, 18, 2, 2));
        line(g, 8, 8, 16, 8);
        line(g, 8, 12, 16, 12);
        line(g, 8, 16, 13, 16);
    }

    private static void paintShield(Graphics2D g) {
        Path2D shield = new Path2D.Double();
        shield.moveTo(12, 2.5);
        shield.lineTo(20, 5.5);
        shield.curveTo(20, 14, 16, 19.5, 12, 21.5);
        shield.curveTo(8, 19.5, 4, 14, 4, 5.5);
        shield.closePath();
        g.draw(shield);
        line(g, 9, 11.5, 11.5, 14);
        line(g, 11.5, 14, 15.5, 9.5);
    }

    private static void paintBell(Graphics2D g) {
        Path2D bell = new Path2D.Double();
        bell.moveTo(12, 4);
        bell.curveTo(8.5, 4, 7, 7, 7, 12);
        bell.lineTo(5, 16);
        bell.lineTo(19, 16);
        bell.lineTo(17, 12);
        bell.curveTo(17, 7, 15.5, 4, 12, 4);
        bell.closePath();
        g.draw(bell);
        g.draw(new Arc2D.Double(10, 16, 4, 4, 180, 180, Arc2D.OPEN));
    }

    private static void paintClose(Graphics2D g) {
        line(g, 6, 6, 18, 18);
        line(g, 18, 6, 6, 18);
    }

    private static void paintChevronRight(Graphics2D g) {
        line(g, 9, 5, 16, 12);
        line(g, 16, 12, 9, 19);
    }

    private static void paintTrash(Graphics2D g) {
        line(g, 4, 6, 20, 6);
        line(g, 9, 3, 15, 3);
        Path2D bin = new Path2D.Double();
        bin.moveTo(6, 6); bin.lineTo(7, 20); bin.lineTo(17, 20); bin.lineTo(18, 6);
        g.draw(bin);
        line(g, 10, 10, 10, 16);
        line(g, 14, 10, 14, 16);
    }

    private static void paintLeave(Graphics2D g) {
        line(g, 14, 4, 20, 4); line(g, 20, 4, 20, 20); line(g, 20, 20, 14, 20);
        line(g, 4, 12, 15, 12);
        line(g, 10, 7, 15, 12);
        line(g, 10, 17, 15, 12);
    }

    private static void paintEye(Graphics2D g) {
        Path2D eye = new Path2D.Double();
        eye.moveTo(3, 12);
        eye.curveTo(7, 5, 17, 5, 21, 12);
        eye.curveTo(17, 19, 7, 19, 3, 12);
        eye.closePath();
        g.draw(eye);
        g.draw(new Ellipse2D.Double(9.5, 9.5, 5, 5));
    }

    private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }
}
