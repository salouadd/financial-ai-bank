package com.neobank.coachservice.service;

import com.neobank.coachservice.exception.RequestRateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class UserRateLimiter {

    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();
    private static final int MAX_TRACKED_USERS = 100_000;

    private final int aiAnalysesPerMinute;
    private final int salaryDetectionsPerMinute;
    private final Map<Key, Window> windows = new HashMap<>();
    private long lastCleanupNanos = System.nanoTime();

    public UserRateLimiter(
            @Value("${coach.rate-limit.ai-analyses-per-minute:5}") int aiAnalysesPerMinute,
            @Value("${coach.rate-limit.salary-detections-per-minute:10}") int salaryDetectionsPerMinute) {
        if (aiAnalysesPerMinute < 1 || salaryDetectionsPerMinute < 1) {
            throw new IllegalArgumentException("Rate limits must be positive.");
        }
        this.aiAnalysesPerMinute = aiAnalysesPerMinute;
        this.salaryDetectionsPerMinute = salaryDetectionsPerMinute;
    }

    public void checkAiAnalysis(UUID userId) {
        check("ai-analysis", userId, aiAnalysesPerMinute);
    }

    public void checkSalaryDetection(UUID userId) {
        check("salary-detection", userId, salaryDetectionsPerMinute);
    }

    public synchronized void forget(UUID userId) {
        windows.keySet().removeIf(key -> key.userId().equals(userId));
    }

    private synchronized void check(String operation, UUID userId, int limit) {
        long now = System.nanoTime();
        if (now - lastCleanupNanos >= WINDOW_NANOS) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAtNanos >= WINDOW_NANOS);
            lastCleanupNanos = now;
        }

        Key key = new Key(operation, userId);
        Window window = windows.get(key);
        if (window != null && now - window.startedAtNanos >= WINDOW_NANOS) {
            windows.remove(key);
            window = null;
        }
        if (window == null) {
            if (windows.size() >= MAX_TRACKED_USERS) {
                throw new RequestRateLimitExceededException(operation);
            }
            windows.put(key, new Window(now));
            return;
        }
        if (window.count >= limit) {
            throw new RequestRateLimitExceededException(operation);
        }
        window.count++;
    }

    private record Key(String operation, UUID userId) {}

    private static final class Window {
        private final long startedAtNanos;
        private int count = 1;

        private Window(long startedAtNanos) {
            this.startedAtNanos = startedAtNanos;
        }
    }
}
