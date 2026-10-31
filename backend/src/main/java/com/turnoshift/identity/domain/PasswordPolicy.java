package com.turnoshift.identity.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Política de contraseñas del dominio de identidad.
 *
 * <p><b>Reglas adoptadas (NIST SP 800-63B):</b>
 * <ul>
 *   <li>Mínimo: {@value #MIN_CODE_POINTS} puntos de código Unicode.</li>
 *   <li>Máximo: {@value #MAX_CODE_POINTS} puntos de código Unicode.</li>
 *   <li>Se acepta cualquier carácter Unicode imprimible, incluidos espacios
 *       internos y secuencias multibyte.</li>
 *   <li>No se exigen clases artificiales (mayúsculas, dígitos, símbolos).</li>
 *   <li>Los espacios exteriores NO se recortan; se incluyen en el recuento
 *       y en el proceso de codificación.</li>
 *   <li>La contraseña se normaliza a NFC antes de medir y codificar.</li>
 * </ul>
 *
 * <p><b>Medida:</b> {@link String#codePointCount(int, int)}, no
 * {@link String#length()} (que devuelve unidades UTF-16) ni bytes.
 * Un carácter fuera del BMP ocupa 2 unidades UTF-16 pero 1 punto de código.
 *
 * <p><b>Sin dependencias de Spring, JDBC ni HTTP.</b> Dominio puro.
 */
public final class PasswordPolicy {

    /** Mínimo de puntos de código Unicode aceptado. */
    public static final int MIN_CODE_POINTS = 15;

    /** Máximo de puntos de código Unicode aceptado. */
    public static final int MAX_CODE_POINTS = 128;

    private PasswordPolicy() {
        // Utilidad estática — no instanciar
    }

    /**
     * Valida una contraseña en texto plano contra la política.
     *
     * <p>Aplica NFC antes de validar. Los errores se devuelven como lista
     * de códigos estables; no se registra ni retorna la contraseña.
     *
     * @param contrasena contraseña en texto plano (puede ser nula o vacía)
     * @param blocklist  proveedor de contraseñas bloqueadas
     * @return lista de violaciones ({@link ViolacionPolitica}); vacía si es válida
     * @throws IllegalStateException si {@code blocklist} es nulo (bloqueo explícito)
     */
    public static List<ViolacionPolitica> validar(String contrasena, PasswordBlocklist blocklist) {
        if (blocklist == null) {
            throw new IllegalStateException(
                    "[IDENTITY_BLOCKLIST_NOT_PROVIDED] PasswordBlocklist es obligatorio. "
                    + "Sin proveedor de contraseñas comprometidas no se permite establecer "
                    + "ni cambiar contraseñas. Configure una implementación antes de habilitar "
                    + "el flujo de contraseñas.");
        }

        List<ViolacionPolitica> violaciones = new ArrayList<>();

        if (contrasena == null || contrasena.isEmpty()) {
            violaciones.add(ViolacionPolitica.CONTRASENA_VACIA);
            return violaciones;
        }

        // Normalización NFC antes de medir
        String nfc = Normalizer.normalize(contrasena, Normalizer.Form.NFC);
        int puntosDeCodigo = nfc.codePointCount(0, nfc.length());

        if (puntosDeCodigo < MIN_CODE_POINTS) {
            violaciones.add(ViolacionPolitica.DEMASIADO_CORTA);
        }
        if (puntosDeCodigo > MAX_CODE_POINTS) {
            violaciones.add(ViolacionPolitica.DEMASIADO_LARGA);
        }

        if (violaciones.isEmpty() && blocklist.estaBloquada(nfc)) {
            violaciones.add(ViolacionPolitica.CONTRASENA_BLOQUEADA);
        }

        return violaciones;
    }

    /**
     * Prepara una contraseña para codificación aplicando normalización NFC.
     *
     * <p>Solo debe llamarse después de validar con {@link #validar}.
     * No recorta espacios exteriores.
     *
     * @param contrasena contraseña validada
     * @return contraseña normalizada NFC lista para Argon2id
     */
    public static String prepararParaCodificacion(String contrasena) {
        return Normalizer.normalize(contrasena, Normalizer.Form.NFC);
    }
}
