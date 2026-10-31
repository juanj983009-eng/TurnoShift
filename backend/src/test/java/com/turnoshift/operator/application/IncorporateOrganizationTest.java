package com.turnoshift.operator.application;

import com.turnoshift.operator.domain.ApiOperationRecord;
import com.turnoshift.operator.domain.ApiOperationRepository;
import com.turnoshift.operator.domain.IncorporateOrganizationCommand;
import com.turnoshift.operator.domain.OrganizationOnboardingRepository;
import com.turnoshift.operator.domain.OrganizationOnboardingResult;
import com.turnoshift.operator.domain.RequestFingerprintCalculator;
import com.turnoshift.operator.infrastructure.OrganizationProperties;
import com.turnoshift.operator.infrastructure.web.IncorporateOrganizationResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Unit: IncorporateOrganization — caso de uso mono-transaccional de onboarding")
class IncorporateOrganizationTest {

    private ApiOperationRepository apiOperationRepository;
    private OrganizationOnboardingRepository onboardingRepository;
    private OrganizationProperties organizationProperties;
    private ObjectMapper objectMapper;
    private Clock clock;

    private IncorporateOrganization useCase;

    private static final UUID OPERATOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");

    private IncorporateOrganizationCommand command;
    private byte[] fingerprint;

    @BeforeEach
    void setUp() {
        apiOperationRepository = mock(ApiOperationRepository.class);
        onboardingRepository = mock(OrganizationOnboardingRepository.class);
        organizationProperties = new OrganizationProperties();
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(NOW, ZoneId.of("UTC"));

        useCase = new IncorporateOrganization(
                apiOperationRepository,
                onboardingRepository,
                organizationProperties,
                objectMapper,
                clock
        );

        command = new IncorporateOrganizationCommand(
                "CAFE-DEMO",
                "Café Demo S.A.",
                ZoneId.of("America/Lima"),
                "Sede Central Lima",
                "admin@cafedemo.example",
                "Ana García"
        );
        fingerprint = RequestFingerprintCalculator.calculate(
                IncorporateOrganization.METHOD,
                IncorporateOrganization.PATH,
                IncorporateOrganization.SCHEMA_VERSION,
                command
        );
    }

    @Test
    @DisplayName("Happy Path: código libre crea organización, sede, admin y guarda api_operations exitoso")
    void happyPath_creaOrganizacionYGuardaApiOperation() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(onboardingRepository.existsOrganizationCode("CAFE-DEMO")).thenReturn(false);

        OrganizationOnboardingResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(OrganizationOnboardingResult.Success.class);
        OrganizationOnboardingResult.Success success = (OrganizationOnboardingResult.Success) result;
        assertThat(success.replayed()).isFalse();
        assertThat(success.response().organization().code()).isEqualTo("CAFE-DEMO");
        assertThat(success.response().site().displayName()).isEqualTo("Sede Central Lima");
        assertThat(success.response().firstAdmin().email()).isEqualTo("admin@cafedemo.example");
        assertThat(success.response().firstAdmin().role()).isEqualTo("ADMINISTRADOR");

        // Verifica inserción de entidades
        verify(onboardingRepository).insertOrganizationSiteAndAdmin(
                any(UUID.class),
                eq("CAFE-DEMO"),
                eq("Café Demo S.A."),
                eq("America/Lima"),
                eq(50),
                any(UUID.class),
                eq("PRINCIPAL"),
                eq("Sede Central Lima"),
                any(UUID.class),
                eq("admin@cafedemo.example"),
                eq("admin@cafedemo.example"),
                eq("Ana García")
        );

        // Verifica inserción de api_operations
        verify(apiOperationRepository).saveCompletedOperation(
                eq(OPERATOR_ID),
                eq(IDEMPOTENCY_KEY),
                eq("POST"),
                eq("/api/v1/operator/setup/organizations"),
                eq(fingerprint),
                eq("1.0"),
                eq("SUCCEEDED"),
                eq(201),
                eq("application/json"),
                anyString(),
                eq(NOW),
                eq(NOW.plus(Duration.ofHours(24)))
        );

