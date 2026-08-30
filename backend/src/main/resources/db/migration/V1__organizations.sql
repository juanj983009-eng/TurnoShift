-- =============================================================================
-- V1: organizations
-- =============================================================================
-- Campos, tipos y restricciones según Nota 07, §4.2 y §6.2.
--
-- COLLATION explícita en columnas de búsqueda:
--   code        → Latin1_General_100_BIN2  (comparación binaria exacta, ASCII)
--   service_status, timezone_id → Latin1_General_100_CI_AS (case-insensitive
--     seguro para valores controlados del sistema)
--
-- DEC-08 (parcial, concretado en DECISIONS.md):
--   code       : varchar(32), 2–32 caracteres, patrón [A-Z0-9\-].
--   display_name: nvarchar(150), obligatorio y no vacío.
--   timezone_id : varchar(64), obligatorio.
--
-- current_policy_version_id:
--   Se incluye como columna nullable pero SIN FK por ahora.
--   La FK se añadirá cuando se cree planning_policy_versions (paso 5 del orden
--   de implementación de Nota 07, §17). Ver DECISIONS.md — DEC-08-DEFER-FK-POLICY.
--
-- No se usa borrado en cascada.
-- =============================================================================

CREATE TABLE organizations (
    -- Identificación de negocio
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),

    -- Código de acceso — ASCII, binario, comparación exacta
    code                        VARCHAR(32)
                                COLLATE Latin1_General_100_BIN2
                                NOT NULL,

    -- Nombre visible
    display_name                NVARCHAR(150)       NOT NULL,

    -- Estado del servicio
    service_status              VARCHAR(16)
                                COLLATE Latin1_General_100_CI_AS
                                NOT NULL            DEFAULT 'ACTIVA',

    -- Zona horaria IANA; longitud máxima real ~50 chars, reservamos 64
    timezone_id                 VARCHAR(64)
                                COLLATE Latin1_General_100_CI_AS
                                NOT NULL,

    -- Capacidades (positivas, requeridas por restricción CHECK)
    max_active_employees        INT                 NOT NULL,
    max_active_sites            INT                 NOT NULL    DEFAULT 1,

    -- FK diferida (política vigente); nullable hasta configuración completa.
    -- La FK a planning_policy_versions se añade en una migración posterior.
    current_policy_version_id   UNIQUEIDENTIFIER    NULL,

    -- Auditoría de fila
    created_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    lock_version                BIGINT              NOT NULL    DEFAULT 0,

    -- Clave primaria
    CONSTRAINT pk_organizations
        PRIMARY KEY (id),

    -- Código único global (en toda la plataforma)
    CONSTRAINT uq_organizations_code
        UNIQUE (code),

    -- Estado válido
    CONSTRAINT chk_organizations_service_status
        CHECK (service_status IN ('ACTIVA', 'SUSPENDIDA', 'CERRADA')),

    -- Capacidades positivas
    CONSTRAINT chk_organizations_max_active_employees_positive
        CHECK (max_active_employees > 0),

    CONSTRAINT chk_organizations_max_active_sites_positive
        CHECK (max_active_sites > 0),

    -- code: mínimo 2 caracteres, patrón [A-Z0-9\-]
    -- La validación de longitud mínima la impone la aplicación;
    -- la base evita cadenas vacías y caracteres prohibidos.
    CONSTRAINT chk_organizations_code_format
        CHECK (
            LEN(code) >= 2
            AND code NOT LIKE '%[^A-Z0-9-]%'
            AND code NOT LIKE ' %'
            AND code NOT LIKE '% '
        ),

    -- display_name no vacío (trim en aplicación; la base evita el caso extremo)
    CONSTRAINT chk_organizations_display_name_not_empty
        CHECK (LEN(LTRIM(RTRIM(display_name))) > 0),

    -- timezone_id no vacío
    CONSTRAINT chk_organizations_timezone_id_not_empty
        CHECK (LEN(LTRIM(RTRIM(timezone_id))) > 0)
);

-- =============================================================================
-- Índice para lookups por código (el login usa code + email)
-- =============================================================================
CREATE UNIQUE INDEX uix_organizations_code
    ON organizations (code);

-- Nota: uq_organizations_code ya crea un índice único implícito en SQL Server.
-- El índice explícito no es redundante si se necesita cubrir otras columnas en
-- el futuro; por ahora ambas restricciones apuntan al mismo índice efectivo.
-- La restricción UNIQUE prevalece; el índice explícito se puede eliminar si
-- el optimizador lo identifica como duplicado en una revisión posterior.
