# REGISTRO DE DECISIONES — TURNOSHIFT

Este documento clasifica las decisiones clave del proyecto (`DEC-01` a `DEC-09`) distinguiendo su nivel de madurez: **Especificado** (definido en planificación), **Pendiente** (por confirmar u homologar) o **Verificado** (comprobado en código/ejecución).

| ID | Decisión | Estado | Descripción y Momento de Aplicación |
| :--- | :--- | :--- | :--- |
| **DEC-01** | Rúbrica y restricciones académicas | **Pendiente** | Pendiente de confirmación. Afecta compromisos académicos sin bloquear el desarrollo funcional básico. |
| **DEC-02** | Equipo y disponibilidad | **Pendiente** | Pendiente de confirmación por el propietario antes de asignar responsables y fechas definitivas. |
| **DEC-03** | Publicación vacía | **Especificado** | Permitida con confirmación explícita y motivo justificado, conservando turnos ya iniciados. Implementar en HU-14. |
| **DEC-04** | Advertencias y motivos | **Especificado** | Exige reconocimiento de advertencias concretas y motivos según el tipo de operación. Aplicar en borrador, publicaciones e intercambios. |
| **DEC-05** | Activación de políticas | **Especificado** | Activación inmediata, versionada y con evaluación de impactos. |
| **DEC-06** | Acceso inicial y recuperación | **Especificado / Pendiente** | Mecanismo técnico especificado; canal y procedimiento operativo de entrega para piloto pendiente de concretar. |
| **DEC-07** | Idempotencia y previsualización | **Especificado** | Diseñado en notas 07 y 10. Implementación y pruebas de concurrencia pendientes. |
| **DEC-08** | Detalles de OpenAPI y límites | **Especificado / Pendiente** | Contrato I1 creado en `docs/api/openapi.yaml`. Pendiente de concretar antes de implementar: longitudes de `organizationCode`, `displayName`, `password`; vencimiento del enlace (30 min propuesto); límites de intentos de login; tamaño máximo de petición. Ver `docs/api/README.md` para la lista completa con comentarios `# TODO: DEC-08`. |
| **DEC-09** | Entorno y operación piloto | **Pendiente** | Confirmar infraestructura, presupuesto y soporte antes de incorporar datos reales. |

| **PROP-OP-01** | Mecanismo de autenticación del operador | **Pendiente de revisión** | La planificación especifica que el alta de organizaciones se realiza mediante herramienta restringida (Nota 10, §17.1) y que `platform_operators` es una entidad separada de `app_users` (Nota 07, §6.1). No especifica el mecanismo técnico. Propuesta mínima: sesión propia gestionada por el servidor, identificada por `platform_operators.subject`, accesible únicamente desde red restringida. Rutas provisionales: `/operator/setup/*`. Bloquea implementación de HU-01 y HU-04.1. |

*Nota: Ninguna propuesta arquitectónica o técnica se considera verificada en código hasta que exista implementación ejecutada y probada.*

---

## Decisiones técnicas adoptadas en I0 (2026-08-30)

### DEC-08-FIELDS — Límites de campos físicos (parcial, organizations y sites)

Concretado al implementar V1/V2. Pendientes: contraseñas, rate limiting, operador.

| Campo | Tabla(s) | Tipo SQL | Longitud | Reglas |
|---|---|---|---|---|
| `code` | `organizations`, `sites` | `VARCHAR(32) COLLATE Latin1_General_100_BIN2` | 2–32 chars | Solo `[A-Z0-9-]`; sin espacios; comparación binaria exacta (sin depender de collation predeterminada del servidor) |
| `display_name` / `name` | `organizations`, `sites` | `NVARCHAR(150)` | ≥1 char no vacío (LTRIM/RTRIM en CHECK) | Unicode; la normalización NFC corresponde a la aplicación |
| `timezone_id` | `organizations` | `VARCHAR(64) COLLATE Latin1_General_100_CI_AS` | ≥1 char | Identificador de zona IANA; la validación contra zonas reconocidas la hace la aplicación |

**Normalización y comparaciones:**
- `code` usa collation `Latin1_General_100_BIN2` para comparación binaria exacta. No depende del collation por defecto del servidor ni de la base de datos.
- Unicidad de `code` en `organizations` es global (toda la plataforma). En `sites` es local (por `organization_id`).
- `display_name` y `name` son `NVARCHAR`; la normalización Unicode (NFC/NFD) debe aplicarse en capa de aplicación antes de persistir.
- No existe truncamiento silencioso: las columnas tienen `CHECK` de longitud mínima y el tipo impone el máximo.

**Pendientes de DEC-08:** longitudes de contraseñas, rate limiting de intentos, tamaño máximo de petición HTTP, vencimiento del enlace de acceso inicial.

### DEC-08-DEFER-FK-POLICY — FK de `current_policy_version_id` diferida

`organizations.current_policy_version_id` referenciará `planning_policy_versions.id`.  
Esta tabla se crea en el paso 5 del orden de implementación (Nota 07, §17) mientras `organizations` se crea en el paso 2, formando una referencia circular.

**Decisión:** La columna existe como `UNIQUEIDENTIFIER NULL` desde V1, pero la FK se añade en una migración posterior (cuando `planning_policy_versions` exista). Esto es explícito en el comentario de V1.  
La condición de referencia circular se resuelve creando primero las tablas, luego la FK, según Nota 07, §17: "Cuando existan referencias circulares, se crean las tablas y después las FKs". No se deshabilitan restricciones en producción.

