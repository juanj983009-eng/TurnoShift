package com.turnoshift.operator.application;

import com.turnoshift.identity.domain.EmailNormalizer;
import com.turnoshift.identity.domain.PasswordBlocklist;
import com.turnoshift.identity.domain.PasswordBlocklistUnavailableException;
import com.turnoshift.identity.domain.PasswordPolicy;
import com.turnoshift.identity.domain.ViolacionPolitica;
import com.turnoshift.identity.infrastructure.security.Argon2PasswordService;
import com.turnoshift.operator.domain.PlatformOperatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.Arrays;
import java.util.List;

/**
 * Bootstrap seguro del primer operador de plataforma.
 *
 * <p>Solo se ejecuta cuando {@code turnoshift.operator.bootstrap.enabled=true}.
 * Por defecto está desactivado; no lee variables de entorno ni contacta la BD si está inactivo.
 *
 * <p><b>Credenciales:</b> leídas exclusivamente de las variables de entorno:
 * <ul>
 *   <li>{@code TURNOSHIFT_OPERATOR_EMAIL}</li>
 *   <li>{@code TURNOSHIFT_OPERATOR_DISPLAY_NAME}</li>
 *   <li>{@code TURNOSHIFT_OPERATOR_PASSWORD}</li>
 * </ul>
 *
 * <p><b>Fail-closed:</b>
 * <ul>
 *   <li>Variables de entorno ausentes → falla sin tocar la BD.</li>
 *   <li>Contraseña que viola la política → falla sin insertar.</li>
 *   <li>HIBP no disponible → falla cerrado sin insertar.</li>
 *   <li>Ya existe un operador → omite la inserción silenciosamente.</li>
 * </ul>
 *
 * <p><b>Concurrencia:</b> la inserción se delega a
 * {@link com.turnoshift.operator.infrastructure.TransactionalOperatorInserter}
 * que usa {@code sp_getapplock} dentro de una transacción SQL Server para
 * garantizar que solo una instancia puede insertar cuando no existe ningún operador.
 *
 * <p><b>Seguridad:</b> nunca registra correo, contraseña, hash, valores de
 * variables de entorno ni detalles de excepción. Mensajes fijos únicamente.
 * El array de chars de contraseña se limpia en el bloque {@code finally}.
 */
public class OperatorBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OperatorBootstrapRunner.class);

    private static final String ENV_EMAIL = "TURNOSHIFT_OPERATOR_EMAIL";
    private static final String ENV_DISPLAY_NAME = "TURNOSHIFT_OPERATOR_DISPLAY_NAME";
    private static final String ENV_PASSWORD = "TURNOSHIFT_OPERATOR_PASSWORD";

    private final boolean enabled;
    private final PlatformOperatorRepository repository;
    private final PasswordBlocklist blocklist;
    private final Argon2PasswordService argon2;
    private final OperatorInserter inserter;

    /**
     * Constructor para producción e integración (inyectado por Spring a través de OperatorConfiguration).
     */
    public OperatorBootstrapRunner(boolean enabled,
                                   PlatformOperatorRepository repository,
                                   PasswordBlocklist blocklist,
                                   Argon2PasswordService argon2,
                                   OperatorInserter inserter) {
        this.enabled = enabled;
        this.repository = repository;
        this.blocklist = blocklist;
        this.argon2 = argon2;
        this.inserter = inserter;
    }

    @Override
    public final void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            // Bootstrap desactivado — no se toca la BD ni se leen variables de entorno
            return;
        }

        char[] password = null;
        try {
            // -- Leer credenciales desde variables de entorno -------------------------
            String email = getEnvEmail();
            String displayName = getEnvDisplayName();
            password = getEnvPassword();

            if (isBlank(email) || isBlank(displayName) || password == null || password.length == 0) {
                log.error("[OPERATOR_BOOTSTRAP] Variables de entorno de bootstrap incompletas o ausentes.");
                throw new BootstrapConfigurationException();
            }

            // -- Normalizar correo ---------------------------------------------------
            String normalizedEmail;
            try {
                normalizedEmail = EmailNormalizer.normalizar(email);
            } catch (Exception e) {
                log.error("[OPERATOR_BOOTSTRAP] El correo configurado para bootstrap es inválido.");
                throw new BootstrapConfigurationException();
            }

            // -- Validar contraseña con política + blocklist -------------------------
            String passwordStr = new String(password);
            List<ViolacionPolitica> violations;
            try {
                violations = PasswordPolicy.validar(passwordStr, blocklist);
            } catch (PasswordBlocklistUnavailableException e) {
                log.error("[OPERATOR_BOOTSTRAP] Lista de contraseñas comprometidas no disponible. "
                        + "No se creará el operador de plataforma.");
                throw new BootstrapConfigurationException();
            }

            if (!violations.isEmpty()) {
                log.error("[OPERATOR_BOOTSTRAP] La contraseña configurada no cumple la política de seguridad.");
                throw new BootstrapConfigurationException();
            }

            // -- Codificar contraseña ------------------------------------------------
            String nfcPassword = PasswordPolicy.prepararParaCodificacion(passwordStr);
            String encodedHash = argon2.codificar(nfcPassword);

            // -- Insertar con exclusión transaccional --------------------------------
            insertFirstOperatorIfNone(email, normalizedEmail, displayName, encodedHash);

        } finally {
            // Limpiar el array de chars en memoria (best-effort; JVM no garantiza borrado de env vars)
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    /**
     * Delega la inserción transaccional al puerto OperatorInserter.
     * Sobreescribible en tests unitarios para simular la BD en memoria.
     */
    protected void insertFirstOperatorIfNone(String email,
                                             String normalizedEmail,
                                             String displayName,
                                             String encodedHash) {
        if (inserter != null) {
            inserter.insertFirstOperatorIfNone(email, normalizedEmail, displayName, encodedHash);
        }
    }

    // ---- Métodos sobreescribibles para test ------------------------------------

    /** Sobreescribible en tests para evitar llamar a System.getenv(). */
    String getEnvEmail() {
        return System.getenv(ENV_EMAIL);
    }

    /** Sobreescribible en tests para evitar llamar a System.getenv(). */
    String getEnvDisplayName() {
        return System.getenv(ENV_DISPLAY_NAME);
    }

    /** Sobreescribible en tests para evitar llamar a System.getenv(). */
    char[] getEnvPassword() {
        String pwd = System.getenv(ENV_PASSWORD);
        return pwd != null ? pwd.toCharArray() : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
