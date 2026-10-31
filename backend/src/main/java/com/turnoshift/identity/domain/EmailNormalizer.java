package com.turnoshift.identity.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normaliza una dirección de correo electrónico para permitir comparaciones
 * de unicidad independientes de variaciones de presentación.
 *
 * <p><b>Proceso exacto (en orden):</b>
 * <ol>
 *   <li>Normalización Unicode NFC (descomponer y recomponer canónicamente).</li>
 *   <li>Eliminación de espacio exterior: elimina los puntos de código Unicode
 *       con propiedad White_Space al inicio y al final.</li>
 *   <li>Conversión a minúsculas con {@link Locale#ROOT} para independencia
 *       regional (evita la transformación de ı/İ turco, etc.).</li>
 * </ol>
 *
 * <p><b>No modifica el contenido interno:</b> no elimina puntos, no interpreta
 * ni elimina sufijos {@code +}, no elimina caracteres internos.
 *
 * <p><b>Responsabilidad de la BD:</b> {@code normalized_email} usa
 * {@code COLLATE Latin1_General_100_BIN2} (comparación binaria exacta).
 * La BD no normaliza; almacena el valor producido aquí.
 *
 * <p><b>Validación sintáctica:</b> no incluida aquí; corresponde a la capa
 * de entrada HTTP. Este componente solo normaliza para persistencia.
 *
 * <p><b>Límite de capacidad UTF-16:</b> validar antes de persistir con
 * {@link #validarCapacidadUtf16(String, String)} — {@code email} y
 * {@code normalized_email} son {@code NVARCHAR(254)}, que admite hasta
 * 254 unidades UTF-16. Esto NO equivale al límite RFC 5321 en octetos.
 *
 * <p>Sin dependencias de Spring, JDBC ni HTTP. Dominio puro.
 */
public final class EmailNormalizer {

    /**
     * Capacidad máxima en unidades UTF-16 para las columnas email y
     * normalized_email (NVARCHAR(254) en SQL Server).
     * Distinto del límite RFC 5321 (254 octetos ASCII).
     */
    public static final int MAX_UTF16_UNITS = 254;

    private EmailNormalizer() {
        // Utilidad estática — no instanciar
    }

    /**
     * Aplica el proceso de normalización completo.
     *
     * @param email dirección de correo tal como fue introducida
     * @return valor normalizado listo para comparación y persistencia
     * @throws IllegalArgumentException si {@code email} es nulo
     */
    public static String normalizar(String email) {
        if (email == null) {
            throw new IllegalArgumentException("El correo no puede ser nulo");
        }
        // Paso 1: Normalización Unicode NFC
        String nfc = Normalizer.normalize(email, Normalizer.Form.NFC);
        // Paso 2: Eliminación de espacio exterior Unicode
        String trimmed = trimUnicodeWhitespace(nfc);
        // Paso 3: Minúsculas independientes de configuración regional
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * Valida que una cadena cabe en una columna NVARCHAR(254) de SQL Server,
     * contando unidades UTF-16 (no puntos de código ni bytes).
     *
     * <p>Los caracteres fuera del BMP (plano 0) ocupan 2 unidades UTF-16.
     * NVARCHAR(254) puede rechazar cadenas que superen esas 254 unidades
     * aunque tengan menos de 254 puntos de código.
     *
     * @param valor     la cadena a validar
     * @param nombreCampo nombre del campo para el mensaje de error
     * @throws CapacidadEmailExcedidaException si la longitud supera el límite
     */
    public static void validarCapacidadUtf16(String valor, String nombreCampo) {
        if (valor == null) return;
        int unidades = valor.length(); // String.length() devuelve unidades UTF-16
        if (unidades > MAX_UTF16_UNITS) {
            throw new CapacidadEmailExcedidaException(
                    nombreCampo, unidades, MAX_UTF16_UNITS);
        }
    }

    /**
     * Elimina puntos de código con propiedad Unicode {@code White_Space}
     * al inicio y al final. No usa {@link String#trim()}, que solo elimina
     * caracteres ≤ U+0020 ASCII.
     */
    static String trimUnicodeWhitespace(String s) {
        if (s.isEmpty()) return s;
        int[] codePoints = s.codePoints().toArray();
        int inicio = 0;
        while (inicio < codePoints.length && isUnicodeWhitespace(codePoints[inicio])) {
            inicio++;
        }
        int fin = codePoints.length - 1;
        while (fin >= inicio && isUnicodeWhitespace(codePoints[fin])) {
            fin--;
        }
        if (inicio > fin) return "";
        return new String(codePoints, inicio, fin - inicio + 1);
    }

    private static boolean isUnicodeWhitespace(int cp) {
        return Character.getType(cp) == Character.SPACE_SEPARATOR
                || cp == '\t' || cp == '\n' || cp == '\u000B'
                || cp == '\f' || cp == '\r'
                || cp == '\u001C' || cp == '\u001D'
                || cp == '\u001E' || cp == '\u001F'
                || cp == '\u0085' // NEL
                || cp == '\u2028' // LINE SEPARATOR
                || cp == '\u2029'; // PARAGRAPH SEPARATOR
    }
}
