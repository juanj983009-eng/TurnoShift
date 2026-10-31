package com.turnoshift.operator.application;

import com.turnoshift.operator.domain.ApiOperationRecord;
import com.turnoshift.operator.domain.ApiOperationRepository;
import com.turnoshift.operator.domain.IncorporateOrganizationCommand;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.OrganizationOnboardingRepository;
import com.turnoshift.operator.domain.OrganizationOnboardingResult;
import com.turnoshift.operator.infrastructure.OrganizationProperties;
import com.turnoshift.operator.infrastructure.web.IncorporateOrganizationResponse;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso orquestador para la incorporación de organizaciones por el operador de plataforma.
 *
 * <p>Aplica un límite mono-transaccional estricto con bloqueos de aplicación jerárquicos
 * (anti-deadlock) y deduplicación de idempotencia:
 * <ol>
 *   <li>Adquiere el bloqueo de idempotencia por {@code (actorOperatorId, idempotencyKey)}.</li>
 *   <li>Si la operación ya existe en {@code api_operations}:
 *       <ul>
 *         <li>Si ha expirado (más de 24h), retorna {@link OrganizationOnboardingResult.IdempotencyKeyExpired}.</li>
 *         <li>Si la huella coincide, reproduce el resultado terminal previo (éxito o rechazo).</li>
 *         <li>Si la huella difiere, retorna {@link OrganizationOnboardingResult.IdempotencyKeyReused}.</li>
 *       </ul>
 *   </li>
 *   <li>Si la clave es nueva:
 *       <ul>
 *         <li>Adquiere el bloqueo por código de organización normalizado.</li>
 *         <li>Si el código ya existe, registra {@code REJECTED / 409} en {@code api_operations}
 *             sin crear entidades de dominio y confirma la transacción.</li>
 *         <li>Si el código está libre, crea la organización, la sede 'PRINCIPAL', el administrador
 *             sin contraseña, registra {@code SUCCEEDED / 201} en {@code api_operations}
 *             y confirma la transacción.</li>
 *       </ul>
 *   </li>
 *   <li>Ante fallo técnico no controlado, realiza rollback total de la transacción.</li>
 * </ol>
 */
public class IncorporateOrganization {

