-- V9: spring_session
-- Esquema de Spring Session JDBC para SQL Server.
-- Fuente: spring-session-jdbc:4.0.0 (JAR oficial, org/springframework/session/jdbc/schema-sqlserver.sql).
--         Extraído directamente del JAR resuelto por el BOM de Spring Boot 4.0.0.
--         Versión exacta verificada el 2026-08-31.
--
-- Condiciones:
-- - Flyway es el único propietario del esquema (spring.session.jdbc.initialize-schema=never).
-- - Sin separadores GO (incompatibles con Flyway); sentencias separadas por punto y coma.
-- - Sin IF EXISTS / IF NOT EXISTS para evitar ocultar estados parciales.
-- - Los nombres de tablas e índices son los estándar de Spring Session (SPRING_SESSION,
--   SPRING_SESSION_ATTRIBUTES) para compatibilidad con JdbcIndexedSessionRepository.
-- - ON DELETE CASCADE en SPRING_SESSION_ATTRIBUTES → SPRING_SESSION está presente en el
--   esquema oficial. Afecta únicamente a las tablas técnicas de sesión; no hay dependencias
--   hacia entidades de negocio (organizations, app_users, etc.).
-- - PRINCIPAL_NAME se almacena cuando la sesión está autenticada. En sesiones anónimas es NULL.
-- - ATTRIBUTE_BYTES usa IMAGE (varbinary(max) heredado), igual que el esquema oficial.
--
-- Para descartar las tablas usar el esquema oficial schema-drop-sqlserver.sql del mismo JAR.

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES IMAGE NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);
