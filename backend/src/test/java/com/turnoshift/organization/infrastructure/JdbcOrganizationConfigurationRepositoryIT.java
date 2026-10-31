package com.turnoshift.organization.infrastructure;

import com.turnoshift.organization.application.GetOrganizationConfigurationUseCase;
import com.turnoshift.organization.application.UpdateActiveSiteNameUseCase;
import com.turnoshift.organization.application.UpdateOrganizationDisplayNameUseCase;
import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.OrganizationConfiguration;
import com.turnoshift.organization.domain.OrganizationOptimisticLockException;
import com.turnoshift.organization.domain.OrganizationView;
import com.turnoshift.organization.domain.SiteOptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@DisplayName("IT: Persistencia JDBC de Configuración de Organización y Sede Única con SQL Server")
class JdbcOrganizationConfigurationRepositoryIT {

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
    private JdbcOrganizationConfigurationRepository repository;

    @Autowired
    private GetOrganizationConfigurationUseCase getConfigurationUseCase;

    @Autowired
    private UpdateOrganizationDisplayNameUseCase updateDisplayNameUseCase;

    @Autowired
    private UpdateActiveSiteNameUseCase updateSiteNameUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID orgA;
    private UUID orgB;
    private UUID siteA;
    private UUID siteB;
    private String orgACode;
    private String orgBCode;
    private String siteACode;
    private String siteBCode;

    @BeforeEach
    void setUpData() {
        orgA = UUID.randomUUID();
        orgB = UUID.randomUUID();
        siteA = UUID.randomUUID();
        siteB = UUID.randomUUID();

        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        orgACode = "ORG-CFG-A-" + suffix;
        orgBCode = "ORG-CFG-B-" + suffix;
        siteACode = "SITE-A-" + suffix;
        siteBCode = "SITE-B-" + suffix;

        // Sembrar Org A
        jdbc.update("""
                INSERT INTO organizations (
                    id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites, lock_version
                ) VALUES (
                    ?, ?, 'Organización Alfa', 'ACTIVA', 'America/Bogota',
                    15, 1, 0
                )
                """, orgA, orgACode);

        jdbc.update("""
                INSERT INTO sites (
                    organization_id, id, code, name, is_active, lock_version
                ) VALUES (
                    ?, ?, ?, 'Sede Alfa Central', 1, 0
                )
                """, orgA, siteA, siteACode);

        // Sembrar Org B
        jdbc.update("""
                INSERT INTO organizations (
                    id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites, lock_version
                ) VALUES (
                    ?, ?, 'Organización Beta', 'ACTIVA', 'America/Lima',
                    25, 1, 0
                )
                """, orgB, orgBCode);

        jdbc.update("""
                INSERT INTO sites (
                    organization_id, id, code, name, is_active, lock_version
                ) VALUES (
                    ?, ?, ?, 'Sede Beta Norte', 1, 0
                )
                """, orgB, siteB, siteBCode);
    }

