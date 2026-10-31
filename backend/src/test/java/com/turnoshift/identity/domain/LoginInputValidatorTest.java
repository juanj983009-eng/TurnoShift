package com.turnoshift.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit: LoginInputValidator — validador puro de entradas estructurales de login")
class LoginInputValidatorTest {

    @Test
    @DisplayName("Éxito: datos estructuralmente válidos devuelven lista vacía de errores")
    void datosValidos_devuelvenListaVacia() {
        List<LoginValidationError> errors = LoginInputValidator.validate(
                "CAFE-DEMO",
                "ana@cafedemo.example",
                "clave-valida-123"
        );
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Request nulo: todos los campos nulos producen error REQUIRED para cada uno")
    void todosNulos_producenRequiredParaCadaCampo() {
        List<LoginValidationError> errors = LoginInputValidator.validate(null, null, null);
        assertThat(errors).hasSize(3);
        assertThat(errors).extracting(LoginValidationError::field)
                .containsExactly("organizationCode", "email", "password");
        assertThat(errors).extracting(LoginValidationError::code)
                .containsOnly("REQUIRED");
    }

    @Test
    @DisplayName("organizationCode en blanco produce REQUIRED")
    void orgCodeBlanco_produceRequired() {
        List<LoginValidationError> errors = LoginInputValidator.validate("   ", "a@b.com", "pass");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("organizationCode");
        assertThat(errors.get(0).code()).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("organizationCode menor a 2 caracteres produce INVALID_LENGTH")
    void orgCodeDemasiadoCorto_produceInvalidLength() {
        List<LoginValidationError> errors = LoginInputValidator.validate("A", "a@b.com", "pass");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("organizationCode");
        assertThat(errors.get(0).code()).isEqualTo("INVALID_LENGTH");
    }

    @Test
    @DisplayName("organizationCode mayor a 32 caracteres produce INVALID_LENGTH")
    void orgCodeDemasiadoLargo_produceInvalidLength() {
        List<LoginValidationError> errors = LoginInputValidator.validate("A".repeat(33), "a@b.com", "pass");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("organizationCode");
        assertThat(errors.get(0).code()).isEqualTo("INVALID_LENGTH");
    }

    @Test
    @DisplayName("organizationCode con caracteres no alfanuméricos ni guion produce INVALID_FORMAT")
    void orgCodeCaracteresInvalidos_produceInvalidFormat() {
        List<LoginValidationError> errors = LoginInputValidator.validate("ORG INVALID!", "a@b.com", "pass");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("organizationCode");
        assertThat(errors.get(0).code()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("email en blanco produce REQUIRED")
    void emailBlanco_produceRequired() {
        List<LoginValidationError> errors = LoginInputValidator.validate("CAFE-DEMO", "   ", "pass");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("email");
        assertThat(errors.get(0).code()).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("email sin formato válido produce INVALID_FORMAT")
    void emailInvalido_produceInvalidFormat() {
        List<LoginValidationError> errors = LoginInputValidator.validate("CAFE-DEMO", "not-an-email", "pass");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("email");
        assertThat(errors.get(0).code()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("email que excede capacidad UTF-16 produce MAX_LENGTH_EXCEEDED")
    void emailCapacidadExcedida_produceMaxLengthExceeded() {
        String longEmail = "a".repeat(250) + "@cafedemo.example";
        List<LoginValidationError> errors = LoginInputValidator.validate("CAFE-DEMO", longEmail, "pass");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("email");
        assertThat(errors.get(0).code()).isEqualTo("MAX_LENGTH_EXCEEDED");
    }

    @Test
    @DisplayName("password vacío produce REQUIRED")
    void passwordVacio_produceRequired() {
        List<LoginValidationError> errors = LoginInputValidator.validate("CAFE-DEMO", "a@b.com", "");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("password");
        assertThat(errors.get(0).code()).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("password mayor a 128 caracteres produce MAX_LENGTH_EXCEEDED")
    void passwordExcesivo_produceMaxLengthExceeded() {
        String longPass = "p".repeat(129);
        List<LoginValidationError> errors = LoginInputValidator.validate("CAFE-DEMO", "a@b.com", longPass);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("password");
        assertThat(errors.get(0).code()).isEqualTo("MAX_LENGTH_EXCEEDED");
    }

    @Test
    @DisplayName("password exactamente de 128 caracteres es válido")
    void passwordLimite128_esValido() {
        String pass128 = "p".repeat(128);
        List<LoginValidationError> errors = LoginInputValidator.validate("CAFE-DEMO", "a@b.com", pass128);
        assertThat(errors).isEmpty();
    }
}
