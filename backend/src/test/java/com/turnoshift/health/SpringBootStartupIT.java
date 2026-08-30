package com.turnoshift.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración: verifica que el contexto Spring Boot arranca correctamente
 * con Flyway integrado contra SQL Server 2022 efímero, y que el endpoint
 * de salud responde HTTP 200 OK.
 *
 * <p>Esta prueba complementa a {@link com.turnoshift.persistence.OrganizationsSitesSchemaIT}:
 * mientras aquella prueba las restricciones de persistencia mediante Flyway manual,
 * esta verifica que la auto-configuración de Spring Boot (Flyway + DataSource) funciona
 * de extremo a extremo.
 *
 * <p><b>Alcance (I0 — arranque limpio):</b>
 * <ul>
 *   <li>Spring Boot arranca sin errores con Flyway sobre SQL Server efímero.</li>
 *   <li>GET /health responde 200 OK con {"status":"OK"}.</li>
 * </ul>
 *
 * <p><b>Requisito:</b> Docker disponible. SQL Server 2022 Developer Edition (solo pruebas).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("IT: Arranque Spring Boot + Flyway + SQL Server 2022")
class SpringBootStartupIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    /**
     * Inyecta la URL del contenedor como propiedad de contexto.
     *
     * <p>@DynamicPropertySource tiene mayor prioridad que application.properties,
     * garantizando que DataSource y Flyway usen el contenedor de esta clase.
     */
    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @LocalServerPort
    private int puerto;

    @Test
    @DisplayName("El endpoint /api/health responde 200 OK con status UP")
    void healthEndpointRespondeCorrecto() {
        String url = "http://localhost:" + puerto + "/api/health";
        ResponseEntity<String> response = new RestTemplate().getForEntity(url, String.class);

        assertThat(response.getStatusCode())
                .as("El endpoint /api/health debe responder HTTP 200")
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .as("El cuerpo debe contener status:UP")
                .contains("\"status\"")
                .contains("UP");
    }
}
