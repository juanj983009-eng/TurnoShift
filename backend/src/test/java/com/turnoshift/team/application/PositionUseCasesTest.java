package com.turnoshift.team.application;

import com.turnoshift.team.domain.Position;
import com.turnoshift.team.domain.PositionCodeAlreadyExistsException;
import com.turnoshift.team.domain.PositionNotFoundException;
import com.turnoshift.team.domain.PositionOptimisticLockException;
import com.turnoshift.team.domain.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: Position Application Use Cases")
class PositionUseCasesTest {

    @Mock
    private PositionRepository positionRepository;

    private Clock clock;
    private Instant now;

    private CreatePositionUseCase createPositionUseCase;
    private UpdatePositionUseCase updatePositionUseCase;
    private ChangePositionStatusUseCase changePositionStatusUseCase;
    private GetPositionsUseCase getPositionsUseCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID otherOrgId = UUID.randomUUID();
    private final UUID posId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-09-16T14:30:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);

        createPositionUseCase = new CreatePositionUseCase(positionRepository, clock);
        updatePositionUseCase = new UpdatePositionUseCase(positionRepository, clock);
        changePositionStatusUseCase = new ChangePositionStatusUseCase(positionRepository, clock);
        getPositionsUseCase = new GetPositionsUseCase(positionRepository);
    }

    // =========================================================================
    // CreatePositionUseCase
    // =========================================================================

    @Test
    @DisplayName("CreatePosition: éxito cuando el código no existe en la organización")
    void createPosition_exito() {
        when(positionRepository.findByCode(orgId, "ENF-01")).thenReturn(Optional.empty());

        Position created = createPositionUseCase.execute(orgId, "enf-01", "Enfermero");

        assertThat(created).isNotNull();
        assertThat(created.code()).isEqualTo("ENF-01");
        assertThat(created.name()).isEqualTo("Enfermero");
        assertThat(created.organizationId()).isEqualTo(orgId);
        assertThat(created.isActive()).isTrue();
        assertThat(created.lockVersion()).isEqualTo(0L);

        verify(positionRepository).insert(created);
    }

    @Test
    @DisplayName("CreatePosition: lanza PositionCodeAlreadyExistsException si la consulta previa detecta duplicado")
    void createPosition_codigoDuplicado_lanzaExcepcion() {
        Position existing = Position.reconstruct(UUID.randomUUID(), orgId, "ENF-01", "Enfermero", true, 0L, now, now);
        when(positionRepository.findByCode(orgId, "ENF-01")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> createPositionUseCase.execute(orgId, "ENF-01", "Otro Enfermero"))
                .isInstanceOf(PositionCodeAlreadyExistsException.class)
                .hasMessageContaining("ENF-01");

        verify(positionRepository, never()).insert(any());
    }

    @Test
    @DisplayName("CreatePosition: carrera de concurrencia al insertar código duplicado propagada por el repositorio")
    void createPosition_carreraCodigoDuplicadoEnRepositorio_lanzaExcepcion() {
        when(positionRepository.findByCode(orgId, "ENF-01")).thenReturn(Optional.empty());
        doThrow(new PositionCodeAlreadyExistsException("ENF-01"))
                .when(positionRepository).insert(any());

        assertThatThrownBy(() -> createPositionUseCase.execute(orgId, "ENF-01", "Enfermero"))
                .isInstanceOf(PositionCodeAlreadyExistsException.class)
                .hasMessageContaining("ENF-01");
    }

    // =========================================================================
    // UpdatePositionUseCase
    // =========================================================================

    @Test
    @DisplayName("UpdatePosition: éxito al cambiar nombre y retorna la versión persistida por el repositorio")
    void updatePosition_exito() {
        Position existing = Position.reconstruct(posId, orgId, "MED-01", "Médico", true, 2L, now, now);
        Instant serverPersistedTime = now.plusSeconds(5);
        Position persisted = Position.reconstruct(posId, orgId, "MED-01", "Médico Especialista", true, 3L, now, serverPersistedTime);

        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.of(existing));
        when(positionRepository.update(any(), eq(2L))).thenReturn(Optional.of(persisted));

        Position updated = updatePositionUseCase.execute(orgId, posId, "Médico Especialista", 2L);

        assertThat(updated).isSameAs(persisted);
        assertThat(updated.name()).isEqualTo("Médico Especialista");
        assertThat(updated.lockVersion()).isEqualTo(3L);
        assertThat(updated.updatedAt()).isEqualTo(serverPersistedTime);
        verify(positionRepository).update(any(), eq(2L));
    }

    @Test
    @DisplayName("UpdatePosition: lanza PositionNotFoundException si el puesto no pertenece a la organización")
    void updatePosition_noEncontradoEnTenant_lanzaExcepcion() {
        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updatePositionUseCase.execute(orgId, posId, "Nuevo Nombre", 0L))
                .isInstanceOf(PositionNotFoundException.class)
                .hasMessageContaining(posId.toString());

        verify(positionRepository, never()).update(any(), any(Long.class));
    }

    @Test
    @DisplayName("UpdatePosition: lanza PositionOptimisticLockException si la versión esperada no coincide")
    void updatePosition_versionDesactualizada_lanzaExcepcion() {
        Position existing = Position.reconstruct(posId, orgId, "MED-01", "Médico", true, 5L, now, now);
        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> updatePositionUseCase.execute(orgId, posId, "Nuevo Nombre", 4L))
                .isInstanceOf(PositionOptimisticLockException.class);

        verify(positionRepository, never()).update(any(), any(Long.class));
    }

    @Test
    @DisplayName("UpdatePosition: lanza PositionOptimisticLockException si repository.update devuelve Optional.empty()")
    void updatePosition_falloUpdateEnRepositorio_lanzaExcepcion() {
        Position existing = Position.reconstruct(posId, orgId, "MED-01", "Médico", true, 2L, now, now);
        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.of(existing));
        when(positionRepository.update(any(), eq(2L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updatePositionUseCase.execute(orgId, posId, "Nuevo Nombre", 2L))
                .isInstanceOf(PositionOptimisticLockException.class);
    }

    // =========================================================================
    // ChangePositionStatusUseCase
    // =========================================================================

    @Test
    @DisplayName("ChangePositionStatus: éxito al activar o desactivar puesto y retorna la versión persistida")
    void changePositionStatus_exito() {
        Position existing = Position.reconstruct(posId, orgId, "MED-01", "Médico", true, 1L, now, now);
        Instant serverPersistedTime = now.plusSeconds(8);
        Position persisted = Position.reconstruct(posId, orgId, "MED-01", "Médico", false, 2L, now, serverPersistedTime);

        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.of(existing));
        when(positionRepository.update(any(), eq(1L))).thenReturn(Optional.of(persisted));

        Position updated = changePositionStatusUseCase.execute(orgId, posId, false, 1L);

        assertThat(updated).isSameAs(persisted);
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.lockVersion()).isEqualTo(2L);
        assertThat(updated.updatedAt()).isEqualTo(serverPersistedTime);
        verify(positionRepository).update(any(), eq(1L));
    }

    @Test
    @DisplayName("ChangePositionStatus: lanza PositionNotFoundException si no existe en la organización")
    void changePositionStatus_noExiste_lanzaExcepcion() {
        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changePositionStatusUseCase.execute(orgId, posId, false, 0L))
                .isInstanceOf(PositionNotFoundException.class);

        verify(positionRepository, never()).update(any(), any(Long.class));
    }

    @Test
    @DisplayName("ChangePositionStatus: lanza PositionOptimisticLockException si versión desactualizada")
    void changePositionStatus_versionDesactualizada_lanzaExcepcion() {
        Position existing = Position.reconstruct(posId, orgId, "MED-01", "Médico", true, 3L, now, now);
        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> changePositionStatusUseCase.execute(orgId, posId, false, 1L))
                .isInstanceOf(PositionOptimisticLockException.class);

        verify(positionRepository, never()).update(any(), any(Long.class));
    }

    @Test
    @DisplayName("ChangePositionStatus: lanza PositionOptimisticLockException si repository.update devuelve Optional.empty()")
    void changePositionStatus_falloUpdateEnRepositorio_lanzaExcepcion() {
        Position existing = Position.reconstruct(posId, orgId, "MED-01", "Médico", true, 1L, now, now);
        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.of(existing));
        when(positionRepository.update(any(), eq(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changePositionStatusUseCase.execute(orgId, posId, false, 1L))
                .isInstanceOf(PositionOptimisticLockException.class);
    }

    // =========================================================================
    // GetPositionsUseCase
    // =========================================================================

    @Test
    @DisplayName("GetPositions: retorna lista aislada por tenant ordenada por código")
    void getPositions_retornaPuestosDelTenant() {
        Position p1 = Position.reconstruct(UUID.randomUUID(), orgId, "ADM-01", "Administrativo", true, 0L, now, now);
        Position p2 = Position.reconstruct(UUID.randomUUID(), orgId, "MED-01", "Médico", true, 0L, now, now);
        when(positionRepository.findAll(orgId, null)).thenReturn(List.of(p1, p2));

        List<Position> result = getPositionsUseCase.execute(orgId, null);

        assertThat(result).containsExactly(p1, p2);
        verify(positionRepository).findAll(orgId, null);
    }

    @Test
    @DisplayName("GetPositions: aplica filtro de activos correctamente")
    void getPositions_conFiltroActivos() {
        Position p1 = Position.reconstruct(UUID.randomUUID(), orgId, "ADM-01", "Administrativo", true, 0L, now, now);
        when(positionRepository.findAll(orgId, true)).thenReturn(List.of(p1));

        List<Position> result = getPositionsUseCase.execute(orgId, true);

        assertThat(result).containsExactly(p1);
        verify(positionRepository).findAll(orgId, true);
    }
}
