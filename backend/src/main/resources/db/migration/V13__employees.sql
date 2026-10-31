-- =============================================================================
-- V13: employees
-- =============================================================================
-- Catálogo de colaboradores/empleados según Nota 07, §6.8 y decisiones de I2.2.
--
-- Clave candidata (organization_id, id) para permitir FKs empresariales compuestas
-- desde app_users, asignaciones e indisponibilidades.
--
-- Unicidad de código por organización: UNIQUE (organization_id, code).
-- code: NVARCHAR(50), BIN2 para comparación exacta case-sensitive.
-- display_name: NVARCHAR(150), no vacío.
--
-- FKs empresariales compuestas:
--   - (organization_id, position_id) -> positions(organization_id, id)
--   - (organization_id, site_id)     -> sites(organization_id, id)
--   - organization_id                 -> organizations(id)
-- Todas con ON DELETE / ON UPDATE NO ACTION.
--
-- Timestamps generados autoritativamente con SYSUTCDATETIME().
-- Sin índices adicionales salvo los creados por las restricciones UNIQUE y PK.
-- =============================================================================

CREATE TABLE employees (
    -- Identificación empresarial (§4.3)
    organization_id     UNIQUEIDENTIFIER    NOT NULL,
    id                  UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),

    -- Código de colaborador — único por organización
    code                NVARCHAR(50)
                        COLLATE Latin1_General_100_BIN2
                        NOT NULL,

    -- Nombre visible
    display_name        NVARCHAR(150)       NOT NULL,

    -- Puesto principal (FK empresarial compuesta)
    position_id         UNIQUEIDENTIFIER    NOT NULL,

    -- Sede asignada (FK empresarial compuesta)
    site_id             UNIQUEIDENTIFIER    NOT NULL,

    -- Estado de actividad
    is_active           BIT                 NOT NULL    DEFAULT 1,

    -- Auditoría de fila
    created_at          DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    lock_version        BIGINT              NOT NULL    DEFAULT 0,

    -- Clave primaria simple
    CONSTRAINT pk_employees
        PRIMARY KEY (id),

    -- Clave candidata empresarial: requerida para que FKs empresariales futuras
    -- (incluyendo app_users) garanticen pertenencia a la misma organización.
    CONSTRAINT uq_employees_org_id
        UNIQUE (organization_id, id),

    -- Código único dentro de la organización
    CONSTRAINT uq_employees_org_code
        UNIQUE (organization_id, code),

    -- FK a organizations — sin CASCADE
    CONSTRAINT fk_employees_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- FK compuesta a positions — garantiza pertenencia a la misma organización
    CONSTRAINT fk_employees_position
        FOREIGN KEY (organization_id, position_id)
        REFERENCES positions (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- FK compuesta a sites — garantiza pertenencia a la misma organización
    CONSTRAINT fk_employees_site
        FOREIGN KEY (organization_id, site_id)
        REFERENCES sites (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- code: no vacío
    CONSTRAINT chk_employees_code_not_empty
        CHECK (LEN(LTRIM(RTRIM(code))) > 0),

    -- display_name: no vacío
    CONSTRAINT chk_employees_display_name_not_empty
        CHECK (LEN(LTRIM(RTRIM(display_name))) > 0),

    -- lock_version: no negativo
    CONSTRAINT chk_employees_lock_version_non_negative
        CHECK (lock_version >= 0)
);
