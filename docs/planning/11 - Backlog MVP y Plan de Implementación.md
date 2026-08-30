
## 1. Propósito

Convertir la planificación de TurnoShift en trabajo concreto, ordenado y verificable.

Este documento define:

- Habilitadores técnicos.
    
- Historias funcionales.
    
- Trabajo operativo.
    
- Dependencias.
    
- Incrementos de construcción.
    
- Criterios para iniciar y terminar tareas.
    
- Evidencias de avance.
    
- Control de defectos y cambios de alcance.
    

El objetivo es construir un MVP funcional que pueda probarse con empresas y evolucionar hacia un SaaS comercial.

Una demostración académica, un conjunto de pantallas o una lista de tareas completadas no demuestran por sí solos que el sistema esté preparado para operar con datos reales.

**Estado actual:** backlog propuesto. Esta revisión no acredita implementación, pruebas ni despliegues realizados.

### Cambios de esta revisión

- Actualiza decisiones que ya fueron especificadas en otras notas.
    
- Corrige la dependencia circular del acceso inicial.
    
- Distingue entregas parciales de historias terminadas.
    
- Incorpora confirmaciones específicas, publicación vacía y recuperación de operaciones.
    
- Precisa la revisión independiente de intercambios.
    
- Alinea reportes y exportaciones con la API.
    
- Añade trabajo operativo explícito para la salida asistida ya incluida en RF-003.
    
- Separa preparación funcional, académica y operativa.
    

## 2. Documentos relacionados

- [[01 - Formulación y Visión del Producto]]
    
- [[02 - Modelo de Negocio SaaS y Validación Comercial]]
    
- [[03 - Alcance MVP y Roadmap Comercial]]
    
- [[04 - Roles, Procesos y Reglas de Negocio]]
    
- [[05 - Requisitos y Casos de Uso]]
    
- [[06 - Arquitectura y Stack Tecnológico]]
    
- [[07 - Modelo de Datos y DER]]
    
- [[08 - Navegación, Pantallas y Flujos de Usuario]]
    
- [[09 - Design System y Wireframes]]
    
- [[10 - Contratos API e Integraciones]]
    
- [[12 - Pruebas, Seguridad y Operación]]
    
- [[13 - Lanzamiento Piloto y Evolución Comercial]]
    
- [[14 - Gestión Académica y Entregables]]
    
- [[15 - Contexto de Desarrollo y Cierre de Planificación]]
    

Las historias complementan esos documentos. No reemplazan sus reglas ni autorizan a omitir criterios porque aquí estén resumidos.

## 3. Resultado esperado del MVP

El recorrido principal debe permitir:

1. Incorporar una empresa mediante el operador autorizado.
    
2. Establecer de forma segura el acceso del primer administrador.
    
3. Configurar sede, puestos, colaboradores y políticas.
    
4. Habilitar las cuentas necesarias.
    
5. Registrar indisponibilidades.
    
6. Crear, guardar y validar un borrador semanal.
    
7. Revisar impactos y publicar.
    
8. Consultar el horario personal.
    
9. Proponer un intercambio.
    
10. Obtener la respuesta del compañero.
    
11. Resolverlo mediante un responsable independiente.
    
12. Actualizar ambos turnos y las publicaciones afectadas.
    
13. Consultar reportes, avisos e historial.
    
14. Operar, respaldar y recuperar el servicio.
    
15. Ejecutar una salida asistida cuando corresponda.
    

Todo ello debe respetar aislamiento empresarial, permisos, historial, tiempo del servidor y consistencia de datos.

El sistema representa programación, no asistencia ni trabajo efectivamente realizado.

## 4. Límites de alcance

### 4.1. Incluido

- Organizaciones aisladas.
    
- Una sede activa por organización.
    
- Incorporación y administración asistida del servicio.
    
- Roles predefinidos.
    
- Usuarios y colaboradores como entidades diferentes.
    
- Indisponibilidades administradas.
    
- Políticas versionadas con activación inmediata.
    
- Planificación manual.
    
- Guardado explícito.
    
- Publicaciones inmutables y revisión mediante borradores.
    
- Publicación vacía con confirmación y motivo.
    
- Reconocimiento específico de advertencias.
    
- Horario personal.
    
- Intercambios entre dos personas y dos turnos.
    
- Revisión independiente.
    
- Reportes operativos y CSV consistente.
    
- Avisos internos.
    
- Auditoría.
    
- Recuperación de acceso.
    
- Manejo de concurrencia y reintentos.
    
- Operación mínima segura.
    
- Salida asistida.
    

### 4.2. Excluido

- Registro empresarial autoservicio.
    
- Cobros automáticos.
    
- Facturación electrónica.
    
- Operación multisede completa.
    
- Asistencia, nómina, GPS o biometría.
    
- Aplicación móvil nativa.
    
- Funcionamiento offline.
    
- WhatsApp, SMS o correo automático de avisos.
    
- Chat.
    
- Optimización automática o inteligencia artificial.
    
- Integraciones de calendario.
    
- API pública para terceros.
    
- Roles personalizados.
    
- Constructor de reportes.
    
- Solicitudes de vacaciones con otro flujo de aprobación.
    
- Activación programada de políticas.
    
- Migración automática de zona tras publicar.
    
- Portal automático de baja y eliminación.
    

El interés comercial en una función futura no la convierte en requisito de esta entrega.

## 5. Organización del trabajo

### 5.1. Tipos de elementos

|Tipo|Uso|
|---|---|
|DEC|Decisión que condiciona una parte del trabajo|
|HT|Habilitador técnico compartido|
|HU|Historia con resultado funcional verificable|
|OP|Trabajo operativo necesario|
|VAL|Validación de funcionamiento, experiencia o preparación|
|Defecto|Incumplimiento observado|

### 5.2. Prioridades

|   |   |
|---|---|
|Prioridad|Significado|
|P0|Necesario para el recorrido principal o para proteger datos y operación|
|P1|Parte del MVP, construible después del núcleo|
|P2|Evolución posterior, fuera de esta entrega|

**P1 no significa opcional.**

Retirar una función del MVP exige una decisión de alcance explícita.

La seguridad, integridad y recuperación no se posponen por considerarse tareas secundarias.

### 5.3. Estados

- Pendiente.
    
- En definición.
    
- Lista para desarrollar.
    
- En desarrollo.
    
- En revisión.
    
- En pruebas.
    
- Terminada.
    

“Bloqueado” es una marca adicional con causa y condición de desbloqueo.

### 5.4. Responsabilidad

Cada elemento activo tendrá una persona responsable.

No se asignan nombres, disponibilidad ni fechas todavía no confirmados.

Si el trabajo es individual, una misma persona puede asumir varias funciones, pero debe conservar las revisiones y evidencias.

### 5.5. Identificadores y entregas parciales

Se conservan HT-01 a HT-07, HU-01 a HU-28, OP-01 a OP-03 y VAL-01 a VAL-03.

Se añade **OP-04** para hacer explícito el trabajo de salida asistida ya requerido.

Una historia puede dividirse en subtareas, por ejemplo `HU-04.1`, sin crear otra función ni alterar su identificador principal.

Terminar una subtarea no termina la historia completa.

## 6. Decisiones y condiciones previas

Se conservan los identificadores originales, actualizando su situación.

|   |   |   |
|---|---|---|
|ID|Tema|Situación y siguiente acción|
|DEC-01|Rúbrica y restricciones del curso|Pendiente de confirmación; afecta compromisos académicos, no bloquea por sí sola el desarrollo funcional|
|DEC-02|Integrantes y disponibilidad|Pendiente; necesario antes de asignar responsables y fechas|
|DEC-03|Publicación vacía|Especificada en 04, 05 y 10: permitida con confirmación y motivo, conservando turnos iniciados|
|DEC-04|Advertencias y motivos|Especificada: reconocimiento de advertencias concretas y motivos según cada operación|
|DEC-05|Activación de políticas|Especificada: inmediata, versionada, con evaluación y confirmación de impacto|
|DEC-06|Acceso inicial y recuperación|Mecanismo especificado; falta concretar y ensayar responsables, verificación de identidad y canal de entrega para el piloto|
|DEC-07|Idempotencia y previsualización|Diseño especificado en 07 y 10; implementación y pruebas pendientes|
|DEC-08|Detalles del contrato|Completar OpenAPI, límites de campos, tamaños y respuestas pendientes antes del endpoint afectado|
|DEC-09|Entorno y operación piloto|Confirmar presupuesto, infraestructura, soporte y responsables antes de incorporar datos reales|

