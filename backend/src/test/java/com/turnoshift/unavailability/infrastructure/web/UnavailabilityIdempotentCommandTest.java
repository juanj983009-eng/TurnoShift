package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.unavailability.infrastructure.web.dto.EmployeeUnavailabilityResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit: UnavailabilityIdempotentCommand — Comportamiento de Idempotencia Tipada")
class UnavailabilityIdempotentCommandTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID idemKey = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();
    private final UUID unavId = UUID.randomUUID();

    @Test
    @DisplayName("Comando Create: estado 201 en éxito y serialización/deserialización correcta")
    void createSuccessSerialization() {
        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavId, empId, Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"),
                "MEDICO", "Cita médica", false, null, null, userId, 0L, Instant.now(), Instant.now()
        );

        String canonical = UnavailabilityIdempotentCommand.buildCreateCanonicalPayload(
                orgId, userId, empId, resp.startsAt(), resp.endsAt(), resp.reasonCode(), resp.restrictedNote()
        );

        UnavailabilityIdempotentCommand cmd = new UnavailabilityIdempotentCommand(
                orgId, userId, idemKey, "POST", "/api/v1/employees/" + empId + "/unavailabilities",
                canonical, true, objectMapper
        );

        assertThat(cmd.httpStatus(resp)).isEqualTo(201);
        assertThat(cmd.outcome(resp)).isEqualTo("SUCCEEDED");
        assertThat(cmd.responseContentType()).isEqualTo("application/json");
        assertThat(cmd.responseContentType(resp)).isEqualTo("application/json");

        String serialized = cmd.sanitizeResponseBody(resp);
        EmployeeUnavailabilityResponse deserialized = cmd.deserializeResponseBody(serialized);

        assertThat(deserialized.id()).isEqualTo(unavId);
        assertThat(deserialized.employeeId()).isEqualTo(empId);
        assertThat(deserialized.reasonCode()).isEqualTo("MEDICO");
        assertThat(deserialized.restrictedNote()).isEqualTo("Cita médica");
        assertThat(deserialized.cancelled()).isFalse();
    }

    @Test
    @DisplayName("Comando Update: estado 200 en éxito y serialización/deserialización")
    void updateSuccessSerialization() {
        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavId, empId, Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"),
                "VACACIONES", null, false, null, null, userId, 1L, Instant.now(), Instant.now()
        );

        String canonical = UnavailabilityIdempotentCommand.buildUpdateCanonicalPayload(
                orgId, userId, unavId, resp.startsAt(), resp.endsAt(), resp.reasonCode(), null, 0L
        );

        UnavailabilityIdempotentCommand cmd = new UnavailabilityIdempotentCommand(
                orgId, userId, idemKey, "PUT", "/api/v1/unavailabilities/" + unavId,
                canonical, false, objectMapper
        );

        assertThat(cmd.httpStatus(resp)).isEqualTo(200);
        assertThat(cmd.outcome(resp)).isEqualTo("SUCCEEDED");
        assertThat(cmd.method()).isEqualTo("PUT");
        assertThat(cmd.path()).isEqualTo("/api/v1/unavailabilities/" + unavId);

        String serialized = cmd.sanitizeResponseBody(resp);
        EmployeeUnavailabilityResponse deserialized = cmd.deserializeResponseBody(serialized);

        assertThat(deserialized.id()).isEqualTo(unavId);
        assertThat(deserialized.reasonCode()).isEqualTo("VACACIONES");
        assertThat(deserialized.restrictedNote()).isNull();
        assertThat(deserialized.lockVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Comando Cancel: estado 200 en éxito y serialización/deserialización")
    void cancelSuccessSerialization() {
        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavId, empId, Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"),
                "VACACIONES", null, true, Instant.now(), userId, userId, 2L, Instant.now(), Instant.now()
        );

        String canonical = UnavailabilityIdempotentCommand.buildCancelCanonicalPayload(
                orgId, userId, unavId, 1L
        );

        UnavailabilityIdempotentCommand cmd = new UnavailabilityIdempotentCommand(
                orgId, userId, idemKey, "POST", "/api/v1/unavailabilities/" + unavId + "/cancel",
                canonical, false, objectMapper
        );

        assertThat(cmd.httpStatus(resp)).isEqualTo(200);
        assertThat(cmd.outcome(resp)).isEqualTo("SUCCEEDED");
        assertThat(cmd.method()).isEqualTo("POST");
        assertThat(cmd.path()).isEqualTo("/api/v1/unavailabilities/" + unavId + "/cancel");

        String serialized = cmd.sanitizeResponseBody(resp);
        EmployeeUnavailabilityResponse deserialized = cmd.deserializeResponseBody(serialized);

        assertThat(deserialized.id()).isEqualTo(unavId);
        assertThat(deserialized.cancelled()).isTrue();
        assertThat(deserialized.cancelledByUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Diferentes operaciones con la misma clave producen diferentes huellas criptográficas")
    void differentOperationsProduceDifferentFingerprints() {
        Instant start = Instant.parse("2026-10-15T14:00:00Z");
        Instant end = Instant.parse("2026-10-15T18:00:00Z");

        String createPayload = UnavailabilityIdempotentCommand.buildCreateCanonicalPayload(
                orgId, userId, empId, start, end, "MEDICO", "Nota"
        );
        UnavailabilityIdempotentCommand createCmd = new UnavailabilityIdempotentCommand(
                orgId, userId, idemKey, "POST", "/api/v1/employees/" + empId + "/unavailabilities",
                createPayload, true, objectMapper
        );

        String updatePayload = UnavailabilityIdempotentCommand.buildUpdateCanonicalPayload(
                orgId, userId, unavId, start, end, "MEDICO", "Nota", 0L
        );
        UnavailabilityIdempotentCommand updateCmd = new UnavailabilityIdempotentCommand(
                orgId, userId, idemKey, "PUT", "/api/v1/unavailabilities/" + unavId,
                updatePayload, false, objectMapper
        );

        String cancelPayload = UnavailabilityIdempotentCommand.buildCancelCanonicalPayload(
                orgId, userId, unavId, 0L
        );
        UnavailabilityIdempotentCommand cancelCmd = new UnavailabilityIdempotentCommand(
                orgId, userId, idemKey, "POST", "/api/v1/unavailabilities/" + unavId + "/cancel",
                cancelPayload, false, objectMapper
        );

        byte[] fpCreate = createCmd.calculateFingerprint();
        byte[] fpUpdate = updateCmd.calculateFingerprint();
        byte[] fpCancel = cancelCmd.calculateFingerprint();

        assertThat(fpCreate).isNotEqualTo(fpUpdate);
        assertThat(fpCreate).isNotEqualTo(fpCancel);
        assertThat(fpUpdate).isNotEqualTo(fpCancel);
    }

    @Test
    @DisplayName("Diferencias en campos semánticos producen huellas distintas")
    void differentPayloadsProduceDifferentFingerprints() {
        Instant start1 = Instant.parse("2026-10-15T14:00:00Z");
        Instant start2 = Instant.parse("2026-10-15T15:00:00Z");
        Instant end = Instant.parse("2026-10-15T18:00:00Z");

        String payload1 = UnavailabilityIdempotentCommand.buildCreateCanonicalPayload(
                orgId, userId, empId, start1, end, "MEDICO", "Nota"
        );
        String payload2 = UnavailabilityIdempotentCommand.buildCreateCanonicalPayload(
                orgId, userId, empId, start2, end, "MEDICO", "Nota"
        );

        UnavailabilityIdempotentCommand cmd1 = new UnavailabilityIdempotentCommand(
                orgId, userId, idemKey, "POST", "/api/v1/employees/" + empId + "/unavailabilities",
                payload1, true, objectMapper
        );
        UnavailabilityIdempotentCommand cmd2 = new UnavailabilityIdempotentCommand(
                orgId, userId, idemKey, "POST", "/api/v1/employees/" + empId + "/unavailabilities",
                payload2, true, objectMapper
        );

        assertThat(cmd1.calculateFingerprint()).isNotEqualTo(cmd2.calculateFingerprint());
    }

    @Test
    @DisplayName("Diferentes organizaciones o actores producen huellas distintas")
    void differentOrganizationOrActorProducesDifferentFingerprints() {
        Instant start = Instant.parse("2026-10-15T14:00:00Z");
        Instant end = Instant.parse("2026-10-15T18:00:00Z");

        String payloadOrg1 = UnavailabilityIdempotentCommand.buildCreateCanonicalPayload(
                orgId, userId, empId, start, end, "MEDICO", "Nota"
        );
        String payloadOrg2 = UnavailabilityIdempotentCommand.buildCreateCanonicalPayload(
                UUID.randomUUID(), userId, empId, start, end, "MEDICO", "Nota"
        );
        String payloadActor2 = UnavailabilityIdempotentCommand.buildCreateCanonicalPayload(
                orgId, UUID.randomUUID(), empId, start, end, "MEDICO", "Nota"
        );

        UnavailabilityIdempotentCommand cmd1 = new UnavailabilityIdempotentCommand(
                orgId, userId, idemKey, "POST", "/api/v1/employees/" + empId + "/unavailabilities",
                payloadOrg1, true, objectMapper
        );
        UnavailabilityIdempotentCommand cmdOrg2 = new UnavailabilityIdempotentCommand(
                UUID.randomUUID(), userId, idemKey, "POST", "/api/v1/employees/" + empId + "/unavailabilities",
                payloadOrg2, true, objectMapper
        );
        UnavailabilityIdempotentCommand cmdActor2 = new UnavailabilityIdempotentCommand(
                orgId, UUID.randomUUID(), idemKey, "POST", "/api/v1/employees/" + empId + "/unavailabilities",
                payloadActor2, true, objectMapper
        );

        assertThat(cmd1.calculateFingerprint()).isNotEqualTo(cmdOrg2.calculateFingerprint());
        assertThat(cmd1.calculateFingerprint()).isNotEqualTo(cmdActor2.calculateFingerprint());
    }
}