    @Test
    @DisplayName("1. Lectura aislada: dos organizaciones consultan su configuración sin mezclar datos")
    void lecturaAislada_dosOrganizacionesNoMezclanDatos() {
        OrganizationConfiguration configA = getConfigurationUseCase.execute(orgA);
        assertThat(configA.organization().id()).isEqualTo(orgA);
        assertThat(configA.organization().displayName()).isEqualTo("Organización Alfa");
        assertThat(configA.organization().timezoneId()).isEqualTo("America/Bogota");
        assertThat(configA.organization().lockVersion()).isEqualTo(0L);
        assertThat(configA.activeSite().id()).isEqualTo(siteA);
        assertThat(configA.activeSite().code()).isEqualTo(siteACode);
        assertThat(configA.activeSite().name()).isEqualTo("Sede Alfa Central");
        assertThat(configA.activeSite().lockVersion()).isEqualTo(0L);

        OrganizationConfiguration configB = getConfigurationUseCase.execute(orgB);
        assertThat(configB.organization().id()).isEqualTo(orgB);
        assertThat(configB.organization().displayName()).isEqualTo("Organización Beta");
        assertThat(configB.organization().timezoneId()).isEqualTo("America/Lima");
        assertThat(configB.organization().lockVersion()).isEqualTo(0L);
        assertThat(configB.activeSite().id()).isEqualTo(siteB);
        assertThat(configB.activeSite().code()).isEqualTo(siteBCode);
        assertThat(configB.activeSite().name()).isEqualTo("Sede Beta Norte");
        assertThat(configB.activeSite().lockVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("2. Actualización de displayName de organización: incrementa versión y actualiza updated_at")
    void actualizacionDisplayName_incrementaVersionYActualizaTimestamp() {
        OrganizationView updated = updateDisplayNameUseCase.execute(orgA, "Alfa Renovada S.A.", 0L);

        assertThat(updated.id()).isEqualTo(orgA);
        assertThat(updated.displayName()).isEqualTo("Alfa Renovada S.A.");
        assertThat(updated.timezoneId()).isEqualTo("America/Bogota");
        assertThat(updated.lockVersion()).isEqualTo(1L);

        // Verificación directa en base de datos
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT display_name, lock_version, timezone_id, code, service_status, max_active_employees, max_active_sites FROM organizations WHERE id = ?",
                orgA
        );
        assertThat(row.get("display_name")).isEqualTo("Alfa Renovada S.A.");
        assertThat(((Number) row.get("lock_version")).longValue()).isEqualTo(1L);
        assertThat(row.get("timezone_id")).isEqualTo("America/Bogota");
        assertThat(row.get("code")).isEqualTo(orgACode);
        assertThat(row.get("service_status")).isEqualTo("ACTIVA");
        assertThat(((Number) row.get("max_active_employees")).intValue()).isEqualTo(15);
        assertThat(((Number) row.get("max_active_sites")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("3. Actualización de nombre de sede activa: incrementa versión y persiste en base de datos")
    void actualizacionNombreSede_incrementaVersionYPersiste() {
        ActiveSiteView updated = updateSiteNameUseCase.execute(orgA, "Sede Alfa Principal Nueva", 0L);

        assertThat(updated.id()).isEqualTo(siteA);
        assertThat(updated.code()).isEqualTo(siteACode);
        assertThat(updated.name()).isEqualTo("Sede Alfa Principal Nueva");
        assertThat(updated.lockVersion()).isEqualTo(1L);

        // Verificación directa en base de datos
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT name, lock_version, code, is_active FROM sites WHERE id = ?",
                siteA
        );
        assertThat(row.get("name")).isEqualTo("Sede Alfa Principal Nueva");
        assertThat(((Number) row.get("lock_version")).longValue()).isEqualTo(1L);
        assertThat(row.get("code")).isEqualTo(siteACode);
        assertThat(row.get("is_active")).isEqualTo(true);
    }

    @Test
    @DisplayName("4. Stale version: rechaza actualización concurrente tanto para organización como para sede")
    void versionDesactualizada_rechazaActualizacionConcurrente() {
        // Incrementar primero la organización
        updateDisplayNameUseCase.execute(orgA, "Nombre Actualizado 1", 0L);

        // Reintento con versión obsoleta 0L debe fallar
        assertThatThrownBy(() -> updateDisplayNameUseCase.execute(orgA, "Nombre Actualizado 2", 0L))
                .isInstanceOf(OrganizationOptimisticLockException.class)
                .satisfies(e -> {
                    OrganizationOptimisticLockException ole = (OrganizationOptimisticLockException) e;
                    assertThat(ole.getOrganizationId()).isEqualTo(orgA);
                    assertThat(ole.getExpectedLockVersion()).isEqualTo(0L);
                });

        // Intentar update directo en repositorio con versión obsoleta devuelve Optional.empty()
        Optional<OrganizationView> repoDirecto = repository.updateOrganizationDisplayName(orgA, "Fallo Directo", 0L);
        assertThat(repoDirecto).isEmpty();

        // Incrementar primero la sede
        updateSiteNameUseCase.execute(orgA, "Sede Actualizada 1", 0L);

        // Reintento de sede con versión obsoleta 0L debe fallar
        assertThatThrownBy(() -> updateSiteNameUseCase.execute(orgA, "Sede Actualizada 2", 0L))
                .isInstanceOf(SiteOptimisticLockException.class)
                .satisfies(e -> {
                    SiteOptimisticLockException sole = (SiteOptimisticLockException) e;
                    assertThat(sole.getSiteId()).isEqualTo(siteA);
                    assertThat(sole.getExpectedLockVersion()).isEqualTo(0L);
                });

        // Intentar update directo de sede en repositorio con versión obsoleta devuelve Optional.empty()
        Optional<ActiveSiteView> repoDirectoSede = repository.updateActiveSiteName(orgA, "Sede Fallo", 0L);
        assertThat(repoDirectoSede).isEmpty();
    }

    @Test
    @DisplayName("5. Inmutabilidad de campos críticos: timezone_id, code, service_status, capacidades e is_active no se alteran")
    void camposCriticos_permanecenEstrictamenteInmutables() {
        updateDisplayNameUseCase.execute(orgA, "Nuevo Display Name", 0L);
        updateSiteNameUseCase.execute(orgA, "Nuevo Site Name", 0L);

        Map<String, Object> orgRow = jdbc.queryForMap(
                "SELECT code, service_status, timezone_id, max_active_employees, max_active_sites, current_policy_version_id FROM organizations WHERE id = ?",
                orgA
        );
        assertThat(orgRow.get("code")).isEqualTo(orgACode);
        assertThat(orgRow.get("service_status")).isEqualTo("ACTIVA");
        assertThat(orgRow.get("timezone_id")).isEqualTo("America/Bogota");
        assertThat(((Number) orgRow.get("max_active_employees")).intValue()).isEqualTo(15);
        assertThat(((Number) orgRow.get("max_active_sites")).intValue()).isEqualTo(1);
        assertThat(orgRow.get("current_policy_version_id")).isNull();

        Map<String, Object> siteRow = jdbc.queryForMap(
                "SELECT code, is_active FROM sites WHERE id = ?",
                siteA
        );
        assertThat(siteRow.get("code")).isEqualTo(siteACode);
        assertThat(siteRow.get("is_active")).isEqualTo(true);
    }

    @Test
    @DisplayName("6. Aislamiento de actualización: actualizar sede o nombre de Org A jamás afecta a Org B")
    void actualizacionDeOrgA_nuncaAfectaOrgB() {
        // Actualizar A
        updateDisplayNameUseCase.execute(orgA, "Alfa Modificada", 0L);
        updateSiteNameUseCase.execute(orgA, "Sede Alfa Modificada", 0L);

        // Verificar que B permanece exactamente con sus valores originales
        OrganizationConfiguration configB = getConfigurationUseCase.execute(orgB);
        assertThat(configB.organization().displayName()).isEqualTo("Organización Beta");
        assertThat(configB.organization().lockVersion()).isEqualTo(0L);
        assertThat(configB.organization().timezoneId()).isEqualTo("America/Lima");
        assertThat(configB.activeSite().name()).isEqualTo("Sede Beta Norte");
        assertThat(configB.activeSite().lockVersion()).isEqualTo(0L);

        // Intentar actualizar sede de B enviando orgA como tenant: falla y no modifica B
        Optional<ActiveSiteView> intromision = repository.updateActiveSiteName(orgA, "Intrusión", 0L);
        // Debe afectar a la sede de A (si coincidiera versión) pero nunca a B
        Map<String, Object> siteBRow = jdbc.queryForMap("SELECT name, lock_version FROM sites WHERE id = ?", siteB);
        assertThat(siteBRow.get("name")).isEqualTo("Sede Beta Norte");
        assertThat(((Number) siteBRow.get("lock_version")).longValue()).isEqualTo(0L);
    }
}
