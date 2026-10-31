package com.turnoshift.operator.infrastructure;

import com.turnoshift.operator.application.BootstrapConfigurationException;
import com.turnoshift.operator.application.OperatorInserter;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.PlatformOperator;
import com.turnoshift.operator.domain.PlatformOperatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Componente transaccional que inserta el primer operador de plataforma
 * si y solo si no existe ninguno, usando exclusión mutua con {@code sp_getapplock}.
 *
 * <p>La separación en un {@code @Component} independiente evita el problema de
 * self-invocation con {@code @Transactional}: el proxy CGLIB de Spring intercepta
 * la llamada correctamente porque proviene desde fuera de la clase.
 *
 * <p>Patrón idéntico a {@code TransactionalInitialPasswordSetupApplier}.
 */
@Component
public class TransactionalOperatorInserter implements OperatorInserter {

    private static final Logger log = LoggerFactory.getLogger(TransactionalOperatorInserter.class);

    /** Nombre único del recurso para sp_getapplock. */
    private static final String LOCK_NAME = "TurnoShift_OperatorBootstrap";

    /** Tiempo de espera máximo para adquirir el bloqueo, en ms. */
    private static final int LOCK_TIMEOUT_MS = 10_000;

    private final PlatformOperatorRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public TransactionalOperatorInserter(PlatformOperatorRepository repository,
                                         JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserta el primer operador dentro de una transacción con bloqueo exclusivo.
     *
     * <ol>
     *   <li>Adquiere {@code sp_getapplock} con {@code LockOwner=Transaction}.</li>
     *   <li>Si el bloqueo falla (retorno < 0), lanza {@link BootstrapConfigurationException}.</li>
     *   <li>Cuenta operadores existentes; si ya existe alguno, retorna sin insertar.</li>
     *   <li>Inserta el operador y registra un mensaje seguro.</li>
     * </ol>
     *
     * @param email         correo original (no normalizado)
     * @param normalizedEmail correo normalizado (para búsqueda BIN2)
     * @param displayName   nombre visible del operador
     * @param encodedHash   hash Argon2id del password ya codificado
     */
    @Transactional
    public void insertFirstOperatorIfNone(String email,
                                          String normalizedEmail,
                                          String displayName,
                                          String encodedHash) {
        // Adquirir bloqueo exclusivo de aplicación de SQL Server (LockOwner=Transaction).
        // El bloqueo se libera automáticamente al finalizar la transacción.
        Integer lockStatus;
        try {
            lockStatus = jdbcTemplate.queryForObject(
                    "IF @@TRANCOUNT = 0 BEGIN TRANSACTION; " +
                    "DECLARE @res INT; " +
                    "EXEC @res = sp_getapplock " +
                    "  @Resource = '" + LOCK_NAME + "', " +
                    "  @LockMode = 'Exclusive', " +
                    "  @LockOwner = 'Transaction', " +
                    "  @LockTimeout = " + LOCK_TIMEOUT_MS + "; " +
                    "SELECT @res",
                    Integer.class);
        } catch (Exception e) {
            log.error("[OPERATOR_BOOTSTRAP] No se pudo adquirir el bloqueo de bootstrap.");
            throw new BootstrapConfigurationException();
        }

        if (lockStatus == null || lockStatus < 0) {
            log.error("[OPERATOR_BOOTSTRAP] No se pudo adquirir el bloqueo de bootstrap.");
            throw new BootstrapConfigurationException();
        }

        // Dentro del bloqueo: verificar si ya existe un operador
        long count;
        try {
            count = repository.count();
        } catch (OperatorDataAccessException e) {
            log.error("[OPERATOR_BOOTSTRAP] Error al acceder a los datos de persistencia del operador de plataforma.");
            throw new BootstrapConfigurationException();
        }

        if (count > 0) {
            log.info("[OPERATOR_BOOTSTRAP] Bootstrap omitido: ya existe un operador de plataforma.");
            return;
        }

        // Insertar el primer operador
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PlatformOperator operator = new PlatformOperator(
                UUID.randomUUID(),
                email,
                normalizedEmail,
                displayName,
                encodedHash,
                true,
                1L,
                0L,
                now,
                now
        );
        try {
            repository.save(operator);
        } catch (OperatorDataAccessException e) {
            log.error("[OPERATOR_BOOTSTRAP] Error al acceder a los datos de persistencia del operador de plataforma.");
            throw new BootstrapConfigurationException();
        }
        log.info("[OPERATOR_BOOTSTRAP] Primer operador de plataforma creado.");
    }
}
