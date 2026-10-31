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
import com.turnoshift.operator.infrastructure.InitialSetupProperties;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso orquestador para la emisión o reemplazo del enlace inicial de configuración de contraseña.
 *
 * <p>Aplica un límite mono-transaccional estricto con bloqueos de aplicación jerárquicos (anti-deadlock):
 * <ol>
 *   <li>Adquiere el bloqueo de idempotencia por {@code (actorOperatorId, idempotencyKey)}.</li>
 *   <li>Si la operación ya existe en {@code api_operations}:
 *       <ul>
 *         <li>Si ha expirado (>24h), retorna {@link EmitInitialAccessLinkResult.IdempotencyKeyExpired}.</li>
 *         <li>Si la huella difiere, retorna {@link EmitInitialAccessLinkResult.IdempotencyKeyReused}.</li>
 *         <li>Si la huella coincide y fue exitosa, retorna {@link EmitInitialAccessLinkResult.LinkAlreadyEmitted}
 *             (el secreto no se almacena ni se reproduce; no se genera un nuevo enlace en replay).</li>
 *         <li>Si la huella coincide y fue un rechazo determinista, reproduce dicho rechazo.</li>
 *       </ul>
 *   </li>
 *   <li>Si la clave es nueva:
 *       <ul>
 *         <li>Adquiere el bloqueo por {@code adminUserId}.</li>
 *         <li>Verifica existencia de la organización y pertenencia del administrador (404 si no existen).</li>
 *         <li>Valida estado: organización activa, usuario activo, rol ADMINISTRADOR y sin contraseña previa (409 si falla).</li>
 *         <li>Valida concurrencia optimista: {@code lock_version == expectedVersion} (409 si no coincide).</li>
 *         <li>Emite el token con {@link IssueInitialSetupToken} (revoca anteriores, genera secreto de 256 bits, hash SHA-256 en BD).</li>
 *         <li>Avanza {@code app_users.lock_version = lock_version + 1}.</li>
 *         <li>Registra en {@code api_operations} metadatos estrictamente sanitizados (sin secreto ni enlace).</li>
 *         <li>Confirma la transacción y retorna {@link EmitInitialAccessLinkResult.Success}.</li>
 *       </ul>
 *   </li>
 *   <li>Ante cualquier fallo técnico imprevisto, realiza rollback total de la transacción.</li>
 * </ol>
 */
public class EmitInitialAccessLink {

