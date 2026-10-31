package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.identity.domain.AuthenticationCandidate;
import com.turnoshift.identity.domain.AuthenticationOutcome;
import com.turnoshift.identity.domain.EmailNormalizer;
import com.turnoshift.identity.domain.LoginInputValidator;
import com.turnoshift.identity.domain.LoginValidationError;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Caso de uso: autenticación empresarial multi-tenant.
 *
 * <p>Normaliza la entrada, busca el candidato por código de organización
 * + correo normalizado, verifica la contraseña y produce un resultado
 * tipado ({@link AuthenticationOutcome}).
 *
 * <p><b>Mitigación de coste:</b> cuando no existe un hash real utilizable
 * (usuario inexistente, hash NULL), se ejecuta una verificación contra un
 * hash señuelo válido para igualar aproximadamente el coste computacional
 * del camino exitoso. El hash señuelo se genera una sola vez por instancia
 * del servicio. Esto no garantiza tiempo constante; solo iguala
 * aproximadamente el coste de Argon2id entre ambas rutas.
 *
 * <p><b>Fail-closed:</b> estados, roles o datos desconocidos producen
 * INVALID_CREDENTIALS. No se concede autenticación por defecto.
 *
 * <p>Sin dependencias de Spring ni HTTP. Caso de uso puro.
 */
public final class AuthenticateUser {

    /** Máximo de puntos de código Unicode aceptados para contraseña en login. */
    static final int MAX_PASSWORD_CODE_POINTS = LoginInputValidator.MAX_PASSWORD_CODE_POINTS;

    private static final Set<String> KNOWN_ROLES =
            Set.of("ADMINISTRADOR", "SUPERVISOR", "COLABORADOR");
    private static final String ACTIVE_ORG_STATUS = "ACTIVA";
    private static final Set<String> DENIED_ORG_STATUSES =
            Set.of("SUSPENDIDA", "CERRADA");

    private final AuthenticationCandidateRepository repository;
    private final PasswordVerifier verifier;
    private final String decoyHash;

    /**
     * @param repository repositorio de candidatos
     * @param verifier   verificador de contraseñas (Argon2 en producción, stub en tests)
     * @param decoyHash  hash señuelo Argon2id válido generado una sola vez por instancia.
     *                   No corresponde a ninguna credencial de producción.
     *                   Se usa para igualar el coste computacional cuando no hay hash real.
     */
    public AuthenticateUser(AuthenticationCandidateRepository repository,
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
     * Ejecuta el intento de autenticación.
     *
     * @param rawOrgCode código de empresa tal como lo introdujo el usuario
     * @param rawEmail   correo electrónico tal como lo introdujo el usuario
     * @param rawPassword contraseña en texto plano (nunca se almacena ni registra)
     * @return resultado tipado sin excepciones por causa de fallo
     */
    public AuthenticationOutcome execute(String rawOrgCode, String rawEmail, String rawPassword) {

        // -- Validación estructural de la entrada (reutiliza LoginInputValidator compartido) --
        List<LoginValidationError> validationErrors =
                LoginInputValidator.validate(rawOrgCode, rawEmail, rawPassword);
        if (!validationErrors.isEmpty()) {
            return AuthenticationOutcome.validationFailed(validationErrors);
        }

        String normalizedOrgCode = LoginInputValidator.normalizeOrgCode(rawOrgCode);
        String normalizedEmail = EmailNormalizer.normalizar(rawEmail);
        String nfcPassword = Normalizer.normalize(rawPassword, Normalizer.Form.NFC);

        // -- Búsqueda del candidato -----------------------------------------------

        Optional<AuthenticationCandidate> optCandidate =
                repository.findByOrgCodeAndEmail(normalizedOrgCode, normalizedEmail);

        if (optCandidate.isEmpty()) {
            // Usuario u organización no existe — ejecutar verificación señuelo
            verifier.matches(nfcPassword, decoyHash);
            return AuthenticationOutcome.invalidCredentials();
        }

        AuthenticationCandidate candidate = optCandidate.get();

        // -- Fail-closed: rol desconocido o datos corruptos -----------------------

        if (candidate.roleCode() == null || !KNOWN_ROLES.contains(candidate.roleCode())) {
            // Dato corrupto: no conceder autenticación, ejecutar verificación para igualar coste
            consumeHash(nfcPassword, candidate.passwordHash());
            return AuthenticationOutcome.invalidCredentials();
        }

        if (candidate.organizationStatus() == null) {
            consumeHash(nfcPassword, candidate.passwordHash());
            return AuthenticationOutcome.invalidCredentials();
        }

        // -- Verificación de contraseña -------------------------------------------

        String storedHash = candidate.passwordHash();
        boolean passwordMatches;

        if (storedHash == null || storedHash.isBlank()) {
            // Hash NULL o vacío — ejecutar verificación señuelo, luego fallo genérico
            verifier.matches(nfcPassword, decoyHash);
            return AuthenticationOutcome.invalidCredentials();
        }

        try {
            passwordMatches = verifier.matches(nfcPassword, storedHash);
        } catch (Exception e) {
            // Hash malformado — fail-closed sin revelar contenido del hash
            return AuthenticationOutcome.invalidCredentials();
        }

        // -- Usuario inactivo (con hash existente, coste ya consumido) -------------

        if (!candidate.isActive()) {
            return AuthenticationOutcome.invalidCredentials();
        }

        if (!passwordMatches) {
            return AuthenticationOutcome.invalidCredentials();
        }

        // -- Estado de la organización --------------------------------------------

        String orgStatus = candidate.organizationStatus().toUpperCase(Locale.ROOT).trim();

        if (ACTIVE_ORG_STATUS.equals(orgStatus)) {
            // Éxito
            AuthenticatedIdentity identity = new AuthenticatedIdentity(
                    candidate.userId(),
                    candidate.organizationId(),
                    candidate.normalizedEmail(),
                    candidate.displayName(),
                    candidate.roleCode(),
                    candidate.employeeId(),
                    candidate.authVersion(),
                    candidate.organizationDisplayName(),
                    candidate.organizationTimeZone()
            );
            return AuthenticationOutcome.success(identity);
        }

        if (DENIED_ORG_STATUSES.contains(orgStatus)) {
            return AuthenticationOutcome.organizationAccessDenied();
        }

        // Estado desconocido — fail-closed
        return AuthenticationOutcome.invalidCredentials();
    }

    /**
     * Consume el coste de verificación del hash existente o del señuelo.
     * Para usuario inactivo con hash real, no omite el coste.
     */
    private void consumeHash(String nfcPassword, String storedHash) {
        if (storedHash != null && !storedHash.isBlank()) {
            try {
                verifier.matches(nfcPassword, storedHash);
            } catch (Exception ignored) {
                // Hash malformado — coste consumido, fallo cerrado
            }
        } else {
            verifier.matches(nfcPassword, decoyHash);
        }
    }

    /**
     * Normaliza código de empresa: trim exterior, uppercase Locale.ROOT.
     */
    static String normalizeOrgCode(String raw) {
        return LoginInputValidator.normalizeOrgCode(raw);
    }
}
