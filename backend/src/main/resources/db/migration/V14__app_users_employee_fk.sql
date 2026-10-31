-- =============================================================================
-- V14: Desmantelamiento de guard de employee_id y FK empresarial en app_users
-- =============================================================================
-- 1. Elimina la restricción temporal chk_app_users_employee_id_null_guard (V7).
-- 2. Agrega la clave foránea empresarial compuesta:
--      (organization_id, employee_id) -> employees(organization_id, id)
--    con ON DELETE NO ACTION y ON UPDATE NO ACTION.
--
-- El índice filtrado uix_app_users_org_employee (definido en V5) se conserva
-- intacto para asegurar la relación 1:1 por organización (máximo una cuenta por colaborador).
-- No se pueblan vínculos automáticamente ni se alteran datos existentes.
-- =============================================================================

-- Eliminar el guard temporal instalado en V7
ALTER TABLE app_users
    DROP CONSTRAINT chk_app_users_employee_id_null_guard;

-- Agregar clave foránea empresarial compuesta
ALTER TABLE app_users
    ADD CONSTRAINT fk_app_users_employee
        FOREIGN KEY (organization_id, employee_id)
        REFERENCES employees (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION;
