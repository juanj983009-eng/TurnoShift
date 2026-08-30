# TurnoShift SaaS

Sistema web de planificación de turnos de trabajo, horarios e intercambios entre colaboradores para pequeñas y medianas empresas.

## Estado del Proyecto
Incremento `I0` en curso. Estructura técnica base creada para backend (Spring Boot 4.0.0, Java 21) y frontend (React 19, Vite 6, TypeScript).

## Navegación y Contexto
Toda la documentación técnica y del sistema se encuentra organizada en el paquete de contexto:

👉 **[Índice de Navegación del Repositorio](docs/context/INDEX.md)**

Para consultar las normas de desarrollo y restricciones, véase [DEVELOPMENT_RULES.md](docs/context/DEVELOPMENT_RULES.md).

## Instrucciones de Ejecución Local

### Backend (Spring Boot — Java 21)
El backend requiere Java 21. Si el JDK en PATH por defecto es anterior, especifique `JAVA_HOME` hacia la ubicación de Java 21 al ejecutar:

```bash
# Desde la carpeta backend/
cd backend

# En Windows CMD (usando JDK 21 local):
set JAVA_HOME=C:\Users\juan9\.jdks\temurin-21.0.10&& mvnw spring-boot:run

# Compilación y verificación:
set JAVA_HOME=C:\Users\juan9\.jdks\temurin-21.0.10&& mvnw compile
```

Endpoint de salud verificado: `http://localhost:8081/api/health`

### Frontend (React + TypeScript + Vite)
```bash
# Desde la carpeta frontend/
cd frontend

# Modo desarrollo:
npm run dev

# Compilación de producción:
npm run build
```

## PoC-01 — Verificación de compatibilidad: Flyway + SQL Server 2022 + Testcontainers

Prueba técnica reproducible que verifica la compatibilidad del stack de persistencia
sin introducir tablas de negocio. Cubre la base de HT-02 y HT-07 del incremento I0.

### Requisitos

- **Docker** en ejecución en el host (Docker Desktop o equivalente).
  Si Docker no está disponible, las pruebas fallan con error claro; no se omiten ni se sustituye por H2.
- **Java 21** local (se usa `JAVA_HOME` explícito; no modifica el PATH global).
- Acceso a internet para descargar la imagen en la primera ejecución
  (en ejecuciones posteriores se usa la imagen local en caché).

### Imagen SQL Server

```
mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04
sha256:45a1a9d13ca5574cf8e0fe4ae73ab77248b66d9c3132ac9658fb6c16dd72a8af
```

**Licencia:** SQL Server 2022 Developer Edition — autorizada para desarrollo y pruebas.
Uso en producción prohibido por los términos de Microsoft.
La aceptación programática se realiza mediante `.acceptLicense()` en la clase de prueba.

### Comando reproducible

```cmd
cd backend
set JAVA_HOME=C:\Users\juan9\.jdks\temurin-21.0.10&& mvnw.cmd verify -B
```

En PowerShell:
```powershell
cd backend
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
cmd /c "mvnw.cmd verify -B"
```

### Qué se verifica (5 pruebas, todas aprobadas 2026-08-30)

| Prueba | Resultado |
|--------|-----------|
| V1 + V2 se ejecutan desde base vacía (2 migraciones registradas) | ✅ PASS |
| Segunda ejecución no reaplica migraciones (idempotencia Flyway) | ✅ PASS |
| Escritura y lectura JDBC contra SQL Server 2022 real | ✅ PASS |
| Transacción revertida no persiste filas | ✅ PASS |
| Constraint CHECK de V2 rechaza valores negativos | ✅ PASS |

Las migraciones ficticias están en `backend/src/test/resources/db/poc/migration/`
y **no se empaquetan en el JAR de producción** (verificado sobre `turnoshift-backend-0.1.0.jar`).
