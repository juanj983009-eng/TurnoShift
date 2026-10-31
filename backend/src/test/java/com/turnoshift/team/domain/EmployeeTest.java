package com.turnoshift.team.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: Dominio Employee")
class EmployeeTest {

    private final UUID orgId = UUID.randomUUID();
    private final UUID posId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-16T12:00:00Z");

    @Test
    @DisplayName("Creación válida produce empleado activo con lockVersion 0")
    void creacionValida() {
        Employee employee = Employee.create(orgId, "  EMP-001  ", "  Ana Gomez  ", posId, siteId, now);

        assertThat(employee.id()).isNotNull();
        assertThat(employee.organizationId()).isEqualTo(orgId);
        assertThat(employee.code()).isEqualTo("EMP-001");
        assertThat(employee.displayName()).isEqualTo("Ana Gomez");
        assertThat(employee.positionId()).isEqualTo(posId);
        assertThat(employee.siteId()).isEqualTo(siteId);
        assertThat(employee.isActive()).isTrue();
        assertThat(employee.lockVersion()).isEqualTo(0L);
        assertThat(employee.createdAt()).isEqualTo(now);
        assertThat(employee.updatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Código nulo, vacío o mayor a 50 caracteres es rechazado")
    void validacionCodigo() {
        assertThatThrownBy(() -> Employee.validateAndNormalizeCode(null))
                .isInstanceOf(InvalidEmployeeDataException.class);

        assertThatThrownBy(() -> Employee.validateAndNormalizeCode("   "))
                .isInstanceOf(InvalidEmployeeDataException.class);

        String tooLong = "A".repeat(51);
        assertThatThrownBy(() -> Employee.validateAndNormalizeCode(tooLong))
                .isInstanceOf(InvalidEmployeeDataException.class);

        assertThat(Employee.validateAndNormalizeCode("  COD-123  ")).isEqualTo("COD-123");
    }

    @Test
    @DisplayName("Nombre visible nulo, vacío o mayor a 150 caracteres es rechazado")
    void validacionDisplayName() {
        assertThatThrownBy(() -> Employee.validateAndNormalizeDisplayName(null))
                .isInstanceOf(InvalidEmployeeDataException.class);

        assertThatThrownBy(() -> Employee.validateAndNormalizeDisplayName("   "))
                .isInstanceOf(InvalidEmployeeDataException.class);

        String tooLong = "A".repeat(151);
        assertThatThrownBy(() -> Employee.validateAndNormalizeDisplayName(tooLong))
                .isInstanceOf(InvalidEmployeeDataException.class);

        assertThat(Employee.validateAndNormalizeDisplayName("  Carlos Ruiz  ")).isEqualTo("Carlos Ruiz");
    }

    @Test
    @DisplayName("Actualizar nombre y puesto modifica updatedAt e incrementa versión conceptualmente")
    void actualizacion() {
        Employee emp = Employee.create(orgId, "EMP-01", "Nombre 1", posId, siteId, now);
        UUID nuevoPosId = UUID.randomUUID();
        Instant next = now.plusSeconds(3600);

        Employee updated = emp.update("  Nombre 2  ", nuevoPosId, next);

        assertThat(updated.displayName()).isEqualTo("Nombre 2");
        assertThat(updated.positionId()).isEqualTo(nuevoPosId);
        assertThat(updated.updatedAt()).isEqualTo(next);
    }

    @Test
    @DisplayName("Cambio de estado actualiza isActive y updatedAt")
    void cambioEstado() {
        Employee emp = Employee.create(orgId, "EMP-01", "Nombre 1", posId, siteId, now);
        Instant next = now.plusSeconds(3600);

        Employee deactivated = emp.changeStatus(false, next);
        assertThat(deactivated.isActive()).isFalse();
        assertThat(deactivated.updatedAt()).isEqualTo(next);

        Employee activated = deactivated.changeStatus(true, next.plusSeconds(3600));
        assertThat(activated.isActive()).isTrue();
    }
}
