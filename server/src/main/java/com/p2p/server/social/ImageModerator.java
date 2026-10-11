package com.p2p.server.social;

/** Injectable image inference boundary; test fixtures never replace production inference. */
@FunctionalInterface
public interface ImageModerator {
    Decision classify(byte[] image, String mimeType) throws Exception;

    record Decision(String status, double violenceScore, double graphicScore,
                    String provider, String model, String reason) {
        public Decision {
            if (!java.util.Set.of("CLEAR", "WARNING", "BLOCKED", "UNAVAILABLE").contains(status))
                throw new IllegalArgumentException("Invalid moderation decision");
            if (!Double.isFinite(violenceScore) || !Double.isFinite(graphicScore)
                    || violenceScore < 0 || violenceScore > 1 || graphicScore < 0 || graphicScore > 1)
                throw new IllegalArgumentException("Invalid moderation scores");
            java.util.Objects.requireNonNull(provider);
            java.util.Objects.requireNonNull(model);
            java.util.Objects.requireNonNull(reason);
        }
        public static Decision unavailable(String reason) {
            return new Decision("UNAVAILABLE", 0, 0, "OpenAI", "omni-moderation-latest", reason);
        }
    }
}
