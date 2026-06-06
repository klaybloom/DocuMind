package com.demo.ragchat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private static final long WINDOW_MILLIS = 60_000L;

    @Value("${app.chat.rate-limit-per-minute:30}")
    private int maxRequestsPerMinute;

    private Clock clock = Clock.systemUTC();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitDecision check(String actor) {
        int limit = Math.max(0, maxRequestsPerMinute);
        if (limit == 0) {
            return RateLimitDecision.allowed(0);
        }

        long now = clock.millis();
        String key = actorKey(actor);
        Window window = windows.compute(key, (ignored, existing) -> nextWindow(existing, now));
        if (window.count() <= limit) {
            return RateLimitDecision.allowed(limit);
        }

        long retryAfterMillis = Math.max(1L, window.windowStartedAt() + WINDOW_MILLIS - now);
        long retryAfterSeconds = Math.max(1L, (long) Math.ceil(retryAfterMillis / 1000.0));
        return RateLimitDecision.rejected(limit, retryAfterSeconds);
    }

    private Window nextWindow(Window existing, long now) {
        if (existing == null || now - existing.windowStartedAt() >= WINDOW_MILLIS) {
            return new Window(now, 1);
        }
        return new Window(existing.windowStartedAt(), existing.count() + 1);
    }

    private String actorKey(String actor) {
        if (actor == null || actor.trim().isEmpty()) {
            return "anonymous";
        }
        return actor.trim().toLowerCase();
    }

    public record RateLimitDecision(boolean allowed, int limit, long retryAfterSeconds) {
        public static RateLimitDecision allowed(int limit) {
            return new RateLimitDecision(true, limit, 0);
        }

        public static RateLimitDecision rejected(int limit, long retryAfterSeconds) {
            return new RateLimitDecision(false, limit, retryAfterSeconds);
        }
    }

    private record Window(long windowStartedAt, int count) {
    }
}
