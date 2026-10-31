package com.turnoshift.security.idempotency;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@DisplayName("IT: Idempotencia empresarial con SQL Server real y Testcontainers")
class BusinessIdempotencyIT {

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
    private BusinessIdempotentOperationExecutor executor;

    @Autowired
    private BusinessIdempotencyRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID orgA;
    private UUID userA;
    private UUID orgB;
    private UUID userB;

    record SampleResponse(String id, String name) {}

    static class SampleCommand implements BusinessIdempotentCommand<SampleResponse> {
        private final UUID orgId;
        private final UUID userId;
        private final UUID key;
        private final String code;
        private final String name;

        SampleCommand(UUID orgId, UUID userId, UUID key, String code, String name) {
            this.orgId = orgId;
            this.userId = userId;
            this.key = key;
            this.code = code;
            this.name = name;
        }

        @Override
        public UUID organizationId() { return orgId; }

        @Override
        public UUID actorUserId() { return userId; }

        @Override
        public UUID idempotencyKey() { return key; }

        @Override
        public String method() { return "POST"; }

        @Override
        public String path() { return "/api/v1/positions"; }

        @Override
        public String schemaVersion() { return "1.0"; }

        @Override
        public String canonicalPayload() {
            StringBuilder sb = new StringBuilder();
            BusinessFingerprintCalculator.appendField(sb, code);
            BusinessFingerprintCalculator.appendField(sb, name);
            return sb.toString();
        }

        @Override
        public int httpStatus(SampleResponse response) { return 201; }

        @Override
        public String sanitizeResponseBody(SampleResponse response) {
            return "{\"id\":\"" + response.id() + "\",\"name\":\"" + response.name() + "\"}";
        }

        @Override
        public SampleResponse deserializeResponseBody(String responseBody) {
            // Deserialización controlada
            String id = responseBody.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
            String name = responseBody.replaceAll(".*\"name\":\"([^\"]+)\".*", "$1");
            return new SampleResponse(id, name);
        }
    }

    @BeforeEach
    void setUpData() {
        orgA = UUID.randomUUID();
        userA = UUID.randomUUID();
        orgB = UUID.randomUUID();
        userB = UUID.randomUUID();

        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);

        String orgACode = "ORG-IDEM-A-" + suffix;
        String orgBCode = "ORG-IDEM-B-" + suffix;

        // Crear organizaciones
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Empresa Idem A', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgA, orgACode);

        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Empresa Idem B', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgB, orgBCode);

        // Crear usuarios en sus respectivas organizaciones
        jdbc.update("""
                INSERT INTO app_users (organization_id, id, email, normalized_email, display_name,
                    password_hash, role_code)
                VALUES (?, ?, 'adminA@idem.com', 'admina@idem.com', 'Admin A', 'hash', 'ADMINISTRADOR')
                """, orgA, userA);

