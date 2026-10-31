-- V5: app_users
-- email/normalized_email: NVARCHAR(254), physical capacity per RFC 5321 (may be <254 UTF-16 units for chars outside BMP)
-- normalized_email: trim exterior + lowercase via application; no dot removal, no + removal
-- Application normalizes and validates syntax; DB does not enforce syntax
-- password_hash nullable for INITIAL_SETUP pending accounts; VARCHAR(512), never plaintext, never empty string when present
-- employee_id: deferred FK — column exists nullable but FK to employees deferred until employees table is created (I2+). Referential integrity is intentionally incomplete in I1; the full constraint will be added when employees are introduced. No fictional employees are inserted to fill this gap.
-- auth_version and lock_version: non-negative (CHECK constraint)
-- DEC-08-IDENTITY fields documented here

CREATE TABLE app_users (
    organization_id UNIQUEIDENTIFIER NOT NULL,
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    email NVARCHAR(254) NOT NULL,
    normalized_email NVARCHAR(254) NOT NULL,
    display_name NVARCHAR(150) NOT NULL,
    password_hash VARCHAR(512) NULL,
    role_code VARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    employee_id UNIQUEIDENTIFIER NULL,
    password_changed_at DATETIMEOFFSET(3) NULL,
    must_change_password BIT NOT NULL DEFAULT 0,
    auth_version BIGINT NOT NULL DEFAULT 0,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIMEOFFSET(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIMEOFFSET(3) NOT NULL DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT uq_app_users_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_app_users_org_normalized_email UNIQUE (organization_id, normalized_email),
    CONSTRAINT fk_app_users_organization FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE NO ACTION,
    CONSTRAINT fk_app_users_role FOREIGN KEY (role_code) REFERENCES roles(code) ON DELETE NO ACTION,
    CONSTRAINT chk_app_users_email_not_empty CHECK (LEN(LTRIM(RTRIM(email))) > 0),
    CONSTRAINT chk_app_users_normalized_email_not_empty CHECK (LEN(LTRIM(RTRIM(normalized_email))) > 0),
    CONSTRAINT chk_app_users_display_name_not_empty CHECK (LEN(LTRIM(RTRIM(display_name))) > 0),
    CONSTRAINT chk_app_users_password_hash_not_empty CHECK (password_hash IS NULL OR LEN(password_hash) > 0),
    CONSTRAINT chk_app_users_auth_version_non_negative CHECK (auth_version >= 0),
    CONSTRAINT chk_app_users_lock_version_non_negative CHECK (lock_version >= 0)
);

CREATE UNIQUE INDEX uix_app_users_org_employee
    ON app_users (organization_id, employee_id)
    WHERE employee_id IS NOT NULL;
