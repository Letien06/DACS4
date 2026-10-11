package com.p2p.server.social;

import com.p2p.common.social.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Real HTTPS adapter for the documented image moderation endpoint. */
public final class OpenAiImageModerator implements ImageModerator {
    private static final String MODEL = "omni-moderation-latest";
    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final Duration timeout;
    private final double warningThreshold, graphicThreshold;

    public OpenAiImageModerator() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(),
            URI.create("https://api.openai.com/v1/moderations"), System.getenv("OPENAI_API_KEY"),
            Duration.ofSeconds(15), 0.5, 0.8);
    }

    // Package-private constructor permits a loopback contract server in tests only.
    OpenAiImageModerator(HttpClient http, URI endpoint, String apiKey, Duration timeout,
                         double warningThreshold, double graphicThreshold) {
        if (warningThreshold <= 0 || warningThreshold > 1 || graphicThreshold <= 0 || graphicThreshold > 1)
            throw new IllegalArgumentException("Invalid moderation thresholds");
        this.http = http; this.endpoint = endpoint; this.apiKey = apiKey; this.timeout = timeout;
        this.warningThreshold = warningThreshold; this.graphicThreshold = graphicThreshold;
    }

    @Override public Decision classify(byte[] image, String mimeType) throws Exception {
        if (apiKey == null || apiKey.isBlank())
            return Decision.unavailable("Server chưa cấu hình khóa API AI. Ảnh chưa được kiểm tra và chưa được gửi.");
        if (image.length == 0 || image.length > 5 * 1024 * 1024
                || !Set.of("image/png", "image/jpeg").contains(mimeType))
            return Decision.unavailable("Ảnh vượt phạm vi kiểm tra PNG/JPEG tối đa 5 MiB.");
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image);
        byte[] body = Json.MAPPER.writeValueAsBytes(Map.of("model", MODEL, "input",
            List.of(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)))));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
            .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        try {
            CompletableFuture<HttpResponse<byte[]>> pending = http.sendAsync(request, info -> new LimitedBody());
            HttpResponse<byte[]> response;
            try { response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
            catch (Exception e) { pending.cancel(true); throw e; }
                if (response.statusCode() != 200)
                    return Decision.unavailable(response.statusCode() == 401 ? "Khóa API AI không hợp lệ."
                        : response.statusCode() == 429 ? "Dịch vụ AI đang giới hạn lượt kiểm tra. Hãy thử lại sau."
                        : "Dịch vụ AI chưa xử lý được ảnh (HTTP " + response.statusCode() + ").");
                byte[] bytes = response.body();
                if (bytes.length > 128 * 1024) return Decision.unavailable("Phản hồi AI vượt giới hạn.");
                JsonNode root = Json.MAPPER.readTree(bytes);
                JsonNode results = root.path("results");
                if (!results.isArray() || results.size() != 1 || !root.path("model").isTextual())
                    return Decision.unavailable("Phản hồi AI thiếu thông tin kết quả hoặc mô hình.");
                JsonNode result = results.get(0), scores = result.path("category_scores"), categories = result.path("categories");
                JsonNode applied = result.path("category_applied_input_types");
                if (!supportsImage(applied.path("violence")) || !supportsImage(applied.path("violence/graphic")))
                    return Decision.unavailable("Kết quả AI không xác nhận phân tích ảnh cho nhóm bạo lực.");
                double violence = score(scores, "violence"), graphic = score(scores, "violence/graphic");
                if (!categories.path("violence").isBoolean() || !categories.path("violence/graphic").isBoolean())
                    return Decision.unavailable("Phản hồi AI thiếu nhãn nhận diện bạo lực.");
                String status = categories.path("violence/graphic").asBoolean() || graphic >= graphicThreshold ? "BLOCKED"
                    : categories.path("violence").asBoolean() || violence >= warningThreshold ? "WARNING" : "CLEAR";
                String reason = switch (status) {
                    case "BLOCKED" -> "AI phát hiện dấu hiệu bạo lực trực diện hoặc thương tích nghiêm trọng. Ảnh không được gửi.";
                    case "WARNING" -> "AI phát hiện dấu hiệu bạo lực hoặc thương tích. Cần xác nhận trước khi gửi và xem ảnh.";
                    default -> "Chưa phát hiện dấu hiệu vượt ngưỡng cảnh báo; kết quả AI có thể nhầm.";
                };
                return new Decision(status, violence, graphic, "OpenAI", root.path("model").asText(), reason);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Decision.unavailable("Lượt kiểm tra AI bị gián đoạn. Hãy thử lại.");
        } catch (Exception e) {
            // Do not return provider response bodies, credentials or submitted bytes in errors.
            return Decision.unavailable("Không kiểm tra được ảnh do lỗi kết nối, timeout hoặc phản hồi AI không hợp lệ.");
        }
    }
    /** Bounds response memory and completes only after the entire body, inside our deadline. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription s) { subscription=s; s.request(1); }
        public void onNext(List<ByteBuffer> chunks) {
            for (ByteBuffer chunk:chunks) {
                if(bytes.size()+chunk.remaining()>128*1024) {
                    subscription.cancel(); result.completeExceptionally(new IllegalStateException("Response too large")); return;
                }
                byte[] part=new byte[chunk.remaining()]; chunk.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable e) { result.completeExceptionally(e); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
    private static boolean supportsImage(JsonNode types) {
        if (!types.isArray()) return false;
        for (JsonNode type : types) if ("image".equals(type.asText())) return true;
        return false;
    }
    private static double score(JsonNode scores, String name) {
        JsonNode value = scores.path(name);
        if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() < 0 || value.asDouble() > 1)
            throw new IllegalArgumentException("Invalid AI score");
        return value.asDouble();
    }
}
