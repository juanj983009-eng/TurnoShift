-- =============================================================================
-- V17: employee_unavailabilities
-- =============================================================================
-- Tabla multi-tenant para indisponibilidades temporales de personal (MVP universitario).
-- Soporta tanto indisponibilidades por horas (parciales) como por días completos
-- convertidos a instantes UTC [starts_at, ends_at) según la zona horaria del tenant.
--
-- Integridad relacional multi-tenant garantizada mediante claves foráneas compuestas:
--   - (organization_id, employee_id)         -> employees(organization_id, id)
--   - (organization_id, created_by_user_id)   -> app_users(organization_id, id)
--   - (organization_id, cancelled_by_user_id) -> app_users(organization_id, id)
-- Todas configuradas con ON DELETE NO ACTION ON UPDATE NO ACTION.
--
-- Concurrencia optimista mediante lock_version BIGINT.
-- Auditoría de cancelación lógica atómica (cancelled_at y cancelled_by_user_id
-- deben ser ambos nulos o ambos no nulos).
-- =============================================================================

CREATE TABLE employee_unavailabilities (
    -- Identificación técnica y empresarial (§4.3)
    organization_id         UNIQUEIDENTIFIER    NOT NULL,
    id                      UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),

    -- Colaborador afectado (FK compuesta a employees)
    employee_id             UNIQUEIDENTIFIER    NOT NULL,

    -- Intervalo temporal semiabierto [starts_at, ends_at) en UTC
    starts_at               DATETIMEOFFSET(3)   NOT NULL,
    ends_at                 DATETIMEOFFSET(3)   NOT NULL,

    -- Motivo estandarizado opcional y nota privada confidencial (RN-014)
    reason_code             VARCHAR(32)         NULL,
    restricted_note         NVARCHAR(500)       NULL,

    -- Auditoría de cancelación lógica
    cancelled_at            DATETIMEOFFSET(3)   NULL,
    cancelled_by_user_id    UNIQUEIDENTIFIER    NULL,

    -- Auditoría de creación y actualización
    created_by_user_id      UNIQUEIDENTIFIER    NOT NULL,
    created_at              DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    -- Control de concurrencia optimista
    lock_version            BIGINT              NOT NULL    DEFAULT 0,

    -- Clave primaria simple
    CONSTRAINT pk_employee_unavailabilities
        PRIMARY KEY (id),

    -- Clave candidata empresarial: habilita integridad multi-tenant en relaciones compuestas
    CONSTRAINT uq_unavailabilities_org_id
        UNIQUE (organization_id, id),

    -- Clave foránea simple a organizations (sin borrado en cascada)
    CONSTRAINT fk_unavailabilities_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- Clave foránea compuesta a employees: garantiza pertenencia a la misma organización
    CONSTRAINT fk_unavailabilities_employee
        FOREIGN KEY (organization_id, employee_id)
        REFERENCES employees (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- Clave foránea compuesta a usuario autor de creación
    CONSTRAINT fk_unavailabilities_created_by
        FOREIGN KEY (organization_id, created_by_user_id)
        REFERENCES app_users (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- Clave foránea compuesta a usuario autor de cancelación
    CONSTRAINT fk_unavailabilities_cancelled_by
        FOREIGN KEY (organization_id, cancelled_by_user_id)
        REFERENCES app_users (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- Restricción cronológica: inicio estrictamente menor al fin
    CONSTRAINT chk_unavailabilities_interval
        CHECK (starts_at < ends_at),

    -- Restricción de lock_version no negativo
    CONSTRAINT chk_unavailabilities_lock_version_non_negative
        CHECK (lock_version >= 0),

    -- Restricción de nota privada: si no es nula, no puede ser una cadena en blanco
    CONSTRAINT chk_unavailabilities_note_not_blank
        CHECK (restricted_note IS NULL OR LEN(LTRIM(RTRIM(restricted_note))) > 0),

    -- Restricción de motivo: catálogo controlado de motivos iniciales
    CONSTRAINT chk_unavailabilities_reason_code
        CHECK (reason_code IS NULL OR reason_code IN ('VACACIONES', 'PERMISO_PERSONAL', 'MEDICO', 'FORMACION', 'OTRO')),

    -- Restricción de coherencia en cancelación: ambos presentes o ambos nulos
    CONSTRAINT chk_unavailabilities_cancellation_coherence
        CHECK (
            (cancelled_at IS NULL AND cancelled_by_user_id IS NULL)
            OR
            (cancelled_at IS NOT NULL AND cancelled_by_user_id IS NOT NULL)
        )
);

-- =============================================================================
-- Índices Filtrados para Consultas de Indisponibilidades Activas
-- =============================================================================

-- Índice filtrado para resolver indisponibilidades vigentes de un colaborador
CREATE INDEX ix_unavailabilities_employee_active
    ON employee_unavailabilities (organization_id, employee_id, starts_at, ends_at)
    WHERE cancelled_at IS NULL;

-- Índice filtrado para carga masiva por rango de tiempo y organización (motor I3)
CREATE INDEX ix_unavailabilities_org_interval_active
    ON employee_unavailabilities (organization_id, starts_at, ends_at)
    INCLUDE (employee_id, reason_code)
    WHERE cancelled_at IS NULL;
