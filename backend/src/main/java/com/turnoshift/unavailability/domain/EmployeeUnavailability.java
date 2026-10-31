package com.turnoshift.unavailability.domain;

import com.turnoshift.unavailability.domain.exceptions.PastIntervalModificationProhibitedException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityAlreadyCancelledException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class EmployeeUnavailability {

    private final UUID id;
    private final UUID organizationId;
    private final UUID employeeId;
    private final TimeInterval interval;
    private final UnavailabilityReason reason;
    private final RestrictedNote restrictedNote;
    private final UUID createdByUserId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long lockVersion;
    private final CancellationInfo cancellationInfo;

    private EmployeeUnavailability(
            UUID id,
            UUID organizationId,
            UUID employeeId,
            TimeInterval interval,
            UnavailabilityReason reason,
            RestrictedNote restrictedNote,
            UUID createdByUserId,
            Instant createdAt,
            Instant updatedAt,
            long lockVersion,
            CancellationInfo cancellationInfo
    ) {
        this.id = Objects.requireNonNull(id, "id no puede ser nulo");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        this.interval = Objects.requireNonNull(interval, "interval no puede ser nulo");
        this.reason = reason;
        this.restrictedNote = restrictedNote;
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId no puede ser nulo");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");
        this.lockVersion = lockVersion;
        this.cancellationInfo = cancellationInfo;
    }

    public static EmployeeUnavailability create(
            UUID organizationId,
            UUID employeeId,
            TimeInterval interval,
            UnavailabilityReason reason,
            RestrictedNote restrictedNote,
            UUID createdByUserId,
            Instant now
    ) {
        Objects.requireNonNull(now, "now no puede ser nulo");
        return new EmployeeUnavailability(
                UUID.randomUUID(),
                organizationId,
                employeeId,
                interval,
                reason,
                restrictedNote,
                createdByUserId,
                now,
                now,
                0L,
                null
        );
    }

    public static EmployeeUnavailability reconstruct(
            UUID id,
            UUID organizationId,
            UUID employeeId,
            TimeInterval interval,
            UnavailabilityReason reason,
            RestrictedNote restrictedNote,
            UUID createdByUserId,
            Instant createdAt,
            Instant updatedAt,
            long lockVersion,
            CancellationInfo cancellationInfo
    ) {
        return new EmployeeUnavailability(
                id,
                organizationId,
                employeeId,
                interval,
                reason,
                restrictedNote,
                createdByUserId,
                createdAt,
                updatedAt,
                lockVersion,
                cancellationInfo
        );
    }

    public EmployeeUnavailability update(
            TimeInterval newInterval,
            UnavailabilityReason newReason,
            RestrictedNote newRestrictedNote,
            Instant now
    ) {
        Objects.requireNonNull(newInterval, "newInterval no puede ser nulo");
        Objects.requireNonNull(now, "now no puede ser nulo");

        if (isCancelled()) {
            throw new UnavailabilityAlreadyCancelledException("No se puede modificar una indisponibilidad cancelada");
        }
        if (!this.interval.startsAt().isAfter(now)) {
            throw new PastIntervalModificationProhibitedException("No se puede modificar una indisponibilidad que ya inició o finalizó");
        }
        if (!newInterval.startsAt().isAfter(now)) {
            throw new PastIntervalModificationProhibitedException("El nuevo intervalo debe iniciar estrictamente en el futuro");
        }

        return new EmployeeUnavailability(
                this.id,
                this.organizationId,
                this.employeeId,
                newInterval,
                newReason,
                newRestrictedNote,
                this.createdByUserId,
                this.createdAt,
                now,
                this.lockVersion + 1,
                this.cancellationInfo
        );
    }

    public EmployeeUnavailability cancel(UUID actorUserId, Instant now) {
        Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");
        Objects.requireNonNull(now, "now no puede ser nulo");

        if (isCancelled()) {
            throw new UnavailabilityAlreadyCancelledException("La indisponibilidad ya se encuentra cancelada");
        }
        if (!this.interval.startsAt().isAfter(now)) {
            throw new PastIntervalModificationProhibitedException("No se puede cancelar una indisponibilidad que ya inició o finalizó");
        }

        return new EmployeeUnavailability(
                this.id,
                this.organizationId,
                this.employeeId,
                this.interval,
                this.reason,
                this.restrictedNote,
                this.createdByUserId,
                this.createdAt,
                now,
                this.lockVersion + 1,
                new CancellationInfo(now, actorUserId)
        );
    }

    public UUID id() {
        return id;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public UUID employeeId() {
        return employeeId;
    }

    public TimeInterval interval() {
        return interval;
    }

    public Optional<UnavailabilityReason> reason() {
        return Optional.ofNullable(reason);
    }

    public Optional<RestrictedNote> restrictedNote() {
        return Optional.ofNullable(restrictedNote);
    }

    public UUID createdByUserId() {
        return createdByUserId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long lockVersion() {
        return lockVersion;
    }

    public Optional<CancellationInfo> cancellationInfo() {
        return Optional.ofNullable(cancellationInfo);
    }

    public boolean isCancelled() {
        return cancellationInfo != null;
    }

    public boolean isActive() {
        return !isCancelled();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeUnavailability that = (EmployeeUnavailability) o;
        return lockVersion == that.lockVersion &&
                Objects.equals(id, that.id) &&
                Objects.equals(organizationId, that.organizationId) &&
                Objects.equals(employeeId, that.employeeId) &&
                Objects.equals(interval, that.interval) &&
                reason == that.reason &&
                Objects.equals(restrictedNote, that.restrictedNote) &&
                Objects.equals(createdByUserId, that.createdByUserId) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(updatedAt, that.updatedAt) &&
                Objects.equals(cancellationInfo, that.cancellationInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, organizationId, employeeId, interval, reason, restrictedNote, createdByUserId, createdAt, updatedAt, lockVersion, cancellationInfo);
    }

    @Override
    public String toString() {
        return "EmployeeUnavailability{" +
                "id=" + id +
                ", organizationId=" + organizationId +
                ", employeeId=" + employeeId +
                ", interval=" + interval +
                ", reason=" + reason +
                ", lockVersion=" + lockVersion +
                ", isCancelled=" + isCancelled() +
                '}';
    }
}
