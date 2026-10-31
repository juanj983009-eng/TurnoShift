-- =============================================================================
-- V3: Restricciones adicionales sobre organizations
-- =============================================================================
-- Cambios (no se modifica V1 ni V2 ya versionadas):
--
--   1. max_active_sites = 1 (MVP: una organización tiene exactamente una sede).
--      Si alguna fila existente tiene max_active_sites != 1, ALTER TABLE falla
--      con un error de constraint explícito. Esa condición indica un conflicto
--      entre datos previos y el nuevo modelo; se debe resolver antes de aplicar V3.
--
--   2. current_policy_version_id IS NULL.
--      La FK a planning_policy_versions no existe aún (DEC-08-DEFER-FK-POLICY).
--      Cualquier valor no nulo anticiparía una referencia huérfana. Si alguna
--      fila tiene un valor distinto de NULL, ALTER TABLE falla con error claro.
--
--   3. Se elimina el índice explícito uix_organizations_code, creado en V1 como
--      UNIQUE INDEX además de la restricción UNIQUE uq_organizations_code.
--      En SQL Server, una restricción UNIQUE crea su propio índice implícito;
--      el índice explícito adicional es redundante y ocupa espacio innecesario.
--      La restricción uq_organizations_code permanece intacta.
--
-- Precondiciones de aplicación (verificadas por SQL Server al ejecutar):
--   - organizations.max_active_sites = 1 en todas las filas (o tabla vacía).
--   - organizations.current_policy_version_id IS NULL en todas las filas (o vacía).
-- =============================================================================

-- 1. max_active_sites debe ser exactamente 1.
ALTER TABLE organizations
    ADD CONSTRAINT chk_organizations_max_active_sites_one
    CHECK (max_active_sites = 1);

-- 2. current_policy_version_id debe ser NULL mientras no exista la tabla de políticas.
ALTER TABLE organizations
    ADD CONSTRAINT chk_organizations_policy_version_null
    CHECK (current_policy_version_id IS NULL);

-- 3. Eliminar el índice explícito redundante.
--    La unicidad de code sigue garantizada por uq_organizations_code.
DROP INDEX uix_organizations_code ON organizations;
