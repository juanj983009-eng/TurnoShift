package com.turnoshift.identity.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: InMemoryPasswordResetRateLimiter — limitador de tasa por IP para password reset")
class InMemoryPasswordResetRateLimiterTest {

    private MutableClock clock;
    private InMemoryPasswordResetRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-15T12:00:00Z"));
        // Límites: 10 inspect, 5 complete por ventana de 60s
        rateLimiter = new InMemoryPasswordResetRateLimiter(10, 5, Duration.ofSeconds(60), clock);
    }

    @Test
    @DisplayName("Inspect: permite hasta 10 intentos y decrementa restantes")
    void inspect_permiteHastaDiezIntentos() {
        String ip = "192.168.1.50";

        for (int i = 1; i <= 10; i++) {
            PasswordResetRateLimiter.RateLimitResult result = rateLimiter.tryAcquireInspect(ip);
            assertThat(result).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Allowed.class);
            PasswordResetRateLimiter.RateLimitResult.Allowed allowed = (PasswordResetRateLimiter.RateLimitResult.Allowed) result;
            assertThat(allowed.remainingAttempts()).isEqualTo(10 - i);
        }

        // Intento 11 debe ser rechazado
        PasswordResetRateLimiter.RateLimitResult rejected = rateLimiter.tryAcquireInspect(ip);
        assertThat(rejected).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Rejected.class);
        PasswordResetRateLimiter.RateLimitResult.Rejected rej = (PasswordResetRateLimiter.RateLimitResult.Rejected) rejected;
        assertThat(rej.retryAfterSeconds()).isGreaterThan(0).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("Complete: permite hasta 5 intentos y decrementa restantes")
    void complete_permiteHastaCincoIntentos() {
        String ip = "192.168.1.50";

        for (int i = 1; i <= 5; i++) {
            PasswordResetRateLimiter.RateLimitResult result = rateLimiter.tryAcquireComplete(ip);
            assertThat(result).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Allowed.class);
            PasswordResetRateLimiter.RateLimitResult.Allowed allowed = (PasswordResetRateLimiter.RateLimitResult.Allowed) result;
            assertThat(allowed.remainingAttempts()).isEqualTo(5 - i);
        }

        // Intento 6 debe ser rechazado
        PasswordResetRateLimiter.RateLimitResult rejected = rateLimiter.tryAcquireComplete(ip);
        assertThat(rejected).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Rejected.class);
        PasswordResetRateLimiter.RateLimitResult.Rejected rej = (PasswordResetRateLimiter.RateLimitResult.Rejected) rejected;
        assertThat(rej.retryAfterSeconds()).isGreaterThan(0).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("Inspect y Complete tienen cuotas separadas e independientes para la misma IP")
    void inspectYCompleteSonIndependientes() {
        String ip = "10.0.0.1";

        // Consumir todos los intentos de complete (5)
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquireComplete(ip);
        }
        assertThat(rateLimiter.tryAcquireComplete(ip)).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Rejected.class);

        // Inspect debe seguir disponible para esa misma IP
        PasswordResetRateLimiter.RateLimitResult inspectResult = rateLimiter.tryAcquireInspect(ip);
        assertThat(inspectResult).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Allowed.class);
        assertThat(((PasswordResetRateLimiter.RateLimitResult.Allowed) inspectResult).remainingAttempts()).isEqualTo(9);
    }

    @Test
    @DisplayName("Diferentes IPs tienen cuotas independientes")
    void diferentesIpsSonIndependientes() {
        String ipA = "192.168.1.1";
        String ipB = "192.168.1.2";

        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquireComplete(ipA);
        }
        assertThat(rateLimiter.tryAcquireComplete(ipA)).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Rejected.class);

        // IP B sigue con cuota intacta
        PasswordResetRateLimiter.RateLimitResult resultB = rateLimiter.tryAcquireComplete(ipB);
        assertThat(resultB).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Allowed.class);
        assertThat(((PasswordResetRateLimiter.RateLimitResult.Allowed) resultB).remainingAttempts()).isEqualTo(4);
    }

    @Test
    @DisplayName("resetComplete limpia el contador de la IP para complete pero no altera inspect")
    void resetComplete_limpiaSoloComplete() {
        String ip = "172.16.0.5";

        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquireComplete(ip);
        }
        assertThat(rateLimiter.tryAcquireComplete(ip)).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Rejected.class);

        rateLimiter.resetComplete(ip);

        PasswordResetRateLimiter.RateLimitResult result = rateLimiter.tryAcquireComplete(ip);
        assertThat(result).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Allowed.class);
        assertThat(((PasswordResetRateLimiter.RateLimitResult.Allowed) result).remainingAttempts()).isEqualTo(4);
    }

    @Test
    @DisplayName("Permite nuevamente tras expirar la ventana de tiempo")
    void permiteNuevamenteTrasExpirarVentana() {
        String ip = "10.0.0.2";

        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquireComplete(ip);
        }
        assertThat(rateLimiter.tryAcquireComplete(ip)).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Rejected.class);

        // Avanzar 61 segundos
        clock.advance(Duration.ofSeconds(61));

        PasswordResetRateLimiter.RateLimitResult result = rateLimiter.tryAcquireComplete(ip);
        assertThat(result).isInstanceOf(PasswordResetRateLimiter.RateLimitResult.Allowed.class);
        assertThat(((PasswordResetRateLimiter.RateLimitResult.Allowed) result).remainingAttempts()).isEqualTo(4);
    }

    @Test
    @DisplayName("Valida IP obligatoria y parámetros de constructor")
    void validaIpObligatoria() {
        assertThatThrownBy(() -> rateLimiter.tryAcquireInspect(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rateLimiter.tryAcquireInspect("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rateLimiter.tryAcquireComplete(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rateLimiter.tryAcquireComplete("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new InMemoryPasswordResetRateLimiter(0, 5, Duration.ofSeconds(60), clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryPasswordResetRateLimiter(10, 0, Duration.ofSeconds(60), clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant) {
            this.instant = instant;
            this.zone = ZoneId.of("UTC");
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