### Interpretación

“Especificada” no significa “implementada”, “probada” ni “validada por un cliente”.

Las decisiones documentadas sirven como base de construcción. Una objeción pendiente debe resolverse antes de marcar la historia afectada como lista.

No se bloquea todo el proyecto por una condición que afecta únicamente al piloto, al calendario o a una función concreta.

Los valores reales de las políticas del cliente no se inventan. Durante el desarrollo se utilizan datos ficticios identificados.

## 7. Criterio para empezar una tarea

Una tarea está **lista para desarrollar** cuando tiene:

- Objetivo claro.
    
- Alcance y exclusiones.
    
- Actor y permisos.
    
- Criterios observables.
    
- Reglas de negocio relacionadas.
    
- Contrato y modelo suficientes.
    
- Dependencias disponibles.
    
- Decisiones bloqueantes resueltas.
    
- Datos de prueba.
    
- Forma prevista de verificar el resultado.
    
- Tamaño controlable.
    

Una tarea demasiado grande se divide.

No es necesario definir por adelantado cada detalle visual menor, pero sí resolver permisos, efectos, integridad y contratos que condicionen la implementación.

## 8. Habilitadores técnicos

### HT-01 — Repositorio y ejecución local

**Prioridad:** P0.

**Trabajo:**

- Estructura de frontend, backend y documentación.
    
- Configuración externa.
    
- Ejemplo de configuración sin secretos.
    
- Instrucciones para preparar el entorno.
    
- Separación entre desarrollo, prueba y operación.
    

**Aceptación:**

- El proyecto puede prepararse desde una copia limpia.
    
- Las dependencias necesarias están documentadas.
    
- La configuración ausente produce errores comprensibles.
    
- No hay credenciales reales en el repositorio.
    

### HT-02 — Migraciones y datos de prueba

**Prioridad:** P0.  
**Depende de:** HT-01.

**Trabajo:**

- Implementar progresivamente el modelo de la nota 07.
    
- Incorporar restricciones de pertenencia e integridad.
    
- Preparar datos ficticios reproducibles.
    
- Separar datos de demostración de operación real.
    

**Aceptación:**

- Una base vacía puede prepararse mediante migraciones.
    
- Existen al menos dos empresas ficticias para pruebas de aislamiento.
    
- Publicaciones, revisiones y decisiones conservan sus relaciones.
    
- Las restricciones relevantes se prueban en la base seleccionada.
    
- La carga ficticia no se ejecuta accidentalmente en producción.
    

No se exige escribir todas las migraciones futuras antes de entregar el primer recorrido.

### HT-03 — Sesión, permisos y errores comunes

**Prioridad:** P0.  
**Depende de:** HT-02.

**Trabajo:**

- Sesiones gestionadas en servidor.
    
- Protección de solicitudes según arquitectura.
    
- Contexto de usuario y empresa.
    
- Autorización por rol y recurso.
    
- Errores comunes y correlación.
    
- Revocación de acceso.
    
- Protección de registros frente a secretos.
    

**Aceptación:**

- Una petición sin permiso no modifica datos.
    
- Cambiar un identificador no permite acceder a otra empresa.
    
- Las sesiones vencidas o revocadas dejan de operar.
    
- Los cambios de permisos afectan solicitudes posteriores.
    
- Los errores coinciden con la nota 10.
    
- La consulta periódica de avisos no mantiene indefinidamente una sesión inactiva.
    

### HT-04 — Transacciones, versiones e idempotencia

**Prioridad:** P0.  
**Depende de:** HT-02 y HT-03.

**Trabajo:**

- Coordinación de escrituras críticas.
    
- Versiones esperadas.
    
- Registro persistente de operaciones.
    
- Recuperación de resultados.
    
- Huellas de previsualización.
    
- Pruebas de concurrencia y respuesta perdida.
    

**Aceptación:**

- Misma clave y contenido no duplican efectos.
    
- Misma clave con otro contenido produce conflicto.
    
- Una versión antigua no sobrescribe datos actuales.
    
- El resultado y el cambio de negocio se confirman consistentemente.
    
- Recuperar un resultado distingue éxito y rechazo.
    
- No encontrarlo no se interpreta como prueba de que nunca se ejecutó.
    
- La repetición comprueba autorización actual antes de devolver información.
    
- No se almacenan contraseñas, cookies ni enlaces secretos.
    
- La retención prevista y sus límites coinciden con la nota 10.
    
- Una previsualización no mantiene bloqueos mientras el usuario la revisa.
    
- El tiempo y las reglas se comprueban nuevamente al confirmar.
    

Este habilitador incluye el seguimiento de la intención en el frontend, no solamente la protección del backend.

### HT-05 — Auditoría, decisiones y bandeja de salida

**Prioridad:** P0.  
**Depende de:** HT-02 y HT-03.

**Trabajo:**

- Auditoría empresarial y del operador.
    
- Registro de decisiones de planificación.
    
- Evidencia de advertencias reconocidas.
    
- Eventos pendientes para avisos.
    
- Reintentos y deduplicación de entrega.
    

**Aceptación:**

- Cada operación crítica conserva su evidencia.
    
- Fallar al registrar auditoría crítica impide confirmar la operación.
    
- Una reversión no aparece como éxito.
    
- Publicación, aprobación y activación de política conservan actor y motivo según las reglas.
    
- Las advertencias se registran con valores calculados por el servidor.
    
- El fallo posterior de un aviso no revierte ni repite la operación original.
    

### HT-06 — Componentes visuales y estados compartidos

**Prioridad:** P0.  
**Depende de:** HT-01.

**Trabajo:**

- Componentes DS-01 a DS-14 según se necesiten.
    
- Navegación por permisos.
    
- Formularios, mensajes y diálogos.
    
- Estados de guardado, validación y publicación.
    
- Reconocimientos individuales.
    
- Resultado desconocido.
    
- Presentación móvil y teclado.
    

**Aceptación:**

- Las primeras pantallas reutilizan componentes.
    
- Fallo de carga y ausencia de datos se distinguen.
    
- Guardar no se presenta como publicar.
    
- Las confirmaciones explican sus efectos.
    
- Los controles principales funcionan con teclado.
    
- Las decisiones extensas siguen siendo revisables en móvil.
    
- Los componentes no inventan permisos ni filtros.
    

### HT-07 — Verificación automatizada y contrato

**Prioridad:** P0.  
**Depende de:** HT-01; se amplía con cada incremento.

**Trabajo:**

- Ejecución repetible de pruebas.
    
- Comprobaciones antes de integrar cambios.
    
- OpenAPI sincronizado.
    
- Pruebas de integración con la base seleccionada.
    
- Pruebas de los recorridos completos.
    

**Aceptación:**

- Los fallos son visibles.
    
- Las comprobaciones pueden repetirse.
    
- Las pruebas de integridad no se sustituyen únicamente por simulaciones en memoria.
    
- Los contratos se verifican contra el comportamiento implementado.
    
- Cada incremento agrega evidencia de sus reglas críticas.
    

## 9. Backlog funcional

Todas las historias permanecen **pendientes** hasta que exista evidencia de ejecución.

### Dependencias comunes

Además de las dependencias particulares:

- Toda escritura utiliza los controles aplicables de HT-03, HT-04 y HT-05.
    
- Toda pantalla utiliza HT-06.
    
- Toda historia incorpora verificaciones de HT-07.
    
- Las restricciones se comprueban también mediante acceso directo a la API.
    

Las dependencias siguientes son funcionales; no repiten todos esos habilitadores.

