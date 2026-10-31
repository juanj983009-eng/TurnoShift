-- V6: password_reset_tokens
-- token_hash BINARY(32): SHA-256 hash of the secret token; the original secret is NEVER stored
-- Purposes: INITIAL_SETUP (first password setup), PASSWORD_RESET (recovery)
-- This migration creates the SCHEMA only. Emission, consumption, expiration, revocation, and endpoints are NOT implemented yet — those runtime rules are pending.
-- expires_at must be after created_at (CHECK constraint enforced)
-- Composite FK (organization_id, user_id) ensures a token cannot reference a user from a different organization
-- DEC-08-IDENTITY: purpose VARCHAR(32)

CREATE TABLE password_reset_tokens (
    organization_id UNIQUEIDENTIFIER NOT NULL,
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    user_id UNIQUEIDENTIFIER NOT NULL,
    token_hash BINARY(32) NOT NULL,
    purpose VARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    expires_at DATETIMEOFFSET(3) NOT NULL,
    consumed_at DATETIMEOFFSET(3) NULL,
    revoked_at DATETIMEOFFSET(3) NULL,
    created_at DATETIMEOFFSET(3) NOT NULL DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_prt_app_user FOREIGN KEY (organization_id, user_id) REFERENCES app_users(organization_id, id) ON DELETE NO ACTION,
    CONSTRAINT chk_prt_purpose CHECK (purpose IN ('INITIAL_SETUP', 'PASSWORD_RESET')),
    CONSTRAINT chk_prt_expires_after_created CHECK (expires_at > created_at)
);
