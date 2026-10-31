# TurnoShift SaaS

Sistema web de planificación de turnos de trabajo, horarios e intercambios entre colaboradores para pequeñas y medianas empresas.

## Estado del Proyecto

Incremento `I0` completado (dentro de su alcance). Backend Spring Boot 4.0.0 + Java 21 con persistencia Flyway sobre SQL Server 2022 operativa. Frontend React 19 + Vite 6 + TypeScript construido limpiamente.

## Navegación y Contexto

Toda la documentación técnica se encuentra en:

 **[Índice de Navegación del Repositorio](docs/context/INDEX.md)**

Para normas de desarrollo y restricciones: [DEVELOPMENT_RULES.md](docs/context/DEVELOPMENT_RULES.md)

---

## Ejecución Local — Backend (Windows PowerShell)

### Requisitos previos

- **Java 21** local (JDK Temurin 21 o equivalente).
- **Docker Desktop** en ejecución.
- **Maven Wrapper** incluido en `backend/` (`mvnw.cmd`). No requiere Maven instalado.

### 1. Iniciar SQL Server de desarrollo

```powershell
# Desde la raíz del repositorio
# Copiar la plantilla si es la primera vez:
Copy-Item infra/.env.dev.example infra/.env.dev
# Editar infra/.env.dev y asignar una contraseña fuerte a MSSQL_SA_PASSWORD
# (mínimo 8 caracteres, mayúsculas, minúsculas, dígito y símbolo)

# Iniciar el contenedor (crea el volumen turnoshift_sqldev_data si no existe)
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env.dev up -d

# Verificar que el servicio está listo (health: healthy)
docker ps --filter name=turnoshift-sqldev
```

> **Nota:** `--env-file` pasa las variables SOLO al contenedor Docker; NO las exporta al shell PowerShell anfitrión.

### 2. Crear la base de datos (primera vez o tras destruir el volumen)

```powershell
# Leer la contraseña desde .env.dev en PowerShell (sin mostrarla en pantalla)
$sa = ((Get-Content infra/.env.dev | Where-Object { $_ -match "^MSSQL_SA_PASSWORD=" }) -split "=",2)[1]

docker compose -f infra/docker-compose.dev.yml --env-file infra/.env.dev `
  exec turnoshift-sqldev `
  /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$sa" `
  -Q "IF DB_ID('turnoshift_dev') IS NULL CREATE DATABASE turnoshift_dev"
```

### 3. Configurar variables del backend

Spring Boot **no lee archivos `.env` automáticamente**. Las variables se deben exportar en el shell antes de arrancar el backend:

```powershell
# Leer contraseña desde .env.dev
$sa = ((Get-Content infra/.env.dev | Where-Object { $_ -match "^MSSQL_SA_PASSWORD=" }) -split "=",2)[1]

$env:TURNOSHIFT_DB_URL      = "jdbc:sqlserver://localhost:11433;databaseName=turnoshift_dev;encrypt=false"
$env:TURNOSHIFT_DB_USER     = "sa"
$env:TURNOSHIFT_DB_PASSWORD = $sa
$env:JAVA_HOME              = "C:\Users\juan9\.jdks\temurin-21.0.10"  # Ajustar si es distinto
```

> **Nunca incluyas contraseñas reales en comandos documentados.** Lee siempre desde el archivo local ignorado por Git.

### 4. Arrancar el backend

```powershell
# Puerto por defecto: 8081. Si está ocupado, añadir: -Dspring-boot.run.jvmArguments="-Dserver.port=8082"
cmd /c "mvnw.cmd spring-boot:run"
```

Al arrancar, Flyway aplica automáticamente las migraciones V1/V2/V3 en `turnoshift_dev`. El log confirma:

```
Migrating schema [dbo] to version "1 - organizations"
Migrating schema [dbo] to version "2 - sites"
Migrating schema [dbo] to version "3 - organizations constraints"
Successfully applied 3 migrations to schema [dbo], now at version v3
```

### 5. Verificar arranque

```
GET http://localhost:8081/api/health
→ {"status":"UP","service":"turnoshift-backend"}
```

### 6. Parar (sin destruir datos)

```powershell
docker compose -f infra/docker-compose.dev.yml stop
```

---

## Pruebas de integración (Testcontainers)

Las pruebas usan contenedores SQL Server **efímeros e independientes** del contenedor de desarrollo. No requieren que el contenedor de desarrollo esté en ejecución.

```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
cmd /c "mvnw.cmd verify -B"
```

**Resultado verificado (2026-08-30):** BUILD SUCCESS — 30/30 pruebas PASS.

| Clase | Tests | Descripción |
|---|---|---|
| `SchemaUpgradeV2ToV3IT` | 8 | Actualización V2→V3 con datos preexistentes |
| `OrganizationsSitesSchemaIT` | 12 | Schema con FlywayAutoConfiguration y @SpringBootTest |
| `SpringBootStartupIT` | 5 | Bean Flyway inyectado, tablas, health endpoint |
| `FlywayMssqlCompatibilityIT` | 5 | PoC-01 compatibilidad Flyway + SQL Server 2022 |

Docker debe estar en ejecución. Si no está disponible, las pruebas fallan con error claro; no se omiten ni se sustituyen por H2.

### Imagen SQL Server

```
mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04
```

**Licencia:** SQL Server 2022 Developer Edition — autorizada para desarrollo y pruebas únicamente.

---

## Frontend (React 19 + TypeScript + Vite 6)

```powershell
cd frontend
npm install      # solo la primera vez
npm run dev      # modo desarrollo
npm run build    # compilación de producción (verificado: 0 errores, 1.10s)
```

---

## Estructura del repositorio

```
backend/    Spring Boot 4.0.0 — Java 21
frontend/   React 19 + TypeScript + Vite 6
docs/       Documentación técnica y planificación
infra/      docker-compose.dev.yml + .env.dev.example
```
