package com.turnoshift.operator.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit: OperatorLoginInputValidator — validador estructural de entrada para login de operador")
class OperatorLoginInputValidatorTest {

    @Test
    @DisplayName("Petición válida retorna lista vacía de errores")
    void peticionValida_sinErrores() {
        List<OperatorValidationError> errors = OperatorLoginInputValidator.validate(
                "operador@turnoshift.example",
                "ClaveSegura12345!"
        );
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Email nulo o en blanco genera error REQUIRED")
    void emailNuloOEnBlanco_generaErrorRequired() {
        List<OperatorValidationError> errorsNull = OperatorLoginInputValidator.validate(null, "clave");
        assertThat(errorsNull).anyMatch(e -> e.field().equals("email") && e.code().equals("REQUIRED"));

        List<OperatorValidationError> errorsBlank = OperatorLoginInputValidator.validate("   ", "clave");
        assertThat(errorsBlank).anyMatch(e -> e.field().equals("email") && e.code().equals("REQUIRED"));
    }

    @Test
    @DisplayName("Email con formato inválido genera error INVALID_FORMAT")
    void emailFormatoInvalido_generaErrorInvalidFormat() {
        List<OperatorValidationError> errors = OperatorLoginInputValidator.validate("correo-sin-arroba", "clave");
        assertThat(errors).anyMatch(e -> e.field().equals("email") && e.code().equals("INVALID_FORMAT"));
    }

    @Test
    @DisplayName("Email que excede 254 unidades UTF-16 genera error MAX_LENGTH_EXCEEDED")
    void emailCapacidadExcedida_generaErrorMaxLength() {
        String longEmail = "a".repeat(250) + "@example.com";
        List<OperatorValidationError> errors = OperatorLoginInputValidator.validate(longEmail, "clave");
        assertThat(errors).anyMatch(e -> e.field().equals("email") &&
                (e.code().equals("MAX_LENGTH_EXCEEDED") || e.code().equals("INVALID_FORMAT")));
    }

    @Test
    @DisplayName("Password nulo o vacío genera error REQUIRED")
    void passwordNuloOVacio_generaErrorRequired() {
        List<OperatorValidationError> errorsNull = OperatorLoginInputValidator.validate("op@example.com", null);
        assertThat(errorsNull).anyMatch(e -> e.field().equals("password") && e.code().equals("REQUIRED"));

        List<OperatorValidationError> errorsEmpty = OperatorLoginInputValidator.validate("op@example.com", "");
        assertThat(errorsEmpty).anyMatch(e -> e.field().equals("password") && e.code().equals("REQUIRED"));
    }

    @Test
    @DisplayName("Password con más de 128 puntos de código genera error MAX_LENGTH_EXCEEDED")
    void passwordMayor128CodePoints_generaErrorMaxLength() {
        String longPassword = "p".repeat(129);
        List<OperatorValidationError> errors = OperatorLoginInputValidator.validate("op@example.com", longPassword);
        assertThat(errors).anyMatch(e -> e.field().equals("password") && e.code().equals("MAX_LENGTH_EXCEEDED"));
    }

    @Test
    @DisplayName("Todos los campos ausentes acumulan errores para cada uno")
    void camposAusentes_acumulanErrores() {
        List<OperatorValidationError> errors = OperatorLoginInputValidator.validate(null, null);
        assertThat(errors).hasSize(2);
        assertThat(errors).extracting(OperatorValidationError::field).containsExactly("email", "password");
    }
}
