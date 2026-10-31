-- =============================================================================
-- V11: api_operations
-- Registro técnico de deduplicación e idempotencia de comandos.
-- =============================================================================

CREATE TABLE api_operations (
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id             UNIQUEIDENTIFIER    NULL,
    actor_user_id               UNIQUEIDENTIFIER    NULL,
    actor_operator_id           UNIQUEIDENTIFIER    NULL,
    idempotency_key             UNIQUEIDENTIFIER    NOT NULL,
    request_method              VARCHAR(16)         NOT NULL,
    request_path                NVARCHAR(255)       NOT NULL,
    request_fingerprint         BINARY(32)          NOT NULL,
    request_schema_version      VARCHAR(32)         NOT NULL    DEFAULT '1.0',
    status                      VARCHAR(16)         NOT NULL,
    outcome                     VARCHAR(16)         NULL,
    http_status                 INT                 NULL,
    response_content_type       VARCHAR(64)         NULL,
    response_body               NVARCHAR(MAX)       NULL,
    response_location           NVARCHAR(512)       NULL,
    created_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    completed_at                DATETIMEOFFSET(3)   NULL,
    expires_at                  DATETIMEOFFSET(3)   NOT NULL,
    lock_version                BIGINT              NOT NULL    DEFAULT 0,

    CONSTRAINT pk_api_operations PRIMARY KEY (id),

    -- FK a operadores de plataforma (sin cascada)
    CONSTRAINT fk_api_operations_operator
        FOREIGN KEY (actor_operator_id) REFERENCES platform_operators(id)
        ON DELETE NO ACTION,

    -- FK simple a organizations
    CONSTRAINT fk_api_operations_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id)
        ON DELETE NO ACTION,

    -- FK compuesta empresarial (garantiza que actor_user_id pertenece a organization_id)
    CONSTRAINT fk_api_operations_user
        FOREIGN KEY (organization_id, actor_user_id) REFERENCES app_users(organization_id, id)
        ON DELETE NO ACTION,

    CONSTRAINT chk_api_operations_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),

    CONSTRAINT chk_api_operations_outcome
        CHECK (outcome IS NULL OR outcome IN ('SUCCEEDED', 'REJECTED')),

    -- Coherencia estricta de actor:
    -- Si es operador: actor_operator_id NOT NULL, organization_id IS NULL, actor_user_id IS NULL.
    -- Si es usuario empresarial: actor_operator_id IS NULL, organization_id NOT NULL, actor_user_id NOT NULL.
    CONSTRAINT chk_api_operations_actor_coherence
        CHECK (
            (actor_operator_id IS NOT NULL AND actor_user_id IS NULL AND organization_id IS NULL)
            OR (actor_operator_id IS NULL AND actor_user_id IS NOT NULL AND organization_id IS NOT NULL)
        ),

    -- Coherencia entre status, outcome y respuesta HTTP
    CONSTRAINT chk_api_operations_status_coherence
        CHECK (
            (status = 'IN_PROGRESS' AND outcome IS NULL AND http_status IS NULL AND completed_at IS NULL AND response_body IS NULL)
            OR (status = 'COMPLETED' AND outcome IS NOT NULL AND http_status IS NOT NULL AND completed_at IS NOT NULL)
        ),

    CONSTRAINT chk_api_operations_expiry
        CHECK (expires_at > created_at)
);

-- Índice único filtrado para comandos de operadores (donde organization_id es NULL)
CREATE UNIQUE INDEX uix_api_operations_operator
    ON api_operations (actor_operator_id, idempotency_key)
    WHERE actor_operator_id IS NOT NULL;

-- Índice único filtrado para comandos empresariales
CREATE UNIQUE INDEX uix_api_operations_user
    ON api_operations (organization_id, actor_user_id, idempotency_key)
    WHERE actor_user_id IS NOT NULL;
