-- =============================================================================
-- MIGRACIÓN POC-01 · V2 — Evolución del esquema ficticio de prueba técnica
-- =============================================================================
-- Propósito: verificar que Flyway ejecuta migraciones en orden y registra
--            el historial correctamente (flyway_schema_history).
-- Esta migración añade una columna a la tabla ficticia creada en V1.
-- Solo existe en src/test/resources y nunca se empaqueta en producción.
-- =============================================================================

ALTER TABLE poc_probe_entries
    ADD notes NVARCHAR(255) NULL;

-- Restricción de comprobación: fuerza que el valor numérico sea no negativo.
-- Permite verificar que Flyway aplica DDL con constraints correctamente.
ALTER TABLE poc_probe_entries
    ADD CONSTRAINT chk_poc_probe_numeric_val_gte_zero
        CHECK (numeric_val >= 0);
