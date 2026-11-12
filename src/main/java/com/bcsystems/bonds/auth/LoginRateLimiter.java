package com.bcsystems.bonds.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;

    public LoginRateLimiter(@Value("${app.security.login.max-attempts:5}") int maxAttempts,
                            @Value("${app.security.login.window-seconds:900}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public boolean isAllowed(String key) {
        AttemptWindow current = attempts.get(key);
        if (current == null || current.expired(window)) { attempts.remove(key, current); return true; }
        boolean allowed = current.failures < maxAttempts;
        if (!allowed) log.warn("Intentos de login bloqueados temporalmente para {}", key);
        return allowed;
    }

    public void recordFailure(String key) {
        attempts.compute(key, (ignored, current) -> current == null || current.expired(window)
                ? new AttemptWindow(1, Instant.now()) : new AttemptWindow(current.failures + 1, current.startedAt));
    }

    public void clear(String key) { attempts.remove(key); }

    private record AttemptWindow(int failures, Instant startedAt) {
        boolean expired(Duration duration) { return startedAt.plus(duration).isBefore(Instant.now()); }
    }
}
