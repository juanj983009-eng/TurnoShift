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

@DisplayName("Unit: InMemoryLoginRateLimiter — limitador de tasa por IP en memoria")
class InMemoryLoginRateLimiterTest {

    private MutableClock clock;
    private InMemoryLoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-15T12:00:00Z"));
        // Límite: 5 intentos por ventana de 60 segundos
        rateLimiter = new InMemoryLoginRateLimiter(5, Duration.ofSeconds(60), clock);
    }

    @Test
    @DisplayName("Permite solicitudes dentro del límite y decrementa intentos restantes")
    void permiteSolicitudesDentroDelLimite() {
        String ip = "192.168.1.100";

        for (int i = 1; i <= 5; i++) {
            LoginRateLimiter.RateLimitResult result = rateLimiter.tryAcquire(ip);
            assertThat(result).isInstanceOf(LoginRateLimiter.RateLimitResult.Allowed.class);
            LoginRateLimiter.RateLimitResult.Allowed allowed = (LoginRateLimiter.RateLimitResult.Allowed) result;
            assertThat(allowed.remainingAttempts()).isEqualTo(5 - i);
        }
    }

    @Test
    @DisplayName("Rechaza solicitud al superar el límite y devuelve Retry-After positivo")
    void rechazaSolicitudAlSuperarLimite() {
        String ip = "192.168.1.100";

        // Consumir los 5 intentos permitidos
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryAcquire(ip)).isInstanceOf(LoginRateLimiter.RateLimitResult.Allowed.class);
        }

        // Intento 6 debe ser rechazado
        LoginRateLimiter.RateLimitResult result = rateLimiter.tryAcquire(ip);
        assertThat(result).isInstanceOf(LoginRateLimiter.RateLimitResult.Rejected.class);

        LoginRateLimiter.RateLimitResult.Rejected rejected = (LoginRateLimiter.RateLimitResult.Rejected) result;
        assertThat(rejected.retryAfterSeconds()).isGreaterThan(0);
        assertThat(rejected.retryAfterSeconds()).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("Permite solicitudes nuevamente tras expirar la ventana de tiempo")
    void permiteNuevamenteTrasExpirarVentana() {
        String ip = "10.0.0.1";

        // Consumir los 5 intentos
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(ip);
        }
        assertThat(rateLimiter.tryAcquire(ip)).isInstanceOf(LoginRateLimiter.RateLimitResult.Rejected.class);

        // Avanzar el reloj 61 segundos (ventana expirada)
        clock.advance(Duration.ofSeconds(61));

        LoginRateLimiter.RateLimitResult result = rateLimiter.tryAcquire(ip);
        assertThat(result).isInstanceOf(LoginRateLimiter.RateLimitResult.Allowed.class);
        LoginRateLimiter.RateLimitResult.Allowed allowed = (LoginRateLimiter.RateLimitResult.Allowed) result;
        assertThat(allowed.remainingAttempts()).isEqualTo(4);
    }

    @Test
    @DisplayName("Diferentes IPs tienen contadores independientes")
    void diferentesIpsSonIndependientes() {
        String ipA = "192.168.1.1";
        String ipB = "192.168.1.2";

        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(ipA);
        }
        assertThat(rateLimiter.tryAcquire(ipA)).isInstanceOf(LoginRateLimiter.RateLimitResult.Rejected.class);

        // IP B no debe estar bloqueada
        LoginRateLimiter.RateLimitResult resultB = rateLimiter.tryAcquire(ipB);
        assertThat(resultB).isInstanceOf(LoginRateLimiter.RateLimitResult.Allowed.class);
    }

    @Test
    @DisplayName("Reset borra el historial de la IP y permite nuevos intentos de inmediato")
    void resetPermiteNuevosIntentos() {
        String ip = "172.16.0.5";

        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(ip);
        }
        assertThat(rateLimiter.tryAcquire(ip)).isInstanceOf(LoginRateLimiter.RateLimitResult.Rejected.class);

        rateLimiter.reset(ip);

        LoginRateLimiter.RateLimitResult result = rateLimiter.tryAcquire(ip);
        assertThat(result).isInstanceOf(LoginRateLimiter.RateLimitResult.Allowed.class);
        assertThat(((LoginRateLimiter.RateLimitResult.Allowed) result).remainingAttempts()).isEqualTo(4);
    }

    @Test
    @DisplayName("Rechaza IP nula o en blanco con IllegalArgumentException")
    void validaIpObligatoria() {
        assertThatThrownBy(() -> rateLimiter.tryAcquire(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rateLimiter.tryAcquire("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Helper clock para pruebas deterministas
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

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
