package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.security.idempotency.BusinessFingerprintCalculator;
import com.turnoshift.security.idempotency.BusinessIdempotentCommand;
import com.turnoshift.unavailability.infrastructure.web.dto.EmployeeUnavailabilityResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando idempotente empresarial para mutaciones sobre indisponibilidades.
 *
 * <p>Gestiona operaciones sobre la tabla {@code api_operations} con respuestas tipadas
 * en {@link EmployeeUnavailabilityResponse}.
 */
public class UnavailabilityIdempotentCommand implements BusinessIdempotentCommand<EmployeeUnavailabilityResponse> {

    private final UUID organizationId;
    private final UUID actorUserId;
    private final UUID idempotencyKey;
    private final String method;
    private final String path;
    private final String canonicalPayload;
    private final boolean isCreate;
    private final ObjectMapper objectMapper;

    public UnavailabilityIdempotentCommand(
            UUID organizationId,
            UUID actorUserId,
            UUID idempotencyKey,
            String method,
            String path,
            String canonicalPayload,
            boolean isCreate,
            ObjectMapper objectMapper
    ) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey no puede ser nulo");
        this.method = Objects.requireNonNull(method, "method no puede ser nulo");
        this.path = Objects.requireNonNull(path, "path no puede ser nulo");
        this.canonicalPayload = Objects.requireNonNull(canonicalPayload, "canonicalPayload no puede ser nulo");
        this.isCreate = isCreate;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    @Override
    public UUID organizationId() {
        return organizationId;
    }

    @Override
    public UUID actorUserId() {
        return actorUserId;
    }

    @Override
    public UUID idempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String schemaVersion() {
        return "1.0";
    }

    @Override
    public String canonicalPayload() {
        return canonicalPayload;
    }

    @Override
    public int httpStatus(EmployeeUnavailabilityResponse response) {
        return isCreate ? 201 : 200;
    }

    @Override
    public String outcome(EmployeeUnavailabilityResponse response) {
        return "SUCCEEDED";
    }

    @Override
    public String responseContentType() {
        return "application/json";
    }

    @Override
    public String responseContentType(EmployeeUnavailabilityResponse response) {
        return "application/json";
    }

    @Override
    public String sanitizeResponseBody(EmployeeUnavailabilityResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Error sanitizando respuesta de indisponibilidad", e);
        }
    }

    @Override
    public EmployeeUnavailabilityResponse deserializeResponseBody(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, EmployeeUnavailabilityResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializando cuerpo persistido de indisponibilidad", e);
        }
    }

    // =========================================================================
    // Builders de payload canónico inequívoco
    // =========================================================================

    public static String buildCreateCanonicalPayload(
            UUID organizationId,
            UUID actorUserId,
            UUID employeeId,
            Instant startsAt,
            Instant endsAt,
            String reasonCode,
            String restrictedNote
    ) {
        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, "CREATE");
        BusinessFingerprintCalculator.appendField(sb, organizationId.toString());
        BusinessFingerprintCalculator.appendField(sb, actorUserId.toString());
        BusinessFingerprintCalculator.appendField(sb, employeeId.toString());
        BusinessFingerprintCalculator.appendField(sb, startsAt != null ? startsAt.toString() : "");
        BusinessFingerprintCalculator.appendField(sb, endsAt != null ? endsAt.toString() : "");
        BusinessFingerprintCalculator.appendField(sb, reasonCode != null ? reasonCode : "");
        BusinessFingerprintCalculator.appendField(sb, restrictedNote != null ? restrictedNote : "");
        return sb.toString();
    }

    public static String buildUpdateCanonicalPayload(
            UUID organizationId,
            UUID actorUserId,
            UUID unavailabilityId,
            Instant startsAt,
            Instant endsAt,
            String reasonCode,
            String restrictedNote,
            long expectedLockVersion
    ) {
        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, "UPDATE");
        BusinessFingerprintCalculator.appendField(sb, organizationId.toString());
        BusinessFingerprintCalculator.appendField(sb, actorUserId.toString());
        BusinessFingerprintCalculator.appendField(sb, unavailabilityId.toString());
        BusinessFingerprintCalculator.appendField(sb, startsAt != null ? startsAt.toString() : "");
        BusinessFingerprintCalculator.appendField(sb, endsAt != null ? endsAt.toString() : "");
        BusinessFingerprintCalculator.appendField(sb, reasonCode != null ? reasonCode : "");
        BusinessFingerprintCalculator.appendField(sb, restrictedNote != null ? restrictedNote : "");
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(expectedLockVersion));
        return sb.toString();
    }

    public static String buildCancelCanonicalPayload(
            UUID organizationId,
            UUID actorUserId,
            UUID unavailabilityId,
            long expectedLockVersion
    ) {
        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, "CANCEL");
        BusinessFingerprintCalculator.appendField(sb, organizationId.toString());
        BusinessFingerprintCalculator.appendField(sb, actorUserId.toString());
        BusinessFingerprintCalculator.appendField(sb, unavailabilityId.toString());
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(expectedLockVersion));
        return sb.toString();
    }
}