    public static final String METHOD = "POST";
    public static final String PATH_TEMPLATE = "/api/v1/operator/setup/organizations/%s/initial-access-link";
    public static final String SCHEMA_VERSION = "1.0";
    public static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ApiOperationRepository apiOperationRepository;
    private final OperatorInitialLinkRepository initialLinkRepository;
    private final IssueInitialSetupToken issueInitialSetupToken;
    private final InitialSetupProperties initialSetupProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EmitInitialAccessLink(
            ApiOperationRepository apiOperationRepository,
            OperatorInitialLinkRepository initialLinkRepository,
            IssueInitialSetupToken issueInitialSetupToken,
            InitialSetupProperties initialSetupProperties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.apiOperationRepository = Objects.requireNonNull(apiOperationRepository, "apiOperationRepository no puede ser nulo");
        this.initialLinkRepository = Objects.requireNonNull(initialLinkRepository, "initialLinkRepository no puede ser nulo");
        this.issueInitialSetupToken = Objects.requireNonNull(issueInitialSetupToken, "issueInitialSetupToken no puede ser nulo");
        this.initialSetupProperties = Objects.requireNonNull(initialSetupProperties, "initialSetupProperties no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    @Transactional
    public EmitInitialAccessLinkResult execute(
            UUID actorOperatorId,
            UUID idempotencyKey,
            EmitInitialAccessLinkCommand command,
            byte[] requestFingerprint
    ) {
        Objects.requireNonNull(actorOperatorId, "actorOperatorId no puede ser nulo");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey no puede ser nulo");
        Objects.requireNonNull(command, "command no puede ser nulo");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint no puede ser nulo");

        String requestPath = String.format(PATH_TEMPLATE, command.organizationId());

        // 1. Adquirir Lock 1: Idempotencia por (actor, clave)
        apiOperationRepository.acquireLock("TurnoShift_Idem_" + actorOperatorId + "_" + idempotencyKey, 10000);

        // 2. Verificar existencia previa en api_operations
        Optional<ApiOperationRecord> existingOp = apiOperationRepository.findOperation(actorOperatorId, idempotencyKey);
        if (existingOp.isPresent()) {
            ApiOperationRecord record = existingOp.get();
            Instant now = clock.instant();

            // 2a. Clave expirada (>24h)
            if (now.isAfter(record.expiresAt())) {
                return new EmitInitialAccessLinkResult.IdempotencyKeyExpired();
            }

            // 2b. Misma clave con huella diferente
            if (!Arrays.equals(record.requestFingerprint(), requestFingerprint)) {
                return new EmitInitialAccessLinkResult.IdempotencyKeyReused();
            }

            // 2c. Misma clave y misma huella -> Reproducción de resultado terminal
            if ("SUCCEEDED".equals(record.outcome())) {
                String detail = "El enlace inicial ya fue emitido para esta clave de idempotencia. "
                        + "Por motivos de seguridad, el secreto no se almacena y no puede ser reproducido. "
                        + "Si perdió la respuesta y necesita un nuevo enlace, emita deliberadamente una nueva solicitud "
                        + "con una nueva cabecera Idempotency-Key y la versión actual del administrador (el enlace anterior quedará revocado).";
                return new EmitInitialAccessLinkResult.LinkAlreadyEmitted(detail);
            } else if ("REJECTED".equals(record.outcome())) {
                try {
                    JsonNode node = objectMapper.readTree(record.responseBody());
                    int status = node.has("status") ? node.get("status").asInt() : 409;
                    String type = node.has("type") ? node.get("type").asText() : "/problems/conflict";
                    String title = node.has("title") ? node.get("title").asText() : "Conflicto";
                    String code = node.has("code") ? node.get("code").asText() : "CONFLICT";
                    String detail = node.has("detail") ? node.get("detail").asText() : "";
                    return new EmitInitialAccessLinkResult.ReplayedRejection(status, type, title, code, detail);
                } catch (Exception e) {
                    throw new OperatorDataAccessException();
                }
            }
        }

        // 3. Clave nueva: Adquirir Lock 2 sobre el administrador objetivo
        apiOperationRepository.acquireLock("TurnoShift_AdminSetup_" + command.adminUserId(), 10000);

        // 4. Inspeccionar organización y administrador con bloqueo pesimista
        Optional<OperatorInitialLinkRepository.AdminLinkTarget> targetOpt =
                initialLinkRepository.findAdminLinkTarget(command.organizationId(), command.adminUserId());

        if (targetOpt.isEmpty()) {
            return new EmitInitialAccessLinkResult.OrganizationNotFound(command.organizationId());
        }

        OperatorInitialLinkRepository.AdminLinkTarget target = targetOpt.get();
        if (target.adminUserId() == null) {
            return new EmitInitialAccessLinkResult.AdminNotFound(command.organizationId(), command.adminUserId());
        }

        Instant now = clock.instant();

        // 5. Validaciones de transición de estado de negocio
        if (!"ACTIVA".equalsIgnoreCase(target.orgServiceStatus())) {
            String detail = "La organización no está activa (estado actual: " + target.orgServiceStatus() + ").";
            saveRejection(actorOperatorId, idempotencyKey, requestPath, requestFingerprint,
                    409, "/problems/invalid-state-transition", "Transición de estado inválida", "INVALID_STATE_TRANSITION", detail, now);
            return new EmitInitialAccessLinkResult.InvalidStateTransition(detail, false);
        }

        if (!target.isUserActive()) {
            String detail = "El usuario administrador no está activo.";
            saveRejection(actorOperatorId, idempotencyKey, requestPath, requestFingerprint,
                    409, "/problems/invalid-state-transition", "Transición de estado inválida", "INVALID_STATE_TRANSITION", detail, now);
            return new EmitInitialAccessLinkResult.InvalidStateTransition(detail, false);
        }

        if (!"ADMINISTRADOR".equals(target.roleCode())) {
            String detail = "El usuario no tiene el rol de ADMINISTRADOR requerido para emisión de enlace inicial.";
            saveRejection(actorOperatorId, idempotencyKey, requestPath, requestFingerprint,
                    409, "/problems/invalid-state-transition", "Transición de estado inválida", "INVALID_STATE_TRANSITION", detail, now);
            return new EmitInitialAccessLinkResult.InvalidStateTransition(detail, false);
        }

        if (target.hasPasswordSet()) {
            String detail = "El administrador ya tiene contraseña configurada.";
            saveRejection(actorOperatorId, idempotencyKey, requestPath, requestFingerprint,
                    409, "/problems/invalid-state-transition", "Transición de estado inválida", "INVALID_STATE_TRANSITION", detail, now);
            return new EmitInitialAccessLinkResult.InvalidStateTransition(detail, false);
        }

        // 6. Validación de concurrencia optimista (lock_version)
        if (target.lockVersion() != command.expectedVersion()) {
            String detail = String.format("La versión esperada (%d) no coincide con la versión actual (%d) del administrador.",
                    command.expectedVersion(), target.lockVersion());
            saveRejection(actorOperatorId, idempotencyKey, requestPath, requestFingerprint,
                    409, "/problems/concurrent-modification", "Conflicto de versión del administrador", "CONCURRENT_MODIFICATION", detail, now);
            return new EmitInitialAccessLinkResult.VersionConflict(command.expectedVersion(), target.lockVersion(), detail, false);
        }

        // 7. Emisión del secreto criptográfico (revoca tokens previos INITIAL_SETUP y persiste hash SHA-256)
        Optional<InitialSetupTokenEmission> emissionOpt;
        try {
            emissionOpt = issueInitialSetupToken.issue(command.organizationId(), command.adminUserId());
        } catch (TokenDataAccessException e) {
            throw new OperatorDataAccessException(e);
        }
        if (emissionOpt.isEmpty()) {
            throw new OperatorDataAccessException();
        }
        InitialSetupTokenEmission emission = emissionOpt.get();

        // 8. Avance atómico de la versión de bloqueo del usuario
        boolean versionUpdated = initialLinkRepository.incrementUserLockVersion(
                command.organizationId(), command.adminUserId(), command.expectedVersion(), now);
        if (!versionUpdated) {
            throw new OperatorDataAccessException();
        }

        // 9. Construir enlace (secreto viaja en el fragmento #)
        String setupLink = initialSetupProperties.getInitialSetupBaseUrl() + "#token=" + emission.rawToken();

        // 10. Persistir metadatos estrictamente sanitizados en api_operations (sin secreto ni URL completa)
        String sanitizedResponseBody = String.format(
                "{\"tokenId\":\"%s\",\"adminUserId\":\"%s\",\"expiresAt\":\"%s\",\"secretRedacted\":true}",
                emission.tokenId(), emission.userId(), emission.expiresAt()
        );

        apiOperationRepository.saveCompletedOperation(
                actorOperatorId,
                idempotencyKey,
                METHOD,
                requestPath,
                requestFingerprint,
                SCHEMA_VERSION,
                "SUCCEEDED",
                200,
                "application/json",
                sanitizedResponseBody,
                now,
                now.plus(IDEMPOTENCY_TTL)
        );

        return new EmitInitialAccessLinkResult.Success(setupLink, emission.expiresAt());
    }

    private void saveRejection(
            UUID actorOperatorId,
            UUID idempotencyKey,
            String requestPath,
            byte[] requestFingerprint,
            int httpStatus,
            String type,
            String title,
            String code,
            String detail,
            Instant now
    ) {
        String rejectionTemplate = """
                {"type":"%s","title":"%s","status":%d,"detail":"%s","code":"%s","errors":[]}
                """.formatted(type, title, httpStatus, detail, code).strip();

        apiOperationRepository.saveCompletedOperation(
                actorOperatorId,
                idempotencyKey,
                METHOD,
                requestPath,
                requestFingerprint,
                SCHEMA_VERSION,
                "REJECTED",
                httpStatus,
                "application/problem+json",
                rejectionTemplate,
                now,
                now.plus(IDEMPOTENCY_TTL)
        );
    }
}
