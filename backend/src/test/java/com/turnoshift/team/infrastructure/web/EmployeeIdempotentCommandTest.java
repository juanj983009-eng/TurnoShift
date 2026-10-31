package com.turnoshift.team.infrastructure.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: EmployeeIdempotentCommand — Serialización y Deserialización de Respuestas Idempotentes")
class EmployeeIdempotentCommandTest {

    private ObjectMapper objectMapper;
    private EmployeeIdempotentCommand command;

    private final UUID orgId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID idemKey = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID empId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID posId = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private final UUID siteId = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        command = new EmployeeIdempotentCommand(
                orgId,
                userId,
                idemKey,
                "POST",
                "/api/v1/employees",
                "canonical-payload",
                true,
                objectMapper
        );
    }

    @Test
    @DisplayName("Fase Roja TDD: code dentro de detail no debe clasificarse como EMPLOYEE_NOT_FOUND")
    void deserialize_casoAdversarial_codeEnDetailNoDebeSerNotFound() {
        String adversarialJson = "{\"type\":\"/problems/validation-failed\",\"title\":\"Error de validación\",\"status\":400,\"detail\":\"Valor con '\"code\":\"EMPLOYEE_NOT_FOUND\"' no admitido\",\"code\":\"VALIDATION_FAILED\",\"errors\":[]}";

        assertThatThrownBy(() -> command.deserializeResponseBody(adversarialJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageNotContaining(adversarialJson);
    }

    @Test
    @DisplayName("serialize de cada rechazo produce JSON válido con campos requeridos y sin instance ni requestId")
    void serialize_rechazos_producenJsonValidoSinInstanceNiRequestId() throws Exception {
        List<EmployeeExecutionResult> rechazos = List.of(
                new EmployeeExecutionResult.NotFound(),
                new EmployeeExecutionResult.PositionNotFound(),
                new EmployeeExecutionResult.CodeAlreadyExists(),
                new EmployeeExecutionResult.PositionInactive(),
                new EmployeeExecutionResult.NoActiveSite(),
                new EmployeeExecutionResult.LicenseLimitExceeded(),
                new EmployeeExecutionResult.StaleVersion()
        );

        for (EmployeeExecutionResult rechazo : rechazos) {
            String json = command.sanitizeResponseBody(rechazo);
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.hasNonNull("type")).isTrue();
            assertThat(root.hasNonNull("title")).isTrue();
            assertThat(root.hasNonNull("status")).isTrue();
            assertThat(root.hasNonNull("detail")).isTrue();
            assertThat(root.hasNonNull("code")).isTrue();
            assertThat(root.has("errors")).isTrue();

            assertThat(root.has("instance")).isFalse();
            assertThat(root.has("requestId")).isFalse();
        }
    }

    @Test
    @DisplayName("deserialize identifica el code raíz correcto para cada rechazo")
    void deserialize_identificaCodeRaizCorrecto() {
        List<EmployeeExecutionResult> rechazos = List.of(
                new EmployeeExecutionResult.NotFound(),
                new EmployeeExecutionResult.PositionNotFound(),
                new EmployeeExecutionResult.CodeAlreadyExists(),
                new EmployeeExecutionResult.PositionInactive(),
                new EmployeeExecutionResult.NoActiveSite(),
                new EmployeeExecutionResult.LicenseLimitExceeded(),
                new EmployeeExecutionResult.StaleVersion()
        );

        for (EmployeeExecutionResult original : rechazos) {
            String json = command.sanitizeResponseBody(original);
            EmployeeExecutionResult deserialized = command.deserializeResponseBody(json);
            assertThat(deserialized.getClass()).isEqualTo(original.getClass());
        }
    }

    @Test
    @DisplayName("response de éxito se serializa y deserializa como EmployeeResponse")
    void exito_serializaYDeserializaCorrectamente() {
        EmployeeResponse response = new EmployeeResponse(
                empId,
                "EMP-01",
                "Juan Pérez",
                posId,
                siteId,
                true,
                2L
        );
        EmployeeExecutionResult.Success success = new EmployeeExecutionResult.Success(response);

        String json = command.sanitizeResponseBody(success);
        EmployeeExecutionResult result = command.deserializeResponseBody(json);

        assertThat(result).isInstanceOf(EmployeeExecutionResult.Success.class);
        EmployeeResponse deserializedResponse = ((EmployeeExecutionResult.Success) result).employee();
        assertThat(deserializedResponse).isEqualTo(response);
    }

    @Test
    @DisplayName("deserialize con JSON que no tiene code soportado ni EmployeeResponse lanza excepción controlada")
    void deserialize_jsonDesconocido_lanzaExcepcionControlada() {
        String desconocidoJson = """
                {"foo":"bar","numero":123}
                """.strip();

        assertThatThrownBy(() -> command.deserializeResponseBody(desconocidoJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageNotContaining(desconocidoJson);
    }
}
