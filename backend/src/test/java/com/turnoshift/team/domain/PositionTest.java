package com.turnoshift.team.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: Position Domain Entity")
class PositionTest {

    private final UUID orgId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-16T14:00:00Z");

    @Test
    @DisplayName("Creación exitosa: normaliza código con Locale.ROOT y trim de nombre")
    void creacionExitosa_normalizaCodigoYNombre() {
        Position pos = Position.create(orgId, "  med-01  ", "  Médico General  ", now);

        assertThat(pos.id()).isNotNull();
        assertThat(pos.organizationId()).isEqualTo(orgId);
        assertThat(pos.code()).isEqualTo("MED-01");
        assertThat(pos.name()).isEqualTo("Médico General");
        assertThat(pos.isActive()).isTrue();
        assertThat(pos.lockVersion()).isEqualTo(0L);
        assertThat(pos.createdAt()).isEqualTo(now);
        assertThat(pos.updatedAt()).isEqualTo(now);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "A", "CODIGO-QUE-SUPERA-LOS-TREINTA-Y-DOS-CARACTERES-INVALIDO", "MED 01", "MED_01", "MED.01", "MED@01", "ENF/01"})
    @DisplayName("Códigos inválidos: longitudes fuera de [2, 32], espacios o caracteres no permitidos")
    void codigosInvalidos_lanzanExcepcion(String invalidCode) {
        assertThatThrownBy(() -> Position.create(orgId, invalidCode, "Nombre Válido", now))
                .isInstanceOf(InvalidPositionDataException.class);
    }

    @Test
    @DisplayName("Código nulo lanza excepción")
    void codigoNulo_lanzaExcepcion() {
        assertThatThrownBy(() -> Position.create(orgId, null, "Nombre Válido", now))
                .isInstanceOf(InvalidPositionDataException.class);
    }

    @Test
    @DisplayName("OrganizationId nulo lanza excepción")
    void organizationIdNulo_lanzaExcepcion() {
        assertThatThrownBy(() -> Position.create(null, "MED-01", "Nombre Válido", now))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Nombre vacío o solo espacios en blanco lanza excepción")
    void nombreVacio_lanzaExcepcion(String invalidName) {
        assertThatThrownBy(() -> Position.create(orgId, "MED-01", invalidName, now))
                .isInstanceOf(InvalidPositionDataException.class);
    }

    @Test
    @DisplayName("Nombre nulo lanza excepción")
    void nombreNulo_lanzaExcepcion() {
        assertThatThrownBy(() -> Position.create(orgId, "MED-01", null, now))
                .isInstanceOf(InvalidPositionDataException.class);
    }

    @Test
    @DisplayName("Nombre con más de 100 caracteres lanza excepción")
    void nombreMayorA100_lanzaExcepcion() {
        String longName = "A".repeat(101);
        assertThatThrownBy(() -> Position.create(orgId, "MED-01", longName, now))
                .isInstanceOf(InvalidPositionDataException.class);
    }

    @Test
    @DisplayName("updateName: actualiza nombre, preserva identidad, incrementa lockVersion y actualiza updatedAt")
    void updateName_actualizaNombreEIncrementaVersion() {
        Position pos = Position.create(orgId, "MED-01", "Médico General", now);
        Instant later = now.plusSeconds(300);

        Position updated = pos.updateName("  Médico Especialista  ", later);

        assertThat(updated.id()).isEqualTo(pos.id());
        assertThat(updated.organizationId()).isEqualTo(pos.organizationId());
        assertThat(updated.code()).isEqualTo("MED-01");
        assertThat(updated.name()).isEqualTo("Médico Especialista");
        assertThat(updated.isActive()).isTrue();
        assertThat(updated.lockVersion()).isEqualTo(1L);
        assertThat(updated.createdAt()).isEqualTo(now);
        assertThat(updated.updatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("changeStatus: desactiva/activa, preserva identidad, incrementa lockVersion y actualiza updatedAt")
    void changeStatus_cambiaEstadoEIncrementaVersion() {
        Position pos = Position.create(orgId, "MED-01", "Médico General", now);
        Instant later = now.plusSeconds(600);

        Position inactive = pos.changeStatus(false, later);

        assertThat(inactive.id()).isEqualTo(pos.id());
        assertThat(inactive.organizationId()).isEqualTo(pos.organizationId());
        assertThat(inactive.code()).isEqualTo("MED-01");
        assertThat(inactive.name()).isEqualTo(pos.name());
        assertThat(inactive.isActive()).isFalse();
        assertThat(inactive.lockVersion()).isEqualTo(1L);
        assertThat(inactive.createdAt()).isEqualTo(now);
        assertThat(inactive.updatedAt()).isEqualTo(later);
    }
}
