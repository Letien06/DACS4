package com.p2p.server.social;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded inference workers and persistent server-owned decisions. */
public final class ImageModerationService implements AutoCloseable {
    public static final String POLICY_VERSION = "safechat-image-v1";
    private final SocialDatabase db;
    private final ImageModerator classifier;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(16), task -> { Thread t = new Thread(task, "safechat-image-ai"); t.setDaemon(true); return t; },
        new ThreadPoolExecutor.AbortPolicy());

    public ImageModerationService(SocialDatabase db, ImageModerator classifier) throws Exception {
        this.db = db; this.classifier = Objects.requireNonNull(classifier);
        db.write(c -> {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS image_moderations(media_id TEXT PRIMARY KEY REFERENCES media(id),sha256 TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN('CHECKING','CLEAR','WARNING','BLOCKED','UNAVAILABLE')),violence_score REAL NOT NULL DEFAULT 0,graphic_score REAL NOT NULL DEFAULT 0,provider TEXT NOT NULL,model TEXT NOT NULL,reason TEXT NOT NULL,policy_version TEXT NOT NULL,sender_confirmed INTEGER NOT NULL DEFAULT 0 CHECK(sender_confirmed IN(0,1)),checked_at INTEGER NOT NULL)");
                s.execute("UPDATE image_moderations SET status='UNAVAILABLE',reason='Server khởi động lại khi đang kiểm tra. Hãy thử lại.',sender_confirmed=0 WHERE status='CHECKING'");
            }
            return null;
        });
    }

    public void queue(String id, String sha256, Path image, String mime) throws Exception {
        boolean accepted = db.write(c -> {
            Map<String,Object> old = read(c, id);
            if ("CHECKING".equals(old.get("status"))) return false;
            try (PreparedStatement s = c.prepareStatement("INSERT INTO image_moderations(media_id,sha256,status,provider,model,reason,policy_version,checked_at) VALUES(?,?,'CHECKING','OpenAI','omni-moderation-latest','Đang kiểm tra ảnh bằng AI',?,?) ON CONFLICT(media_id) DO UPDATE SET status='CHECKING',violence_score=0,graphic_score=0,reason=excluded.reason,sender_confirmed=0,checked_at=excluded.checked_at")) {
                s.setString(1, id); s.setString(2, sha256); s.setString(3, POLICY_VERSION); s.setLong(4, System.currentTimeMillis()); s.executeUpdate();
            }
            return true;
        });
        if (!accepted) return;
        try {
            workers.execute(() -> {
                ImageModerator.Decision decision;
                try {
                    if (Files.size(image) > 5L * 1024 * 1024) throw new IllegalArgumentException("Image too large");
                    decision = classifier.classify(Files.readAllBytes(image), mime);
                    if (decision == null) throw new IllegalArgumentException("Missing result");
                } catch (Exception e) { decision = ImageModerator.Decision.unavailable("Chưa kiểm tra được ảnh. Hãy thử lại."); }
                try { save(id, sha256, decision); } catch (Exception ignored) { /* stays CHECKING until restart; never becomes CLEAR */ }
            });
        } catch (RejectedExecutionException e) {
            save(id, sha256, ImageModerator.Decision.unavailable("Hàng đợi AI đã đầy hoặc server đang dừng. Hãy thử lại."));
        }
    }
    private void save(String id, String sha, ImageModerator.Decision d) throws Exception {
        db.write(c -> {
            try (PreparedStatement s = c.prepareStatement("UPDATE image_moderations SET status=?,violence_score=?,graphic_score=?,provider=?,model=?,reason=?,checked_at=? WHERE media_id=? AND sha256=? AND status='CHECKING'")) {
                s.setString(1,d.status()); s.setDouble(2,d.violenceScore()); s.setDouble(3,d.graphicScore());
                s.setString(4,d.provider()); s.setString(5,d.model()); s.setString(6,d.reason());
                s.setLong(7,System.currentTimeMillis()); s.setString(8,id); s.setString(9,sha); s.executeUpdate();
            }
            return null;
        });
    }
    public Map<String,Object> review(String id) throws Exception { return db.read(c -> read(c,id)); }
    static Map<String,Object> read(Connection c, String id) throws Exception {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM image_moderations WHERE media_id=?")) {
            s.setString(1,id);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return Map.of();
                Map<String,Object> result = new LinkedHashMap<>();
                result.put("status",r.getString("status")); result.put("violenceScore",r.getDouble("violence_score"));
                result.put("graphicScore",r.getDouble("graphic_score")); result.put("provider",r.getString("provider"));
                result.put("model",r.getString("model")); result.put("reason",r.getString("reason"));
                result.put("policyVersion",r.getString("policy_version")); result.put("senderConfirmed",r.getInt("sender_confirmed")==1);
                result.put("checkedAt",r.getLong("checked_at")); return result;
            }
        }
    }
    public void confirm(long actor, String id) throws Exception {
        db.write(c -> {
            try (PreparedStatement s = c.prepareStatement("UPDATE image_moderations SET sender_confirmed=1 WHERE media_id=? AND status='WARNING' AND EXISTS(SELECT 1 FROM media m JOIN users u ON u.id=m.owner_id WHERE m.id=media_id AND m.owner_id=? AND m.state='READY' AND u.status='ACTIVE')")) {
                s.setString(1,id); s.setLong(2,actor);
                if (s.executeUpdate()!=1) throw new SocialException("INVALID_STATE","Chỉ người gửi được xác nhận ảnh đang ở mức cảnh báo.");
            }
            return null;
        });
    }
    static void requireSendable(Connection c, String id, boolean isImage) throws Exception {
        Map<String,Object> review = read(c,id);
        if (review.isEmpty()) {
            if (isImage) throw new SocialException("IMAGE_NOT_REVIEWED","Ảnh chưa được AI kiểm tra.");
            return;
        }
        switch (String.valueOf(review.get("status"))) {
            case "CLEAR" -> { }
            case "WARNING" -> { if (!Boolean.TRUE.equals(review.get("senderConfirmed"))) throw new SocialException("IMAGE_WARNING","Cần xác nhận cảnh báo trước khi gửi ảnh.",review); }
            case "BLOCKED" -> throw new SocialException("IMAGE_BLOCKED","Ảnh có dấu hiệu bạo lực trực diện và không được gửi.",review);
            default -> throw new SocialException("IMAGE_NOT_REVIEWED","Ảnh chưa được kiểm tra thành công. Hãy thử lại.",review);
        }
    }
    @Override public void close() {
        workers.shutdownNow();
        try { workers.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
