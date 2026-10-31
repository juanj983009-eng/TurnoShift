-- V4: roles
-- Catálogo global de roles empresariales. No contiene organization_id.
-- Roles fijos del MVP: ADMINISTRADOR, SUPERVISOR, COLABORADOR.
-- El operador de plataforma NO es un rol empresarial (entidad separada platform_operators).
-- DEC-08-IDENTITY (parcial): code VARCHAR(32), name NVARCHAR(150).

CREATE TABLE roles (
    code VARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    name NVARCHAR(150) NOT NULL,
    description NVARCHAR(500) NULL,
    
    CONSTRAINT pk_roles PRIMARY KEY (code),
    CONSTRAINT chk_roles_code_not_empty CHECK (LEN(LTRIM(RTRIM(code))) > 0),
    CONSTRAINT chk_roles_name_not_empty CHECK (LEN(LTRIM(RTRIM(name))) > 0)
);

INSERT INTO roles (code, name, description) VALUES
('ADMINISTRADOR', N'Administrador', N'Gestiona la organización, usuarios y planificación.'),
('SUPERVISOR', N'Supervisor', N'Supervisa planificación e intercambios.'),
('COLABORADOR', N'Colaborador', N'Consulta y solicita intercambios de turno.');
