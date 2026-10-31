package com.turnoshift.operator.infrastructure.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementación en memoria del limitador de tasa de login para operadores de plataforma.
 *
 * <p>Utiliza una ventana fija con expiración determinada por un {@link Clock} inyectable
 * para garantizar pruebas deterministas.
 *
 * <p>Por defecto: 5 intentos por ventana de 60 segundos.
 */
@Component
public class InMemoryOperatorLoginRateLimiter implements OperatorLoginRateLimiter {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final Duration DEFAULT_WINDOW_DURATION = Duration.ofSeconds(60);

    private final int maxAttempts;
    private final Duration windowDuration;
    private final Clock clock;
    private final ConcurrentMap<String, IpAttemptTracker> attemptsByIp = new ConcurrentHashMap<>();

    public InMemoryOperatorLoginRateLimiter() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW_DURATION, Clock.systemUTC());
    }

    public InMemoryOperatorLoginRateLimiter(int maxAttempts, Duration windowDuration, Clock clock) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts debe ser mayor a 0");
        }
        this.maxAttempts = maxAttempts;
        this.windowDuration = Objects.requireNonNull(windowDuration, "windowDuration no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    @Override
    public RateLimitResult tryAcquire(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("ipAddress no puede ser nula ni vacía");
        }

        long now = clock.instant().toEpochMilli();
        long windowDurationMillis = windowDuration.toMillis();
        AtomicBoolean rejected = new AtomicBoolean(false);

        IpAttemptTracker tracker = attemptsByIp.compute(ipAddress, (ip, current) -> {
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

    @Override
    public void reset(String ipAddress) {
        if (ipAddress != null && !ipAddress.isBlank()) {
            attemptsByIp.remove(ipAddress);
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
