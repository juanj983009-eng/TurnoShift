-- =============================================================================
-- V12: positions
-- =============================================================================
-- Catálogo de puestos de trabajo por organización (HU-06).
-- Multi-tenant estricto con clave candidata (organization_id, id) para futuras
-- referencias foráneas compuestas seguras desde employees.
--
-- Restricciones:
--   - PK id (DEFAULT NEWID())
--   - UNIQUE (organization_id, id) como clave candidata empresarial
--   - UNIQUE (organization_id, code) con collation BIN2
--   - FK organization_id -> organizations(id) sin cascada
--   - CHECK code format: 2-32 caracteres, solo A-Z, 0-9 y guion, sin espacios
--   - CHECK name no vacío tras trim
-- =============================================================================

CREATE TABLE positions (
    id                  UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id     UNIQUEIDENTIFIER    NOT NULL,
    code                VARCHAR(32)
                        COLLATE Latin1_General_100_BIN2
                        NOT NULL,
    name                NVARCHAR(100)       NOT NULL,
    is_active           BIT                 NOT NULL    DEFAULT 1,
    lock_version        BIGINT              NOT NULL    DEFAULT 0,
    created_at          DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_positions
        PRIMARY KEY (id),

    CONSTRAINT uq_positions_org_id
        UNIQUE (organization_id, id),

    CONSTRAINT uq_positions_org_code
        UNIQUE (organization_id, code),

    CONSTRAINT fk_positions_organizations
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT chk_positions_code_format
        CHECK (
            LEN(code) >= 2
            AND LEN(code) <= 32
            AND code NOT LIKE '%[^A-Z0-9-]%'
            AND code NOT LIKE ' %'
            AND code NOT LIKE '% '
        ),

    CONSTRAINT chk_positions_name_not_empty
        CHECK (LEN(LTRIM(RTRIM(name))) > 0)
);
