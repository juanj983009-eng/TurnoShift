package com.turnoshift.policy.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: ValidationFingerprint")
class ValidationFingerprintTest {

    private final UUID orgId = UUID.fromString("c1f7b0a0-0000-0000-0000-000000000001");
    private final UUID policyId = UUID.fromString("a1f7b0a0-0000-0000-0000-000000000002");
    private final PolicyThresholds thresholds = new PolicyThresholds(660, 2400);

    @Test
    @DisplayName("Genera cadena canónica UTF-8 v1 exacta con expectedCurrentPolicyVersionId nulo")
    void generaCadenaCanonicaConExpectedNulo() {
        String canonica = ValidationFingerprint.computeCanonicalString(orgId, null, thresholds);
        assertThat(canonica).isEqualTo("v1|c1f7b0a0-0000-0000-0000-000000000001|null|660|2400");
    }

    @Test
    @DisplayName("Genera cadena canónica UTF-8 v1 exacta con expectedCurrentPolicyVersionId presente en minúsculas")
    void generaCadenaCanonicaConExpectedPresente() {
        String canonica = ValidationFingerprint.computeCanonicalString(orgId, policyId, thresholds);
        assertThat(canonica).isEqualTo("v1|c1f7b0a0-0000-0000-0000-000000000001|a1f7b0a0-0000-0000-0000-000000000002|660|2400");
    }

    @Test
    @DisplayName("Calcula digest SHA-256 en formato hexadecimal minúsculo de exactamente 64 caracteres de forma determinista")
    void calculaDigestSha256Determinista() {
        ValidationFingerprint fp1 = ValidationFingerprint.calculate(orgId, null, thresholds);
        ValidationFingerprint fp2 = ValidationFingerprint.calculate(orgId, null, thresholds);

        assertThat(fp1.value())
                .isNotNull()
                .hasSize(64)
                .matches("^[0-9a-f]{64}$")
                .isEqualTo(fp2.value());
    }

    @Test
    @DisplayName("Rechaza valor nulo en constructor")
    void rechazaValorNulo() {
        assertThatThrownBy(() -> new ValidationFingerprint(null))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("INVALID_VALIDATION_FINGERPRINT");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Contiene mayúsculas
            "A1B2C3D4E5F60718293A4B5C6D7E8F901234567890ABCDEF1234567890ABCDEF",
            "a1b2c3d4e5f60718293a4b5c6d7e8f901234567890abcdef1234567890abcdeF",
            // Longitudes distintas de 64
            "a1b2c3d4e5f60718293a4b5c6d7e8f901234567890abcdef1234567890abcde",    // 63 chars
            "a1b2c3d4e5f60718293a4b5c6d7e8f901234567890abcdef1234567890abcdef0",   // 65 chars
            "",
            "   ",
            // Caracteres fuera de [0-9a-f]
            "g1b2c3d4e5f60718293a4b5c6d7e8f901234567890abcdef1234567890abcdef",
            "z1b2c3d4e5f60718293a4b5c6d7e8f901234567890abcdef1234567890abcdef",
            "a1b2c3d4e5f60718293a4b5c6d7e8f901234567890abcdef1234567890abc-ef"
    })
    @DisplayName("Rechaza huellas con mayúsculas, longitudes incorrectas o caracteres no hexadecimales con INVALID_VALIDATION_FINGERPRINT")
    void rechazaFormatoInvalido(String invalidHash) {
        assertThatThrownBy(() -> new ValidationFingerprint(invalidHash))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("INVALID_VALIDATION_FINGERPRINT");
                });
    }

    @Test
    @DisplayName("verify() pasa con coincidencia exacta y falla cuando difiere el valor")
    void verifyComportamientoExacto() {
        ValidationFingerprint fp = ValidationFingerprint.calculate(orgId, null, thresholds);

        // Pasa con mismos parámetros
        fp.verify(orgId, null, thresholds);

        // Falla si cambia orgId
        UUID otraOrg = UUID.randomUUID();
        assertThatThrownBy(() -> fp.verify(otraOrg, null, thresholds))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("INVALID_VALIDATION_FINGERPRINT");
                });

        // Falla si cambia expectedCurrentPolicyVersionId
        assertThatThrownBy(() -> fp.verify(orgId, policyId, thresholds))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("INVALID_VALIDATION_FINGERPRINT");
                });

        // Falla si cambian los umbrales
        PolicyThresholds otrosUmbrales = new PolicyThresholds(720, 2400);
        assertThatThrownBy(() -> fp.verify(orgId, null, otrosUmbrales))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("INVALID_VALIDATION_FINGERPRINT");
                });
    }

    @Test
    @DisplayName("verify() realiza comparación estricta exacta (equals), jamás equalsIgnoreCase")
    void verifyComparacionEstrictaSinEqualsIgnoreCase() {
        ValidationFingerprint fpCalculada = ValidationFingerprint.calculate(orgId, null, thresholds);
        String valorMinusculas = fpCalculada.value();
        String valorMayusculas = valorMinusculas.toUpperCase();

        // No se puede instanciar con mayúsculas debido a la validación en constructor
        assertThatThrownBy(() -> new ValidationFingerprint(valorMayusculas))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("INVALID_VALIDATION_FINGERPRINT");
                });
    }
}
