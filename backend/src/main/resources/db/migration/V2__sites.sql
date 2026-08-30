-- =============================================================================
-- V2: sites
-- =============================================================================
-- Campos, tipos y restricciones según Nota 07, §6.6 y §4.4.
--
-- Clave candidata (organization_id, id) requerida por §4.4 para futuras FKs
-- empresariales que necesiten garantizar pertenencia a la misma organización.
--
-- DEC-08 (parcial):
--   code : varchar(32), patrón [A-Z0-9\-], único POR ORGANIZACIÓN.
--   name : nvarchar(150), obligatorio y no vacío.
--
-- Índice filtrado: como máximo una sede activa por organización (MVP).
-- Sin borrado en cascada sobre la FK a organizations.
-- =============================================================================

CREATE TABLE sites (
    -- Identificación empresarial (§4.3)
    organization_id     UNIQUEIDENTIFIER    NOT NULL,
    id                  UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),

    -- Código de sede — único por organización (no globalmente)
    code                VARCHAR(32)
                        COLLATE Latin1_General_100_BIN2
                        NOT NULL,

    -- Nombre visible
    name                NVARCHAR(150)       NOT NULL,

    -- Indicador de actividad
    is_active           BIT                 NOT NULL    DEFAULT 1,

    -- Auditoría de fila
    created_at          DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    lock_version        BIGINT              NOT NULL    DEFAULT 0,

    -- Clave primaria simple (GUID de sede)
    CONSTRAINT pk_sites
        PRIMARY KEY (id),

    -- Clave candidata empresarial (organization_id, id) requerida por §4.4.
    -- Permite que FKs empresariales futuras garanticen pertenencia a la misma
    -- organización referenciando ambas columnas en lugar de solo id.
    CONSTRAINT uq_sites_org_id
        UNIQUE (organization_id, id),

    -- Código único dentro de la organización
    CONSTRAINT uq_sites_org_code
        UNIQUE (organization_id, code),

    -- FK a organizations — sin CASCADE (Nota 07: suspensión/cierre no elimina dependencias)
    CONSTRAINT fk_sites_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- code: mínimo 2 caracteres, patrón [A-Z0-9\-]
    CONSTRAINT chk_sites_code_format
        CHECK (
            LEN(code) >= 2
            AND code NOT LIKE '%[^A-Z0-9-]%'
            AND code NOT LIKE ' %'
            AND code NOT LIKE '% '
        ),

    -- name no vacío
    CONSTRAINT chk_sites_name_not_empty
        CHECK (LEN(LTRIM(RTRIM(name))) > 0)
);

-- =============================================================================
-- Índice filtrado: máximo UNA sede activa por organización (MVP)
-- Nota 07, §6.6: "Índice único de organization_id filtrado por sede activa."
-- is_active = 1 equivale a BIT = 1 en SQL Server.
-- =============================================================================
CREATE UNIQUE INDEX uix_sites_org_single_active
    ON sites (organization_id)
    WHERE is_active = 1;
