-- =============================================================================
-- MIGRACIÓN POC-01 · V1 — Tabla mínima ficticia de prueba técnica
-- =============================================================================
-- Propósito: verificar que Flyway puede ejecutar migraciones en SQL Server 2022
--            mediante Testcontainers.
-- Esta tabla NO pertenece al modelo de dominio de TurnoShift.
-- Solo existe en src/test/resources y nunca se empaqueta en producción.
-- Ubicación de prueba: db/poc/migration/
-- =============================================================================

CREATE TABLE poc_probe_entries (
    id           UNIQUEIDENTIFIER   NOT NULL  DEFAULT NEWID(),
    label        NVARCHAR(100)      NOT NULL,
    numeric_val  INT                NOT NULL  DEFAULT 0,
    created_at   DATETIMEOFFSET(3)  NOT NULL  DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_poc_probe_entries PRIMARY KEY (id),
    CONSTRAINT uq_poc_probe_label   UNIQUE (label)
);
