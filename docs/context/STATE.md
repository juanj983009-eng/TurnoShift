# ESTADO REAL DEL PROYECTO — TURNOSHIFT

## 1. Hechos Comprobados (con fecha de verificación)

### Backend (Spring Boot 4.0.0 + Java 21)
- Endpoint `/api/health` → HTTP 200 `{"service":"turnoshift-backend","status":"UP"}`. **Verificado.**
- Compilación con JDK 21 local (`C:\Users\juan9\.jdks\temurin-21.0.10`). Maven Wrapper presente.

### Frontend (React 19 + TypeScript + Vite 6)
- `npm run build` genera `dist/` sin errores. **Verificado.**

### Contrato HTTP I1
- `docs/api/openapi.yaml` v0.1.0-i1: `npx @redocly/cli lint` → 0 errores, 0 advertencias. **Validado.**
- Los endpoints **no están implementados** en el backend.

### PoC-01 — Compatibilidad Flyway + SQL Server 2022 + Testcontainers
**Ejecutado:** 2026-08-30. **Resultado: 5/5 PASS.**

| Verificación | Resultado |
|---|---|
| V1 + V2 desde base vacía (2 en `poc_flyway_schema_history`) | ✅ |
| Segunda ejecución idempotente | ✅ |
| Escritura/lectura JDBC con SQL Server 2022 real | ✅ |
| Transacción revertida no persiste filas | ✅ |
| CHECK constraint de V2 rechaza valores negativos | ✅ |
| JAR de producción sin migraciones ficticias de prueba | ✅ |

### HT-02 / HT-07 — Esquema real organizations + sites
**Ejecutado:** 2026-08-30. **Resultado: `mvnw.cmd verify -B` → BUILD SUCCESS — 13/13 PASS.**

| Clase | Tests | Resultado |
|---|---|---|
| `OrganizationsSitesSchemaIT` | 7 | ✅ PASS |
| `FlywayMssqlCompatibilityIT` (PoC-01) | 5 | ✅ PASS |
| `SpringBootStartupIT` | 1 | ✅ PASS |

**Verificaciones de OrganizationsSitesSchemaIT (7 tests):**

| Verificación | Resultado |
|---|---|
| Flyway aplica V1 (`organizations`) y V2 (`sites`) desde base vacía | ✅ |
| `flyway_schema_history` contiene exactamente versiones "1" y "2" | ✅ |
| Insertar 2 organizaciones ficticias (Alfa, Beta) | ✅ |
| Código de organización duplicado rechazado (unicidad global) | ✅ |
| Sede activa insertada correctamente | ✅ |
| Segunda sede activa en misma organización rechazada (índice filtrado) | ✅ |
| Sede con organización inexistente rechazada (FK) | ✅ |
| Mismo código de sede en organizaciones distintas permitido | ✅ |

**Verificaciones de SpringBootStartupIT (1 test):**

| Verificación | Resultado |
|---|---|
| Spring Boot arranca con Flyway auto-config + SQL Server efímero | ✅ |
| `GET /api/health` → HTTP 200 `{"status":"UP"}` | ✅ |

**Nota de entorno:** `ryuk.disabled=true` en `~/.testcontainers.properties`. Los tests de constraints
usan Flyway + JdbcTemplate manuales (patrón PoC) para evitar colisiones de historial en el mismo JVM.
`SpringBootStartupIT` usa `@SpringBootTest` + `@DynamicPropertySource` para validar la auto-config.
Migraciones PoC → `poc_flyway_schema_history`. Producción → `flyway_schema_history`.

**Versiones efectivas (BOM Spring Boot 4.0.0):**

| Componente | Artifact | Versión |
|---|---|---|
| Flyway core | `org.flywaydb:flyway-core` | 11.14.1 |
| Flyway SQL Server | `org.flywaydb:flyway-sqlserver` | 11.14.1 |
| SQL Server JDBC | `com.microsoft.sqlserver:mssql-jdbc` | 13.2.1.jre11 |
| Testcontainers core | `org.testcontainers:testcontainers` | 2.0.2 |
| Testcontainers JUnit | `org.testcontainers:testcontainers-junit-jupiter` | 2.0.2 |
| Testcontainers MSSQL | `org.testcontainers:testcontainers-mssqlserver` | 2.0.2 |
| JUnit Jupiter | `org.junit.jupiter:junit-jupiter` | 6.0.1 |
| Spring JDBC | `org.springframework:spring-jdbc` | 7.0.1 |
| maven-failsafe-plugin | — | 3.5.4 |

**Imagen SQL Server:** `mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04`
**Licencia:** SQL Server 2022 Developer Edition. Solo desarrollo/pruebas. No apto para producción.

## 2. Estado de Incrementos

| Incremento | Estado | Notas |
|---|---|---|
| **I0 — Preparación** | **COMPLETO** | HT-01 ✅, HT-02 ✅ (PoC + esquema real), HT-07 ✅ (13/13 IT). |
| **I1 — Acceso seguro** | Diseño adelantado | Contrato OpenAPI 3.1 validado. Implementación pendiente. |
| I2 – I7 | PENDIENTES | — |

## 3. Trabajo Pendiente para Iniciar I1

- Resolver **PROP-OP-01**: mecanismo de autenticación del operador de plataforma.
- Resolver **DEC-06**: canal de entrega del enlace inicial.
- Implementar endpoints del contrato `docs/api/openapi.yaml`.
- Añadir Spring Security y Spring Session JDBC.

## 4. Nota sobre alcance de las pruebas

- **PoC-01:** compatibilidad Java 21 + Spring Boot 4.0.0 + Flyway + SQL Server 2022 + Testcontainers.
- **OrganizationsSitesSchemaIT:** migraciones reales y constraints de `organizations` + `sites`.
- **SpringBootStartupIT:** arranque Spring Boot con Flyway auto-config y health endpoint.
- **No verificado aún:** Spring Security, Spring Session JDBC, autenticación, aislamiento multi-tenant.