## 9.1. E01 — Organización y acceso

### HU-01 — Habilitar una organización

**Actor:** operador.  
**Prioridad:** P0.  
**Depende de:** base de seguridad, persistencia, auditoría y mecanismo de acceso inicial de HU-04.1.  
**Requisito:** RF-001.

**Aceptación:**

- Crea empresa, sede, capacidad y primer administrador consistentemente.
    
- Se ejecuta mediante el mecanismo restringido del operador.
    
- No exige iniciar sesión previamente como administrador de esa empresa.
    
- No usa contraseñas predeterminadas compartidas.
    
- Se entrega el mecanismo temporal mediante el procedimiento autorizado.
    
- Se informa la configuración operativa pendiente.
    
- El alta no genera un horario automáticamente.
    
- Los reintentos no crean otra empresa para la misma intención.
    

### HU-02 — Iniciar sesión y acceder según el rol

**Actor:** usuario empresarial.  
**Prioridad:** P0.  
**Depende de:** HU-01 y establecimiento inicial de contraseña.  
**Requisito:** RF-004.  
**Pantallas:** UI-01, UI-04 y acceso a UI-20.

**Aceptación:**

- Acceso mediante código de empresa, correo y contraseña.
    
- Errores de credenciales sin revelar cuentas.
    
- Cuenta sin contraseña configurada no puede ingresar.
    
- Navegación conforme al contexto autorizado.
    
- Cierre de sesión efectivo en servidor.
    
- Permisos y estado empresarial comprobados en solicitudes posteriores.
    
- Respuesta incierta del acceso se resuelve consultando la sesión.
    
- Las vistas no reutilizan datos de otro usuario.
    

### HU-03 — Administrar cuentas

**Actor:** administrador.  
**Prioridad:** P0.  
**Depende de:** HU-02; vinculación integrada con HU-07.  
**Requisito:** RF-006.  
**Pantalla:** UI-15.

**Aceptación:**

- Crea y administra cuentas con roles predefinidos.
    
- Crear una cuenta no crea un colaborador.
    
- Preserva al último administrador activo.
    
- Vincula únicamente dentro de la empresa y respeta la relación uno a uno.
    
- Cambiar el vínculo se bloquea por solicitudes pendientes cuando corresponda.
    
- No transfiere solicitudes a otra persona.
    
- Desactivar impide acceso e invalida solicitudes afectadas.
    
- No desactiva automáticamente al colaborador.
    
- Comprueba versiones y registra auditoría.
    

**Entrega gradual:** el alta sin vínculo puede construirse antes de HU-07. La historia no termina hasta verificar vinculación e impactos sobre solicitudes.

### HU-04 — Configurar, recuperar y cambiar la contraseña

**Actores:** usuario, administrador y operador dentro de sus procedimientos.  
**Prioridad:** P0.  
**Requisito:** RF-005.  
**Pantallas:** UI-02, UI-03, UI-15 y UI-20.

Se divide para evitar una dependencia circular:

- **HU-04.1 — Base de acceso inicial:** establecimiento mediante enlace e integración con la incorporación del primer administrador. Depende de los habilitadores, no de HU-02 ni HU-03.
    
- **HU-04.2 — Gestión posterior:** cambio autenticado y recuperación asistida desde una empresa operativa. Depende de HU-02 y HU-03.
    

**Aceptación conjunta:**

- Distingue configuración inicial y recuperación.
    
- Enlace de un solo uso y vencimiento efectivo.
    
- Inspeccionarlo no lo consume.
    
- Emitir otro revoca el anterior.
    
- Cambio correcto invalida las sesiones correspondientes.
    
- No se guardan secretos en auditoría o resultados genéricos.
    
- Crear cuenta y emitir enlace son acciones diferenciadas.
    
- La pérdida de una respuesta utiliza el procedimiento específico de seguridad.
    
- El canal y la verificación de identidad se ensayan antes del piloto.
    

## 9.2. E02 — Configuración y equipo

### HU-05 — Configurar organización y sede

**Actor:** administrador.  
**Prioridad:** P0.  
**Depende de:** HU-02.  
**Requisito:** RF-007.  
**Pantalla:** UI-14.

**Aceptación:**

- Modifica únicamente campos permitidos.
    
- Mantiene una sede activa.
    
- Consulta, pero no modifica, los límites del servicio.
    
- Cambiar zona antes de publicar exige previsualización.
    
- Conserva instantes y bloquea líneas que quedarían fuera de su semana.
    
- Después de la primera publicación, incluso vacía, impide cambiar zona.
    
- Comprueba nuevamente versiones e impactos al confirmar.
    
- No altera información histórica.
    

### HU-06 — Administrar puestos

**Actor:** administrador; supervisor en consulta.  
**Prioridad:** P0.  
**Depende de:** HU-05.  
**Requisito:** RF-007.  
**Pantalla:** UI-14.

**Aceptación:**

- Crea, modifica y desactiva puestos según las reglas.
    
- Limita las consultas al ámbito autorizado.
    
- Conserva referencias históricas.
    
- No permite evadir restricciones de planificación modificando catálogos.
    
- El supervisor no recibe controles de administración.
    

### HU-07 — Administrar colaboradores

**Actor:** administrador; supervisor en consulta.  
**Prioridad:** P0.  
**Depende de:** HU-06; integración de vínculos con HU-03.  
**Requisito:** RF-008.  
**Pantalla:** UI-12.

**Aceptación:**

- Un colaborador tiene puesto principal.
    
- Puede planificarse sin cuenta.
    
- El supervisor recibe una proyección limitada.
    
- La vinculación respeta pertenencia y unicidad.
    
- La previsualización de desactivación identifica compromisos.
    
- La confirmación revalida esos compromisos.
    
- Se bloquean desactivaciones y cambios de puesto incompatibles.
    
- No elimina ni reasigna compromisos automáticamente.
    
- No reescribe publicaciones históricas.
    

No se hace depender el alta básica de colaboradores de la existencia previa de cuentas personales.

### HU-08 — Mantener plantillas de turno

**Actor:** administrador; supervisor en consulta.  
**Prioridad:** P1.  
**Depende de:** HU-06; aplicación en editor con HU-12.  
**Requisitos:** RF-007 y RF-012.  
**Pantallas:** UI-14 y UI-07.

**Aceptación:**

- Crea, modifica y desactiva plantillas.
    
- Permite planificar sin usarlas.
    
- Aplicarlas permite revisar fechas, horas y puesto antes de guardar.
    
- Cambiar una plantilla no modifica asignaciones existentes.
    
- No añade sincronización automática entre plantilla y turnos.
    

### HU-09 — Configurar políticas operativas

**Actor:** administrador.  
**Prioridad:** P0.  
**Depende de:** HU-05 y evaluación compartida de reglas.  
**Requisito:** RF-010.  
**Pantalla:** UI-16.

**Aceptación:**

- Define descanso mínimo y umbral semanal.
    
- Cada activación crea una versión inmutable.
    
- La activación es inmediata, no programada.
    
- Evalúa impacto sobre programación publicada futura y turnos adyacentes relevantes.
    
- Los conflictos bloqueantes impiden activar.
    
- Las advertencias exigen reconocimiento específico y motivo.
    
- La confirmación revalida la política base y el contexto.
    
- No altera publicaciones históricas.
    
- No presenta los parámetros como garantía legal.
    

**Entrega gradual:**

- Política inicial sobre empresa sin publicaciones.
    
- Cambio de política con publicaciones existentes e impacto completo.
    

La segunda parte reutiliza el evaluador desarrollado en HU-13. No se marca toda HU-09 como terminada por haber guardado los primeros valores.

### HU-10 — Registrar indisponibilidades

**Actor:** administrador o supervisor.  
**Prioridad:** P0.  
**Depende de:** HU-07.  
**Requisito:** RF-009.  
**Pantalla:** UI-13.

**Aceptación:**

- Registra, modifica y cancela intervalos válidos.
    
- Rechaza incompatibilidades con publicaciones.
    
- Coordina esa comprobación con escrituras concurrentes.
    
