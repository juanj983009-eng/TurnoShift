package com.turnoshift.security.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: BusinessIdempotentOperationExecutor")
class BusinessIdempotentOperationExecutorTest {

    @Mock
    private BusinessIdempotencyRepository repository;

    private Clock clock;
    private Instant fixedInstant;
    private BusinessIdempotentOperationExecutor executor;

    private final UUID orgId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID idempotencyKey = UUID.fromString("33333333-3333-3333-3333-333333333333");

    record TestResponse(String message, int code) {}

    static class TestCommand implements BusinessIdempotentCommand<TestResponse> {
        private final UUID orgId;
        private final UUID userId;
        private final UUID key;
        private final String payload;

        TestCommand(UUID orgId, UUID userId, UUID key, String payload) {
            this.orgId = orgId;
            this.userId = userId;
            this.key = key;
            this.payload = payload;
        }

        @Override
        public UUID organizationId() { return orgId; }

        @Override
        public UUID actorUserId() { return userId; }

        @Override
        public UUID idempotencyKey() { return key; }

        @Override
        public String method() { return "POST"; }

        @Override
        public String path() { return "/api/v1/positions"; }

        @Override
        public String schemaVersion() { return "1.0"; }

        @Override
        public String canonicalPayload() {
            return payload;
        }

        @Override
        public int httpStatus(TestResponse response) {
            return 201;
        }

        @Override
        public String sanitizeResponseBody(TestResponse response) {
            return "{\"message\":\"" + response.message() + "\",\"code\":" + response.code() + "}";
        }

