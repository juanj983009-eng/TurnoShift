package com.turnoshift.policy.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

public record ValidationFingerprint(String value) {

    private static final Pattern HEX_LOWERCASE_64 = Pattern.compile("^[0-9a-f]{64}$");

    public ValidationFingerprint {
        if (value == null || !HEX_LOWERCASE_64.matcher(value).matches()) {
            throw new PolicyValidationException(
                    "INVALID_VALIDATION_FINGERPRINT",
                    "La huella de validación debe ser una cadena hexadecimal de 64 caracteres en minúsculas"
            );
        }
    }

    public static String computeCanonicalString(
            UUID organizationId,
            UUID expectedCurrentPolicyVersionId,
            PolicyThresholds thresholds
    ) {
        String expectedCurrentStr = expectedCurrentPolicyVersionId == null ? "null" : expectedCurrentPolicyVersionId.toString().toLowerCase();
        return "v1|" + organizationId.toString().toLowerCase() + "|" + expectedCurrentStr + "|"
                + thresholds.minimumRestMinutes() + "|" + thresholds.weeklyWarningMinutes();
    }

    public static ValidationFingerprint calculate(
            UUID organizationId,
            UUID expectedCurrentPolicyVersionId,
            PolicyThresholds thresholds
    ) {
        String canonical = computeCanonicalString(organizationId, expectedCurrentPolicyVersionId, thresholds);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return new ValidationFingerprint(HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }

    public void verify(
            UUID organizationId,
            UUID expectedCurrentPolicyVersionId,
            PolicyThresholds thresholds
    ) {
        ValidationFingerprint expected = calculate(organizationId, expectedCurrentPolicyVersionId, thresholds);
        if (!this.value.equals(expected.value())) {
            throw new PolicyValidationException(
                    "INVALID_VALIDATION_FINGERPRINT",
                    "La huella de validación proporcionada no coincide con la propuesta calculada"
            );
        }
    }
}