- No permite modificaciones retroactivas de periodos iniciados.
    
- Restringe los motivos privados.
    
- No interpreta ausencia de registros como disponibilidad.
    
- Conserva trazabilidad.
    
- No añade solicitudes de vacaciones.
    

## 9.3. E03 — Planificación

### HU-11 — Consultar el estado de una semana

**Actor:** administrador o supervisor.  
**Prioridad:** P0.  
**Depende de:** HU-05 y estructura de planificación.  
**Requisitos:** RF-011 y RF-016.  
**Pantalla:** UI-06.

**Aceptación:**

- Consultar una semana no crea registros.
    
- Distingue ausencia de publicación, publicación vacía y publicación con asignaciones.
    
- Identifica borrador abierto y desactualización.
    
- Muestra semana y zona.
    
- Ofrece navegación entre semanas.
    
- No etiqueta una celda vacía como disponibilidad.
    
- Incluye agenda móvil.
    
- Los filtros de presentación no alteran el alcance semanal de publicar.
    

### HU-12 — Crear y editar un borrador

**Actor:** administrador o supervisor.  
**Prioridad:** P0.  
**Depende de:** HU-11; colaboradores para agregar asignaciones.  
**Requisitos:** RF-011 y RF-012.  
**Pantalla:** UI-07.

**Aceptación:**

- Como máximo un borrador abierto por semana.
    
- Crea desde publicación vigente o sin publicación previa.
    
- Agrega, modifica y retira líneas permitidas.
    
- Guardado explícito con versión del borrador.
    
- Datos estructuralmente inválidos rechazados.
    
- Conflictos operativos permitidos conservados como pendientes.
    
- Diferencia línea nueva de asignación oficial existente.
    
- Protege turnos iniciados.
    
- Editar invalida evaluaciones anteriores.
    
- Descartar no cambia el horario oficial.
    
- No hay fusión automática ni copias privadas ocultas.
    

### HU-13 — Validar la planificación

**Actor:** administrador o supervisor.  
**Prioridad:** P0.  
**Depende de:** modelos de políticas, indisponibilidades y asignaciones; interfaz integrada con HU-12.  
**Requisito:** RF-013.  
**Pantalla:** UI-07.

**Aceptación:**

- Comprueba pertenencia, actividad, puesto e intervalos.
    
- Detecta solapamiento, indisponibilidad y descanso insuficiente.
    
- Considera semanas adyacentes.
    
- Sustituye la publicación de la semana evaluada, sin contar borrador y publicación dos veces.
    
- Distribuye horas entre periodos correctamente.
    
- Distingue bloqueos y advertencias.
    
- Identifica recursos relacionados.
    
- Respeta asignaciones iniciadas.
    
- Una evaluación favorable no elimina la validación final.
    

**Subtarea inicial:** el evaluador de reglas puede construirse y probarse con datos ficticios antes de completar las pantallas. Se reutiliza en publicaciones, políticas e intercambios.

### HU-14 — Publicar un horario

**Actor:** administrador o supervisor.  
**Prioridad:** P0.  
**Depende de:** HU-12, HU-13 y registro de decisiones.  
**Requisito:** RF-014.  
**Pantallas:** UI-07 y UI-08.

**Aceptación:**

- Previsualiza el contenido completo y sus impactos.
    
- Comprueba versión del borrador y publicación base.
    
- Exige reconocimientos exactos de advertencias.
    
- Aplica motivos según el tipo de publicación.
    
- Permite semana vacía con confirmación y motivo.
    
- No utiliza esa opción para retirar turnos iniciados.
    
- Revalida contenido, contexto y tiempo al confirmar.
    
- Crea publicación inmutable y actualiza su referencia vigente.
    
- Cierra borrador y registra decisión, auditoría, invalidaciones y eventos de aviso en la transacción.
    
- Un reintento no crea otra publicación.
    
- Una respuesta perdida activa recuperación, no una nueva intención automática.
    

La publicación vacía ya tiene una regla definida: no queda pendiente de DEC-03.

### HU-15 — Revisar un horario publicado

**Actor:** administrador o supervisor.  
**Prioridad:** P0.  
**Depende de:** HU-14.  
**Requisito:** RF-015.  
**Pantallas:** UI-06 y UI-07.

**Aceptación:**

- Parte de la publicación vigente.
    
- Mantiene el horario oficial mientras se edita.
    
- Exige motivo al publicar la revisión.
    
- Protege turnos iniciados.
    
- Identifica solicitudes afectadas.
    
- Cambiar la revisión referenciada invalida el intercambio correspondiente.
    
- Cambiar otra asignación no invalida automáticamente todas las solicitudes de esa semana.
    
- Un borrador desactualizado no se publica.
    
- Descartar y crear otro se tratan como operaciones separadas.
    
- No se sobrescriben cambios ajenos silenciosamente.
    

### HU-16 — Consultar publicaciones históricas

**Actor:** administrador o supervisor.  
**Prioridad:** P1.  
**Depende de:** HU-14.  
**Requisito:** RF-016 y trazabilidad de publicación.  
**Pantalla:** UI-08.

**Aceptación:**

- Consulta versiones anteriores.
    
- Identifica origen, fecha, política y zona.
    
- Obtiene autor y motivo de la decisión registrada.
    
- Muestra evidencia de advertencias según permisos.
    
- Distingue permanentemente la versión histórica.
    
- Permite volver a la vigente.
    
- No ofrece edición, eliminación ni restauración automática.
    
- No añade un reporte histórico configurable.
    

## 9.4. E04 — Experiencia del colaborador

### HU-17 — Consultar el horario propio

**Actor:** usuario con colaborador activo vinculado.  
**Prioridad:** P0.  
**Depende de:** HU-02, vinculación y HU-14.  
**Requisito:** RF-016.  
**Pantalla:** UI-09.

**Aceptación:**

- Muestra únicamente asignaciones propias publicadas.
    
- Diferencia semana no publicada, publicación vacía y ausencia de turnos propios.
    
- Muestra ambas fechas de turnos nocturnos.
    
- Consulta el próximo turno y contempla su ausencia.
    
- Explica la falta de acceso personal sin simular una agenda vacía.
    
- No permite seleccionar libremente otro colaborador.
    
- Respeta la propiedad actual después de un intercambio.
    
- No incorpora asistencia.
    

### HU-18 — Consultar avisos propios

**Actor:** usuario activo.  
**Prioridad:** P1.  
**Depende de:** HU-02 y procesamiento de eventos de HT-05.  
**Requisito:** RF-023.  
**Pantalla:** UI-19.

**Aceptación:**

- Muestra solo avisos del destinatario.
    
- Permite marcar como leído.
    
- Repetir la lectura conserva su primera fecha.
    
- Recomprueba permisos al abrir el recurso.
    
- Los reintentos del procesamiento no duplican avisos equivalentes.
    
- Leer no acepta un turno ni un intercambio.
    
- Los avisos retrasados no alteran el resultado de negocio.
    
- La consulta periódica respeta el vencimiento de sesión.
    
- No se incorporan avisos externos ni “marcar todos” sin contrato.
    

## 9.5. E05 — Intercambios

### HU-19 — Proponer un intercambio

**Actor:** usuario con acceso personal habilitado.  
**Prioridad:** P0.  
**Depende de:** HU-17 y base de vigencia y reservas de HU-22.1.  
**Requisito:** RF-017.  
**Pantallas:** UI-09, UI-10 y UI-11.

**Aceptación:**

- Parte de un turno propio, publicado y futuro.
    
- Consulta candidatos en periodo acotado y con datos mínimos.
    
- Buscar no reserva asignaciones.
    
- Exige personas diferentes y compatibilidad.
    
- Ambos participantes tienen cuentas y colaboradores activos.
    
- Comprueba revisiones actuales al enviar.
    
- Reserva exactamente las dos asignaciones.
    
- Impide que cualquiera participe en otra solicitud pendiente.
    
- Crea `PENDIENTE_COMPANERO`.
    
- No cambia el horario publicado.
    

### HU-20 — Responder o cancelar una propuesta

