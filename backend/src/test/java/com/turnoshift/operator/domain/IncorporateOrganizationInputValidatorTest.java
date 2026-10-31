package com.turnoshift.operator.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit: IncorporateOrganizationInputValidator — validación estructural pura de onboarding")
class IncorporateOrganizationInputValidatorTest {

    private static final String VALID_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String VALID_CODE = "CAFE-DEMO";
    private static final String VALID_ORG_NAME = "Café Demo S.A.";
    private static final String VALID_TIMEZONE = "America/Lima";
    private static final String VALID_SITE_NAME = "Sede Central Lima";
    private static final String VALID_EMAIL = "admin@cafedemo.example";
    private static final String VALID_ADMIN_NAME = "Ana García";

    @Test
    @DisplayName("Petición válida completa retorna cero errores")
    void peticionValida_retornaCeroErrores() {
        List<OperatorValidationError> errors = IncorporateOrganizationInputValidator.validate(
                VALID_KEY, VALID_CODE, VALID_ORG_NAME, VALID_TIMEZONE, VALID_SITE_NAME, VALID_EMAIL, VALID_ADMIN_NAME);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Campos obligatorios nulos o en blanco devuelven error REQUIRED")
    void camposObligatoriosFaltantes_devuelvenRequired() {
        List<OperatorValidationError> errors = IncorporateOrganizationInputValidator.validate(
                null, null, "", "   ", null, "", null);

        assertThat(errors)
                .extracting(OperatorValidationError::field)
                .containsExactlyInAnyOrder(
                        "idempotencyKey",
                        "organization.code",
                        "organization.displayName",
                        "organization.timeZone",
                        "site.displayName",
                        "firstAdmin.email",
                        "firstAdmin.displayName"
                );

        assertThat(errors).allMatch(e -> "REQUIRED".equals(e.code()));
    }

    @Test
    @DisplayName("Idempotency-Key con formato no UUID devuelve INVALID_FORMAT")
    void idempotencyKeyInvalido_devuelveInvalidFormat() {
        List<OperatorValidationError> errors = IncorporateOrganizationInputValidator.validate(
                "no-es-un-uuid", VALID_CODE, VALID_ORG_NAME, VALID_TIMEZONE, VALID_SITE_NAME, VALID_EMAIL, VALID_ADMIN_NAME);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("idempotencyKey");
        assertThat(errors.get(0).code()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("Código de organización inválido por longitud o formato")
    void codigoOrganizacionInvalido_devuelveError() {
        // Longitud < 2
        List<OperatorValidationError> errorsShort = IncorporateOrganizationInputValidator.validate(
                VALID_KEY, "A", VALID_ORG_NAME, VALID_TIMEZONE, VALID_SITE_NAME, VALID_EMAIL, VALID_ADMIN_NAME);
        assertThat(errorsShort).hasSize(1);
        assertThat(errorsShort.get(0).code()).isEqualTo("INVALID_LENGTH");

        // Longitud > 32
        List<OperatorValidationError> errorsLong = IncorporateOrganizationInputValidator.validate(
                VALID_KEY, "A".repeat(33), VALID_ORG_NAME, VALID_TIMEZONE, VALID_SITE_NAME, VALID_EMAIL, VALID_ADMIN_NAME);
        assertThat(errorsLong).hasSize(1);
        assertThat(errorsLong.get(0).code()).isEqualTo("INVALID_LENGTH");

        // Caracteres no permitidos (guion bajo)
        List<OperatorValidationError> errorsUnderscore = IncorporateOrganizationInputValidator.validate(
                VALID_KEY, "CAFE_DEMO", VALID_ORG_NAME, VALID_TIMEZONE, VALID_SITE_NAME, VALID_EMAIL, VALID_ADMIN_NAME);
        assertThat(errorsUnderscore).hasSize(1);
        assertThat(errorsUnderscore.get(0).code()).isEqualTo("INVALID_FORMAT");

        // Guión al inicio o al final
        List<OperatorValidationError> errorsLeadingHyphen = IncorporateOrganizationInputValidator.validate(
                VALID_KEY, "-CAFE", VALID_ORG_NAME, VALID_TIMEZONE, VALID_SITE_NAME, VALID_EMAIL, VALID_ADMIN_NAME);
        assertThat(errorsLeadingHyphen).hasSize(1);
        assertThat(errorsLeadingHyphen.get(0).code()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("Zona horaria inexistente devuelve INVALID_FORMAT")
    void zonaHorariaInexistente_devuelveInvalidFormat() {
        List<OperatorValidationError> errors = IncorporateOrganizationInputValidator.validate(
                VALID_KEY, VALID_CODE, VALID_ORG_NAME, "Continente/CiudadInexistente", VALID_SITE_NAME, VALID_EMAIL, VALID_ADMIN_NAME);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("organization.timeZone");
        assertThat(errors.get(0).code()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("Correo electrónico inválido devuelve INVALID_FORMAT")
    void correoInvalido_devuelveInvalidFormat() {
        List<OperatorValidationError> errors = IncorporateOrganizationInputValidator.validate(
                VALID_KEY, VALID_CODE, VALID_ORG_NAME, VALID_TIMEZONE, VALID_SITE_NAME, "admin-sin-arroba", VALID_ADMIN_NAME);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("firstAdmin.email");
        assertThat(errors.get(0).code()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("Nombres que exceden 150 caracteres devuelven INVALID_LENGTH")
    void nombresExceden150_devuelvenInvalidLength() {
        String longText = "A".repeat(151);
        List<OperatorValidationError> errors = IncorporateOrganizationInputValidator.validate(
                VALID_KEY, VALID_CODE, longText, VALID_TIMEZONE, longText, VALID_EMAIL, longText);

        assertThat(errors)
                .extracting(OperatorValidationError::field)
                .containsExactlyInAnyOrder("organization.displayName", "site.displayName", "firstAdmin.displayName");
        assertThat(errors).allMatch(e -> "INVALID_LENGTH".equals(e.code()));
    }
}
