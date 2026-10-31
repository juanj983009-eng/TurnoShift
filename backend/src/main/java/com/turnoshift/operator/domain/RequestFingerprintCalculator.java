package com.turnoshift.operator.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Calculador de huella criptográfica inequívoca para comandos de idempotencia.
 *
 * <p>Aplica codificación prefijada por longitud (formato {@code <longitud_bytes_utf8>:<valor>;})
 * para cada uno de los campos en secuencia determinista invariable, eliminando cualquier
 * ambigüedad o colisión ante caracteres delimitadores o saltos de línea dentro de los valores.
 *
 * <p>Calcula el hash SHA-256 (32 bytes) sobre la representación en bytes UTF-8.
 */
public final class RequestFingerprintCalculator {

    private RequestFingerprintCalculator() {
        // Utilidad estática pura
    }

    /**
     * Calcula el hash SHA-256 de 32 bytes de la representación unívoca del comando.
     *
     * @param method        método HTTP (ej. "POST")
     * @param path          ruta canónica del endpoint (ej. "/api/v1/operator/setup/organizations")
     * @param schemaVersion versión del esquema de petición (ej. "1.0")
     * @param cmd           comando validado y normalizado
     * @return arreglo de 32 bytes correspondiente al hash SHA-256
     */
    public static byte[] calculate(String method, String path, String schemaVersion, IncorporateOrganizationCommand cmd) {
        Objects.requireNonNull(method, "method no puede ser nulo");
        Objects.requireNonNull(path, "path no puede ser nulo");
        Objects.requireNonNull(schemaVersion, "schemaVersion no puede ser nulo");
        Objects.requireNonNull(cmd, "cmd no puede ser nulo");

        StringBuilder sb = new StringBuilder();
        appendField(sb, method);
        appendField(sb, path);
        appendField(sb, schemaVersion);
        appendField(sb, cmd.orgCode());
        appendField(sb, cmd.orgDisplayName());
        appendField(sb, cmd.timeZone().getId());
        appendField(sb, cmd.siteDisplayName());
        appendField(sb, cmd.adminEmail());
        appendField(sb, cmd.adminDisplayName());

        byte[] canonicalBytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(canonicalBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM", e);
        }
    }

    /**
     * Calcula el hash SHA-256 de 32 bytes de la representación unívoca del comando de emisión de enlace inicial.
     *
     * @param method        método HTTP (ej. "POST")
     * @param path          ruta canónica del endpoint
     * @param schemaVersion versión del esquema de petición (ej. "1.0")
     * @param cmd           comando validado y normalizado
     * @return arreglo de 32 bytes correspondiente al hash SHA-256
     */
    public static byte[] calculate(String method, String path, String schemaVersion, EmitInitialAccessLinkCommand cmd) {
        Objects.requireNonNull(method, "method no puede ser nulo");
        Objects.requireNonNull(path, "path no puede ser nulo");
        Objects.requireNonNull(schemaVersion, "schemaVersion no puede ser nulo");
        Objects.requireNonNull(cmd, "cmd no puede ser nulo");

        StringBuilder sb = new StringBuilder();
        appendField(sb, method);
        appendField(sb, path);
        appendField(sb, schemaVersion);
        appendField(sb, cmd.organizationId().toString());
        appendField(sb, cmd.adminUserId().toString());
        appendField(sb, Long.toString(cmd.expectedVersion()));

        byte[] canonicalBytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(canonicalBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM", e);
        }
    }

    private static void appendField(StringBuilder sb, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        sb.append(bytes.length).append(':').append(value).append(';');
    }
}
