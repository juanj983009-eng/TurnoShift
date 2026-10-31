package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.identity.domain.AuthenticationDataAccessException;
import com.turnoshift.identity.domain.SessionRevalidationCandidate;
import com.turnoshift.identity.domain.SessionRevalidationRepository;
import com.turnoshift.identity.domain.SessionSecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Unit: RevalidateSession — límite de 8h y revalidación de estado")
class RevalidateSessionTest {

    private SessionRevalidationRepository repository;
    private Clock fixedClock;
    private RevalidateSession revalidateSession;

    private final Instant now = Instant.parse("2026-09-15T12:00:00Z");
    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID orgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID employeeId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private AuthenticatedIdentity baseIdentity;
    private SessionRevalidationCandidate baseCandidate;

    @BeforeEach
    void setUp() {
        repository = mock(SessionRevalidationRepository.class);
        fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        revalidateSession = new RevalidateSession(repository, fixedClock);

        baseIdentity = new AuthenticatedIdentity(
                userId,
                orgId,
                "ana@empresa.example",
                "Ana López",
                "ADMINISTRADOR",
                employeeId,
                1L,
                "Empresa Demo S.A.",
                "America/Bogota"
        );

        baseCandidate = new SessionRevalidationCandidate(
                userId,
                orgId,
                true,
                1L,
                "ADMINISTRADOR",
                "Ana López",
                employeeId,
                "ACTIVA",
                "Empresa Demo S.A.",
                "America/Bogota"
        );
    }

    // =========================================================================
    // 1. Límite Absoluto de 8 Horas
    // =========================================================================

