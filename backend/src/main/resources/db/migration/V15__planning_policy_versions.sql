-- =============================================================================
-- V15: planning_policy_versions
-- =============================================================================
-- Almacén inmutable y append-only de versiones de políticas operativas.
-- Define descanso mínimo obligatorio y umbral de aviso semanal para el MVP.
-- =============================================================================

CREATE TABLE planning_policy_versions (
    -- Identificación técnica y empresarial
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id             UNIQUEIDENTIFIER    NOT NULL,
    version_number              INT                 NOT NULL,

    -- Parámetros operativos iniciales
    minimum_rest_minutes        INT                 NOT NULL,
    weekly_warning_minutes      INT                 NOT NULL,

    -- Vigencia y auditoría
    effective_at                DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    created_by_user_id          UNIQUEIDENTIFIER    NOT NULL,
    change_reason               NVARCHAR(500)       NOT NULL,
    created_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    -- Clave primaria simple
    CONSTRAINT pk_planning_policy_versions
        PRIMARY KEY (id),

    -- Clave candidata empresarial: requerida para FKs compuestas desde organizations
    CONSTRAINT uq_policy_versions_org_id
        UNIQUE (organization_id, id),

    -- Unicidad de secuencia de versión por empresa (segunda barrera física)
    CONSTRAINT uq_policy_versions_org_version
        UNIQUE (organization_id, version_number),

    -- FK simple a organizations (sin borrado en cascada)
    CONSTRAINT fk_policy_versions_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- FK compuesta a app_users: garantiza que el autor pertenece a la misma organización
    CONSTRAINT fk_policy_versions_author
        FOREIGN KEY (organization_id, created_by_user_id)
        REFERENCES app_users (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- Número de versión estrictamente positivo
    CONSTRAINT chk_policy_version_number_positive
        CHECK (version_number > 0),

    -- Límites operativos validados por CHECK: 1 a 1440 min (1 min a 24 horas)
    CONSTRAINT chk_policy_minimum_rest_range
        CHECK (minimum_rest_minutes >= 1 AND minimum_rest_minutes <= 1440),

    -- Límites operativos validados por CHECK: 1 a 10080 min (1 min a 7 días)
    CONSTRAINT chk_policy_weekly_warning_range
        CHECK (weekly_warning_minutes >= 1 AND weekly_warning_minutes <= 10080),

    -- Protección mínima contra cadena vacía o compuesta solo por espacios ASCII
    CONSTRAINT chk_policy_reason_not_empty
        CHECK (LEN(LTRIM(RTRIM(change_reason))) > 0)
);
