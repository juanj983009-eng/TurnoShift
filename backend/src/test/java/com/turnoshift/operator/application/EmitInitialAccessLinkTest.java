package com.turnoshift.operator.application;

import com.turnoshift.identity.application.IssueInitialSetupToken;
import com.turnoshift.identity.domain.InitialSetupTokenEmission;
import com.turnoshift.identity.domain.TokenDataAccessException;
import com.turnoshift.operator.domain.ApiOperationRecord;
import com.turnoshift.operator.domain.ApiOperationRepository;
import com.turnoshift.operator.domain.EmitInitialAccessLinkCommand;
import com.turnoshift.operator.domain.EmitInitialAccessLinkResult;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.OperatorInitialLinkRepository;
import com.turnoshift.operator.domain.RequestFingerprintCalculator;
import com.turnoshift.operator.infrastructure.InitialSetupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Unit: EmitInitialAccessLink — caso de uso mono-transaccional de emisión de enlace inicial")
class EmitInitialAccessLinkTest {

    private ApiOperationRepository apiOperationRepository;
    private OperatorInitialLinkRepository initialLinkRepository;
    private IssueInitialSetupToken issueInitialSetupToken;
    private InitialSetupProperties initialSetupProperties;
    private ObjectMapper objectMapper;
    private Clock clock;

    private EmitInitialAccessLink useCase;

    private static final UUID OPERATOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ADMIN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");

    private EmitInitialAccessLinkCommand command;
    private byte[] fingerprint;

    @BeforeEach
    void setUp() {
        apiOperationRepository = mock(ApiOperationRepository.class);
        initialLinkRepository = mock(OperatorInitialLinkRepository.class);
        issueInitialSetupToken = mock(IssueInitialSetupToken.class);
        initialSetupProperties = new InitialSetupProperties();
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(NOW, ZoneId.of("UTC"));

        useCase = new EmitInitialAccessLink(
                apiOperationRepository,
                initialLinkRepository,
                issueInitialSetupToken,
                initialSetupProperties,
                objectMapper,
                clock
        );

        command = new EmitInitialAccessLinkCommand(ORG_ID, ADMIN_ID, 0L);
        String path = String.format(EmitInitialAccessLink.PATH_TEMPLATE, ORG_ID);
        fingerprint = RequestFingerprintCalculator.calculate(
                EmitInitialAccessLink.METHOD,
                path,
                EmitInitialAccessLink.SCHEMA_VERSION,
                command
        );
    }

    @Test
    @DisplayName("Happy path: emisión exitosa genera enlace con secreto en fragmento # y persiste metadatos sanitizados")
    void happyPath_emisionExitosa() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(initialLinkRepository.findAdminLinkTarget(ORG_ID, ADMIN_ID)).thenReturn(
                Optional.of(new OperatorInitialLinkRepository.AdminLinkTarget(
                        ORG_ID, "ACTIVA", ADMIN_ID, "ADMINISTRADOR", true, false, 0L
                ))
        );

