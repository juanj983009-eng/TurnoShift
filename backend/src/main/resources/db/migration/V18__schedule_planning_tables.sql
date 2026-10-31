-- =============================================================================
-- V18: schedule_planning_tables
-- =============================================================================
-- Tablas del núcleo de planificación semanal (I3.1-a).
-- Incluye las 9 tablas en orden topológico acíclico:
--   1. schedules
--   2. schedule_drafts
--   3. assignments
--   4. assignment_revisions
--   5. draft_assignments
--   6. planning_change_sets
--   7. planning_warning_acknowledgements
--   8. schedule_publications
--   9. publication_assignments
--
-- Claves candidatas compuestas multi-tenant (organization_id, ...),
-- comprobaciones CHECK de dominio, índices filtrados e índices temporales.
-- Punteros circulares hacia publicaciones quedan diferidos a V19.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. schedules (Contenedor Semanal por Sede)
-- -----------------------------------------------------------------------------
CREATE TABLE schedules (
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id             UNIQUEIDENTIFIER    NOT NULL,
    site_id                     UNIQUEIDENTIFIER    NOT NULL,
    week_start                  DATE                NOT NULL,
    current_publication_id      UNIQUEIDENTIFIER    NULL,
    lock_version                BIGINT              NOT NULL    DEFAULT 0,
    created_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_schedules
        PRIMARY KEY (id),

    CONSTRAINT uq_schedules_org_id
        UNIQUE (organization_id, id),

    CONSTRAINT uq_schedules_org_site_week
        UNIQUE (organization_id, site_id, week_start),

    CONSTRAINT uq_schedules_org_id_current_pub
        UNIQUE (organization_id, id, current_publication_id),

    CONSTRAINT fk_schedules_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_schedules_site
        FOREIGN KEY (organization_id, site_id)
        REFERENCES sites (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT chk_schedules_week_start_monday
        CHECK (DATEDIFF(DAY, '19000101', week_start) % 7 = 0),

    CONSTRAINT chk_schedules_lock_version_non_negative
        CHECK (lock_version >= 0)
);

-- -----------------------------------------------------------------------------
-- 2. schedule_drafts (Borradores Semanales)
-- -----------------------------------------------------------------------------
CREATE TABLE schedule_drafts (
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id             UNIQUEIDENTIFIER    NOT NULL,
    schedule_id                 UNIQUEIDENTIFIER    NOT NULL,
    base_publication_id         UNIQUEIDENTIFIER    NULL,
    status                      VARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL DEFAULT 'BORRADOR',
    result_publication_id       UNIQUEIDENTIFIER    NULL,
    change_reason               NVARCHAR(500)       NULL,
    created_by_user_id          UNIQUEIDENTIFIER    NOT NULL,
    lock_version                BIGINT              NOT NULL    DEFAULT 0,
    created_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_schedule_drafts
        PRIMARY KEY (id),

    CONSTRAINT uq_schedule_drafts_org_id
        UNIQUE (organization_id, id),

    CONSTRAINT uq_schedule_drafts_org_schedule_id
        UNIQUE (organization_id, schedule_id, id),

    CONSTRAINT fk_schedule_drafts_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_schedule_drafts_schedule
        FOREIGN KEY (organization_id, schedule_id)
        REFERENCES schedules (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_schedule_drafts_author
        FOREIGN KEY (organization_id, created_by_user_id)
        REFERENCES app_users (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT chk_schedule_drafts_status
        CHECK (status IN ('BORRADOR', 'PUBLICADO', 'DESCARTADO')),

    CONSTRAINT chk_schedule_drafts_result_coherence
        CHECK ((status = 'PUBLICADO' AND result_publication_id IS NOT NULL) OR (status IN ('BORRADOR', 'DESCARTADO') AND result_publication_id IS NULL)),

    CONSTRAINT chk_schedule_drafts_reason_not_blank
        CHECK (change_reason IS NULL OR LEN(LTRIM(RTRIM(change_reason))) > 0),

    CONSTRAINT chk_schedule_drafts_lock_version_non_negative
        CHECK (lock_version >= 0)
);

CREATE UNIQUE INDEX uix_schedule_drafts_one_open
    ON schedule_drafts (organization_id, schedule_id)
    WHERE status = 'BORRADOR';

-- -----------------------------------------------------------------------------
-- 3. assignments (Identidad Estable de Turnos Publicados)
-- -----------------------------------------------------------------------------
CREATE TABLE assignments (
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id             UNIQUEIDENTIFIER    NOT NULL,
    schedule_id                 UNIQUEIDENTIFIER    NOT NULL,
    created_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_assignments
        PRIMARY KEY (id),

    CONSTRAINT uq_assignments_org_schedule_id
        UNIQUE (organization_id, schedule_id, id),

    CONSTRAINT fk_assignments_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_assignments_schedule
        FOREIGN KEY (organization_id, schedule_id)
        REFERENCES schedules (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION
);

-- -----------------------------------------------------------------------------
-- 4. assignment_revisions (Contenido Inmutable Versionado de Turno)
-- -----------------------------------------------------------------------------
CREATE TABLE assignment_revisions (
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id             UNIQUEIDENTIFIER    NOT NULL,
    schedule_id                 UNIQUEIDENTIFIER    NOT NULL,
    assignment_id               UNIQUEIDENTIFIER    NOT NULL,
    revision_number             INT                 NOT NULL,
    employee_id                 UNIQUEIDENTIFIER    NOT NULL,
    position_id                 UNIQUEIDENTIFIER    NOT NULL,
    starts_at                   DATETIMEOFFSET(3)   NOT NULL,
    ends_at                     DATETIMEOFFSET(3)   NOT NULL,
    employee_name_snapshot      NVARCHAR(150)       NOT NULL,
    position_name_snapshot      NVARCHAR(100)       NOT NULL,
    created_by_user_id          UNIQUEIDENTIFIER    NOT NULL,
    created_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_assignment_revisions
        PRIMARY KEY (id),

    CONSTRAINT uq_assignment_revisions_org_sched_assign_id
        UNIQUE (organization_id, schedule_id, assignment_id, id),

    CONSTRAINT uq_assignment_revisions_number
        UNIQUE (organization_id, assignment_id, revision_number),

    CONSTRAINT fk_assignment_revisions_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_assignment_revisions_assignment
        FOREIGN KEY (organization_id, schedule_id, assignment_id)
        REFERENCES assignments (organization_id, schedule_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_assignment_revisions_employee
        FOREIGN KEY (organization_id, employee_id)
        REFERENCES employees (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_assignment_revisions_position
        FOREIGN KEY (organization_id, position_id)
        REFERENCES positions (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_assignment_revisions_author
        FOREIGN KEY (organization_id, created_by_user_id)
        REFERENCES app_users (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT chk_assignment_revisions_rev_positive
        CHECK (revision_number > 0),

    CONSTRAINT chk_assignment_revisions_interval
        CHECK (starts_at < ends_at AND DATEDIFF(MINUTE, starts_at, ends_at) BETWEEN 15 AND 960),

    CONSTRAINT chk_assignment_revisions_emp_snapshot_not_empty
        CHECK (LEN(LTRIM(RTRIM(employee_name_snapshot))) > 0),

    CONSTRAINT chk_assignment_revisions_pos_snapshot_not_empty
        CHECK (LEN(LTRIM(RTRIM(position_name_snapshot))) > 0)
);

CREATE INDEX ix_assignment_revisions_org_emp_interval
    ON assignment_revisions (organization_id, employee_id, starts_at, ends_at)
    INCLUDE (schedule_id, assignment_id, position_id);

-- -----------------------------------------------------------------------------
-- 5. draft_assignments (Líneas Editables del Borrador)
-- -----------------------------------------------------------------------------
CREATE TABLE draft_assignments (
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id             UNIQUEIDENTIFIER    NOT NULL,
    schedule_id                 UNIQUEIDENTIFIER    NOT NULL,
    draft_id                    UNIQUEIDENTIFIER    NOT NULL,
    assignment_id               UNIQUEIDENTIFIER    NULL,
    base_revision_id            UNIQUEIDENTIFIER    NULL,
    employee_id                 UNIQUEIDENTIFIER    NOT NULL,
    position_id                 UNIQUEIDENTIFIER    NOT NULL,
    starts_at                   DATETIMEOFFSET(3)   NOT NULL,
    ends_at                     DATETIMEOFFSET(3)   NOT NULL,
    created_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_draft_assignments
        PRIMARY KEY (id),

    CONSTRAINT uq_draft_assignments_org_id
        UNIQUE (organization_id, id),

    CONSTRAINT fk_draft_assignments_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_draft_assignments_draft
        FOREIGN KEY (organization_id, schedule_id, draft_id)
        REFERENCES schedule_drafts (organization_id, schedule_id, id)
        ON DELETE CASCADE
        ON UPDATE NO ACTION,

    CONSTRAINT fk_draft_assignments_employee
        FOREIGN KEY (organization_id, employee_id)
        REFERENCES employees (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_draft_assignments_position
        FOREIGN KEY (organization_id, position_id)
        REFERENCES positions (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_draft_assignments_assignment
        FOREIGN KEY (organization_id, schedule_id, assignment_id)
        REFERENCES assignments (organization_id, schedule_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_draft_assignments_base_revision
        FOREIGN KEY (organization_id, schedule_id, assignment_id, base_revision_id)
        REFERENCES assignment_revisions (organization_id, schedule_id, assignment_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT chk_draft_assignments_base_coherence
        CHECK ((assignment_id IS NULL AND base_revision_id IS NULL) OR (assignment_id IS NOT NULL AND base_revision_id IS NOT NULL)),

    CONSTRAINT chk_draft_assignments_interval
        CHECK (starts_at < ends_at AND DATEDIFF(MINUTE, starts_at, ends_at) BETWEEN 15 AND 960)
);

CREATE UNIQUE INDEX uix_draft_assignments_unique_published_ref
    ON draft_assignments (organization_id, draft_id, assignment_id)
    WHERE assignment_id IS NOT NULL;

CREATE INDEX ix_draft_assignments_draft_starts
    ON draft_assignments (organization_id, draft_id, starts_at, ends_at)
    INCLUDE (employee_id, position_id);

-- -----------------------------------------------------------------------------
-- 6. planning_change_sets (Conjuntos de Cambio de Planificación)
-- -----------------------------------------------------------------------------
CREATE TABLE planning_change_sets (
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id             UNIQUEIDENTIFIER    NOT NULL,
    schedule_id                 UNIQUEIDENTIFIER    NOT NULL,
    change_type                 VARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    actor_user_id               UNIQUEIDENTIFIER    NOT NULL,
    policy_version_id           UNIQUEIDENTIFIER    NOT NULL,
    source_draft_id             UNIQUEIDENTIFIER    NULL,
    reason                      NVARCHAR(500)       NULL,
    empty_schedule_confirmed    BIT                 NOT NULL    DEFAULT 0,
    validation_fingerprint      BINARY(32)          NOT NULL,
    validation_schema_version   INT                 NOT NULL    DEFAULT 1,
    correlation_id              UNIQUEIDENTIFIER    NULL,
    confirmed_at                DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_planning_change_sets
        PRIMARY KEY (id),

    CONSTRAINT uq_planning_change_sets_org_id
        UNIQUE (organization_id, id),

    CONSTRAINT uq_planning_change_sets_org_sched_id_policy
        UNIQUE (organization_id, schedule_id, id, policy_version_id),

    CONSTRAINT fk_planning_change_sets_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_planning_change_sets_schedule
        FOREIGN KEY (organization_id, schedule_id)
        REFERENCES schedules (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_planning_change_sets_actor
        FOREIGN KEY (organization_id, actor_user_id)
        REFERENCES app_users (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_planning_change_sets_policy_version
        FOREIGN KEY (organization_id, policy_version_id)
        REFERENCES planning_policy_versions (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_planning_change_sets_source_draft
        FOREIGN KEY (organization_id, schedule_id, source_draft_id)
        REFERENCES schedule_drafts (organization_id, schedule_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT chk_planning_change_sets_type
        CHECK (change_type IN ('PUBLICACION_BORRADOR', 'REVISION_PUBLICACION', 'APROBACION_INTERCAMBIO')),

    CONSTRAINT chk_planning_change_sets_draft_coherence
        CHECK ((change_type IN ('PUBLICACION_BORRADOR', 'REVISION_PUBLICACION') AND source_draft_id IS NOT NULL) OR (change_type = 'APROBACION_INTERCAMBIO')),

    CONSTRAINT chk_planning_change_sets_empty_reason
        CHECK (empty_schedule_confirmed = 0 OR (reason IS NOT NULL AND LEN(LTRIM(RTRIM(reason))) > 0)),

    CONSTRAINT chk_planning_change_sets_reason_not_blank
        CHECK (reason IS NULL OR LEN(LTRIM(RTRIM(reason))) > 0),

    CONSTRAINT chk_planning_change_sets_schema_ver_pos
        CHECK (validation_schema_version > 0)
);

-- -----------------------------------------------------------------------------
-- 7. planning_warning_acknowledgements (Reconocimientos Individuales de Advertencias)
-- -----------------------------------------------------------------------------
CREATE TABLE planning_warning_acknowledgements (
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id             UNIQUEIDENTIFIER    NOT NULL,
    change_set_id               UNIQUEIDENTIFIER    NOT NULL,
    employee_id                 UNIQUEIDENTIFIER    NOT NULL,
    warning_code                VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    period_start                DATE                NOT NULL,
    period_end_exclusive        DATE                NOT NULL,
    timezone_id_snapshot        VARCHAR(64)         NOT NULL,
    planned_minutes             INT                 NOT NULL,
    threshold_minutes           INT                 NOT NULL,
    warning_fingerprint         BINARY(32)          NOT NULL,
    context_schema_version      INT                 NOT NULL    DEFAULT 1,
    context_data                NVARCHAR(MAX)       NULL,
    created_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_planning_warning_acknowledgements
        PRIMARY KEY (id),

    CONSTRAINT uq_warning_acks_org_id
        UNIQUE (organization_id, id),

    CONSTRAINT uq_warning_acks_change_set_fingerprint
        UNIQUE (organization_id, change_set_id, warning_fingerprint),

    CONSTRAINT fk_warning_acks_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_warning_acks_change_set
        FOREIGN KEY (organization_id, change_set_id)
        REFERENCES planning_change_sets (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_warning_acks_employee
        FOREIGN KEY (organization_id, employee_id)
        REFERENCES employees (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT chk_warning_acks_code
        CHECK (warning_code IN ('WEEKLY_PLANNED_MINUTES_EXCEEDED')),

    CONSTRAINT chk_warning_acks_minutes_exceeded
        CHECK (threshold_minutes > 0 AND planned_minutes > threshold_minutes),

    CONSTRAINT chk_warning_acks_period
        CHECK (DATEDIFF(DAY, period_start, period_end_exclusive) = 7),

    CONSTRAINT chk_warning_acks_timezone_not_empty
        CHECK (LEN(LTRIM(RTRIM(timezone_id_snapshot))) > 0),

    CONSTRAINT chk_warning_acks_json
        CHECK (context_data IS NULL OR ISJSON(context_data) = 1)
);

-- -----------------------------------------------------------------------------
-- 8. schedule_publications (Instantáneas Oficiales Inmutables)
-- -----------------------------------------------------------------------------
CREATE TABLE schedule_publications (
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id             UNIQUEIDENTIFIER    NOT NULL,
    schedule_id                 UNIQUEIDENTIFIER    NOT NULL,
    publication_number          INT                 NOT NULL,
    previous_publication_id     UNIQUEIDENTIFIER    NULL,
    policy_version_id           UNIQUEIDENTIFIER    NOT NULL,
    timezone_id_snapshot        VARCHAR(64)         NOT NULL,
    origin                      VARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    change_set_id               UNIQUEIDENTIFIER    NOT NULL,
    published_at                DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_schedule_publications
        PRIMARY KEY (id),

    CONSTRAINT uq_schedule_publications_org_schedule_id
        UNIQUE (organization_id, schedule_id, id),

    CONSTRAINT uq_schedule_publications_number
        UNIQUE (organization_id, schedule_id, publication_number),

    CONSTRAINT uq_schedule_publications_change_set
        UNIQUE (organization_id, change_set_id),

    CONSTRAINT fk_schedule_publications_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_schedule_publications_schedule
        FOREIGN KEY (organization_id, schedule_id)
        REFERENCES schedules (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_schedule_publications_previous
        FOREIGN KEY (organization_id, schedule_id, previous_publication_id)
        REFERENCES schedule_publications (organization_id, schedule_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_schedule_publications_policy_version
        FOREIGN KEY (organization_id, policy_version_id)
        REFERENCES planning_policy_versions (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_schedule_publications_change_set
        FOREIGN KEY (organization_id, schedule_id, change_set_id, policy_version_id)
        REFERENCES planning_change_sets (organization_id, schedule_id, id, policy_version_id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT chk_schedule_publications_number_positive
        CHECK (publication_number > 0),

    CONSTRAINT chk_schedule_publications_prev_coherence
        CHECK ((publication_number = 1 AND previous_publication_id IS NULL) OR (publication_number > 1 AND previous_publication_id IS NOT NULL)),

    CONSTRAINT chk_schedule_publications_origin
        CHECK (origin IN ('INICIAL', 'REVISION', 'INTERCAMBIO')),

    CONSTRAINT chk_schedule_publications_origin_coherence
        CHECK ((publication_number = 1 AND origin = 'INICIAL') OR (publication_number > 1 AND origin IN ('REVISION', 'INTERCAMBIO'))),

    CONSTRAINT chk_schedule_publications_timezone_not_empty
        CHECK (LEN(LTRIM(RTRIM(timezone_id_snapshot))) > 0)
);

-- -----------------------------------------------------------------------------
-- 9. publication_assignments (Asociación Publicación ↔ Revisión de Turno)
-- -----------------------------------------------------------------------------
CREATE TABLE publication_assignments (
    organization_id             UNIQUEIDENTIFIER    NOT NULL,
    schedule_id                 UNIQUEIDENTIFIER    NOT NULL,
    publication_id              UNIQUEIDENTIFIER    NOT NULL,
    assignment_id               UNIQUEIDENTIFIER    NOT NULL,
    assignment_revision_id      UNIQUEIDENTIFIER    NOT NULL,

    CONSTRAINT pk_publication_assignments
        PRIMARY KEY (organization_id, schedule_id, publication_id, assignment_id),

    CONSTRAINT fk_pub_assignments_publication
        FOREIGN KEY (organization_id, schedule_id, publication_id)
        REFERENCES schedule_publications (organization_id, schedule_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_pub_assignments_assignment
        FOREIGN KEY (organization_id, schedule_id, assignment_id)
        REFERENCES assignments (organization_id, schedule_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_pub_assignments_revision
        FOREIGN KEY (organization_id, schedule_id, assignment_id, assignment_revision_id)
        REFERENCES assignment_revisions (organization_id, schedule_id, assignment_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION
);

CREATE INDEX ix_pub_assignments_revision
    ON publication_assignments (organization_id, schedule_id, assignment_revision_id)
    INCLUDE (publication_id, assignment_id);
