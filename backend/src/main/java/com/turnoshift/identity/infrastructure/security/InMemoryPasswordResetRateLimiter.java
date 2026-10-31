package com.turnoshift.identity.infrastructure.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementación en memoria del limitador de tasa para inspección y completado de configuración de contraseñas.
 *
 * <p>Utiliza ventanas fijas con expiración evaluada mediante un {@link Clock} inyectable
 * para garantizar pruebas 100% deterministas.
 *
 * <p>Separa estrictamente las cuotas entre operaciones de inspección (10 req/60s)
 * y operaciones de completado (5 req/60s).
 */
@Component
public class InMemoryPasswordResetRateLimiter implements PasswordResetRateLimiter {

    public static final int DEFAULT_INSPECT_MAX_ATTEMPTS = 10;
    public static final int DEFAULT_COMPLETE_MAX_ATTEMPTS = 5;
    public static final Duration DEFAULT_WINDOW_DURATION = Duration.ofSeconds(60);

    private final int inspectMaxAttempts;
    private final int completeMaxAttempts;
    private final Duration windowDuration;
    private final Clock clock;
    private final ConcurrentMap<String, IpAttemptTracker> attempts = new ConcurrentHashMap<>();

    public InMemoryPasswordResetRateLimiter() {
        this(DEFAULT_INSPECT_MAX_ATTEMPTS, DEFAULT_COMPLETE_MAX_ATTEMPTS, DEFAULT_WINDOW_DURATION, Clock.systemUTC());
    }

    public InMemoryPasswordResetRateLimiter(
            int inspectMaxAttempts,
            int completeMaxAttempts,
            Duration windowDuration,
            Clock clock) {
        if (inspectMaxAttempts <= 0) {
            throw new IllegalArgumentException("inspectMaxAttempts debe ser mayor a 0");
        }
        if (completeMaxAttempts <= 0) {
            throw new IllegalArgumentException("completeMaxAttempts debe ser mayor a 0");
        }
        this.inspectMaxAttempts = inspectMaxAttempts;
        this.completeMaxAttempts = completeMaxAttempts;
        this.windowDuration = Objects.requireNonNull(windowDuration, "windowDuration no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    @Override
    public RateLimitResult tryAcquireInspect(String ipAddress) {
        validateIp(ipAddress);
        return tryAcquireKey("INSPECT:" + ipAddress, inspectMaxAttempts);
    }

    @Override
    public RateLimitResult tryAcquireComplete(String ipAddress) {
        validateIp(ipAddress);
        return tryAcquireKey("COMPLETE:" + ipAddress, completeMaxAttempts);
    }

    @Override
    public void resetComplete(String ipAddress) {
        if (ipAddress != null && !ipAddress.isBlank()) {
            attempts.remove("COMPLETE:" + ipAddress);
        }
    }

    @Override
    public void resetInspect(String ipAddress) {
        if (ipAddress != null && !ipAddress.isBlank()) {
            attempts.remove("INSPECT:" + ipAddress);
        }
    }

    private RateLimitResult tryAcquireKey(String key, int maxAttempts) {
        long now = clock.instant().toEpochMilli();
        long windowDurationMillis = windowDuration.toMillis();
        AtomicBoolean rejected = new AtomicBoolean(false);

        IpAttemptTracker tracker = attempts.compute(key, (k, current) -> {
            if (current == null || (now - current.windowStartMillis) >= windowDurationMillis) {
                return new IpAttemptTracker(now, 1);
            }
            if (current.attemptCount < maxAttempts) {
                return new IpAttemptTracker(current.windowStartMillis, current.attemptCount + 1);
            }
            rejected.set(true);
            return current;
        });

        if (rejected.get()) {
            long elapsed = now - tracker.windowStartMillis;
            long remainingMillis = Math.max(0, windowDurationMillis - elapsed);
            long retryAfterSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
            return new RateLimitResult.Rejected(retryAfterSeconds);
        }

        int remaining = Math.max(0, maxAttempts - tracker.attemptCount);
        return new RateLimitResult.Allowed(remaining);
    }

    private void validateIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("ipAddress no puede ser nula ni vacía");
        }
    }

    private static class IpAttemptTracker {
        final long windowStartMillis;
        final int attemptCount;

        IpAttemptTracker(long windowStartMillis, int attemptCount) {
            this.windowStartMillis = windowStartMillis;
            this.attemptCount = attemptCount;
        }
    }
}
