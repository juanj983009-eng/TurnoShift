-- =============================================================================
-- V10: platform_operators
-- =============================================================================
-- Operadores de plataforma independientes del dominio multi-tenant de clientes.
-- No pertenece a ninguna organización cliente. No concede acceso empresarial por sí mismo.
--
-- email/normalized_email: NVARCHAR(254), capacidad física según RFC 5321.
-- normalized_email: trim exterior + minúsculas en capa de aplicación,
--                   comparado byte a byte con collation binaria estricta BIN2.
-- password_hash: VARCHAR(512) no nulo, hash Argon2id válido, nunca vacío.
-- auth_version: control de revocación inmediata de sesiones activas.
-- lock_version: control de concurrencia optimista.
--
-- Restricciones:
--   - PK id
--   - UNIQUE normalized_email
--   - CHECKs no vacíos para email, normalized_email, display_name, password_hash
--   - CHECKs no negativos para auth_version y lock_version
--   - Índice ix_platform_operators_active_lookup para revalidación de sesión
-- =============================================================================

CREATE TABLE platform_operators (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    email NVARCHAR(254) NOT NULL,
    normalized_email NVARCHAR(254) COLLATE Latin1_General_100_BIN2 NOT NULL,
    display_name NVARCHAR(150) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    auth_version BIGINT NOT NULL DEFAULT 1,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIMEOFFSET(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIMEOFFSET(3) NOT NULL DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_platform_operators PRIMARY KEY (id),
    CONSTRAINT uq_platform_operators_normalized_email UNIQUE (normalized_email),
    CONSTRAINT chk_platform_operators_email_not_empty CHECK (LEN(LTRIM(RTRIM(email))) > 0),
    CONSTRAINT chk_platform_operators_normalized_email_not_empty CHECK (LEN(LTRIM(RTRIM(normalized_email))) > 0),
    CONSTRAINT chk_platform_operators_display_name_not_empty CHECK (LEN(LTRIM(RTRIM(display_name))) > 0),
    CONSTRAINT chk_platform_operators_password_hash_not_empty CHECK (LEN(LTRIM(RTRIM(password_hash))) > 0),
    CONSTRAINT chk_platform_operators_auth_version_non_negative CHECK (auth_version >= 0),
    CONSTRAINT chk_platform_operators_lock_version_non_negative CHECK (lock_version >= 0)
);

CREATE INDEX ix_platform_operators_active_lookup
    ON platform_operators (id, is_active, auth_version);