    public static final String METHOD = "POST";
    public static final String PATH = "/api/v1/operator/setup/organizations";
    public static final String SCHEMA_VERSION = "1.0";
    public static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ApiOperationRepository apiOperationRepository;
    private final OrganizationOnboardingRepository onboardingRepository;
    private final OrganizationProperties organizationProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IncorporateOrganization(
            ApiOperationRepository apiOperationRepository,
            OrganizationOnboardingRepository onboardingRepository,
            OrganizationProperties organizationProperties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.apiOperationRepository = Objects.requireNonNull(apiOperationRepository, "apiOperationRepository no puede ser nulo");
        this.onboardingRepository = Objects.requireNonNull(onboardingRepository, "onboardingRepository no puede ser nulo");
        this.organizationProperties = Objects.requireNonNull(organizationProperties, "organizationProperties no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    @Transactional
    public OrganizationOnboardingResult execute(
            UUID actorOperatorId,
            UUID idempotencyKey,
            IncorporateOrganizationCommand command,
            byte[] requestFingerprint
    ) {
        Objects.requireNonNull(actorOperatorId, "actorOperatorId no puede ser nulo");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey no puede ser nulo");
        Objects.requireNonNull(command, "command no puede ser nulo");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint no puede ser nulo");

        // 1. Adquirir Lock 1: Idempotencia por (actor, clave)
        apiOperationRepository.acquireLock("TurnoShift_Idem_" + actorOperatorId + "_" + idempotencyKey, 10000);

        // 2. Verificar existencia en api_operations
        Optional<ApiOperationRecord> existingOp = apiOperationRepository.findOperation(actorOperatorId, idempotencyKey);
        if (existingOp.isPresent()) {
            ApiOperationRecord record = existingOp.get();
            Instant now = clock.instant();

            // 2a. Clave expirada
            if (now.isAfter(record.expiresAt())) {
                return new OrganizationOnboardingResult.IdempotencyKeyExpired();
            }

            // 2b. Misma clave pero huella diferente -> Conflicto de reuso
            if (!Arrays.equals(record.requestFingerprint(), requestFingerprint)) {
                return new OrganizationOnboardingResult.IdempotencyKeyReused();
            }

            // 2c. Misma clave y misma huella -> Replay del resultado terminal
            if ("SUCCEEDED".equals(record.outcome())) {
                try {
                    IncorporateOrganizationResponse response =
                            objectMapper.readValue(record.responseBody(), IncorporateOrganizationResponse.class);
                    return new OrganizationOnboardingResult.Success(response, true);
                } catch (Exception e) {
                    throw new OperatorDataAccessException();
                }
            } else if ("REJECTED".equals(record.outcome())) {
                String detail = "El código '" + command.orgCode() + "' ya está asignado a otra organización.";
                return new OrganizationOnboardingResult.CodeConflict(command.orgCode(), detail, true);
            }
        }

        // 3. Clave nueva: Adquirir Lock 2 por código de organización
        apiOperationRepository.acquireLock("TurnoShift_OnboardingOrgCode_" + command.orgCode(), 10000);

        // 4. Comprobar disponibilidad del código de organización
        if (onboardingRepository.existsOrganizationCode(command.orgCode())) {
            // Conflicto determinista: registrar REJECTED en api_operations sin crear ninguna entidad
            String detail = "El código '" + command.orgCode() + "' ya está asignado a otra organización.";
            String rejectionTemplate = """
                    {"type":"/problems/organization-code-conflict","title":"Código de organización en uso","status":409,"detail":"%s","code":"ORGANIZATION_CODE_CONFLICT","errors":[]}
                    """.formatted(detail).strip();

            Instant now = clock.instant();
            apiOperationRepository.saveCompletedOperation(
                    actorOperatorId,
                    idempotencyKey,
                    METHOD,
                    PATH,
                    requestFingerprint,
                    SCHEMA_VERSION,
                    "REJECTED",
                    409,
                    "application/problem+json",
                    rejectionTemplate,
                    now,
                    now.plus(IDEMPOTENCY_TTL)
            );

            return new OrganizationOnboardingResult.CodeConflict(command.orgCode(), detail, false);
        }

        // 5. Código libre: crear organización, sede y administrador
        UUID orgId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        onboardingRepository.insertOrganizationSiteAndAdmin(
                orgId,
                command.orgCode(),
                command.orgDisplayName(),
                command.timeZone().getId(),
                organizationProperties.getDefaultMaxActiveEmployees(),
                siteId,
                "PRINCIPAL",
                command.siteDisplayName(),
                adminId,
                command.adminEmail(),
                command.adminEmail(),
                command.adminDisplayName()
        );

        IncorporateOrganizationResponse response = new IncorporateOrganizationResponse(
                new IncorporateOrganizationResponse.OrganizationSummary(
                        orgId,
                        command.orgCode(),
                        command.orgDisplayName(),
                        command.timeZone().getId()
                ),
                new IncorporateOrganizationResponse.SiteSummary(
                        siteId,
                        command.siteDisplayName()
                ),
                new IncorporateOrganizationResponse.UserSummary(
                        adminId,
                        command.adminDisplayName(),
                        command.adminEmail(),
                        "ADMINISTRADOR"
                ),
                IncorporateOrganizationResponse.DEFAULT_PENDING_SETUP
        );

        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new OperatorDataAccessException();
        }

        Instant now = clock.instant();
        apiOperationRepository.saveCompletedOperation(
                actorOperatorId,
                idempotencyKey,
                METHOD,
                PATH,
                requestFingerprint,
                SCHEMA_VERSION,
                "SUCCEEDED",
                201,
                "application/json",
                responseJson,
                now,
                now.plus(IDEMPOTENCY_TTL)
        );

        return new OrganizationOnboardingResult.Success(response, false);
    }
}