**Actor:** destinatario o solicitante según acción.  
**Prioridad:** P0.  
**Depende de:** HU-19.  
**Requisito:** RF-018.  
**Pantallas:** UI-10 y UI-11.

**Aceptación:**

- Solo el destinatario acepta o rechaza en la etapa correspondiente.
    
- Aceptar produce `PENDIENTE_SUPERVISOR`.
    
- Aceptar conserva reservas y no reasigna turnos.
    
- Solo el solicitante cancela su solicitud pendiente.
    
- Motivo de rechazo del compañero o cancelación es opcional.
    
- Rechazar o cancelar libera ambas reservas.
    
- Comprueba actor, estado, versión y vigencia.
    
- Solicitudes terminales no se reabren.
    
- Respuestas repetidas no duplican transiciones.
    

### HU-21 — Aprobar o rechazar un intercambio

**Actor:** administrador o supervisor independiente.  
**Prioridad:** P0.  
**Depende de:** HU-20, HU-13 y mecanismo de publicación de HU-14.  
**Requisito:** RF-019.  
**Pantallas:** UI-10 y UI-11.

**Aceptación:**

- El responsable no participa en la solicitud.
    
- Esa independencia se exige para aprobar y para rechazar en la etapa de revisión.
    
- Solo opera sobre solicitudes vigentes en el estado correcto.
    
- La aprobación tiene previsualización y confirmación separadas.
    
- Simula retirando ambos turnos originales antes de evaluar.
    
- Revalida cuentas, colaboradores, reservas, revisiones, intervalos y política.
    
- Reconoce advertencias específicas con motivo cuando corresponde.
    
- Rechazar exige motivo.
    
- Aprobar cambia colaboradores, no las fechas y horas de los turnos.
    
- Actualiza una o dos publicaciones en la misma transacción.
    
- Libera reservas y registra evidencia y avisos.
    
- Deja desactualizados los borradores afectados.
    
- No requiere publicación manual posterior.
    
- Reintentar no aplica otro intercambio.
    

### HU-22 — Resolver vencimientos e invalidaciones

**Actor:** sistema; usuarios autorizados en consulta.  
**Prioridad:** P0.  
**Requisito:** RF-020.  
**Pantallas:** UI-10 y UI-11.

Se divide en:

- **HU-22.1 — Base de vigencia y finalización:** estados, vencimiento, reservas y transiciones terminales. Se construye antes de habilitar solicitudes.
    
- **HU-22.2 — Integración completa:** publicación, cambios de cuentas y colaboradores, revisiones y mantenimiento. Se verifica al completar los flujos relacionados.
    

**Aceptación:**

- Vence al iniciar el primero de los dos turnos.
    
- La protección no depende exclusivamente de una tarea periódica.
    
- Las consultas pueden mostrar vigencia efectiva sin escribir estado de negocio.
    
- Los comandos revalidan antes de actuar.
    
- La finalización persistida libera ambas reservas.
    
- Los cambios incompatibles invalidan según la regla aplicable.
    
- Reactivar usuarios o empresas no reabre solicitudes.
    
- Se conserva causa e historial.
    
- Un comando rechazado puede registrar vencimiento sin aplicar el intercambio.
    
- Una modificación ajena al turno referenciado no invalida indiscriminadamente toda la semana.
    

## 9.6. E06 — Reportes y trazabilidad

### HU-23 — Consultar horas programadas

**Actor:** administrador o supervisor.  
**Prioridad:** P1.  
**Depende de:** publicaciones de HU-14; verificación posterior frente a HU-15 y HU-21.  
**Requisito:** RF-021.  
**Pantalla:** UI-17.

**Aceptación:**

- Usa publicaciones vigentes.
    
- No suma borradores.
    
- Aplica periodo y filtros definidos.
    
- Máximo de 92 días.
    
- Distribuye los segmentos que intersectan el periodo.
    
- Devuelve metadatos y referencia de origen.
    
- Utiliza lectura consistente.
    
- Identifica horas programadas, no trabajadas.
    
- Un intercambio o revisión posterior se refleja en la siguiente consulta.
    

Puede construirse antes de completar intercambios, pero debe verificarse nuevamente con esos cambios.

### HU-24 — Consultar el resumen de intercambios

**Actor:** administrador o supervisor.  
**Prioridad:** P1.  
**Depende de:** HU-19 a HU-22.  
**Requisito:** RF-021.  
**Pantalla:** UI-17.

**Aceptación:**

- El periodo selecciona solicitudes creadas dentro de él.
    
- El estado corresponde al momento de generar el reporte.
    
- No confunde creación con fecha de resolución.
    
- Utiliza únicamente filtros definidos en la API.
    
- Respeta el máximo de 92 días.
    
- Incluye metadatos y referencia de origen.
    
- Tiene en cuenta el vencimiento efectivo.
    
- No presenta conteos como ahorro o productividad demostrada.
    

No se mantiene como requisito un filtro por estado si no está incorporado en el contrato.

### HU-25 — Exportar reportes CSV

**Actor:** administrador o supervisor.  
**Prioridad:** P1.  
**Depende de:** HU-23 y HU-24.  
**Requisito:** RF-022.  
**Pantalla:** UI-17.

**Aceptación:**

- Primero se consulta y revisa el reporte.
    
- La exportación exige la referencia de origen.
    
- Respeta los mismos filtros y permisos.
    
- Si cambia la fuente relevante, solicita actualizar.
    
- Compara y genera sobre una lectura coherente.
    
- Cambiar filtros en pantalla obliga a consultar nuevamente.
    
- Utiliza columnas y codificación definidas.
    
- Trata separadores, comillas, saltos y texto peligroso para hojas de cálculo.
    
- Incluye metadatos.
    
- No se presenta como exportación completa de la empresa.
    

### HU-26 — Consultar historial operativo

**Actor:** administrador o supervisor según alcance.  
**Prioridad:** P1.  
**Depende de:** HT-05 y operaciones funcionales disponibles.  
**Requisito:** RF-024.  
**Pantalla:** UI-18.

**Aceptación:**

- Muestra eventos legibles con actor, fecha, acción y recurso.
    
- Presenta motivos y cambios autorizados.
    
- Relaciona las decisiones con su evidencia.
    
- Restringe eventos administrativos según el actor.
    
- No expone secretos ni estructuras internas completas.
    
- No permite editar o borrar auditoría mediante funciones ordinarias.
    
- Los filtros no permiten eludir permisos.
    

## 9.7. E07 — Preparación y administración del servicio

### HU-27 — Mostrar el inicio operativo

**Actor:** administrador o supervisor.  
**Prioridad:** P1.  
**Depende de:** consultas disponibles de configuración, planificación e intercambios.  
**Pantalla:** UI-05.

**Aceptación:**

- El administrador identifica preparación pendiente.
    
- El supervisor identifica publicación y solicitudes.
    
- Los conteos proceden de datos reales.
    
- Una solicitud pendiente no implica que ese usuario pueda resolverla.
    
- Plantillas y cuentas de todos los colaboradores no se presentan como requisitos universales de publicación.
    
- El colaborador ingresa a su horario.
    
- No muestra ingresos SaaS ni ahorro inventado.
    

### HU-28 — Administrar estado y capacidad del servicio

**Actor:** operador.  
**Prioridad:** P0 para operación con empresas reales.  
**Depende de:** HU-01 y controles de acceso.  
**Requisito:** RF-002.

**Aceptación:**

- Suspende, reactiva y ajusta capacidad mediante el mecanismo restringido.
    
- Registra actor y motivo.
    
- La suspensión afecta solicitudes posteriores de sesiones existentes.
    
- No elimina información.
    
- Reactivar no activa cuentas desactivadas.
    
- No devuelve vigencia a solicitudes terminales.
    
- Reducir capacidad no elimina recursos automáticamente.
    
- Los límites se comprueban en operaciones afectadas.
    
- No concede al operador permisos para resolver intercambios.
    

Los controles básicos de suspensión se prueban desde el acceso; no se espera al final para diseñarlos.

