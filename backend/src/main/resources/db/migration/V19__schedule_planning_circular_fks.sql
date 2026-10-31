-- =============================================================================
-- V19: schedule_planning_circular_fks
-- =============================================================================
-- Resuelve las referencias circulares de planificación semanal (I3.1-a).
-- 1. Preflight fail-closed de integridad de punteros legados no nulos:
--    - schedules.current_publication_id
--    - schedule_drafts.base_publication_id
--    - schedule_drafts.result_publication_id
-- 2. Instala las 3 claves foráneas compuestas hacia schedule_publications:
--    - fk_schedules_current_publication
--    - fk_schedule_drafts_base_publication
--    - fk_schedule_drafts_result_publication
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Preflight fail-closed
-- -----------------------------------------------------------------------------
IF EXISTS (
    SELECT 1
    FROM schedules s
    WHERE s.current_publication_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM schedule_publications p
          WHERE p.organization_id = s.organization_id
            AND p.schedule_id = s.id
            AND p.id = s.current_publication_id
      )
)
BEGIN
    THROW 50000, 'Migración V19 abortada: existen schedules con current_publication_id que no corresponden a una publicación válida de la misma organización y semana.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM schedule_drafts d
    WHERE d.base_publication_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM schedule_publications p
          WHERE p.organization_id = d.organization_id
            AND p.schedule_id = d.schedule_id
            AND p.id = d.base_publication_id
      )
)
BEGIN
    THROW 50000, 'Migración V19 abortada: existen schedule_drafts con base_publication_id que no corresponden a una publicación válida de la misma organización y semana.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM schedule_drafts d
    WHERE d.result_publication_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM schedule_publications p
          WHERE p.organization_id = d.organization_id
            AND p.schedule_id = d.schedule_id
            AND p.id = d.result_publication_id
      )
)
BEGIN
    THROW 50000, 'Migración V19 abortada: existen schedule_drafts con result_publication_id que no corresponden a una publicación válida de la misma organización y semana.', 1;
END;

-- -----------------------------------------------------------------------------
-- 2. Instalación de claves foráneas compuestas circulares
-- -----------------------------------------------------------------------------
ALTER TABLE schedules
ADD CONSTRAINT fk_schedules_current_publication
    FOREIGN KEY (organization_id, id, current_publication_id)
    REFERENCES schedule_publications (organization_id, schedule_id, id)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION;

ALTER TABLE schedule_drafts
ADD CONSTRAINT fk_schedule_drafts_base_publication
    FOREIGN KEY (organization_id, schedule_id, base_publication_id)
    REFERENCES schedule_publications (organization_id, schedule_id, id)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION;

ALTER TABLE schedule_drafts
ADD CONSTRAINT fk_schedule_drafts_result_publication
    FOREIGN KEY (organization_id, schedule_id, result_publication_id)
    REFERENCES schedule_publications (organization_id, schedule_id, id)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION;
