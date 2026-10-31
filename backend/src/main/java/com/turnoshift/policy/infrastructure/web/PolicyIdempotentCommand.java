package com.turnoshift.policy.infrastructure.web;

import com.turnoshift.security.idempotency.BusinessFingerprintCalculator;
import com.turnoshift.security.idempotency.BusinessIdempotentCommand;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.UUID;

/**
 * Comando idempotente empresarial para la activación de políticas operativas.
 *
 * <p>Sujeto a deduplicación e idempotencia a través de {@code api_operations}.
 * Produce y reproduce de forma idéntica respuestas exitosas (201 Created con {@link CurrentPolicyResponse}).
 */
public class PolicyIdempotentCommand implements BusinessIdempotentCommand<CurrentPolicyResponse> {

    private final UUID organizationId;
    private final UUID actorUserId;
    private final UUID idempotencyKey;
    private final String canonicalPayload;
    private final ObjectMapper objectMapper;

    public PolicyIdempotentCommand(
            UUID organizationId,
            UUID actorUserId,
            UUID idempotencyKey,
            String canonicalPayload,
            ObjectMapper objectMapper
    ) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey no puede ser nulo");
        this.canonicalPayload = Objects.requireNonNull(canonicalPayload, "canonicalPayload no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    public static String buildCanonicalPayload(
            UUID expectedCurrentPolicyVersionId,
            int minimumRestMinutes,
            int weeklyWarningMinutes,
            String reason,
            String validationFingerprint
    ) {
        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, expectedCurrentPolicyVersionId != null ? expectedCurrentPolicyVersionId.toString().toLowerCase() : "null");
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(minimumRestMinutes));
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(weeklyWarningMinutes));
        BusinessFingerprintCalculator.appendField(sb, reason != null ? reason : "");
        BusinessFingerprintCalculator.appendField(sb, validationFingerprint != null ? validationFingerprint : "");
        return sb.toString();
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
        return "POST";
    }

    @Override
    public String path() {
        return "/api/v1/policies/activate";
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
    public int httpStatus(CurrentPolicyResponse response) {
        return 201;
    }

    @Override
    public String outcome(CurrentPolicyResponse response) {
        return "SUCCEEDED";
    }

    @Override
    public String responseContentType() {
        return "application/json";
    }

    @Override
    public String responseContentType(CurrentPolicyResponse response) {
        return "application/json";
    }

    @Override
    public String sanitizeResponseBody(CurrentPolicyResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Error sanitizando respuesta de política activada", e);
        }
    }

    @Override
    public CurrentPolicyResponse deserializeResponseBody(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, CurrentPolicyResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializando respuesta persistida de política activada", e);
        }
    }
}
