-- =============================================================================
-- V16: organizations_policy_fk
-- =============================================================================
-- Resuelve la referencia circular diferida en DEC-08-DEFER-FK-POLICY.
-- 1. Preflight fail-closed de integridad de punteros legados no nulos.
-- 2. Desmantela la restricción temporal chk_organizations_policy_version_null (V3).
-- 3. Instala la FK compuesta:
--      organizations(id, current_policy_version_id)
--      -> planning_policy_versions(organization_id, id)
-- =============================================================================

-- 1. Preflight fail-closed: si existe algún puntero legado corrupto o cruzado,
--    la migración debe abortar de inmediato sin modificar ni eliminar datos.
IF EXISTS (
    SELECT 1
    FROM organizations o
    WHERE o.current_policy_version_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM planning_policy_versions p
          WHERE p.organization_id = o.id
            AND p.id = o.current_policy_version_id
      )
)
BEGIN
    THROW 50000, 'Migración V16 abortada: existen organizaciones con current_policy_version_id que no corresponden a una versión válida de la misma organización.', 1;
END;

-- 2. Desmantelar la restricción temporal que obligaba a current_policy_version_id a ser NULL (instalada en V3)
ALTER TABLE organizations
    DROP CONSTRAINT chk_organizations_policy_version_null;

-- 3. Instalación de la clave foránea compuesta
ALTER TABLE organizations
ADD CONSTRAINT fk_organizations_current_policy
    FOREIGN KEY (id, current_policy_version_id)
    REFERENCES planning_policy_versions (organization_id, id)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION;