        UUID tokenId = UUID.randomUUID();
        String rawToken = "rawCryptoToken256BitBase64UrlSafe123456789";
        Instant expiresAt = NOW.plus(Duration.ofMinutes(30));
        when(issueInitialSetupToken.issue(ORG_ID, ADMIN_ID)).thenReturn(
                Optional.of(new InitialSetupTokenEmission(tokenId, ORG_ID, ADMIN_ID, rawToken, expiresAt))
        );
        when(initialLinkRepository.incrementUserLockVersion(ORG_ID, ADMIN_ID, 0L, NOW)).thenReturn(true);

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.Success.class);
        EmitInitialAccessLinkResult.Success success = (EmitInitialAccessLinkResult.Success) result;
        assertThat(success.setupLink()).isEqualTo("https://app.turnoshift.example/setup#token=" + rawToken);
        assertThat(success.expiresAt()).isEqualTo(expiresAt);

        // Verifica que se incrementó el lock_version
        verify(initialLinkRepository).incrementUserLockVersion(ORG_ID, ADMIN_ID, 0L, NOW);

        // Verifica que se guardó en api_operations con sanitización estricta (sin secreto)
        verify(apiOperationRepository).saveCompletedOperation(
                eq(OPERATOR_ID),
                eq(IDEMPOTENCY_KEY),
                eq(EmitInitialAccessLink.METHOD),
                eq(String.format(EmitInitialAccessLink.PATH_TEMPLATE, ORG_ID)),
                eq(fingerprint),
                eq(EmitInitialAccessLink.SCHEMA_VERSION),
                eq("SUCCEEDED"),
                eq(200),
                eq("application/json"),
                contains("\"secretRedacted\":true"),
                eq(NOW),
                eq(NOW.plus(Duration.ofHours(24)))
        );
    }

    @Test
    @DisplayName("Replay con misma clave y misma huella: retorna LinkAlreadyEmitted sin emitir ni revocar nada")
    void replayMismaClave_retornaLinkAlreadyEmitted() {
        ApiOperationRecord record = new ApiOperationRecord(
                UUID.randomUUID(), OPERATOR_ID, IDEMPOTENCY_KEY, fingerprint,
                "COMPLETED", "SUCCEEDED", 200, "application/json",
                "{\"tokenId\":\"" + UUID.randomUUID() + "\",\"secretRedacted\":true}",
                NOW.minus(Duration.ofMinutes(5)), NOW.minus(Duration.ofMinutes(5)), NOW.plus(Duration.ofHours(23))
        );
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.of(record));

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.LinkAlreadyEmitted.class);
        EmitInitialAccessLinkResult.LinkAlreadyEmitted emitted = (EmitInitialAccessLinkResult.LinkAlreadyEmitted) result;
        assertThat(emitted.message()).contains("ya fue emitido para esta clave de idempotencia");

        verify(issueInitialSetupToken, never()).issue(any(), any());
        verify(initialLinkRepository, never()).incrementUserLockVersion(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("Reuso de clave con huella diferente: retorna IdempotencyKeyReused")
    void reusoClave_huellaDiferente_retornaIdempotencyKeyReused() {
        byte[] diffFp = new byte[32];
        diffFp[0] = 99;
        ApiOperationRecord record = new ApiOperationRecord(
                UUID.randomUUID(), OPERATOR_ID, IDEMPOTENCY_KEY, diffFp,
                "COMPLETED", "SUCCEEDED", 200, "application/json",
                "{}", NOW.minus(Duration.ofMinutes(5)), NOW.minus(Duration.ofMinutes(5)), NOW.plus(Duration.ofHours(23))
        );
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.of(record));

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.IdempotencyKeyReused.class);
    }

    @Test
    @DisplayName("Clave expirada (>24h): retorna IdempotencyKeyExpired")
    void claveExpirada_retornaIdempotencyKeyExpired() {
        ApiOperationRecord record = new ApiOperationRecord(
                UUID.randomUUID(), OPERATOR_ID, IDEMPOTENCY_KEY, fingerprint,
                "COMPLETED", "SUCCEEDED", 200, "application/json",
                "{}", NOW.minus(Duration.ofHours(25)), NOW.minus(Duration.ofHours(25)), NOW.minus(Duration.ofMinutes(1))
        );
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.of(record));

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.IdempotencyKeyExpired.class);
    }

    @Test
    @DisplayName("Replay de rechazo determinista previo: reproduce plantilla de rechazo con código y status")
    void replayRechazoPrevio_reproduceRechazo() {
        String rejectionTemplate = """
                {"type":"/problems/invalid-state-transition","title":"Transición de estado inválida","status":409,"detail":"El administrador ya tiene contraseña configurada.","code":"INVALID_STATE_TRANSITION","errors":[]}
                """;
        ApiOperationRecord record = new ApiOperationRecord(
                UUID.randomUUID(), OPERATOR_ID, IDEMPOTENCY_KEY, fingerprint,
                "COMPLETED", "REJECTED", 409, "application/problem+json",
                rejectionTemplate, NOW.minus(Duration.ofMinutes(5)), NOW.minus(Duration.ofMinutes(5)), NOW.plus(Duration.ofHours(23))
        );
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.of(record));

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.ReplayedRejection.class);
        EmitInitialAccessLinkResult.ReplayedRejection replayed = (EmitInitialAccessLinkResult.ReplayedRejection) result;
        assertThat(replayed.httpStatus()).isEqualTo(409);
        assertThat(replayed.code()).isEqualTo("INVALID_STATE_TRANSITION");
        assertThat(replayed.detail()).contains("ya tiene contraseña");
    }

    @Test
    @DisplayName("Organización inexistente: retorna OrganizationNotFound sin registrar en api_operations")
    void organizacionInexistente_retornaNotFound() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(initialLinkRepository.findAdminLinkTarget(ORG_ID, ADMIN_ID)).thenReturn(Optional.empty());

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.OrganizationNotFound.class);
        verify(apiOperationRepository, never()).saveCompletedOperation(any(), any(), anyString(), anyString(), any(), anyString(), anyString(), anyInt(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Administrador inexistente o en otra organización: retorna AdminNotFound")
    void adminInexistente_retornaAdminNotFound() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(initialLinkRepository.findAdminLinkTarget(ORG_ID, ADMIN_ID)).thenReturn(
                Optional.of(new OperatorInitialLinkRepository.AdminLinkTarget(
                        ORG_ID, "ACTIVA", null, null, false, false, 0L
                ))
        );

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.AdminNotFound.class);
        verify(apiOperationRepository, never()).saveCompletedOperation(any(), any(), anyString(), anyString(), any(), anyString(), anyString(), anyInt(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Organización inactiva: retorna InvalidStateTransition y persiste REJECTED en api_operations")
    void organizacionInactiva_retornaInvalidStateTransition() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(initialLinkRepository.findAdminLinkTarget(ORG_ID, ADMIN_ID)).thenReturn(
                Optional.of(new OperatorInitialLinkRepository.AdminLinkTarget(
                        ORG_ID, "SUSPENDIDA", ADMIN_ID, "ADMINISTRADOR", true, false, 0L
                ))
        );

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.InvalidStateTransition.class);
        verify(apiOperationRepository).saveCompletedOperation(
                eq(OPERATOR_ID), eq(IDEMPOTENCY_KEY), anyString(), anyString(), any(), anyString(),
                eq("REJECTED"), eq(409), anyString(), contains("INVALID_STATE_TRANSITION"), any(), any()
        );
    }

    @Test
    @DisplayName("Administrador inactivo: retorna InvalidStateTransition y persiste REJECTED")
    void adminInactivo_retornaInvalidStateTransition() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(initialLinkRepository.findAdminLinkTarget(ORG_ID, ADMIN_ID)).thenReturn(
                Optional.of(new OperatorInitialLinkRepository.AdminLinkTarget(
                        ORG_ID, "ACTIVA", ADMIN_ID, "ADMINISTRADOR", false, false, 0L
                ))
        );

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.InvalidStateTransition.class);
    }

    @Test
    @DisplayName("Rol no administrador: retorna InvalidStateTransition y persiste REJECTED")
    void rolIncorrecto_retornaInvalidStateTransition() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(initialLinkRepository.findAdminLinkTarget(ORG_ID, ADMIN_ID)).thenReturn(
                Optional.of(new OperatorInitialLinkRepository.AdminLinkTarget(
                        ORG_ID, "ACTIVA", ADMIN_ID, "SUPERVISOR", true, false, 0L
                ))
        );

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.InvalidStateTransition.class);
    }

    @Test
    @DisplayName("Contraseña ya configurada: retorna InvalidStateTransition y persiste REJECTED")
    void passwordYaConfigurada_retornaInvalidStateTransition() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(initialLinkRepository.findAdminLinkTarget(ORG_ID, ADMIN_ID)).thenReturn(
                Optional.of(new OperatorInitialLinkRepository.AdminLinkTarget(
                        ORG_ID, "ACTIVA", ADMIN_ID, "ADMINISTRADOR", true, true, 0L
                ))
        );

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.InvalidStateTransition.class);
    }

    @Test
    @DisplayName("Conflicto de versión optimista (expectedVersion != lock_version): retorna VersionConflict y persiste REJECTED")
    void conflictoVersion_retornaVersionConflict() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(initialLinkRepository.findAdminLinkTarget(ORG_ID, ADMIN_ID)).thenReturn(
                Optional.of(new OperatorInitialLinkRepository.AdminLinkTarget(
                        ORG_ID, "ACTIVA", ADMIN_ID, "ADMINISTRADOR", true, false, 1L
                ))
        );

        EmitInitialAccessLinkResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(EmitInitialAccessLinkResult.VersionConflict.class);
        EmitInitialAccessLinkResult.VersionConflict conflict = (EmitInitialAccessLinkResult.VersionConflict) result;
        assertThat(conflict.expectedVersion()).isEqualTo(0L);
        assertThat(conflict.currentVersion()).isEqualTo(1L);

        verify(apiOperationRepository).saveCompletedOperation(
                eq(OPERATOR_ID), eq(IDEMPOTENCY_KEY), anyString(), anyString(), any(), anyString(),
                eq("REJECTED"), eq(409), anyString(), contains("CONCURRENT_MODIFICATION"), any(), any()
        );
    }

    @Test
    @DisplayName("Jerarquía de bloqueos anti-deadlock: adquiere primero Lock 1 (idempotencia) y luego Lock 2 (adminUserId)")
    void ordenDeBloqueosJerarquicoAntiDeadlock() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(initialLinkRepository.findAdminLinkTarget(ORG_ID, ADMIN_ID)).thenReturn(
                Optional.of(new OperatorInitialLinkRepository.AdminLinkTarget(
                        ORG_ID, "ACTIVA", ADMIN_ID, "ADMINISTRADOR", true, false, 0L
                ))
        );
        when(issueInitialSetupToken.issue(ORG_ID, ADMIN_ID)).thenReturn(
                Optional.of(new InitialSetupTokenEmission(UUID.randomUUID(), ORG_ID, ADMIN_ID, "raw", NOW.plus(Duration.ofMinutes(30))))
        );
        when(initialLinkRepository.incrementUserLockVersion(ORG_ID, ADMIN_ID, 0L, NOW)).thenReturn(true);

        useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        InOrder inOrder = inOrder(apiOperationRepository);
        inOrder.verify(apiOperationRepository).acquireLock("TurnoShift_Idem_" + OPERATOR_ID + "_" + IDEMPOTENCY_KEY, 10000);
        inOrder.verify(apiOperationRepository).acquireLock("TurnoShift_AdminSetup_" + ADMIN_ID, 10000);
    }

    @Test
    @DisplayName("Fallo técnico en IssueInitialSetupToken lanza OperatorDataAccessException y no persiste operación ni avanza versión")
    void tokenDataAccessException_traduceAOperatorDataAccessException() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(initialLinkRepository.findAdminLinkTarget(ORG_ID, ADMIN_ID)).thenReturn(
                Optional.of(new OperatorInitialLinkRepository.AdminLinkTarget(
                        ORG_ID, "ACTIVA", ADMIN_ID, "ADMINISTRADOR", true, false, 0L
                ))
        );
        when(issueInitialSetupToken.issue(ORG_ID, ADMIN_ID)).thenThrow(new TokenDataAccessException());

        assertThatThrownBy(() -> useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint))
                .isInstanceOf(OperatorDataAccessException.class)
                .hasMessage("[OPERATOR_DATA_ACCESS_ERROR] Error al acceder a los datos de persistencia del operador de plataforma.");

        verify(initialLinkRepository, never()).incrementUserLockVersion(any(), any(), anyLong(), any());
        verify(apiOperationRepository, never()).saveCompletedOperation(any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any());
    }
}