    @Test
    @DisplayName("Sesión válida: autenticada hace 1 hora (menor a 8h) y datos coincidentes devuelve VALID")
    void sesionValida_menorA8Horas_devuelveValid() {
        long authenticatedAt = now.minus(Duration.ofHours(1)).toEpochMilli();
        when(repository.findCandidate(userId, orgId)).thenReturn(Optional.of(baseCandidate));

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.VALID);
        assertThat(outcome.updatedIdentity()).isEqualTo(baseIdentity);
    }

    @Test
    @DisplayName("authenticatedAt ausente (null): devuelve EXPIRED sin consultar repositorio")
    void authenticatedAtAusente_devuelveExpired() {
        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, null);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.EXPIRED);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("authenticatedAt tipo no numérico: devuelve EXPIRED sin consultar repositorio")
    void authenticatedAtTipoInvalido_devuelveExpired() {
        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, "no-es-un-numero");

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.EXPIRED);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("authenticatedAt en el futuro inaceptable (> now + 60s): devuelve EXPIRED")
    void authenticatedAtFuturoInaceptable_devuelveExpired() {
        long authenticatedAt = now.plus(Duration.ofSeconds(61)).toEpochMilli();

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.EXPIRED);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("authenticatedAt en futuro leve (<= now + 60s tolerancia clock skew): es aceptable")
    void authenticatedAtFuturoTolerable_esAceptable() {
        long authenticatedAt = now.plus(Duration.ofSeconds(10)).toEpochMilli();
        when(repository.findCandidate(userId, orgId)).thenReturn(Optional.of(baseCandidate));

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.VALID);
    }

    @Test
    @DisplayName("authenticatedAt alcanza exactamente 8 horas: devuelve EXPIRED")
    void authenticatedAtExactamente8Horas_devuelveExpired() {
        long authenticatedAt = now.minus(Duration.ofHours(8)).toEpochMilli();

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.EXPIRED);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("authenticatedAt supera 8 horas: devuelve EXPIRED")
    void authenticatedAtSupera8Horas_devuelveExpired() {
        long authenticatedAt = now.minus(Duration.ofHours(8).plusMillis(1)).toEpochMilli();

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.EXPIRED);
        verifyNoInteractions(repository);
    }

    // =========================================================================
    // 2. Revalidación contra Base de Datos
    // =========================================================================

    @Test
    @DisplayName("Fallo técnico de base de datos: propaga DATA_ACCESS_ERROR para respuesta 503")
    void falloTecnicoBd_devuelveDataAccessError() {
        long authenticatedAt = now.minus(Duration.ofMinutes(10)).toEpochMilli();
        when(repository.findCandidate(userId, orgId))
                .thenThrow(new AuthenticationDataAccessException("Conexión perdida", new RuntimeException("Connection timeout")));

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.DATA_ACCESS_ERROR);
    }

    @Test
    @DisplayName("Usuario u organización no encontrados en base de datos: devuelve INVALID")
    void usuarioNoEncontrado_devuelveInvalid() {
        long authenticatedAt = now.minus(Duration.ofMinutes(10)).toEpochMilli();
        when(repository.findCandidate(userId, orgId)).thenReturn(Optional.empty());

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.INVALID);
    }

    @Test
    @DisplayName("Usuario desactivado (is_active = false): devuelve INVALID")
    void usuarioInactivo_devuelveInvalid() {
        long authenticatedAt = now.minus(Duration.ofMinutes(10)).toEpochMilli();
        SessionRevalidationCandidate inactivo = new SessionRevalidationCandidate(
                userId, orgId, false, 1L, "ADMINISTRADOR", "Ana López", employeeId,
                "ACTIVA", "Empresa Demo S.A.", "America/Bogota"
        );
        when(repository.findCandidate(userId, orgId)).thenReturn(Optional.of(inactivo));

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.INVALID);
    }

    @Test
    @DisplayName("auth_version modificado en base de datos: devuelve INVALID")
    void authVersionModificado_devuelveInvalid() {
        long authenticatedAt = now.minus(Duration.ofMinutes(10)).toEpochMilli();
        SessionRevalidationCandidate nuevaVersion = new SessionRevalidationCandidate(
                userId, orgId, true, 2L, "ADMINISTRADOR", "Ana López", employeeId,
                "ACTIVA", "Empresa Demo S.A.", "America/Bogota"
        );
        when(repository.findCandidate(userId, orgId)).thenReturn(Optional.of(nuevaVersion));

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.INVALID);
    }

    @Test
    @DisplayName("Organización en estado SUSPENDIDA: devuelve ORGANIZATION_ACCESS_DENIED (403)")
    void organizacionSuspendida_devuelveOrganizationAccessDenied() {
        long authenticatedAt = now.minus(Duration.ofMinutes(10)).toEpochMilli();
        SessionRevalidationCandidate suspendida = new SessionRevalidationCandidate(
                userId, orgId, true, 1L, "ADMINISTRADOR", "Ana López", employeeId,
                "SUSPENDIDA", "Empresa Demo S.A.", "America/Bogota"
        );
        when(repository.findCandidate(userId, orgId)).thenReturn(Optional.of(suspendida));

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.ORGANIZATION_ACCESS_DENIED);
    }

    @Test
    @DisplayName("Organización en estado CERRADA: devuelve ORGANIZATION_ACCESS_DENIED (403)")
    void organizacionCerrada_devuelveOrganizationAccessDenied() {
        long authenticatedAt = now.minus(Duration.ofMinutes(10)).toEpochMilli();
        SessionRevalidationCandidate cerrada = new SessionRevalidationCandidate(
                userId, orgId, true, 1L, "ADMINISTRADOR", "Ana López", employeeId,
                "CERRADA", "Empresa Demo S.A.", "America/Bogota"
        );
        when(repository.findCandidate(userId, orgId)).thenReturn(Optional.of(cerrada));

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.ORGANIZATION_ACCESS_DENIED);
    }

    @Test
    @DisplayName("Rol modificado en base de datos: devuelve ROLE_CHANGED con nuevo principal actualizado")
    void rolModificado_devuelveRoleChangedConNuevoPrincipal() {
        long authenticatedAt = now.minus(Duration.ofMinutes(10)).toEpochMilli();
        SessionRevalidationCandidate rolCambiado = new SessionRevalidationCandidate(
                userId, orgId, true, 1L, "COLABORADOR", "Ana López", employeeId,
                "ACTIVA", "Empresa Demo S.A.", "America/Bogota"
        );
        when(repository.findCandidate(userId, orgId)).thenReturn(Optional.of(rolCambiado));

        RevalidationOutcome outcome = revalidateSession.execute(baseIdentity, authenticatedAt);

        assertThat(outcome.status()).isEqualTo(RevalidationOutcome.Status.ROLE_CHANGED);
        assertThat(outcome.updatedIdentity()).isNotNull();
        assertThat(outcome.updatedIdentity().role()).isEqualTo("COLABORADOR");
        assertThat(outcome.updatedIdentity().userId()).isEqualTo(userId);
        assertThat(outcome.updatedIdentity().organizationId()).isEqualTo(orgId);
        assertThat(outcome.updatedIdentity().normalizedEmail()).isEqualTo("ana@empresa.example");
    }
}
