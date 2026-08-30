# TAREA ACTUAL — TURNOSHIFT

## Tarea completada: I0 — Persistencia real (organizations + sites)

**Resultado:** BUILD SUCCESS — 13/13 pruebas de integración aprobadas (2026-08-30).

---

## Qué se hizo en esta tarea

### Migraciones de producción
- `V1__organizations.sql` — tabla `organizations` con constraints de Nota 07 §6.2.
- `V2__sites.sql` — tabla `sites` con índice filtrado único para sede activa por organización.

### Pruebas de integración (3 clases IT)
- `FlywayMssqlCompatibilityIT` (PoC-01, 5 tests) — compatibilidad Flyway + SQL Server 2022.
- `OrganizationsSitesSchemaIT` (7 tests) — migraciones reales + constraints, patrón manual.
- `SpringBootStartupIT` (1 test) — arranque Spring Boot + Flyway auto-config + health endpoint.

### Decisiones técnicas registradas
- **DEC-08-FIELDS:** tipos físicos y constraints de `organizations` y `sites`.
- **DEC-08-DEFER-FK-POLICY:** FK de `current_policy_version_id` diferida.
- **Patrón de test:** con `ryuk.disabled=true`, los tests de schema usan Flyway + JdbcTemplate
  manuales para evitar colisiones de historial; `poc_flyway_schema_history` aísla el PoC.

### Infraestructura local
- `infra/docker-compose.dev.yml` — SQL Server 2022 en `127.0.0.1:11433`.
- `infra/.env.dev.example` — plantilla versionada (sin secretos).

---

## Siguiente tarea sugerida: iniciar I1

Prerequisitos pendientes antes de implementar endpoints:
1. Resolver **PROP-OP-01** (autenticación del operador de plataforma).
2. Resolver **DEC-06** (canal de entrega del enlace inicial).
3. Leer la Nota 11 §I1 y el contrato `docs/api/openapi.yaml`.
4. Añadir Spring Security + Spring Session JDBC al pom.xml.
5. Implementar los endpoints del contrato OpenAPI 3.1.

**Archivos de referencia para I1:**
- `docs/api/openapi.yaml` — contrato HTTP validado.
- `docs/context/DECISIONS.md` — decisiones DEC-01 a DEC-08.
- `docs/context/PROJECT_CONTEXT.md` — arquitectura y reglas.
- `docs/planning/11 - Orden de construcción por incrementos.md` §I1.