        @Override
        public TestResponse deserializeResponseBody(String responseBody) {
            return new TestResponse("Replayed: " + responseBody, 201);
        }
    }

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-09-16T12:00:00Z");
        clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        executor = new BusinessIdempotentOperationExecutor(repository, clock);
    }

    @Test
    @DisplayName("Clave nueva: ejecuta efecto una vez y persiste operación COMPLETED/SUCCEEDED")
    void claveNueva_ejecutaEfectoYPersisteOperacionTerminal() {
        TestCommand cmd = new TestCommand(orgId, userId, idempotencyKey, "MED:Médico");
        when(repository.findOperation(orgId, userId, idempotencyKey)).thenReturn(Optional.empty());

        AtomicInteger efectoEjecutado = new AtomicInteger(0);
        TestResponse expectedResponse = new TestResponse("Creado", 201);

        BusinessIdempotencyResult<TestResponse> result = executor.execute(cmd, () -> {
            efectoEjecutado.incrementAndGet();
            return expectedResponse;
        });

        assertThat(efectoEjecutado.get()).isEqualTo(1);
        assertThat(result).isInstanceOf(BusinessIdempotencyResult.Proceed.class);
        BusinessIdempotencyResult.Proceed<TestResponse> proceed = (BusinessIdempotencyResult.Proceed<TestResponse>) result;
        assertThat(proceed.response()).isEqualTo(expectedResponse);

        // Verifica adquisición de lock determinista de SQL Server
        String expectedLock = "TurnoShift:Idem:Org:" + orgId + ":User:" + userId + ":Key:" + idempotencyKey;
        verify(repository).acquireLock(expectedLock, 10000);

        // Verifica persistencia con TTL de 24h exactas y huella válida
        ArgumentCaptor<Instant> expiresCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).saveCompletedOperation(
                eq(orgId),
                eq(userId),
                eq(idempotencyKey),
                eq("POST"),
                eq("/api/v1/positions"),
                eq(cmd.calculateFingerprint()),
                eq("1.0"),
                eq("SUCCEEDED"),
                eq(201),
                eq("application/json"),
                eq("{\"message\":\"Creado\",\"code\":201}"),
                eq(fixedInstant),
                expiresCaptor.capture()
        );

        assertThat(expiresCaptor.getValue()).isEqualTo(fixedInstant.plus(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("Misma clave y misma huella vigente: retorna Replay sin re-ejecutar efecto")
    void mismaClaveMismaHuellaVigente_retornaReplaySinEjecutarEfecto() {
        TestCommand cmd = new TestCommand(orgId, userId, idempotencyKey, "MED:Médico");
        byte[] fingerprint = cmd.calculateFingerprint();

        BusinessApiOperationRecord record = new BusinessApiOperationRecord(
                UUID.randomUUID(),
                orgId,
                userId,
                idempotencyKey,
                fingerprint,
                "COMPLETED",
                "SUCCEEDED",
                201,
                "application/json",
                "{\"message\":\"CreadoOriginal\",\"code\":201}",
                fixedInstant.minus(Duration.ofHours(1)),
                fixedInstant.minus(Duration.ofHours(1)),
                fixedInstant.plus(Duration.ofHours(23))
        );

        when(repository.findOperation(orgId, userId, idempotencyKey)).thenReturn(Optional.of(record));

        AtomicInteger efectoEjecutado = new AtomicInteger(0);

        BusinessIdempotencyResult<TestResponse> result = executor.execute(cmd, () -> {
            efectoEjecutado.incrementAndGet();
            return new TestResponse("NoDeberiaEjecutarse", 999);
        });

        assertThat(efectoEjecutado.get()).isEqualTo(0);
        assertThat(result).isInstanceOf(BusinessIdempotencyResult.Replay.class);
        BusinessIdempotencyResult.Replay<TestResponse> replay = (BusinessIdempotencyResult.Replay<TestResponse>) result;
        assertThat(replay.response().message()).contains("CreadoOriginal");

        verify(repository, never()).saveCompletedOperation(
                any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Misma clave con huella diferente: retorna KeyReused sin ejecutar efecto")
    void mismaClaveHuellaDiferente_retornaKeyReusedSinEjecutarEfecto() {
        TestCommand cmd1 = new TestCommand(orgId, userId, idempotencyKey, "MED:Médico");
        TestCommand cmd2 = new TestCommand(orgId, userId, idempotencyKey, "ENF:Enfermero"); // huella distinta

        BusinessApiOperationRecord record = new BusinessApiOperationRecord(
                UUID.randomUUID(),
                orgId,
                userId,
                idempotencyKey,
                cmd1.calculateFingerprint(),
                "COMPLETED",
                "SUCCEEDED",
                201,
                "application/json",
                "{}",
                fixedInstant.minus(Duration.ofHours(1)),
                fixedInstant.minus(Duration.ofHours(1)),
                fixedInstant.plus(Duration.ofHours(23))
        );

        when(repository.findOperation(orgId, userId, idempotencyKey)).thenReturn(Optional.of(record));

        AtomicInteger efectoEjecutado = new AtomicInteger(0);

        BusinessIdempotencyResult<TestResponse> result = executor.execute(cmd2, () -> {
            efectoEjecutado.incrementAndGet();
            return new TestResponse("NoDeberia", 999);
        });

        assertThat(efectoEjecutado.get()).isEqualTo(0);
        assertThat(result).isInstanceOf(BusinessIdempotencyResult.KeyReused.class);

        verify(repository, never()).saveCompletedOperation(
                any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Clave expirada (> 24h): retorna Expired sin ejecutar efecto")
    void claveExpirada_retornaExpiredSinEjecutarEfecto() {
        TestCommand cmd = new TestCommand(orgId, userId, idempotencyKey, "MED:Médico");

        BusinessApiOperationRecord record = new BusinessApiOperationRecord(
                UUID.randomUUID(),
                orgId,
                userId,
                idempotencyKey,
                cmd.calculateFingerprint(),
                "COMPLETED",
                "SUCCEEDED",
                201,
                "application/json",
                "{}",
                fixedInstant.minus(Duration.ofHours(25)),
                fixedInstant.minus(Duration.ofHours(25)),
                fixedInstant.minus(Duration.ofMinutes(1)) // expirada
        );

        when(repository.findOperation(orgId, userId, idempotencyKey)).thenReturn(Optional.of(record));

        AtomicInteger efectoEjecutado = new AtomicInteger(0);

        BusinessIdempotencyResult<TestResponse> result = executor.execute(cmd, () -> {
            efectoEjecutado.incrementAndGet();
            return new TestResponse("NoDeberia", 999);
        });

        assertThat(efectoEjecutado.get()).isEqualTo(0);
        assertThat(result).isInstanceOf(BusinessIdempotencyResult.Expired.class);

        verify(repository, never()).saveCompletedOperation(
                any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Fallo en efecto de negocio: no persiste operación y propaga excepción")
    void falloEnEfectoDeNegocio_noPersisteOperacionYPropagaExcepcion() {
        TestCommand cmd = new TestCommand(orgId, userId, idempotencyKey, "MED:Médico");
        when(repository.findOperation(orgId, userId, idempotencyKey)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executor.execute(cmd, () -> {
            throw new IllegalStateException("Error de negocio simulado");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Error de negocio simulado");

        verify(repository, never()).saveCompletedOperation(
                any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Fallo al adquirir lock: propaga excepción de acceso a datos sin filtrar SQL")
    void falloAlAdquirirLock_propagaExcepcionSegura() {
        TestCommand cmd = new TestCommand(orgId, userId, idempotencyKey, "MED:Médico");
        org.mockito.Mockito.doThrow(new BusinessIdempotencyDataAccessException())
                .when(repository).acquireLock(anyString(), anyInt());

        assertThatThrownBy(() -> executor.execute(cmd, () -> new TestResponse("OK", 200)))
                .isInstanceOf(BusinessIdempotencyDataAccessException.class)
                .hasMessage("Error de persistencia en la operación de idempotencia empresarial");
    }
}