## 10. Trabajo operativo y de validación

### OP-01 — Preparar despliegue de prueba y actualización

**Prioridad:** P0 antes del piloto.  
**Depende de:** primer recorrido de acceso funcional.

**Incluye:**

- Entornos separados.
    
- Secretos fuera del repositorio.
    
- Transporte cifrado en el entorno publicado.
    
- Acceso controlado.
    
- Migraciones.
    
- Comprobación posterior al despliegue.
    
- Procedimiento de actualización y fallo.
    
- Datos ficticios para pruebas.
    

**Aceptación:** despliegue reproducible y procedimiento ensayado. Retroceder la aplicación no supone revertir automáticamente los datos.

### OP-02 — Preparar respaldo y restauración

**Prioridad:** P0 antes de datos reales.  
**Depende de:** persistencia y entorno disponibles.

**Incluye:**

- Copias recuperables fuera del servidor principal.
    
- Protección de respaldos y claves.
    
- Frecuencia y retención definidas.
    
- Restauración real.
    
- Verificación de integridad.
    
- Registro de tiempos.
    
- Conciliación de accesos, avisos y operaciones.
    

**Objetivos por validar, conforme a la nota 12:**

- RPO: hasta 4 horas.
    
- RTO: hasta 8 horas desde la interrupción.
    

**Aceptación:**

- Se restaura y comprueba un entorno.
    
- Se invalidan sesiones y enlaces restaurados.
    
- Se revisan suspensiones y permisos posteriores al respaldo.
    
- No se repiten automáticamente operaciones inciertas.
    
- Se documenta que la restauración de la base compartida afecta a todas sus empresas.
    
- Los objetivos se contrastan con infraestructura y disponibilidad humana.
    

Generar un archivo de respaldo no termina esta tarea.

### OP-03 — Preparar diagnóstico e incidentes

**Prioridad:** P0 antes del piloto.

**Incluye:**

- Registros sin secretos.
    
- Correlación.
    
- Salud del servicio.
    
- Detección de fallos de respaldo.
    
- Seguimiento de avisos pendientes.
    
- Atención de problemas de acceso, integridad y aislamiento.
    
- Responsable, canal y horario.
    

**Aceptación:** otra persona autorizada puede seguir el procedimiento, identificar la situación y conocer cuándo escalar.

### OP-04 — Preparar salida asistida y entrega de datos

**Prioridad:** P0 antes de incorporar datos reales.  
**Requisito:** RF-003.  
**Depende de:** definición de conservación, acceso restringido del operador y exportación controlada.

Esta tarea hace explícito un requisito existente. No añade un portal de bajas.

**Incluye:**

- Verificación de autoridad del solicitante.
    
- Acuerdo de alcance y fecha.
    
- Procedimiento de extracción de datos de una empresa.
    
- Entrega segura.
    
- Verificación de entrega.
    
- Cierre del acceso.
    
- Conservación o eliminación según política.
    
- Evidencia administrativa.
    

**Aceptación:**

- Se ensaya con datos ficticios.
    
- No mezcla empresas.
    
- La entrega no se confunde con los CSV de reportes.
    
- Cerrar acceso no elimina información automáticamente.
    
- No ejecuta borrados sin alcance y política definidos.
    
- Se documenta el tratamiento de respaldos e información histórica.
    

### VAL-01 — Validar tareas con usuarios representativos

**Incluye:**

- Consulta del próximo turno.
    
- Guardado y publicación.
    
- Comprensión de bloqueos y advertencias.
    
- Publicación vacía.
    
- Propuesta, respuesta y aprobación.
    
- Conflicto de versión.
    
- Resultado desconocido.
    
- Consulta y exportación de reportes.
    
- Uso móvil.
    

**Aceptación:** observaciones registradas sobre tareas, errores y necesidad de ayuda. No basta con recoger comentarios positivos.

### VAL-02 — Preparar demostración académica

**Incluye:**

- Datos ficticios.
    
- Guion reproducible.
    
- Evidencias vinculadas a requisitos.
    
- Limitaciones conocidas.
    
- Correspondencia con la rúbrica confirmada.
    

Su preparación no demuestra operación comercial ni sustituye pruebas.

### VAL-03 — Evaluar preparación para piloto

**Incluye:**

- Alcance realmente implementado.
    
- Seguridad y recuperación.
    
- Incorporación y acceso inicial.
    
- Salida asistida.
    
- Manejo de datos.
    
- Soporte.
    
- Defectos conocidos.
    
- Evidencia de tareas.
    
- Condiciones de aprendizaje comercial.
    

**Aceptación:** decisión documentada de iniciar, limitar o posponer el piloto. No se marca lista la versión solo porque terminó la demostración.

## 11. Orden de construcción por incrementos

Un incremento representa un resultado demostrable, no una semana.

|   |   |   |
|---|---|---|
|Incremento|Trabajo principal|Resultado|
|I0 — Preparación|Condiciones iniciales, HT-01, base de HT-02 y HT-07|Proyecto ejecutable y pruebas ficticias|
|I1 — Acceso seguro|Base HT-03/04/05/06, HU-04.1, HU-01, HU-02, cuentas sin vínculo y recuperación posterior|Primera empresa y acceso seguro sin dependencia circular|
|I2 — Equipo y reglas|HU-05, HU-06, HU-07, vínculos, política inicial e indisponibilidades|Datos operativos listos|
|I3 — Primera publicación|HU-11 a HU-14, evaluador e HU-17|Horario publicado consultable|
|I4 — Cambios controlados|HU-08, HU-15, HU-16, cambios de política y conflictos sobre datos publicados|Revisión e historial consistentes|
|I5 — Intercambios|HU-22.1 antes de propuestas; HU-19 a HU-22, HU-18 e integración con cuentas y publicaciones|Intercambio completo y vigencia segura|
|I6 — Gestión completa|HU-23 a HU-27 y verificación integral de historias parciales|Reportes, exportación y trazabilidad|
|I7 — Candidato a piloto|Cierre de HU-28, OP-01 a OP-04 y validaciones pertinentes|Versión evaluable para una empresa real|

### 11.1. Dependencias que requieren cuidado

**Acceso inicial:**

El mecanismo base para establecer contraseña se construye antes de exigir que el primer administrador pueda iniciar sesión.

**Políticas y validación:**

El evaluador es compartido. No se espera a terminar todas las pantallas para probar reglas con datos ficticios.

**Solicitudes y vigencia:**

Las reservas y reglas de finalización existen antes de habilitar propuestas. El proceso periódico no sustituye esa protección.

**Impactos entre módulos:**

Al incorporar intercambios se vuelven a probar desactivación de cuentas, vínculos, colaboradores, políticas y revisiones.

**Historias parciales:**

Una capacidad construida inicialmente sobre una empresa sin publicaciones no queda completa hasta comprobar sus restricciones con publicaciones y solicitudes existentes.

### 11.2. Trabajo transversal

Desde los primeros incrementos:

- Seguridad.
    
- Auditoría.
    
- Pruebas.
    
- Contratos.
    
- Accesibilidad.
    
- Documentación.
    
- Despliegue de prueba.
    
- Diagnóstico.
    

Los procedimientos de respaldo y salida se preparan con antelación. I7 es su cierre y ensayo integral, no el momento de empezar a pensarlos.

### 11.3. Recorridos principales

Primer recorrido:

**Acceso inicial → equipo y política → borrador → validación → publicación → horario propio.**

Segundo recorrido:

**Turnos publicados → propuesta → respuesta → revisión independiente → actualización conjunta.**

No se incorpora una empresa real mientras existan módulos parciales que puedan vulnerar esos recorridos.

## 12. Hitos y puertas de salida

### H1 — Base segura

Debe demostrarse:

- Incorporación y contraseña inicial.
    
- Acceso por empresa.
    
- Roles.
    
- Aislamiento.
    
- Revocación.
    
- Reintentos de las escrituras implementadas.
    
- Entorno reproducible.
    

### H2 — Primera semana publicada

Debe demostrarse:

- Borrador y guardado.
    
