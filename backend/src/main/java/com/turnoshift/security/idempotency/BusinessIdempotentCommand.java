package com.turnoshift.security.idempotency;

import java.util.UUID;

/**
 * Contrato de comando empresarial sujeto a deduplicación e idempotencia.
 *
 * @param <T> Tipo de dato de la respuesta del comando
 */
public interface BusinessIdempotentCommand<T> {

    UUID organizationId();

    UUID actorUserId();

    UUID idempotencyKey();

    String method();

    String path();

    String schemaVersion();

    /**
     * Representación canónica inequívoca de los campos tipados y normalizados del comando.
     */
    String canonicalPayload();

    int httpStatus(T response);

    default String outcome(T response) {
        return "SUCCEEDED";
    }

    default String responseContentType() {
        return "application/json";
    }

    default String responseContentType(T response) {
        return responseContentType();
    }

    String sanitizeResponseBody(T response);

    T deserializeResponseBody(String responseBody);

    default byte[] calculateFingerprint() {
        return BusinessFingerprintCalculator.calculate(
                method(), path(), schemaVersion(), canonicalPayload());
    }
}
