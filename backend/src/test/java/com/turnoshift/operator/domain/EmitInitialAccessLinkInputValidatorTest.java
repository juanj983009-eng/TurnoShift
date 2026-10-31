package com.turnoshift.operator.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit: EmitInitialAccessLinkInputValidator — validación pura de solicitud de enlace inicial")
class EmitInitialAccessLinkInputValidatorTest {

    private static final String VALID_KEY = UUID.randomUUID().toString();
    private static final String VALID_ORG_ID = UUID.randomUUID().toString();
    private static final String VALID_ADMIN_ID = UUID.randomUUID().toString();
    private static final Long VALID_VERSION = 0L;

    @Test
    @DisplayName("Petición válida retorna cero errores")
    void peticionValida_retornaCeroErrores() {
        List<OperatorValidationError> errors = EmitInitialAccessLinkInputValidator.validate(
                VALID_KEY, VALID_ORG_ID, VALID_ADMIN_ID, VALID_VERSION);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Campos obligatorios nulos o en blanco devuelven error REQUIRED")
    void camposObligatoriosFaltantes_devuelvenRequired() {
        List<OperatorValidationError> errors = EmitInitialAccessLinkInputValidator.validate(
                null, "", "   ", null);

        assertThat(errors)
                .extracting(OperatorValidationError::field)
                .containsExactlyInAnyOrder(
                        "idempotencyKey",
                        "organizationId",
                        "adminUserId",
                        "expectedVersion"
                );

        assertThat(errors).allMatch(e -> "REQUIRED".equals(e.code()));
    }

    @Test
    @DisplayName("Idempotency-Key inválida devuelve INVALID_FORMAT")
    void idempotencyKeyInvalida_devuelveInvalidFormat() {
        List<OperatorValidationError> errors = EmitInitialAccessLinkInputValidator.validate(
                "clave-no-uuid", VALID_ORG_ID, VALID_ADMIN_ID, VALID_VERSION);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("idempotencyKey");
        assertThat(errors.get(0).code()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("organizationId con formato no UUID devuelve INVALID_FORMAT")
    void orgIdInvalido_devuelveInvalidFormat() {
        List<OperatorValidationError> errors = EmitInitialAccessLinkInputValidator.validate(
                VALID_KEY, "invalido", VALID_ADMIN_ID, VALID_VERSION);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("organizationId");
        assertThat(errors.get(0).code()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("adminUserId con formato no UUID devuelve INVALID_FORMAT")
    void adminUserIdInvalido_devuelveInvalidFormat() {
        List<OperatorValidationError> errors = EmitInitialAccessLinkInputValidator.validate(
                VALID_KEY, VALID_ORG_ID, "invalido", VALID_VERSION);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("adminUserId");
        assertThat(errors.get(0).code()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("expectedVersion negativo devuelve INVALID_VALUE")
    void expectedVersionNegativo_devuelveInvalidValue() {
        List<OperatorValidationError> errors = EmitInitialAccessLinkInputValidator.validate(
                VALID_KEY, VALID_ORG_ID, VALID_ADMIN_ID, -1L);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("expectedVersion");
        assertThat(errors.get(0).code()).isEqualTo("INVALID_VALUE");
    }
}
