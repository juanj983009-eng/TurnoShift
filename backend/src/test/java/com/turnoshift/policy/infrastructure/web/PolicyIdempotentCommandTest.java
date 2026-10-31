package com.turnoshift.policy.infrastructure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit: PolicyIdempotentCommand — Comando Idempotente de Activación de Política")
class PolicyIdempotentCommandTest {

    private final ObjectMapper objectMapper = new JsonMapper();
    private final UUID orgId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID idempotencyKey = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID expectedPolicyId = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    @DisplayName("Metadatos fijos del comando cumplen convención de activación")
    void metadatosFijosCumplenConvencion() {
        PolicyIdempotentCommand command = new PolicyIdempotentCommand(
                orgId, userId, idempotencyKey,
                "canonical-payload", objectMapper
        );

        assertThat(command.organizationId()).isEqualTo(orgId);
        assertThat(command.actorUserId()).isEqualTo(userId);
        assertThat(command.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(command.method()).isEqualTo("POST");
        assertThat(command.path()).isEqualTo("/api/v1/policies/activate");
        assertThat(command.schemaVersion()).isEqualTo("1.0");
        assertThat(command.responseContentType()).isEqualTo("application/json");
    }

    @Test
    @DisplayName("Cálculo canónico de payload genera huella idéntica para mismos valores")
    void canonicalPayload_valoresIdenticos_mismaHuella() {
        String payload1 = PolicyIdempotentCommand.buildCanonicalPayload(
                expectedPolicyId, 660, 2400, "Politica inicial", "a".repeat(64)
        );
        String payload2 = PolicyIdempotentCommand.buildCanonicalPayload(
                expectedPolicyId, 660, 2400, "Politica inicial", "a".repeat(64)
        );

        PolicyIdempotentCommand cmd1 = new PolicyIdempotentCommand(
                orgId, userId, idempotencyKey, payload1, objectMapper
        );
        PolicyIdempotentCommand cmd2 = new PolicyIdempotentCommand(
                orgId, userId, idempotencyKey, payload2, objectMapper
        );

        assertThat(cmd1.calculateFingerprint()).isEqualTo(cmd2.calculateFingerprint());
    }

    @Test
    @DisplayName("Cálculo canónico de payload genera huella distinta si cambian campos")
    void canonicalPayload_cambioEnCampo_huellaDiferente() {
        String payloadOriginal = PolicyIdempotentCommand.buildCanonicalPayload(
                expectedPolicyId, 660, 2400, "Politica inicial", "a".repeat(64)
        );
        String payloadDistintoMotivo = PolicyIdempotentCommand.buildCanonicalPayload(
                expectedPolicyId, 660, 2400, "Otro motivo", "a".repeat(64)
        );
        String payloadDistintoExpectedNull = PolicyIdempotentCommand.buildCanonicalPayload(
                null, 660, 2400, "Politica inicial", "a".repeat(64)
        );

        PolicyIdempotentCommand cmdOriginal = new PolicyIdempotentCommand(
                orgId, userId, idempotencyKey, payloadOriginal, objectMapper
        );
        PolicyIdempotentCommand cmdDistintoMotivo = new PolicyIdempotentCommand(
                orgId, userId, idempotencyKey, payloadDistintoMotivo, objectMapper
        );
        PolicyIdempotentCommand cmdDistintoExpectedNull = new PolicyIdempotentCommand(
                orgId, userId, idempotencyKey, payloadDistintoExpectedNull, objectMapper
        );

        assertThat(cmdOriginal.calculateFingerprint()).isNotEqualTo(cmdDistintoMotivo.calculateFingerprint());
        assertThat(cmdOriginal.calculateFingerprint()).isNotEqualTo(cmdDistintoExpectedNull.calculateFingerprint());
    }

    @Test
    @DisplayName("Serialización y deserialización de CurrentPolicyResponse son idénticas")
    void serializacionYDeserializacionDeRespuesta() {
        PolicyIdempotentCommand command = new PolicyIdempotentCommand(
                orgId, userId, idempotencyKey, "payload", objectMapper
        );

        PolicyResponse policyResponse = new PolicyResponse(
                UUID.randomUUID(), 1, 660, 2400,
                Instant.parse("2026-09-24T23:35:00Z"),
                "Motivo de prueba", userId
        );
        CurrentPolicyResponse response = new CurrentPolicyResponse(policyResponse);

        String json = command.sanitizeResponseBody(response);
        assertThat(json).contains("\"versionNumber\":1");
        assertThat(json).contains("\"minimumRestMinutes\":660");

        CurrentPolicyResponse deserialized = command.deserializeResponseBody(json);
        assertThat(deserialized).isEqualTo(response);
        assertThat(command.httpStatus(response)).isEqualTo(201);
        assertThat(command.outcome(response)).isEqualTo("SUCCEEDED");
    }
}
