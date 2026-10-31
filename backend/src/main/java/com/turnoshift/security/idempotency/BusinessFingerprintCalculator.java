package com.turnoshift.security.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Calculador de huella criptográfica SHA-256 inequívoca para comandos empresariales de idempotencia.
 * Aplica codificación con prefijo de longitud (<longitud_bytes_utf8>:<valor>;) para evitar colisiones.
 */
public final class BusinessFingerprintCalculator {

    private BusinessFingerprintCalculator() {
        // Utilidad estática pura
    }

    public static byte[] calculate(String method, String path, String schemaVersion, String canonicalPayload) {
        Objects.requireNonNull(method, "method no puede ser nulo");
        Objects.requireNonNull(path, "path no puede ser nulo");
        Objects.requireNonNull(schemaVersion, "schemaVersion no puede ser nulo");
        Objects.requireNonNull(canonicalPayload, "canonicalPayload no puede ser nulo");

        StringBuilder sb = new StringBuilder();
        appendField(sb, method.trim().toUpperCase());
        appendField(sb, path.trim());
        appendField(sb, schemaVersion.trim());
        appendField(sb, canonicalPayload);

        byte[] canonicalBytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(canonicalBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM", e);
        }
    }

    public static void appendField(StringBuilder sb, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        sb.append(bytes.length).append(':').append(value).append(';');
    }
}
