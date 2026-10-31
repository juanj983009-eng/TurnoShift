package com.turnoshift.operator.application;

import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.PlatformOperator;
import com.turnoshift.operator.domain.PlatformOperatorIdentity;
import com.turnoshift.operator.domain.PlatformOperatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("Unit: RevalidateOperatorSession — revalidación de sesión de operador de plataforma")
class RevalidateOperatorSessionTest {

    private PlatformOperatorRepository repository;
    private MutableClock clock;
    private RevalidateOperatorSession revalidator;

    private final UUID operatorId = UUID.randomUUID();
    private final PlatformOperatorIdentity identity = new PlatformOperatorIdentity(operatorId, "Operador Test", 1L);
    private final Instant baseTime = Instant.parse("2026-09-16T10:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(PlatformOperatorRepository.class);
        clock = new MutableClock(baseTime);
        revalidator = new RevalidateOperatorSession(repository, clock);
    }

    private PlatformOperator createOperator(boolean active, long authVersion) {
        return new PlatformOperator(
                operatorId,
                "op@turnoshift.example",
                "op@turnoshift.example",
                "Operador Test",
                "$argon2id$v=19$m=65536,t=3,p=1$fakeHash",
                active,
                authVersion,
                0L,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    @Test
    @DisplayName("Identidad nula retorna INVALID")
    void identidadNula_retornaInvalid() {
        OperatorRevalidationOutcome outcome = revalidator.execute(null, baseTime.toEpochMilli());
        assertThat(outcome).isEqualTo(OperatorRevalidationOutcome.INVALID);
    }

    @Test
    @DisplayName("authenticatedAt ausente o no numérico retorna EXPIRED")
    void authenticatedAtAusente_retornaExpired() {
        assertThat(revalidator.execute(identity, null))
                .isEqualTo(OperatorRevalidationOutcome.EXPIRED);
        assertThat(revalidator.execute(identity, "no-es-numero"))
                .isEqualTo(OperatorRevalidationOutcome.EXPIRED);
    }

    @Test
    @DisplayName("authenticatedAt en el futuro más allá de tolerancia retorna EXPIRED")
    void authenticatedAtFuturo_retornaExpired() {
        long futureMillis = baseTime.plus(Duration.ofSeconds(65)).toEpochMilli();
        assertThat(revalidator.execute(identity, futureMillis))
                .isEqualTo(OperatorRevalidationOutcome.EXPIRED);
    }

    @Test
    @DisplayName("authenticatedAt transcurridas 8 horas o más retorna EXPIRED")
    void authenticatedAtSupera8Horas_retornaExpired() {
        long pastMillis = baseTime.minus(Duration.ofHours(8)).toEpochMilli();
        assertThat(revalidator.execute(identity, pastMillis))
                .isEqualTo(OperatorRevalidationOutcome.EXPIRED);
    }

    @Test
    @DisplayName("Operador no encontrado en base de datos retorna INVALID")
    void operadorNoEncontrado_retornaInvalid() {
        when(repository.findById(operatorId)).thenReturn(Optional.empty());

        OperatorRevalidationOutcome outcome = revalidator.execute(identity, baseTime.toEpochMilli());
        assertThat(outcome).isEqualTo(OperatorRevalidationOutcome.INVALID);
    }

    @Test
    @DisplayName("Operador inactivo en base de datos retorna INVALID")
    void operadorInactivo_retornaInvalid() {
        when(repository.findById(operatorId)).thenReturn(Optional.of(createOperator(false, 1L)));

        OperatorRevalidationOutcome outcome = revalidator.execute(identity, baseTime.toEpochMilli());
        assertThat(outcome).isEqualTo(OperatorRevalidationOutcome.INVALID);
    }

    @Test
    @DisplayName("auth_version alterada en base de datos retorna INVALID")
    void authVersionAlterada_retornaInvalid() {
        when(repository.findById(operatorId)).thenReturn(Optional.of(createOperator(true, 2L)));

        OperatorRevalidationOutcome outcome = revalidator.execute(identity, baseTime.toEpochMilli());
        assertThat(outcome).isEqualTo(OperatorRevalidationOutcome.INVALID);
    }

    @Test
    @DisplayName("Error técnico de base de datos propaga DATA_ACCESS_ERROR sin invalidar")
    void errorDePersistencia_retornaDataAccessError() {
        when(repository.findById(operatorId)).thenThrow(new OperatorDataAccessException());

        OperatorRevalidationOutcome outcome = revalidator.execute(identity, baseTime.toEpochMilli());
        assertThat(outcome).isEqualTo(OperatorRevalidationOutcome.DATA_ACCESS_ERROR);
    }

    @Test
    @DisplayName("Operador activo con versión idéntica y dentro de las 8 horas retorna VALID")
    void operadorValido_retornaValid() {
        when(repository.findById(operatorId)).thenReturn(Optional.of(createOperator(true, 1L)));

        OperatorRevalidationOutcome outcome = revalidator.execute(identity, baseTime.minus(Duration.ofMinutes(10)).toEpochMilli());
        assertThat(outcome).isEqualTo(OperatorRevalidationOutcome.VALID);
    }

    private static class MutableClock extends Clock {
        private Instant currentInstant;
        private final ZoneId zone = ZoneId.of("UTC");

        MutableClock(Instant initialInstant) {
            this.currentInstant = initialInstant;
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return currentInstant; }
    }
}