        jdbc.update("""
                INSERT INTO app_users (organization_id, id, email, normalized_email, display_name,
                    password_hash, role_code)
                VALUES (?, ?, 'adminB@idem.com', 'adminb@idem.com', 'Admin B', 'hash', 'ADMINISTRADOR')
                """, orgB, userB);
    }

    @Test
    @DisplayName("1. Clave nueva: ejecuta efecto, persiste COMPLETED/SUCCEEDED y actor_operator_id es NULL")
    void claveNueva_ejecutaEfectoYGuardaOperacionEmpresarial() {
        UUID key = UUID.randomUUID();
        SampleCommand cmd = new SampleCommand(orgA, userA, key, "MED-01", "Médico");

        AtomicInteger efectoEjecutado = new AtomicInteger(0);
        String posId = UUID.randomUUID().toString();

        BusinessIdempotencyResult<SampleResponse> result = executor.execute(cmd, () -> {
            efectoEjecutado.incrementAndGet();
            return new SampleResponse(posId, "Médico");
        });

        assertThat(efectoEjecutado.get()).isEqualTo(1);
        assertThat(result).isInstanceOf(BusinessIdempotencyResult.Proceed.class);

        // Verificar registro en api_operations
        Map<String, Object> op = jdbc.queryForMap("""
                SELECT organization_id, actor_user_id, actor_operator_id, status, outcome,
                       http_status, response_body, completed_at, expires_at
                FROM api_operations
                WHERE organization_id = ? AND actor_user_id = ? AND idempotency_key = ?
                """, orgA, userA, key);

        assertThat(UUID.fromString(op.get("organization_id").toString())).isEqualTo(orgA);
        assertThat(UUID.fromString(op.get("actor_user_id").toString())).isEqualTo(userA);
        assertThat(op.get("actor_operator_id")).isNull();
        assertThat(op.get("status")).isEqualTo("COMPLETED");
        assertThat(op.get("outcome")).isEqualTo("SUCCEEDED");
        assertThat(((Number) op.get("http_status")).intValue()).isEqualTo(201);
        assertThat((String) op.get("response_body")).contains(posId);
        assertThat(op.get("completed_at")).isNotNull();
        assertThat(op.get("expires_at")).isNotNull();
    }

    @Test
    @DisplayName("2. Misma clave + misma huella: devuelve Replay sin re-ejecutar y mantiene una sola fila")
    void mismaClaveMismaHuella_retornaReplaySinReejecutar() {
        UUID key = UUID.randomUUID();
        SampleCommand cmd = new SampleCommand(orgA, userA, key, "ENF-01", "Enfermero");
        AtomicInteger efecto = new AtomicInteger(0);

        BusinessIdempotencyResult<SampleResponse> res1 = executor.execute(cmd, () -> {
            efecto.incrementAndGet();
            return new SampleResponse("ID-1", "Enfermero");
        });
        assertThat(res1).isInstanceOf(BusinessIdempotencyResult.Proceed.class);
        assertThat(efecto.get()).isEqualTo(1);

        // Segundo intento con misma clave y misma huella
        BusinessIdempotencyResult<SampleResponse> res2 = executor.execute(cmd, () -> {
            efecto.incrementAndGet();
            return new SampleResponse("ID-2", "NoDeberia");
        });

        assertThat(efecto.get()).isEqualTo(1); // No volvió a ejecutarse
        assertThat(res2).isInstanceOf(BusinessIdempotencyResult.Replay.class);
        BusinessIdempotencyResult.Replay<SampleResponse> replay = (BusinessIdempotencyResult.Replay<SampleResponse>) res2;
        assertThat(replay.response().id()).isEqualTo("ID-1");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_operations WHERE organization_id = ? AND actor_user_id = ? AND idempotency_key = ?",
                Integer.class, orgA, userA, key);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("3. Misma clave + huella diferente: devuelve KeyReused sin ejecutar ni alterar fila original")
    void mismaClaveHuellaDiferente_retornaKeyReusedSinAlterar() {
        UUID key = UUID.randomUUID();
        SampleCommand cmdOriginal = new SampleCommand(orgA, userA, key, "DIR-01", "Director");
        SampleCommand cmdModificado = new SampleCommand(orgA, userA, key, "DIR-02", "Director Modificado");

        AtomicInteger efecto = new AtomicInteger(0);

        executor.execute(cmdOriginal, () -> {
            efecto.incrementAndGet();
            return new SampleResponse("DIR-ID", "Director");
        });
        assertThat(efecto.get()).isEqualTo(1);

        // Intento con misma clave y distinta huella
        BusinessIdempotencyResult<SampleResponse> resReused = executor.execute(cmdModificado, () -> {
            efecto.incrementAndGet();
            return new SampleResponse("MOD-ID", "Modificado");
        });

        assertThat(efecto.get()).isEqualTo(1); // No se ejecutó
        assertThat(resReused).isInstanceOf(BusinessIdempotencyResult.KeyReused.class);

        // Comprobar que el response_body original se mantiene intacto
        String body = jdbc.queryForObject(
                "SELECT response_body FROM api_operations WHERE organization_id = ? AND actor_user_id = ? AND idempotency_key = ?",
                String.class, orgA, userA, key);
        assertThat(body).contains("DIR-ID");
    }

    @Test
    @DisplayName("4. Clave vencida (> 24h): devuelve Expired sin re-ejecutar")
    void claveVencida_retornaExpiredSinReejecutar() {
        UUID key = UUID.randomUUID();
        SampleCommand cmd = new SampleCommand(orgA, userA, key, "AUX-01", "Auxiliar");
        AtomicInteger efecto = new AtomicInteger(0);

        executor.execute(cmd, () -> {
            efecto.incrementAndGet();
            return new SampleResponse("AUX-ID", "Auxiliar");
        });

        // Simular expiración actualizando created_at y expires_at al pasado conservando created_at < expires_at < now
        jdbc.update("""
                UPDATE api_operations
                SET created_at = DATEADD(hour, -26, SYSUTCDATETIME()),
                    expires_at = DATEADD(hour, -2, SYSUTCDATETIME())
                WHERE organization_id = ? AND actor_user_id = ? AND idempotency_key = ?
                """, orgA, userA, key);

        BusinessIdempotencyResult<SampleResponse> resExpired = executor.execute(cmd, () -> {
            efecto.incrementAndGet();
            return new SampleResponse("AUX-ID-2", "Auxiliar 2");
        });

        assertThat(efecto.get()).isEqualTo(1); // No se ejecutó
        assertThat(resExpired).isInstanceOf(BusinessIdempotencyResult.Expired.class);
    }

    @Test
    @DisplayName("5. Dos solicitudes concurrentes reales: sp_getapplock serializa, una ejecuta y la otra recibe Replay")
    void concurrenciaReal_unaEjecutaEfectoYOtraObtieneReplay() throws Exception {
        UUID key = UUID.randomUUID();
        SampleCommand cmd = new SampleCommand(orgA, userA, key, "CONC-01", "Puesto Concurrente");

        AtomicInteger efectoEjecutado = new AtomicInteger(0);
        int hilos = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(hilos);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<BusinessIdempotencyResult<SampleResponse>>> futures = new ArrayList<>();

        for (int i = 0; i < hilos; i++) {
            final int id = i;
            futures.add(executorService.submit(() -> {
                startLatch.await(); // Sincronizar el arranque exacto de ambos hilos
                return executor.execute(cmd, () -> {
                    efectoEjecutado.incrementAndGet();
                    try {
                        Thread.sleep(150); // Simular trabajo bajo el lock
                    } catch (InterruptedException ignored) {}
                    return new SampleResponse("CONC-RES-" + id, "Puesto Concurrente");
                });
            }));
        }

        // Liberar hilos concurrentes
        startLatch.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        List<BusinessIdempotencyResult<SampleResponse>> results = new ArrayList<>();
        for (Future<BusinessIdempotencyResult<SampleResponse>> future : futures) {
            results.add(future.get());
        }

        assertThat(efectoEjecutado.get())
                .as("El efecto de negocio solo debe ejecutarse exactamente una vez en concurrencia")
                .isEqualTo(1);

        long countProceed = results.stream().filter(r -> r instanceof BusinessIdempotencyResult.Proceed).count();
        long countReplay = results.stream().filter(r -> r instanceof BusinessIdempotencyResult.Replay).count();

        assertThat(countProceed).as("Exactamente una solicitud debe proceder").isEqualTo(1);
        assertThat(countReplay).as("La solicitud concurrente debe recibir Replay").isEqualTo(1);

        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_operations WHERE organization_id = ? AND actor_user_id = ? AND idempotency_key = ?",
                Integer.class, orgA, userA, key);
        assertThat(rowCount).as("Debe existir una única fila en api_operations").isEqualTo(1);
    }

    @Test
    @DisplayName("6. Aislamiento: misma clave en organizaciones/usuarios distintos permitida; FK rechaza usuario ajeno")
    void aislamientoEmpresarial_mismaClaveEnOrganizacionesODistintosUsuariosPermitidoYFkRechazaUsuarioAjeno() {
        UUID keyCompartida = UUID.randomUUID();

        // Org A, User A usa keyCompartida
        SampleCommand cmdA = new SampleCommand(orgA, userA, keyCompartida, "P-01", "Puesto Org A");
        BusinessIdempotencyResult<SampleResponse> resA = executor.execute(cmdA, () -> new SampleResponse("A", "Puesto A"));
        assertThat(resA).isInstanceOf(BusinessIdempotencyResult.Proceed.class);

        // Org B, User B usa la MISMA keyCompartida -> permitido y aislado
        SampleCommand cmdB = new SampleCommand(orgB, userB, keyCompartida, "P-01", "Puesto Org B");
        BusinessIdempotencyResult<SampleResponse> resB = executor.execute(cmdB, () -> new SampleResponse("B", "Puesto B"));
        assertThat(resB).isInstanceOf(BusinessIdempotencyResult.Proceed.class);

        Integer countTotal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_operations WHERE idempotency_key = ?",
                Integer.class, keyCompartida);
        assertThat(countTotal).as("La misma clave debe poder coexistir en organizaciones diferentes").isEqualTo(2);

        // FK compuesta fk_api_operations_user debe impedir que userA (de orgA) se inserte bajo orgB
        UUID keyInvalida = UUID.randomUUID();
        byte[] fp = new byte[32];
        assertThatThrownBy(() ->
                repository.saveCompletedOperation(
                        orgB, userA, keyInvalida, "POST", "/api/test", fp, "1.0",
                        "SUCCEEDED", 200, "application/json", "{}",
                        null, java.time.Instant.now().plusSeconds(3600))
        )
                .isInstanceOf(BusinessIdempotencyDataAccessException.class);
    }

    @Test
    @DisplayName("7. Rollback: si el efecto lanza excepción técnica, no queda efecto ni fila en api_operations")
    void rollback_falloEnEfectoNoDejaOperacionNiEfectos() {
        UUID key = UUID.randomUUID();
        SampleCommand cmd = new SampleCommand(orgA, userA, key, "FAIL-01", "Puesto Falla");

        assertThatThrownBy(() -> executor.execute(cmd, () -> {
            // Efecto que inserta y luego falla
            jdbc.update("INSERT INTO positions (id, organization_id, code, name) VALUES (NEWID(), ?, 'ROLL-01', 'Rollback')", orgA);
            throw new RuntimeException("Fallo técnico forzado");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Fallo técnico forzado");

        // Verificar que la inserción de negocio fue revertida
        Integer posCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM positions WHERE organization_id = ? AND code = 'ROLL-01'",
                Integer.class, orgA);
        assertThat(posCount).isEqualTo(0);

        // Verificar que no quedó fila en api_operations
        Integer opCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_operations WHERE organization_id = ? AND actor_user_id = ? AND idempotency_key = ?",
                Integer.class, orgA, userA, key);
        assertThat(opCount).isEqualTo(0);
    }
}
