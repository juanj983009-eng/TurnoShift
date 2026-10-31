package com.turnoshift.operator.application;

import com.turnoshift.identity.application.PasswordVerifier;
import com.turnoshift.identity.domain.EmailNormalizer;
import com.turnoshift.operator.domain.OperatorAuthenticationOutcome;
import com.turnoshift.operator.domain.PlatformOperator;
import com.turnoshift.operator.domain.PlatformOperatorIdentity;
import com.turnoshift.operator.domain.PlatformOperatorRepository;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso: autenticación de operadores de plataforma.
 *
 * <p>Completamente aislado de {@code AuthenticateUser} y de la identidad
 * empresarial multi-tenant. Busca exclusivamente en {@code platform_operators}
 * por {@code normalized_email}, sin involucrar organizaciones ni roles de negocio.
 *
 * <p><b>Mitigación de coste:</b> cuando no existe un hash real utilizable
 * (operador inexistente, hash malformado que lanza excepción), se ejecuta una
 * verificación contra el hash señuelo para aproximar el coste computacional del
 * camino exitoso. El hash señuelo se genera una sola vez por instancia del servicio.
 *
 * <p><b>Fail-closed:</b> hash nulo (imposible por invariante de PlatformOperator),
 * hash malformado, cuenta inactiva, correo inexistente → INVALID_CREDENTIALS.
 * Nunca revela la causa específica del fallo.
 *
 * <p>{@code OperatorDataAccessException} del repositorio se propaga como excepción
 * técnica — nunca se convierte en INVALID_CREDENTIALS.
 *
 * <p>Sin dependencias de Spring ni HTTP. Caso de uso puro.
 */
public final class AuthenticateOperator {

    private final PlatformOperatorRepository repository;
    private final PasswordVerifier verifier;
    private final String decoyHash;

    /**
     * @param repository repositorio de operadores de plataforma
     * @param verifier   verificador de contraseñas (Argon2 en producción, stub en tests)
     * @param decoyHash  hash señuelo Argon2id válido generado una sola vez por instancia.
     *                   No corresponde a ninguna credencial de producción.
     */
    public AuthenticateOperator(PlatformOperatorRepository repository,
                                PasswordVerifier verifier,
                                String decoyHash) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.verifier = Objects.requireNonNull(verifier, "verifier no puede ser nulo");
        this.decoyHash = Objects.requireNonNull(decoyHash, "decoyHash no puede ser nulo");
        if (decoyHash.isBlank()) {
            throw new IllegalArgumentException("decoyHash no puede estar vacío");
        }
    }

    /**
     * Ejecuta el intento de autenticación del operador.
     *
     * @param rawEmail    correo tal como lo introduce el operador
     * @param rawPassword contraseña en texto plano como array de chars (no se almacena ni registra)
     * @return resultado tipado sin excepciones por causa de fallo de credenciales
     * @throws com.turnoshift.operator.domain.OperatorDataAccessException si falla el acceso a datos
     */
    public OperatorAuthenticationOutcome execute(String rawEmail, char[] rawPassword) {

        // -- Validación estructural de entrada -------------------------------------
        if (rawEmail == null || rawEmail.isBlank()) {
            return OperatorAuthenticationOutcome.invalidCredentials();
        }
        if (rawPassword == null || rawPassword.length == 0) {
            return OperatorAuthenticationOutcome.invalidCredentials();
        }

        // -- Normalización ----------------------------------------------------------
        String normalizedEmail;
        try {
            normalizedEmail = EmailNormalizer.normalizar(rawEmail);
        } catch (Exception e) {
            // Email inválido (e.g. excede 254 unidades UTF-16) — fail-closed
            return OperatorAuthenticationOutcome.invalidCredentials();
        }

        // Convertir char[] a String NFC para Argon2 (no se puede garantizar borrado de memoria JVM)
        String nfcPassword = Normalizer.normalize(new String(rawPassword), Normalizer.Form.NFC);

        // -- Búsqueda en repositorio — OperatorDataAccessException se propaga -----
        Optional<PlatformOperator> optOp = repository.findByNormalizedEmail(normalizedEmail);

        if (optOp.isEmpty()) {
            // Operador no encontrado — consumir coste con hash señuelo
            verifier.matches(nfcPassword, decoyHash);
            return OperatorAuthenticationOutcome.invalidCredentials();
        }

        PlatformOperator op = optOp.get();
        String storedHash = op.passwordHash();

        if (storedHash == null || storedHash.isBlank()) {
            // Hash ausente o vacío — consumir coste con hash señuelo
            verifier.matches(nfcPassword, decoyHash);
            return OperatorAuthenticationOutcome.invalidCredentials();
        }

        // -- Verificación de contraseña con coste consumido -----------------------
        boolean passwordMatches;
        try {
            passwordMatches = verifier.matches(nfcPassword, storedHash);
        } catch (Exception e) {
            // Hash malformado / excepción del verificador — consumir coste con hash señuelo
            try {
                verifier.matches(nfcPassword, decoyHash);
            } catch (Exception ignored) {
                // Fail-closed defensivo
            }
            return OperatorAuthenticationOutcome.invalidCredentials();
        }

        // -- Verificar estado activo DESPUÉS de consumir el coste del hash --------
        if (!op.isActive()) {
            return OperatorAuthenticationOutcome.invalidCredentials();
        }

        if (!passwordMatches) {
            return OperatorAuthenticationOutcome.invalidCredentials();
        }

        // -- Éxito ----------------------------------------------------------------
        return OperatorAuthenticationOutcome.success(
                new PlatformOperatorIdentity(op.id(), op.displayName(), op.authVersion()));
    }
}