- Validación.
    
- Advertencias específicas.
    
- Publicación ordinaria y vacía.
    
- Evidencia de decisión.
    
- Consulta personal móvil.
    
- Diferencia entre semana no publicada y publicada sin turnos.
    

### H3 — Revisión segura

Debe demostrarse:

- Historial inmutable.
    
- Protección de turnos iniciados.
    
- Base desactualizada.
    
- Conflictos entre semanas.
    
- Cambios de política sobre publicaciones.
    
- Recuperación de respuesta perdida.
    

### H4 — Intercambio completo

Debe demostrarse:

- Reserva de dos asignaciones.
    
- Aceptación sin cambio de horario.
    
- Independencia para aprobar y rechazar.
    
- Previsualización y advertencias.
    
- Actualización atómica entre una o dos semanas.
    
- Vencimiento e invalidación.
    
- Liberación de reservas.
    
- Reintentos sin duplicados.
    

### H5 — MVP funcional

Debe demostrarse:

- Historias P0 y P1 del alcance verificadas.
    
- Ninguna historia terminada solo por una subtarea.
    
- Reportes y CSV consistente.
    
- Avisos e historial.
    
- Estados de error y permisos.
    
- Experiencia móvil.
    
- Contratos y documentación sincronizados.
    

### H6 — Candidato a piloto

Requiere además:

- Despliegue controlado.
    
- Restauración probada.
    
- Incorporación y salida ensayadas.
    
- Soporte definido.
    
- Revisión de seguridad.
    
- Validación de tareas.
    
- Defectos evaluados.
    
- Condiciones de datos y conservación resueltas.
    

Ningún hito técnico demuestra por sí mismo demanda comercial o disposición a pagar.

## 13. Descomposición de una historia

Cuando corresponda, una historia incluye:

1. Contrato y reglas.
    
2. Modelo y migraciones.
    
3. Backend.
    
4. Autorización.
    
5. Validaciones.
    
6. Interfaz.
    
7. Estados normales y excepcionales.
    
8. Versiones y concurrencia.
    
9. Idempotencia y recuperación.
    
10. Auditoría y eventos.
    
11. Pruebas.
    
12. Documentación y evidencia.
    

No todas necesitan migraciones o avisos. La lista evita omisiones; no genera trabajo artificial.

### Ejemplo: HU-14 — Publicar un horario

- Precisar contrato y errores.
    
- Implementar previsualización.
    
- Calcular impactos completos.
    
- Generar huella de evaluación.
    
- Exigir motivo según el caso.
    
- Verificar reconocimientos individuales.
    
- Implementar confirmación vacía.
    
- Proteger asignaciones iniciadas.
    
- Revalidar dentro de la transacción.
    
- Crear decisión, publicación y referencias.
    
- Cerrar borrador.
    
- Aplicar invalidaciones y liberar reservas.
    
- Registrar auditoría y eventos.
    
- Persistir el resultado recuperable.
    
- Construir confirmación de escritorio y móvil.
    
- Probar respuesta perdida, doble envío y cambios concurrentes.
    
- Verificar ausencia de efectos parciales.
    

## 14. Estimaciones y calendario

### 14.1. No se asume

- Fecha de inicio.
    
- Fecha comprometida de entrega.
    
- Número de integrantes.
    
- Horas disponibles.
    
- Velocidad de implementación.
    

Los incrementos no se convierten automáticamente en semanas.

### 14.2. Procedimiento

1. Confirmar disponibilidad.
    
2. Dividir historias grandes.
    
3. Estimar construcción, integración, revisión y pruebas.
    
4. Identificar incertidumbres.
    
5. Reservar capacidad para defectos.
    
6. Recalibrar con los primeros resultados.
    

Cada estimación registra rango, supuestos, dependencias y responsable.

### 14.3. Investigación acotada

Cuando una duda pueda cambiar la solución, se crea una investigación breve con resultado esperado.

Ejemplos:

- Coordinación de escrituras con la base seleccionada.
    
- Carreras entre aprobación y publicación.
    
- Consistencia de consulta y CSV.
    
- Restauración.
    
- Calendario con teclado y móvil.
    

Termina con conclusión, evidencia o limitación explícita. No se convierte en exploración indefinida.

## 15. Criterio para terminar una historia

Una historia está terminada cuando:

- Cumple todos sus criterios.
    
- Respeta requisitos y reglas.
    
- Verifica permisos en servidor.
    
- Mantiene aislamiento.
    
- Maneja errores pertinentes.
    
- Contempla concurrencia y reintentos aplicables.
    
- Registra evidencia y avisos necesarios.
    
- Tiene pruebas repetibles.
    
- Su contrato coincide con la implementación.
    
- Sus migraciones están verificadas.
    
- La interfaz funciona en los tamaños acordados.
    
- No expone secretos.
    
- La documentación está actualizada.
    
- Existe evidencia de revisión.
    

No basta con compilar, obtener una respuesta exitosa o terminar el aspecto visual.

Las pruebas posteriores a la integración pueden reabrir una historia si descubren incumplimientos.

## 16. Pruebas que acompañan cada incremento

|   |   |
|---|---|
|Área|Comprobación|
|Reglas|Casos válidos, límites y prohibiciones|
|Permisos|Actor, organización y recurso|
|Persistencia|Restricciones y transacciones reales|
|Tiempo|Medianoche, semanas, descanso y vencimiento exacto|
|Previsualización|Evaluación actual y rechazo de confirmación desactualizada|
|Evidencia|Actor, motivo, política y advertencias|
|Concurrencia|Operaciones incompatibles simultáneas|
|Reintentos|Doble envío, respuesta perdida y resultado no encontrado|
|Interfaz|Carga, vacío, restricción, conflicto e incertidumbre|
|Reportes|Lectura consistente, periodo y cambio de origen|
|Accesibilidad|Teclado, foco, etiquetas y móvil|
|Operación|Configuración, respaldo, restauración y diagnóstico|

### Actores mínimos de prueba

- Solicitante.
    
- Destinatario.
    
- Responsable independiente.
    
- Participante con rol administrativo.
    
- Usuario de otra empresa.
    
- Usuario desactivado.
    
- Colaborador inactivo.
    
- Usuario sin vinculación personal.
    

### Referencias de rendimiento

La prueba integral sigue la nota 12:

- Cinco empresas ficticias.
    
- Hasta 50 colaboradores por empresa.
    
- Seis meses de historial representativo.
    
- Veinte sesiones concurrentes.
    
- Reportes acotados.
    

Objetivos propuestos:

- Consultas habituales: percentil 95 menor de 2 segundos.
    
- Guardados y comandos habituales: percentil 95 menor de 5 segundos.
    
- Reporte acotado: menor de 10 segundos en el escenario ensayado.
    

Son objetivos pendientes de comprobación, no capacidad comercial demostrada ni SLA.

## 17. Seguimiento en Obsidian

Esta nota es el índice maestro.

No hace falta crear ahora una nota para cada historia. Se desarrollan las notas de trabajo cuando se activen.

### Contenido mínimo de una historia activa

- ID y título.
    
- Estado.
    
- Prioridad.
    
- Incremento.
    
- Responsable.
    
- Estimación y supuestos.
    
- Objetivo.
    
- Requisitos y reglas relacionadas.
    
- Pantallas y contratos.
    
- Dependencias.
    
- Decisiones pendientes.
    
- Criterios de aceptación.
    
- Subtareas.
    
- Evidencias.
    
- Defectos.
    
- Resultado de revisión.
    

### Reglas

- El ID no cambia al mover la historia.
    
- Las subtareas referencian a su historia.
    
- No se duplica el mismo trabajo en varias notas.
    
- Un bloqueo explica qué necesita para resolverse.
    
- La evidencia apunta a pruebas, cambios o documentos verificables.
    
- No se incluyen secretos ni datos reales innecesarios.
    
- Una casilla marcada sin evidencia no demuestra que el criterio se cumplió.
    

La sincronización de documentación se limita a los documentos afectados por un cambio real.

## 18. Control del trabajo en curso

Se propone:

