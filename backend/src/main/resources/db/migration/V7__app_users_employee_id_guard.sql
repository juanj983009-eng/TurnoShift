-- =============================================================================
-- V7: Protección temporal de employee_id en app_users
-- =============================================================================
-- employee_id fue incluido en V5 como columna nullable, pero la FK empresarial
-- a employees se difirió hasta que esa tabla exista (DEC-08-DEFER-FK-EMPLOYEE).
-- Sin restricción, la columna aceptaría UUIDs arbitrarios sin integridad referencial.
--
-- Esta migración añade un CHECK que exige employee_id IS NULL mientras employees
-- no existe. Si alguna fila ya tiene un valor no nulo, ALTER TABLE falla con un
-- error claro de restricción — no elimina ni convierte datos.
--
-- Al incorporar employees (I2+):
--   1. Se elimina este CHECK (chk_app_users_employee_id_null_guard).
--   2. Se añade la FK empresarial: (organization_id, employee_id) → employees(organization_id, id).
--   3. El índice filtrado uix_app_users_org_employee (ya existente desde V5) garantizará
--      la unicidad del vínculo: un colaborador vinculado a como máximo una cuenta por organización.
--
-- Ver DECISIONS.md — DEC-08-DEFER-FK-EMPLOYEE y DEC-08-EMPLOYEE-ID-GUARD.
-- =============================================================================

ALTER TABLE app_users
    ADD CONSTRAINT chk_app_users_employee_id_null_guard
    CHECK (employee_id IS NULL);