        // Verifica orden estricto de locks (Lock 1 antes de Lock 2)
        InOrder inOrder = inOrder(apiOperationRepository);
        inOrder.verify(apiOperationRepository).acquireLock("TurnoShift_Idem_" + OPERATOR_ID + "_" + IDEMPOTENCY_KEY, 10000);
        inOrder.verify(apiOperationRepository).acquireLock("TurnoShift_OnboardingOrgCode_CAFE-DEMO", 10000);
    }

    @Test
    @DisplayName("Conflicto de código: registra rechazo en api_operations sin crear ninguna entidad")
    void conflictoCodigo_registraRechazoTerminalSinCrearEntidades() {
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(onboardingRepository.existsOrganizationCode("CAFE-DEMO")).thenReturn(true);

        OrganizationOnboardingResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(OrganizationOnboardingResult.CodeConflict.class);
        OrganizationOnboardingResult.CodeConflict conflict = (OrganizationOnboardingResult.CodeConflict) result;
        assertThat(conflict.replayed()).isFalse();
        assertThat(conflict.code()).isEqualTo("CAFE-DEMO");

        // Cero entidades creadas
        verify(onboardingRepository, never()).insertOrganizationSiteAndAdmin(
                any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any());

        // api_operations registra REJECTED / 409
        verify(apiOperationRepository).saveCompletedOperation(
                eq(OPERATOR_ID),
                eq(IDEMPOTENCY_KEY),
                eq("POST"),
                eq("/api/v1/operator/setup/organizations"),
                eq(fingerprint),
                eq("1.0"),
                eq("REJECTED"),
                eq(409),
                eq("application/problem+json"),
                anyString(),
                eq(NOW),
                eq(NOW.plus(Duration.ofHours(24)))
        );
    }

    @Test
    @DisplayName("Replay de éxito: misma clave y misma huella devuelve respuesta guardada")
    void replayExito_mismaClaveYMismaHuella_devuelveRespuestaPrevia() throws Exception {
        IncorporateOrganizationResponse cachedResponse = new IncorporateOrganizationResponse(
                new IncorporateOrganizationResponse.OrganizationSummary(UUID.randomUUID(), "CAFE-DEMO", "Café Demo", "America/Lima"),
                new IncorporateOrganizationResponse.SiteSummary(UUID.randomUUID(), "Sede Central Lima"),
                new IncorporateOrganizationResponse.UserSummary(UUID.randomUUID(), "Ana García", "admin@cafedemo.example", "ADMINISTRADOR"),
                IncorporateOrganizationResponse.DEFAULT_PENDING_SETUP
        );
        String cachedJson = objectMapper.writeValueAsString(cachedResponse);

        ApiOperationRecord record = new ApiOperationRecord(
                UUID.randomUUID(), OPERATOR_ID, IDEMPOTENCY_KEY, fingerprint,
                "COMPLETED", "SUCCEEDED", 201, "application/json", cachedJson,
                NOW.minusSeconds(60), NOW.minusSeconds(60), NOW.plus(Duration.ofHours(23))
        );
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.of(record));

        OrganizationOnboardingResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(OrganizationOnboardingResult.Success.class);
        OrganizationOnboardingResult.Success success = (OrganizationOnboardingResult.Success) result;
        assertThat(success.replayed()).isTrue();
        assertThat(success.response().organization().code()).isEqualTo("CAFE-DEMO");

        // Cero llamadas a repositorio de onboarding
        verify(onboardingRepository, never()).existsOrganizationCode(any());
        verify(onboardingRepository, never()).insertOrganizationSiteAndAdmin(
                any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Replay de rechazo: misma clave y misma huella devuelve conflicto guardado")
    void replayRechazo_mismaClaveYMismaHuella_devuelveRechazoPrevio() {
        String rejectionJson = """
                {"type":"/problems/organization-code-conflict","title":"Código de organización en uso","status":409,"detail":"El código 'CAFE-DEMO' ya está asignado a otra organización.","code":"ORGANIZATION_CODE_CONFLICT","errors":[]}
                """;
        ApiOperationRecord record = new ApiOperationRecord(
                UUID.randomUUID(), OPERATOR_ID, IDEMPOTENCY_KEY, fingerprint,
                "COMPLETED", "REJECTED", 409, "application/problem+json", rejectionJson,
                NOW.minusSeconds(60), NOW.minusSeconds(60), NOW.plus(Duration.ofHours(23))
        );
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.of(record));

        OrganizationOnboardingResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(OrganizationOnboardingResult.CodeConflict.class);
        OrganizationOnboardingResult.CodeConflict conflict = (OrganizationOnboardingResult.CodeConflict) result;
        assertThat(conflict.replayed()).isTrue();
        assertThat(conflict.code()).isEqualTo("CAFE-DEMO");
    }

    @Test
    @DisplayName("Clave reutilizada con distinta huella devuelve IdempotencyKeyReused")
    void claveReutilizada_mismaClaveDistintaHuella_devuelveIdempotencyKeyReused() {
        byte[] differentFingerprint = new byte[32];
        differentFingerprint[0] = 1;

        ApiOperationRecord record = new ApiOperationRecord(
                UUID.randomUUID(), OPERATOR_ID, IDEMPOTENCY_KEY, differentFingerprint,
                "COMPLETED", "SUCCEEDED", 201, "application/json", "{}",
                NOW.minusSeconds(60), NOW.minusSeconds(60), NOW.plus(Duration.ofHours(23))
        );
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.of(record));

        OrganizationOnboardingResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(OrganizationOnboardingResult.IdempotencyKeyReused.class);
    }

    @Test
    @DisplayName("Clave expirada devuelve IdempotencyKeyExpired")
    void claveExpirada_devuelveIdempotencyKeyExpired() {
        ApiOperationRecord expiredRecord = new ApiOperationRecord(
                UUID.randomUUID(), OPERATOR_ID, IDEMPOTENCY_KEY, fingerprint,
                "COMPLETED", "SUCCEEDED", 201, "application/json", "{}",
                NOW.minus(Duration.ofHours(25)), NOW.minus(Duration.ofHours(25)), NOW.minusSeconds(10)
        );
        when(apiOperationRepository.findOperation(OPERATOR_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.of(expiredRecord));

        OrganizationOnboardingResult result = useCase.execute(OPERATOR_ID, IDEMPOTENCY_KEY, command, fingerprint);

        assertThat(result).isInstanceOf(OrganizationOnboardingResult.IdempotencyKeyExpired.class);
    }
}