- Pocas historias activas.
    
- Una historia principal si el desarrollo es individual.
    
- Entregas de extremo a extremo.
    
- Cambios pequeños e integrables.
    
- Revisión temprana de dependencias.
    
- Corrección prioritaria de defectos de base.
    

Si hay equipo, el trabajo paralelo debe respetar contratos y evitar cambios incompatibles en los mismos módulos.

### Control de entregas parciales

Cada revisión del incremento identifica:

- Qué quedó terminado.
    
- Qué quedó integrado parcialmente.
    
- Qué pruebas siguen pendientes.
    
- Qué dependencia impide cerrar la historia.
    
- Qué funciones no deben habilitarse todavía con datos reales.
    

No se contabiliza la historia completa por tener únicamente backend o pantalla.

## 19. Gestión de defectos

|   |   |   |
|---|---|---|
|Severidad|Ejemplo|Tratamiento|
|Crítica|Fuga entre empresas, acceso indebido o corrupción|Bloquea liberación|
|Alta|Publicación incorrecta, intercambio parcial o protección esencial incumplida|Bloquea piloto|
|Media|Función del alcance degradada|Resolver o evaluar explícitamente antes de liberar|
|Baja|Problema menor que no impide la tarea|Priorizar por impacto|

La severidad no depende del tiempo necesario para corregir.

Cada defecto registra:

- Comportamiento observado.
    
- Resultado esperado.
    
- Reproducción.
    
- Usuarios afectados.
    
- Evidencia.
    
- Alternativa segura, si existe.
    
- Entrega bloqueada.
    
- Responsable.
    

No se acepta la pérdida de aislamiento como limitación tolerable.

## 20. Cambios de alcance y correcciones documentales

### 20.1. Evaluación de una propuesta nueva

1. ¿Qué problema resuelve?
    
2. ¿Ya pertenece al alcance?
    
3. ¿Qué evidencia la respalda?
    
4. ¿Qué reglas y datos modifica?
    
5. ¿Qué pantallas, contratos y pruebas afecta?
    
6. ¿Qué trabajo retrasa?
    
7. ¿Puede esperar?
    

Resultado:

- Incorporar con ajuste explícito.
    
- Sustituir algo con autorización.
    
- Investigar.
    
- Posponer.
    
- Descartar.
    

### 20.2. Distinguir tres tipos de cambio

|   |   |
|---|---|
|Tipo|Tratamiento|
|Omisión o contradicción|Corregir los documentos afectados y registrar impacto|
|Detalle de implementación compatible|Resolver dentro de la tarea y documentar donde corresponda|
|Función nueva o cambio de regla|Evaluar alcance y obtener la decisión necesaria|

No se llama “mejora” a una ampliación de alcance para introducirla sin revisión.

### 20.3. Evitar reescrituras innecesarias

Antes de entregar una revisión documental:

- Comparar contra reglas, datos, API y pruebas relevantes.
    
- Identificar contradicciones concretas.
    
- Conservar identificadores.
    
- No reabrir decisiones sin nueva evidencia.
    
- Revisar dependencias de los cambios.
    
- Explicar qué se corrigió.
    

El usuario puede recibir una nota completa para copiar sin que eso implique cambiar partes que ya eran correctas.

### 20.4. Si falta tiempo

1. Revisar estimaciones.
    
2. Reducir trabajo simultáneo.
    
3. Simplificar presentación.
    
4. Posponer trabajo fuera del alcance.
    
5. Negociar fecha o alcance explícitamente.
    

No se eliminan silenciosamente permisos, integridad, recuperación o pruebas críticas.

Una entrega académica parcial se presenta como parcial.

## 21. Línea comercial paralela

La validación comercial acompaña al desarrollo sin introducir módulos no acordados.

Actividades:

- Contrastar el problema con el segmento.
    
- Comprender cómo organiza turnos actualmente.
    
- Identificar comprador y usuarios.
    
- Conseguir interés en probar.
    
- Observar tareas y uso real.
    
- Explorar disposición a pagar.
    
- Registrar objeciones.
    

No demuestran ingresos:

- Tener una landing.
    
- Terminar el software.
    
- Recibir elogios.
    
- Conseguir registros sin uso.
    
- Proponer un precio.
    

Las condiciones y criterios comerciales corresponden a la nota 13.

No se atribuyen métricas al sistema si todavía no existe instrumentación para obtenerlas.

## 22. Riesgos de ejecución

|   |   |   |
|---|---|---|
|Riesgo|Señal|Respuesta|
|Decisiones desactualizadas|Algo definido sigue figurando pendiente|Revisar estado y referencias|
|Dependencia circular|Ninguna historia puede iniciar|Separar subtareas de base e integración|
|Historia parcial marcada terminada|Funciona solo sin datos relacionados|Añadir verificación cruzada|
|Seguridad tardía|Consultas sin ámbito empresarial|Integrarla desde el inicio|
|Concurrencia ignorada|Solo pruebas secuenciales|Ensayar carreras desde planificación|
|Confirmaciones débiles|Se acepta cualquier evaluación|Vincular decisión al contexto revisado|
|Reportes inconsistentes|CSV distinto de la consulta|Comprobar referencia de origen|
|Pantallas sin recorrido|Muchas vistas, pocas tareas completas|Priorizar incrementos verificables|
|Operación improvisada|No se sabe restaurar o cerrar una cuenta empresarial|Ensayar OP antes del piloto|
|Reescritura documental continua|Decisiones se reabren sin motivo|Control de cambios y revisión cruzada|
|Falta de validación comercial|Solo opina el equipo|Contrastar con usuarios del segmento|
|Desalineación académica|Entregables sin rúbrica confirmada|Resolver DEC-01 para esa entrega|

## 23. Condiciones de cierre

### 23.1. Backlog listo para iniciar desarrollo

- Historias vinculadas al alcance.
    
- Dependencias sin ciclos sin resolver.
    
- Decisiones especificadas diferenciadas de pendientes.
    
- Primer incremento descompuesto.
    
- Contratos suficientes para las tareas iniciales.
    
- Evidencias de aceptación definidas.
    
- Sin fechas o responsables inventados.
    
- Trabajo operativo incluido.
    

No es necesario cerrar todas las decisiones del piloto para comenzar con datos ficticios.

### 23.2. MVP funcional completo

- Recorrido de publicación completo.
    
- Intercambio completo y seguro.
    
- Historias P0 y P1 verificadas.
    
- Aislamiento comprobado.
    
- Estados y reintentos probados.
    
- Reportes y exportaciones consistentes.
    
- Historial y avisos.
    
- Recuperación de acceso.
    
- Interfaz móvil utilizable.
    
- Contrato y modelo sincronizados.
    
- Sin defectos críticos o altos pendientes.
    

### 23.3. Preparado para evaluar un piloto

Además:

- Despliegue reproducible.
    
- Respaldo y restauración probados.
    
- Incorporación y salida asistida ensayadas.
    
- Responsables y soporte definidos.
    
- Condiciones de datos y conservación resueltas.
    
- Validación de tareas.
    
- Limitaciones restantes documentadas.
    
- VAL-03 completada.
    

La entrega académica se evalúa mediante VAL-02 y la nota 14; no sustituye estas condiciones.

**Situación actual:** backlog revisado. Ejecución, estimaciones, responsables y evidencias de implementación pendientes.

## 24. Siguiente documento

Continuar con:

[[12 - Pruebas, Seguridad y Operación]]

La revisión comprobará que existan pruebas para:

- Confirmaciones específicas.
    
- Publicación vacía.
    
- Versiones y previsualizaciones desactualizadas.
    
- Revisión independiente.
    
- Intercambios entre semanas.
    
- Resultado desconocido.
    
- CSV consistente.
    
- Cambios de política.
    
- Suspensión y revocación.
    
- Restauración y salida asistida.
    

Después se consolidará [[15 - Contexto de Desarrollo y Cierre de Planificación]] para comenzar a construir con una base coherente.

No se reinicia la planificación ni se vuelven a redactar documentos que no necesiten cambios.