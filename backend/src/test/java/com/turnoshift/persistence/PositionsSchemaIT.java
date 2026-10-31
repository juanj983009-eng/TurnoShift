package com.turnoshift.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de integración: verifica que la migración V12 (positions) se aplica correctamente
 * en un despliegue limpio y que se cumplen todas las restricciones de integridad y modelo
 * de datos corporativo para puestos (HU-06).
 */
@SpringBootTest
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: Schema puestos (V12 positions)")
class PositionsSchemaIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Autowired
    JdbcTemplate jdbc;

    // =========================================================================
    // Helpers
    // =========================================================================

    private String insertarOrganizacion(String code, String displayName) {
        String orgId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, code, displayName);
        return orgId;
    }

    private String insertarPuesto(String orgId, String code, String name) {
        String posId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO positions (id, organization_id, code, name)
                VALUES (?, ?, ?, ?)
                """, posId, orgId, code, name);
        return posId;
    }

    // =========================================================================
    // Test 1: Columnas, tipos, collation y defaults de positions
    // =========================================================================

    @Test
    @DisplayName("positions tiene las columnas, tipos, collation BIN2 y defaults esperados")
    void columnasTiposCollationYDefaults() {
        // Verificar que la tabla existe
        Integer tableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'positions' AND TABLE_TYPE = 'BASE TABLE'",
                Integer.class);
        assertThat(tableCount).isEqualTo(1);

        // Collation de code es Latin1_General_100_BIN2
        String collation = jdbc.queryForObject("""
                SELECT collation_name
                FROM sys.columns
                WHERE object_id = OBJECT_ID('positions')
                  AND name = 'code'
                """, String.class);
        assertThat(collation).isEqualTo("Latin1_General_100_BIN2");

        // Defaults al insertar fila mínima
        String orgId = insertarOrganizacion("ORG-DEF", "Org Defaults Ficticia");
        String posId = insertarPuesto(orgId, "DEV-01", "Desarrollador");

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT is_active, lock_version, created_at, updated_at FROM positions WHERE id = ?",
                posId);

        assertThat(fila.get("is_active")).isEqualTo(true);
        assertThat(((Number) fila.get("lock_version")).longValue()).isEqualTo(0L);
        assertThat(fila.get("created_at")).isNotNull();
        assertThat(fila.get("updated_at")).isNotNull();
    }

    // =========================================================================
    // Test 2: Mismo código permitido entre empresas distintas
    // =========================================================================

    @Test
    @DisplayName("Mismo código de puesto permitido en organizaciones diferentes")
    void mismoCodigoPermitidoEnEmpresasDiferentes() {
        String orgA = insertarOrganizacion("ORG-POS-A", "Org A Ficticia");
        String orgB = insertarOrganizacion("ORG-POS-B", "Org B Ficticia");

        insertarPuesto(orgA, "ENF-01", "Enfermero General");
        insertarPuesto(orgB, "ENF-01", "Enfermero General");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM positions WHERE code = 'ENF-01'",
                Integer.class);
        assertThat(count).isEqualTo(2);
    }

    // =========================================================================
    // Test 3: Código duplicado rechazado dentro de la misma organización
    // =========================================================================

    @Test
    @DisplayName("Código de puesto duplicado dentro de la misma organización es rechazado")
    void codigoDuplicadoRechazadoEnMismaEmpresa() {
        String orgA = insertarOrganizacion("ORG-DUP-P", "Org Duplicado Puesto");
        insertarPuesto(orgA, "MED-01", "Médico Titular");

        assertThatThrownBy(() -> insertarPuesto(orgA, "MED-01", "Médico Suplente"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_positions_org_code");
    }

    // =========================================================================
    // Test 4: FK rechaza organization_id inexistente
    // =========================================================================

    @Test
    @DisplayName("Puesto con organization_id inexistente es rechazado por FK")
    void organizationIdInexistenteRechazado() {
        String orgInexistente = UUID.randomUUID().toString();
        assertThatThrownBy(() -> insertarPuesto(orgInexistente, "ADM-01", "Administrativo"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_positions_organizations");
    }

    // =========================================================================
    // Test 5: Clave candidata empresarial (organization_id, id) existe y es única
    // =========================================================================

    @Test
    @DisplayName("Clave candidata empresarial (organization_id, id) está registrada como UNIQUE")
    void claveCandidataEmpresarialExiste() {
        Integer uqCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys.indexes i
                JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
                JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
                WHERE i.object_id = OBJECT_ID('positions')
                  AND i.name = 'uq_positions_org_id'
                  AND i.is_unique = 1
                """, Integer.class);
        assertThat(uqCount).isEqualTo(2); // 2 columnas en el índice compuesto
    }

    // =========================================================================
    // Test 6: Formato de código (2-32 caracteres, [A-Z0-9-], sin espacios)
    // =========================================================================

    @Test
    @DisplayName("Código menor a 2 caracteres rechazado por CHECK")
    void codigoMenorA2CaracteresRechazado() {
        String orgId = insertarOrganizacion("ORG-CHK-1", "Org Check 1");
        assertThatThrownBy(() -> insertarPuesto(orgId, "A", "Puesto A"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_positions_code_format");
    }

    @Test
    @DisplayName("Código con caracteres inválidos (minúsculas, espacios, símbolos) rechazado por CHECK")
    void codigoConCaracteresInvalidosRechazado() {
        String orgId = insertarOrganizacion("ORG-CHK-2", "Org Check 2");

        // Minúsculas no permitidas en collation BIN2 bajo el CHECK [A-Z0-9-]
        assertThatThrownBy(() -> insertarPuesto(orgId, "med-01", "Médico"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_positions_code_format");

        // Espacios internos o en bordes no permitidos
        assertThatThrownBy(() -> insertarPuesto(orgId, "MED 01", "Médico"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_positions_code_format");

        // Símbolos prohibidos
        assertThatThrownBy(() -> insertarPuesto(orgId, "MED_01", "Médico"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_positions_code_format");
    }

    // =========================================================================
    // Test 7: Nombre no vacío tras trim rechazado por CHECK
    // =========================================================================

    @Test
    @DisplayName("Nombre vacío o solo espacios en blanco rechazado por CHECK")
    void nombreVacioRechazado() {
        String orgId = insertarOrganizacion("ORG-CHK-3", "Org Check 3");

        assertThatThrownBy(() -> insertarPuesto(orgId, "POS-01", ""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_positions_name_not_empty");

        assertThatThrownBy(() -> insertarPuesto(orgId, "POS-02", "   "))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_positions_name_not_empty");
    }

    // =========================================================================
    // Test 8: Historial de Flyway contiene V1 a V14 sin alterar V1-V11
    // =========================================================================

    @Test
    @DisplayName("Historial de Flyway contiene V1 a V14 en orden")
    void historialContieneV1aV12() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(versiones).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
    }
}
