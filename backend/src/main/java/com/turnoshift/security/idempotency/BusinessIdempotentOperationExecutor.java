package com.turnoshift.security.idempotency;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Orquestador transaccional reutilizable para comandos de negocio bajo idempotencia empresarial.
 *
 * <p>Aplica un límite mono-transaccional estricto con bloqueo de aplicación de SQL Server
 * por {@code (organizationId, actorUserId, idempotencyKey)} y persistencia en {@code api_operations}.
 */
@Component
public class BusinessIdempotentOperationExecutor {

    public static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    public static final int LOCK_TIMEOUT_MS = 10_000;

    private final BusinessIdempotencyRepository repository;
    private final Clock clock;

    public BusinessIdempotentOperationExecutor(BusinessIdempotencyRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Ejecuta una operación de negocio con garantía de idempotencia en una única transacción Spring.
     *
     * @param command        definición del comando con metadatos y métodos de serialización/huella
     * @param businessEffect acción que produce el efecto de negocio (solo se ejecuta si la clave es nueva)
     * @param <T>            tipo de la respuesta del comando
     * @return resultado terminal (Proceed, Replay, KeyReused o Expired)
     */
    @Transactional
    public <T> BusinessIdempotencyResult<T> execute(
            BusinessIdempotentCommand<T> command,
            Supplier<T> businessEffect
    ) {
        Objects.requireNonNull(command, "command no puede ser nulo");
        Objects.requireNonNull(businessEffect, "businessEffect no puede ser nulo");

        // 1. Adquirir lock de aplicación determinista por (organizationId, actorUserId, idempotencyKey)
        String lockResource = "TurnoShift:Idem:Org:" + command.organizationId()
                + ":User:" + command.actorUserId()
                + ":Key:" + command.idempotencyKey();
        repository.acquireLock(lockResource, LOCK_TIMEOUT_MS);

        // 2. Consultar existencia previa en api_operations
        Optional<BusinessApiOperationRecord> existing = repository.findOperation(
                command.organizationId(), command.actorUserId(), command.idempotencyKey());

        byte[] currentFingerprint = command.calculateFingerprint();
        Instant now = clock.instant();

        if (existing.isPresent()) {
            BusinessApiOperationRecord record = existing.get();

            // 2a. Clave expirada (> 24h)
            if (now.isAfter(record.expiresAt())) {
                return new BusinessIdempotencyResult.Expired<>();
            }

            // 2b. Misma clave con huella diferente -> KeyReused
            if (!MessageDigest.isEqual(record.requestFingerprint(), currentFingerprint)) {
                return new BusinessIdempotencyResult.KeyReused<>();
            }

            // 2c. Misma clave y misma huella vigente -> Replay de la respuesta persistida
            try {
                T replayedResponse = command.deserializeResponseBody(record.responseBody());
                return new BusinessIdempotencyResult.Replay<>(replayedResponse);
            } catch (Exception e) {
                throw new BusinessIdempotencyDataAccessException(e);
            }
        }

        // 3. Clave nueva: ejecutar efecto de negocio exactamente una vez
        T response = businessEffect.get();

        // 4. Persistir operación terminal COMPLETED en api_operations
        String sanitizedBody = command.sanitizeResponseBody(response);
        String outcome = command.outcome(response);
        int httpStatus = command.httpStatus(response);
        String contentType = command.responseContentType(response);
        Instant expiresAt = now.plus(IDEMPOTENCY_TTL);

        repository.saveCompletedOperation(
                command.organizationId(),
                command.actorUserId(),
                command.idempotencyKey(),
                command.method(),
                command.path(),
                currentFingerprint,
                command.schemaVersion(),
                outcome,
                httpStatus,
                contentType,
                sanitizedBody,
                now,
                expiresAt
        );

        return new BusinessIdempotencyResult.Proceed<>(response);
    }
}
