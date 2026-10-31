-- =============================================================================
-- V8: Collation explícita en normalized_email de app_users
-- =============================================================================
-- En V5, normalized_email fue declarado NVARCHAR(254) sin COLLATE explícito,
-- heredando la collation predeterminada de la base de datos. Esto significa que
-- la comparación de unicidad dependía de la configuración del servidor, no del
-- valor canónico producido por la aplicación.
--
-- Esta migración aplica Latin1_General_100_BIN2, que compara byte a byte el valor
-- ya normalizado (trim exterior + minúsculas en capa de aplicación). La BD no
-- normaliza ni valida el correo; solo almacena el valor que provee la aplicación
-- y lo compara de forma exacta.
--
-- DEC-08-IDENTITY (actualizado): normalized_email usa BIN2 para comparación binaria
-- exacta. La normalización previa sigue siendo responsabilidad de la aplicación.
--
-- Procedimiento:
--   1. Eliminar los objetos que impiden ALTER COLUMN en SQL Server:
--      a. Restricción UNIQUE uq_app_users_org_normalized_email.
--      b. Restricción CHECK chk_app_users_normalized_email_not_empty.
--      SQL Server (error 4922) no permite cambiar la collation de una columna
--      mientras existan restricciones CHECK o UNIQUE que la referencien.
--   2. Alterar la columna para añadir la collation explícita.
--      Si hay valores incompatibles con la nueva collation, ALTER TABLE fallará
--      con error claro — no altera ni elimina correos existentes.
--   3. Recrear ambas restricciones con los mismos nombres, alcance y semántica.
--
-- Si algún paso falla con error claro, la migración no continúa.
-- No hay cascada destructiva: no se alteran ni eliminan correos existentes.
-- =============================================================================

-- Paso 1a: Eliminar la restricción UNIQUE dependiente.
ALTER TABLE app_users
    DROP CONSTRAINT uq_app_users_org_normalized_email;

-- Paso 1b: Eliminar la restricción CHECK que también referencia la columna.
--          SQL Server 4922: cualquier objeto que acceda a la columna bloquea ALTER COLUMN.
ALTER TABLE app_users
    DROP CONSTRAINT chk_app_users_normalized_email_not_empty;

-- Paso 2: Cambiar la collation de la columna a comparación binaria exacta.
--         Si algún valor existente no es compatible, este paso falla claramente.
--         No se alteran ni eliminan correos existentes.
ALTER TABLE app_users
    ALTER COLUMN normalized_email NVARCHAR(254)
        COLLATE Latin1_General_100_BIN2
        NOT NULL;

-- Paso 3a: Recrear la restricción CHECK con el mismo nombre y semántica.
ALTER TABLE app_users
    ADD CONSTRAINT chk_app_users_normalized_email_not_empty
        CHECK (LEN(LTRIM(RTRIM(normalized_email))) > 0);

-- Paso 3b: Recrear la restricción UNIQUE con el mismo nombre y alcance empresarial.
--          Ahora la comparación de unicidad usa BIN2 sobre el valor ya normalizado.
ALTER TABLE app_users
    ADD CONSTRAINT uq_app_users_org_normalized_email
        UNIQUE (organization_id, normalized_email);
