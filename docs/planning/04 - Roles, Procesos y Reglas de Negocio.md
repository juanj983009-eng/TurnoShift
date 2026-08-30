
## 1. Propósito

Definir quién puede realizar cada acción y qué condiciones deben cumplirse para que TurnoShift mantenga una planificación consistente.

Este documento desarrolla el alcance de [[03 - Alcance MVP y Roadmap Comercial]].

Será la referencia funcional para:

- Requisitos.
    
- Casos de uso.
    
- Diseño de datos.
    
- Contratos API.
    
- Interfaces.
    
- Pruebas.
    

Las reglas aquí descritas son propuestas de producto. No representan una interpretación de legislación laboral.

**Estado:** versión consolidada para revisión. Su redacción no significa que las funciones estén implementadas o probadas.

### 1.1. Control de esta revisión

Esta versión sustituye el contenido anterior de la nota 04 e integra los ajustes de funcionamiento.

Se conservan los identificadores **RN-001 a RN-056**. Las reglas adicionales se identifican como **RN-057 a RN-061**, aunque aparezcan junto a las secciones que desarrollan.

No debe mantenerse otro bloque paralelo con reglas contradictorias.

## 2. Alcance funcional de referencia

Se trabaja sobre estas decisiones propuestas:

- Plataforma multiempresa.
    
- Una sede operativa por organización durante el MVP.
    
- Roles predefinidos.
    
- Planificación semanal.
    
- Asignación manual con validaciones.
    
- Borradores y publicaciones versionadas.
    
- Consulta personal del horario.
    
- Intercambio entre dos colaboradores.
    
- Reportes de horas programadas.
    
- Avisos internos y auditoría.
    
- Sin asistencia ni cálculo de nómina.
    

Si cambia este alcance, deberán revisarse las reglas afectadas.

## 3. Conceptos del negocio

|Concepto|Definición|
|---|---|
|Organización|Empresa cliente cuyos datos y configuración están separados de los de otras empresas.|
|Sede|Unidad operativa. El MVP permite una sede operativa por organización.|
|Usuario|Cuenta que permite acceder al sistema.|
|Colaborador o empleado|Persona incluida en la planificación, tenga o no una cuenta habilitada.|
|Rol|Conjunto predefinido de responsabilidades y permisos de una cuenta.|
|Puesto|Función operativa para la que un colaborador puede ser programado.|
|Tipo de turno|Plantilla reutilizable para facilitar la creación de asignaciones.|
|Asignación|Turno concreto, con colaborador, puesto, inicio y fin.|
|Planificación|Conjunto de asignaciones organizado por sede y semana.|
|Borrador|Propuesta editable que todavía no cambia el horario oficial.|
|Publicación|Versión inmutable de una planificación. Una de sus versiones puede ser la vigente.|
|Indisponibilidad|Intervalo en el que un colaborador no debe recibir asignaciones.|
|Intercambio|Solicitud para que dos colaboradores permuten dos asignaciones.|
|Política de planificación|Parámetros versionados que determinan las validaciones aplicables.|
|Auditoría|Registro de acciones relevantes, su actor y momento.|

En los documentos del proyecto, “empleado” y “colaborador” se refieren a la misma entidad de planificación.

El **rol COLABORADOR** pertenece a una cuenta. No debe confundirse con el registro de la persona planificada.

Un tipo de turno no es una asignación: modificar una plantilla no debe alterar turnos ya creados.

## 4. Roles del sistema

### 4.1. Operador de TurnoShift

Administra la prestación del servicio mediante herramientas restringidas, separadas de la navegación ordinaria del cliente.

Puede:

- Registrar empresas.
    
- Establecer límites contratados o de piloto.
    
- Habilitar al primer administrador.
    
- Gestionar activación, suspensión y cierre mediante procedimientos definidos.
    
- Atender incidencias dentro de un alcance autorizado.
    

No puede, por el solo hecho de ser operador:

- Consultar libremente todos los horarios.
    
- Aprobar intercambios empresariales.
    
- Cambiar asignaciones de trabajadores.
    
- Suplantar usuarios.
    
- Acceder a contraseñas.
    

El soporte privilegiado, si se habilita, requerirá autorización, alcance y auditoría específicos.

El cierre de una organización no implica eliminación inmediata de sus datos.

### 4.2. Administrador de empresa

Administra su organización y puede ejecutar las funciones del supervisor.

Puede:

- Gestionar usuarios y roles predefinidos.
    
- Gestionar colaboradores, puestos y tipos de turno.
    
- Configurar políticas.
    
- Registrar indisponibilidades.
    
- Crear y publicar planificaciones.
    
- Resolver intercambios con las restricciones correspondientes.
    
- Consultar reportes y auditoría empresarial.
    

No puede modificar condiciones comerciales reservadas al operador ni acceder a otra organización.

### 4.3. Supervisor

Gestiona la planificación operativa.

Puede:

- Consultar colaboradores y datos necesarios.
    
- Consultar puestos, plantillas y políticas vigentes.
    
- Registrar indisponibilidades.
    
- Crear y editar borradores.
    
- Publicar y revisar horarios.
    
- Resolver intercambios con las restricciones correspondientes.
    
- Consultar reportes y trazabilidad operativa.
    

No puede:

- Administrar cuentas o roles.
    
- Crear o modificar colaboradores y catálogos administrativos.
    
- Cambiar límites comerciales.
    
- Modificar políticas de planificación.
    
- Consultar información ajena a su organización.
    

### 4.4. Colaborador

Con una cuenta activa vinculada a un colaborador activo, puede:

- Consultar su horario publicado.
    
- Consultar oportunidades de intercambio permitidas.
    
- Crear solicitudes.
    
- Aceptar o rechazar solicitudes dirigidas a él.
    
- Cancelar solicitudes propias mientras estén pendientes.
    
- Consultar el estado e historial de sus solicitudes.
    
- Consultar sus avisos.
    

No puede editar directamente asignaciones ni aprobar el resultado final de un intercambio.

Una cuenta sin vínculo válido no obtiene acceso personal a la planificación por tener solamente este rol.

### 4.5. Usuarios con responsabilidades combinadas

Una persona puede ser administrador o supervisor y también estar registrada como colaborador.

Sus acciones personales requieren una vinculación válida con su registro de colaborador activo.

Cada cuenta tendrá un rol empresarial predefinido en el MVP. Los roles administrativos incluirán los permisos personales necesarios cuando exista dicha vinculación.

No se incorpora un selector para simular roles.

Si el vínculo personal deja de estar habilitado, se restringen las acciones personales. Esto no elimina automáticamente las demás facultades del rol empresarial activo.

## 5. Matriz resumida de permisos

|   |   |   |   |   |
|---|---|---|---|---|
|Acción|Operador|Administrador|Supervisor|Colaborador|
|Crear o suspender empresas|Sí|No|No|No|
|Gestionar usuarios empresariales|Alta inicial y recuperación autorizada|Sí|No|No|
|Gestionar colaboradores y catálogos|No|Sí|Consulta autorizada|No|
|Configurar políticas|No|Sí|Consulta|No|
|Registrar indisponibilidades|No|Sí|Sí|No|
|Crear y publicar horarios|No|Sí|Sí|No|
|Consultar planificación de la sede|No|Sí|Sí|Solo ámbito personal y candidatos elegibles|
|Consultar horario propio|No|Si está vinculado y habilitado|Si está vinculado y habilitado|Si está vinculado y habilitado|
|Solicitar o responder intercambios propios|No|Si está vinculado y habilitado|Si está vinculado y habilitado|Si está vinculado y habilitado|
|Aprobar intercambios|No|Sí, con restricciones|Sí, con restricciones|No|
|Exportar reportes empresariales|No|Sí|Sí|No|
|Consultar auditoría|Del servicio y soporte autorizado|Empresarial|Operativa autorizada|No|
|Consultar avisos propios|Según herramienta del operador|Sí|Sí|Sí|

El acceso a candidatos de intercambio no implica acceso al horario completo de todos los compañeros.

El colaborador verá únicamente los datos indispensables: identificación visible del compañero, puesto y horario elegible, sin datos de contacto ni motivos de indisponibilidad.

## 6. Procesos principales

### P-01 — Incorporación de una empresa

1. Se acuerdan condiciones del piloto o servicio.
    
2. El operador registra la organización.
    
3. Configura la sede inicial, zona horaria y límites.
    
4. Habilita al primer administrador mediante un proceso seguro.
    
5. El administrador completa los datos operativos.
    
6. Se verifican las condiciones necesarias para publicar.
    

La organización puede configurarse progresivamente. No queda habilitada para publicar mientras falte información obligatoria.

### P-02 — Preparación de datos

1. El administrador registra puestos y colaboradores.
    
2. Vincula cuentas cuando corresponda.
    
3. Define tipos de turno si desea utilizarlos.
    
4. El administrador o supervisor registra indisponibilidades.
    
5. Se verifica que los datos permitan preparar la planificación.
    

Las plantillas son opcionales.

Las cuentas no sustituyen el catálogo de colaboradores. Un colaborador activo sin cuenta puede recibir asignaciones, pero no consultar el sistema ni participar en intercambios.

### P-03 — Planificación y publicación

1. El responsable crea un borrador semanal.
    
2. Añade o modifica asignaciones.
    
3. El sistema valida los datos al guardar e identifica problemas operativos.
    
4. El responsable solicita revisar la publicación.
    
5. Se ejecuta una validación completa con información actual.
    
6. Se muestran cambios, bloqueos, advertencias e impactos.
    
7. El responsable corrige bloqueos y confirma lo requerido.
    
8. El servidor vuelve a comprobar condiciones y versiones.
    
9. Si procede, se publica una nueva versión.
    
10. Se registran auditoría y eventos para avisos.
    

Guardar un borrador no equivale a aprobarlo.

### P-04 — Revisión de un horario publicado

1. Se crea un borrador basado en la versión vigente.
    
2. El horario publicado sigue visible.
    
3. Se modifican las asignaciones permitidas.
    
4. Se registra el motivo del cambio.
    
5. Se revisan advertencias e intercambios afectados.
    
6. Se verifica que la publicación de origen continúe vigente.
    
7. Se publica la revisión de forma atómica.
    
8. Se conserva la versión anterior.
    

Un borrador no modifica silenciosamente lo que ve el colaborador.

### P-05 — Intercambio

1. Un colaborador selecciona su asignación y otra elegible.
    
2. El sistema valida y reserva la participación de ambas.
    
3. El compañero acepta o rechaza.
    
4. Si acepta, la solicitud pasa a revisión.
    
5. Un administrador o supervisor independiente revisa la solicitud.
    
6. Antes de aprobar, el sistema vuelve a validar el resultado.
    
7. Se reconocen las advertencias aplicables.
    
8. Ambas asignaciones se actualizan en una sola operación.
    
9. Se registra el resultado, se liberan reservas y se generan eventos para avisos.
    

Si no existe un aprobador empresarial independiente, el intercambio no puede aprobarse.

## 7. Reglas de organización y acceso

### RN-001 — Aislamiento empresarial

Toda operación sobre datos empresariales debe estar limitada a la organización autorizada.

Un identificador recibido desde la interfaz no demuestra pertenencia.

### RN-002 — Autorización efectiva

Los permisos deben comprobarse en el servidor para cada operación protegida.

Ocultar botones no sustituye la autorización.

### RN-003 — Organización suspendida

La suspensión bloquea las operaciones ordinarias de los usuarios empresariales, incluso si tenían una sesión abierta.

Se mostrará información suficiente para contactar al responsable del servicio, sin exponer datos internos.

No se eliminarán datos ni se publicarán cambios pendientes.

La reactivación requerirá revisar la vigencia de solicitudes y borradores. No reactiva automáticamente cuentas o colaboradores que permanecían desactivados.

### RN-004 — Usuario inactivo

Un usuario inactivo no puede iniciar sesión ni continuar ejecutando operaciones protegidas.

La desactivación de una cuenta no elimina su historial ni desactiva automáticamente su registro de colaborador.

Las solicitudes pendientes en las que participe deben invalidarse, porque el intercambio requiere cuentas habilitadas.

### RN-005 — Último administrador

No se permitirá una modificación que deje a la organización sin un administrador activo.

La recuperación excepcional se realizará mediante un procedimiento controlado del operador.

### RN-006 — Colaborador y cuenta

Una cuenta podrá vincularse como máximo a un colaborador de su propia organización.

Un colaborador tendrá como máximo una cuenta vinculada en el MVP.

El vínculo se gestionará desde un único flujo administrativo.

Cambiar un vínculo no transfiere automáticamente solicitudes personales a otra persona. Sus efectos deben resolverse de forma controlada antes de confirmar el cambio.

### RN-007 — Límites de servicio

La creación o activación de recursos sujetos a capacidad debe respetar los límites configurados.

Superar un límite no debe producir eliminación automática de datos existentes.

Las condiciones de ampliación se gestionarán fuera del flujo de planificación.

## 8. Reglas de colaboradores y disponibilidad

### RN-008 — Colaborador activo

Solo un colaborador activo puede recibir nuevas asignaciones.

No es obligatorio que tenga cuenta para ser planificado.

### RN-009 — Desactivación con compromisos

No se desactivará un colaborador mientras tenga:

- Asignaciones publicadas todavía no finalizadas.
    
- Solicitudes de intercambio pendientes.
    
- Asignaciones en borradores abiertos.
    

Primero deberán resolverse o retirarse esos compromisos mediante acciones autorizadas.

No se retirarán automáticamente para completar la desactivación.

La información histórica se conserva.

### RN-010 — Puesto operativo

Cada colaborador tendrá un puesto operativo principal en el MVP.

Las asignaciones deberán ser compatibles con ese puesto.

La habilitación de una persona para múltiples puestos queda fuera de esta primera versión.

### RN-011 — Cambio de puesto

Si existen asignaciones, borradores o solicitudes pendientes que quedarían incompatibles, el cambio se bloqueará hasta resolverlos.

No se modificarán retrospectivamente los puestos registrados en publicaciones anteriores.

### RN-012 — Indisponibilidad

Una indisponibilidad contiene colaborador, inicio y fin válidos.

La ausencia de un registro significa “sin restricción registrada”, no una confirmación explícita del trabajador.

Solo el administrador o supervisor puede registrarla, modificarla o cancelarla.

### RN-013 — Indisponibilidad y turnos publicados

No se registrará ni modificará una indisponibilidad de modo que contradiga una asignación publicada sin resolver previamente esa asignación.

Una ausencia de última hora podrá requerir retirar o reasignar un turno futuro mediante una revisión del horario.

El MVP no registra asistencia ni modifica retrospectivamente lo ocurrido en un turno iniciado.

Las operaciones concurrentes deben impedir que una publicación y una indisponibilidad incompatibles se confirmen ambas.

### RN-014 — Privacidad de motivos

Los motivos de indisponibilidad no se mostrarán a compañeros.

Se evitará registrar detalles médicos u otra información sensible innecesaria.

## 9. Reglas temporales y de asignación

### RN-015 — Zona horaria

Los horarios se interpretan en la zona configurada para la organización.

Inicio y fin deben representar instantes inequívocos.

Antes de la primera publicación, el administrador puede cambiar la zona.

Si existen borradores:

- Se informa el impacto.
    
- Se conservan los instantes de sus asignaciones.
    
- Se recalculan las fechas y horas mostradas.
    
- Se comprueba la pertenencia semanal.
    
- Se bloquea el cambio si alguna asignación quedaría fuera de la semana de su borrador.
    

El cambio de zona no traslada silenciosamente asignaciones entre planificaciones.

Después de la primera publicación, la zona no puede cambiarse mediante edición ordinaria.

El MVP no incorpora un procedimiento automático de migración de zona para organizaciones en operación.

### RN-016 — Intervalo válido

Una asignación debe tener inicio anterior al fin.

La fecha de fin puede ser posterior a la de inicio, permitiendo turnos nocturnos.

Las horas locales inexistentes o ambiguas por cambios horarios deben rechazarse o resolverse explícitamente; nunca interpretarse de manera silenciosa.

### RN-017 — Solapamiento

Dos asignaciones se solapan cuando:

`inicio A < fin B` y `inicio B < fin A`.

Una asignación que comienza exactamente al terminar otra no se solapa, aunque puede incumplir el descanso mínimo.

Los intervalos incluyen el inicio y excluyen el fin.

### RN-018 — Descanso mínimo

Entre asignaciones consecutivas debe existir al menos el descanso configurado.

La validación revisará también turnos anteriores y posteriores situados fuera de la semana visible.

Este parámetro no representa por sí solo una certificación de cumplimiento laboral.

### RN-019 — Validación contra indisponibilidad

Una asignación publicada no puede intersectar una indisponibilidad registrada vigente.

En un borrador, el conflicto puede conservarse para corregirse, pero impide publicar.

### RN-020 — Semana de planificación

La semana se define de lunes a domingo según la zona horaria de la organización.

Una asignación pertenece a la planificación de la semana en que comienza.

Para totalizar horas semanales, su duración se distribuirá entre los periodos que atraviese.

No se cambia la pertenencia semanal de una asignación publicada mediante una edición silenciosa de su identidad.

### RN-021 — Horas programadas

Las horas programadas se calculan a partir de los intervalos temporales de las asignaciones vigentes.

No equivalen a horas trabajadas ni remunerables.

El MVP no descontará pausas automáticamente. Si el cliente requiere descansos intraturno o deducciones, deberán modelarse antes de prometer ese cálculo.

### RN-022 — Catálogos y asignaciones existentes

Cambiar un tipo de turno no modifica asignaciones ya creadas.

Desactivar un elemento de catálogo impide nuevos usos, pero no elimina referencias históricas.

Una plantilla facilita completar un formulario; los valores guardados de la asignación son los que definen el turno.

## 10. Políticas y severidad de validación

### Clasificación funcional

|   |   |   |
|---|---|---|
|Validación|Al guardar en borrador|Al publicar o aprobar un intercambio|
|Organización o recurso ajeno|Rechazo|Bloqueo|
|Falta de permiso|Rechazo|Bloqueo|
|Intervalo inválido|Rechazo|Bloqueo|
|Nuevo uso de colaborador inactivo|Rechazo|Bloqueo|
|Puesto incompatible|Rechazo|Bloqueo|
|Modificación prohibida de turno iniciado|Rechazo|Bloqueo|
|Solapamiento|Puede guardarse identificado|Bloqueo|
|Indisponibilidad|Puede guardarse identificada|Bloqueo|
|Descanso inferior al configurado|Puede guardarse identificado|Bloqueo|
|Horas superiores al umbral|Advertencia|Advertencia con reconocimiento|
|Versión modificada desde la lectura|Conflicto|Conflicto|

Ningún rol puede saltarse un bloqueo mediante una confirmación.

### RN-023 — Umbral de horas

El umbral semanal se tratará como una advertencia operativa, no como un límite legal implementado.

Si el contexto requiere una prohibición obligatoria, deberá cambiarse su clasificación antes del piloto.

No se ofrecerá “cumplimiento automático” mientras existan reglas aplicables sin implementar.

### RN-024 — Confirmación de advertencias

Publicar o aprobar un intercambio con advertencias requiere:

- Mostrar los colaboradores y periodos afectados.
    
- Identificar las advertencias concretas.
    
- Confirmarlas expresamente.
    
- Registrar una justificación.
    
- Conservar actor, fecha, política y resultado revisado.
    

La aceptación no elimina la advertencia del historial.

Un reconocimiento previo no cubre advertencias nuevas o materialmente distintas.

El administrador o supervisor puede reconocer advertencias dentro de sus permisos; no puede convertir un bloqueo en advertencia.

### RN-025 — Configuración obligatoria

Antes de publicar deben estar definidos:

- Organización y sede activas.
    
- Zona horaria.
    
- Descanso mínimo.
    
- Umbral de horas programadas.
    
- Usuario activo con autorización.
    

Para cada asignación incluida se requiere un colaborador activo y un puesto compatible.

No se exige tener plantillas ni cuentas vinculadas a todos los colaboradores.

Los valores de las políticas no se inventarán en esta nota. Se acordarán para el escenario de uso y se documentará su origen.

### RN-026 — Cambios de política

Las políticas conservarán versiones y fecha de activación.

En el MVP, la activación será inmediata y controlada. No se incorpora programación de políticas para fechas futuras.

Antes de activar:

- Se comparan valores actuales y propuestos.
    
- Se muestran impactos sobre asignaciones futuras publicadas.
    
- Se consideran turnos anteriores relevantes para calcular el descanso.
    
- Se informa que borradores e intercambios pendientes deberán revalidarse.
    
- Se registra el motivo.
    

Si el nuevo descanso mínimo hace incompatibles compromisos futuros, se bloquea la activación hasta resolverlos.

Si el nuevo umbral genera advertencias, el administrador debe revisar y justificar el impacto. No se alteran los turnos.

La activación:

- No modifica publicaciones históricas.
    
- No reasigna colaboradores.
    
- No convierte automáticamente borradores en horarios válidos.
    
- Obliga a utilizar la política vigente en las siguientes validaciones finales.
    
- Se coordina con publicaciones, indisponibilidades e intercambios concurrentes.
    

Si cambian los impactos desde la vista previa, se requiere nueva revisión.

## 11. Estados y reglas de planificación

La planificación se identifica por organización, sede y semana.

Puede tener:

- Un borrador abierto.
    
- Una publicación vigente.
    
- Publicaciones anteriores.
    

### Estados del borrador

|   |   |
|---|---|
|Estado|Significado|
|BORRADOR|Puede editarse dentro de las reglas.|
|PUBLICADO|Originó una publicación y ya no se edita.|
|DESCARTADO|Se abandonó sin modificar el horario vigente.|

“Desactualizado” es una condición del borrador: su publicación de origen dejó de ser vigente.

### RN-027 — Un borrador abierto

Solo existirá un borrador abierto por sede y semana.

No habrá varios borradores independientes compitiendo por reemplazar el mismo horario.

### RN-028 — Base de validación

Al validar un borrador:

- Sus asignaciones sustituyen provisionalmente a las de la semana que está revisando.
    
- Se consideran asignaciones publicadas de los demás periodos.
    
- No se interpretan otros borradores como compromisos publicados.
    
- Se incluyen las asignaciones iniciadas que deben conservarse.
    

Todas las reglas se ejecutan nuevamente al publicar.

### RN-057 — Guardado de borradores

El guardado es explícito.

El borrador puede conservar conflictos operativos identificados para corregirlos posteriormente, pero no datos imposibles, ajenos o prohibidos.

El sistema diferencia:

- Cambios locales todavía sin guardar.
    
- Borrador guardado.
    
- Borrador validado.
    
- Publicación confirmada.
    

Validar no reserva la posibilidad de publicar más adelante.

Si se guardan cambios después de una validación, su resultado anterior no se presenta como comprobación vigente.

### RN-029 — Publicación atómica

Una publicación entra en vigor completamente o no entra en vigor.

Deben confirmarse consistentemente:

- Nueva versión y sus asignaciones.
    
- Referencia a la publicación vigente.
    
- Cierre del borrador.
    
- Política aplicada.
    
- Motivo y reconocimientos requeridos.
    
- Invalidaciones necesarias y liberación de reservas.
    
- Auditoría obligatoria.
    
- Eventos pendientes para avisos.
    

El fallo de una parte obligatoria impide confirmar la operación.

### RN-030 — Borrador desactualizado

Si la publicación de origen cambió por otra revisión o intercambio, no se publicará el borrador antiguo.

El usuario deberá revisar las diferencias y regenerarlo desde la versión vigente.

No se combinarán cambios automáticamente en el MVP.

Antes de descartar trabajo, se advertirá qué cambios no se trasladarán.

### RN-031 — Turnos iniciados

No se permitirá editar o retirar asignaciones que ya comenzaron.

Una revisión de una semana en curso podrá modificar únicamente asignaciones futuras.

Las asignaciones iniciadas o finalizadas se conservarán sin cambios.

Esta restricción también se aplica al intentar publicar una semana vacía.

### RN-032 — Tiempo de referencia

Las decisiones de inicio, vencimiento y elegibilidad se basan en el tiempo del servidor, no en el reloj del dispositivo.

El paso del tiempo entre una revisión previa y la confirmación puede hacer que una operación deje de ser válida.

### RN-033 — No eliminar publicaciones

Una publicación no se elimina físicamente.

Para retirar turnos futuros se publica una revisión que conserve la trazabilidad.

Las publicaciones anteriores permanecen disponibles mediante consultas autorizadas.

### RN-058 — Publicación de semana vacía

Se permite publicar una semana sin asignaciones si cumple las reglas.

Se exige:

- Advertencia específica.
    
- Confirmación explícita.
    
- Motivo.
    
- Validación final.
    
- Auditoría.
    

Si reemplaza una publicación con turnos, deben mostrarse las asignaciones que se retirarían y los intercambios afectados.

No puede retirarse ningún turno iniciado para conseguir una semana vacía.

Se distinguen:

|   |   |
|---|---|
|Situación|Significado|
|Sin publicación|El horario todavía no ha sido publicado.|
|Publicación sin asignaciones|Existe una decisión publicada de no programar turnos para esa semana.|
|Publicación con turnos, pero ninguno propio|Ese colaborador no tiene turnos programados.|

La ausencia de asignación no se etiqueta automáticamente como disponibilidad confirmada, descanso o ausencia.

### RN-059 — Motivo de publicación

|   |   |
|---|---|
|Operación|Motivo|
|Primera publicación con turnos y sin advertencias|Opcional|
|Publicación con advertencias reconocidas|Obligatorio|
|Publicación de semana vacía|Obligatorio|
|Reemplazo de publicación vigente mediante borrador|Obligatorio|

El motivo explica la decisión sin incluir información privada innecesaria.

La interfaz y el contrato aplicarán la misma obligatoriedad.

Las publicaciones generadas por un intercambio conservan su origen y referencia a la solicitud. No exigen una segunda publicación manual.

### RN-060 — Revisión previa y confirmación

Antes de publicar se muestran:

- Publicación base.
    
- Cambios agregados, modificados y retirados.
    
- Colaboradores afectados.
    
- Bloqueos.
    
- Advertencias.
    
- Intercambios que quedarían invalidados.
    
- Motivo y confirmaciones requeridos.
    

La confirmación queda vinculada al resultado revisado.

Si cambian materialmente el contenido, las reglas aplicables o los impactos, se solicita nueva revisión.

La vista previa no concede permisos ni sustituye la validación transaccional final.

## 12. Estados del intercambio

|   |   |
|---|---|
|Estado|Significado|
|PENDIENTE_COMPANERO|Espera respuesta del destinatario.|
|PENDIENTE_SUPERVISOR|El compañero aceptó; falta resolución.|
|APROBADA|Se aplicó el intercambio.|
|RECHAZADA|El compañero o responsable autorizado rechazó.|
|CANCELADA|El solicitante canceló mientras estaba pendiente.|
|VENCIDA|Comenzó alguno de los turnos sin aprobación final.|
|INVALIDADA|Cambió una condición que impide continuar.|

### Transiciones permitidas

- Creación → `PENDIENTE_COMPANERO`.
    
- Aceptación del destinatario → `PENDIENTE_SUPERVISOR`.
    
- Rechazo del destinatario desde `PENDIENTE_COMPANERO` → `RECHAZADA`.
    
- Rechazo del responsable autorizado desde `PENDIENTE_SUPERVISOR` → `RECHAZADA`.
    
- Aprobación válida desde `PENDIENTE_SUPERVISOR` → `APROBADA`.
    
- Cancelación del solicitante desde cualquier estado pendiente → `CANCELADA`.
    
- Inicio de un turno desde cualquier estado pendiente → `VENCIDA`.
    
- Pérdida de elegibilidad desde cualquier estado pendiente → `INVALIDADA`.
    

Los estados terminales no se reabren. Para intentarlo nuevamente deberá crearse otra solicitud.

## 13. Reglas del intercambio

### RN-034 — Participantes distintos

Solicitante y destinatario deben ser colaboradores diferentes, activos y con cuentas habilitadas.

La identidad personal se deriva del vínculo de la cuenta. No se acepta como autoridad un solicitante arbitrario enviado desde la interfaz.

### RN-035 — Pertenencia y compatibilidad

Ambos pertenecen a la misma organización y sede.

Para el MVP deben tener el mismo puesto operativo.

### RN-036 — Asignaciones elegibles

Se intercambian dos asignaciones publicadas, futuras y pertenecientes respectivamente a los participantes.

No se incluyen:

- Borradores.
    
- Cesiones unilaterales.
    
- Turnos vacantes.
    
- Asignaciones ya iniciadas.
    

### RN-037 — Una solicitud pendiente por asignación

Una asignación no puede participar simultáneamente en varias solicitudes pendientes, ni como origen ni como destino.

La restricción debe mantenerse incluso si las solicitudes se crean concurrentemente.

La creación reserva ambas asignaciones de forma atómica. Si no pueden reservarse las dos, no se crea la solicitud.

Las reservas se liberan cuando la solicitud alcanza un estado terminal.

### RN-038 — Aceptar no modifica horarios

La aceptación del compañero solo habilita la revisión del responsable.

Hasta la aprobación final, cada colaborador conserva su asignación original.

La interfaz debe explicarlo antes y después de aceptar.

### RN-039 — Revisión independiente

El aprobador final no puede ser ninguno de los participantes.

La decisión del administrador o supervisor en la etapa de resolución debe ser independiente de su participación personal.

Si solicita un intercambio personal, necesita otro usuario autorizado para resolverlo.

Si no existe, el intercambio no puede aprobarse. El operador del SaaS no reemplaza al aprobador empresarial.

Esta separación aplica al flujo de intercambio. No implica que toda planificación requiera una segunda firma independiente.

### RN-040 — Revalidación final

Antes de aprobar se comprueban nuevamente:

- Estado pendiente correcto.
    
- Permisos vigentes.
    
- Independencia del aprobador.
    
- Participantes y cuentas habilitados.
    
- Asignaciones y revisiones vigentes.
    
- Reservas.
    
- Horarios todavía futuros.
    
- Puestos y sede.
    
- Indisponibilidad.
    
- Solapamientos.
    
- Descansos.
    
- Horas programadas.
    
- Política vigente.
    
- Estado de la organización.
    

Si existen advertencias de horas programadas, su reconocimiento y justificación siguen RN-024.

La validación inicial no garantiza que una solicitud siga siendo válida después.

### RN-041 — Simulación del resultado

La validación se realiza sobre el horario resultante de retirar ambas asignaciones originales y asignarlas a sus nuevos titulares.

No debe compararse una asignación intercambiada contra su propia versión anterior.

Se mantienen las fechas, horas y características del turno; cambia el colaborador asignado.

### RN-042 — Aplicación atómica

Las dos reasignaciones, las publicaciones afectadas, el estado final, la liberación de reservas, la auditoría y los eventos para avisos se confirman en una sola operación consistente.

Si falla una parte obligatoria, no se aplica ninguna.

### RN-043 — Intercambio entre semanas

Se permite entre semanas distintas si ambas asignaciones cumplen las reglas.

Se actualizan consistentemente las planificaciones afectadas y se validan las horas de todos los periodos involucrados.

No se permite confirmar solo una de las semanas.

### RN-044 — Intercambio y borradores

Si el intercambio modifica una planificación con borrador abierto, dicho borrador queda desactualizado y no puede sobrescribir el resultado.

El usuario deberá revisar y regenerar el trabajo desde la publicación vigente.

### RN-045 — Modificación de una asignación solicitada

Una revisión publicada que cambie o retire cualquiera de las asignaciones referenciadas invalida la solicitud pendiente.

Antes de publicar se informará al responsable sobre las solicitudes afectadas.

Cambiar otra asignación de la misma semana no invalida automáticamente la solicitud si sus referencias se mantienen vigentes. Aun así, debe revalidarse el resultado al aprobar.

No se utiliza únicamente el cambio del número de publicación como prueba de que una asignación referenciada cambió.

### RN-046 — Respuesta duplicada o tardía

Repetir una operación no debe aplicar el intercambio dos veces.

Una aprobación recibida después de un rechazo, cancelación, invalidación o vencimiento no modifica el resultado existente.

### RN-047 — Vencimiento efectivo

Desde el inicio de cualquiera de los turnos, la solicitud deja de ser aprobable aunque una tarea periódica todavía no haya actualizado su estado visible.

El vencimiento libera las reservas mediante el mecanismo consistente previsto.

No puede reutilizarse un estado visible antiguo para aprobar.

### RN-048 — Motivos de resolución

Los rechazos del responsable y las invalidaciones deben conservar un motivo comprensible.

Las invalidaciones y vencimientos generados por el sistema registran su causa.

Los errores internos no se muestran como motivos de negocio.

El motivo del rechazo del destinatario y el de la cancelación del solicitante serán opcionales en el MVP.

No se exige a un colaborador revelar información privada para rechazar una propuesta.

## 14. Concurrencia y consistencia

### RN-049 — Cambios concurrentes

Si dos usuarios editan la misma información, el sistema no debe sobrescribir silenciosamente el trabajo del primero.

La operación basada en una versión antigua deberá detectar el conflicto y solicitar revisión.

Las validaciones finales deben coordinarse con escrituras que cambien sus condiciones, incluyendo políticas, indisponibilidades y activación de recursos.

### RN-050 — Publicaciones de periodos adyacentes

Si dos semanas se publican simultáneamente para un mismo colaborador, la validación final debe impedir solapamientos y preservar el descanso configurado entre periodos.

Las advertencias de horas se recalculan sobre un resultado consistente.

No basta con que cada semana sea válida de forma aislada.

### RN-051 — Auditoría obligatoria

Si una operación crítica no puede registrar su auditoría obligatoria, no se considerará completada.

La auditoría identifica el actor real, la organización, el recurso, la acción y el momento.

No debe registrar contraseñas, tokens ni información privada innecesaria.

### RN-052 — Avisos y operación principal

Un fallo al entregar un aviso no debe duplicar ni revertir una publicación o intercambio confirmado correctamente.

El evento pendiente de aviso debe conservarse para su tratamiento.

Se utilizará la bandeja de salida transaccional definida en la arquitectura, con reintentos y deduplicación.

La entrega de un aviso no demuestra que el destinatario lo haya leído.

### RN-061 — Reintentos y resultado incierto

Los comandos de negocio comprendidos en el contrato de idempotencia deben evitar duplicaciones al repetirse.

Para una misma intención:

- Se conserva la clave de la operación.
    
- Se comprueba si existe un resultado confirmado.
    
- Se rechaza reutilizar la clave con contenido diferente.
    
- Se respetan el ámbito del actor, su organización y sus permisos actuales.
    

Si se pierde la respuesta de red, la interfaz no afirma automáticamente que la operación falló.

Antes de enviar una nueva intención se consulta el resultado o se reintenta de acuerdo con el contrato.

La ventana de deduplicación y las excepciones para credenciales se definen en [[10 - Contratos API e Integraciones]].

## 15. Reportes y visibilidad

### RN-053 — Fuente vigente

Los reportes operativos utilizan publicaciones vigentes.

Los borradores no se mezclan con la planificación oficial.

Las consultas explícitas de publicaciones históricas deben identificarse como tales. No se presupone un constructor de reportes históricos fuera del alcance definido.

### RN-054 — Totales por periodo

Las horas se atribuyen al periodo donde transcurre cada parte del turno.

Un turno nocturno que cruza de domingo a lunes divide su duración entre ambas semanas para los totales semanales.

El cálculo utiliza los instantes de inicio y fin, no únicamente las etiquetas de fecha mostradas.

### RN-055 — Exportación autorizada

Las exportaciones respetan los mismos permisos y límites empresariales que la consulta en pantalla.

También respetan los filtros solicitados.

Si deben corresponder exactamente al reporte visualizado, se verifica que las fuentes no hayan cambiado. Si cambiaron, se solicita actualizar antes de exportar.

### RN-056 — Significado de los datos

Los reportes deben identificar:

- Periodo.
    
- Zona horaria.
    
- Momento de generación.
    
- Publicaciones utilizadas.
    
- Organización.
    
- Naturaleza programada de las horas.
    

No incluirán conclusiones sobre asistencia real o remuneraciones.

Una ausencia de turnos no demuestra ausencia física, descanso concedido ni disponibilidad confirmada.

## 16. Situaciones excepcionales

|   |   |
|---|---|
|Situación|Comportamiento esperado|
|Un colaborador no tiene cuenta|Puede ser planificado si está activo; no puede acceder ni intercambiar.|
|La semana no está publicada|Se informa que todavía no existe horario publicado.|
|Se publica una semana vacía|Se registra motivo y confirmación, sin retirar turnos iniciados.|
|El borrador contiene conflictos|Puede conservarse para corregirlos; no se publica con bloqueos.|
|Aparecen advertencias nuevas|Se solicita nueva revisión y reconocimiento.|
|El compañero no responde|La solicitud sigue pendiente hasta cancelación, invalidación o vencimiento.|
|Se desactiva la cuenta de un participante|Se invalidan las solicitudes pendientes en las que participa.|
|Cambia el horario mientras se revisa|Se rechaza la operación desactualizada y se solicita revisión.|
|El responsable participa en el intercambio|Debe resolver otro administrador o supervisor autorizado.|
|No existe otro aprobador|El intercambio no puede aprobarse.|
|Falla un aviso|Se conserva el resultado y se reintenta la entrega.|
|Se pierde una respuesta|Se comprueba el resultado antes de crear otra operación.|
|Una empresa se suspende|Se bloquean acciones ordinarias y aprobaciones.|
|Una empresa se reactiva|Se revisa vigencia antes de continuar solicitudes y borradores.|
|Un turno ya comenzó|No se modifica ni retira mediante planificación o intercambio.|
|Una política nueva contradice compromisos futuros bloqueantes|No se activa hasta resolverlos.|
|El cliente requiere asistencia|Se registra como necesidad fuera del MVP.|

El MVP no incluye aceptación formal obligatoria de cada turno publicado.

## 17. Casos críticos para pruebas

### Organización y acceso

- Dos empresas con recursos similares sin acceso cruzado.
    
- Usuario desactivado con sesión previamente abierta.
    
- Organización suspendida con sesiones abiertas.
    
- Protección del último administrador.
    
- Colaborador activo sin cuenta que puede ser planificado.
    
- Cuenta sin vínculo que no puede consultar horarios personales.
    
- Desactivación de cuenta que invalida intercambios pendientes.
    
- Cambio de vínculo que no transfiere solicitudes a otra persona.
    

### Tiempo y asignación

- Colaborador inactivo que intenta recibir un turno.
    
- Turno que cruza medianoche.
    
- Turno que cruza de semana.
    
- Asignaciones adyacentes sin solapamiento, pero sin descanso suficiente.
    
- Cambio de zona antes de publicar con borradores afectados.
    
- Cambio de zona bloqueado después de publicar.
    
- Reporte que distribuye minutos entre periodos.
    

### Borradores y publicación

- Segundo borrador abierto rechazado.
    
- Guardado de conflictos operativos identificados.
    
- Datos imposibles rechazados al guardar.
    
- Bloqueos que ningún rol puede ignorar.
    
- Advertencias reconocidas con motivo.
    
- Advertencia nueva que obliga a revisar.
    
- Primera publicación ordinaria sin motivo obligatorio.
    
- Revisión de publicación con motivo obligatorio.
    
- Semana vacía con confirmación y motivo.
    
- Intento de vaciar una semana con turnos iniciados.
    
- Revisión de semana en curso que conserva turnos iniciados.
    
- Publicación que entra en conflicto con otra semana.
    
- Vista previa desactualizada.
    
- Dos publicaciones simultáneas.
    
- Reintento sin duplicar publicación.
    

### Políticas

- Nuevo descanso incompatible con turnos futuros.
    
- Nuevo umbral que genera advertencias.
    
- Activación concurrente con publicación.
    
- Borrador revalidado con política vigente.
    
- Publicación histórica que conserva sus condiciones registradas.
    

### Intercambios

- Dos solicitudes concurrentes sobre una asignación.
    
- La misma asignación utilizada como origen y destino de distintas solicitudes.
    
- Aceptación sin modificación del horario.
    
- Aprobación propia rechazada.
    
- Rechazo final por un responsable participante rechazado.
    
- Aprobación después del inicio de un turno.
    
- Doble envío de aprobación.
    
- Intercambio entre semanas distintas.
    
- Fallo parcial que no deja una sola reasignación aplicada.
    
- Intercambio que desactualiza un borrador.
    
- Modificación de asignación referenciada que invalida la solicitud.
    
- Modificación no relacionada que no invalida automáticamente.
    
- Advertencias del resultado reconocidas por el aprobador.
    
- Liberación de ambas reservas al terminar.
    

### Reportes, auditoría y avisos

- Reporte que excluye borradores.
    
- Exportación con permisos y filtros.
    
- Cambio de fuentes antes de exportar.
    
- Fallo de auditoría que impide confirmar una operación crítica.
    
- Fallo de aviso sin pérdida ni duplicación del cambio.
    
- Resultado incierto recuperado sin crear otra operación.
    

Estos casos se convertirán en pruebas con datos, pasos y resultados esperados dentro del plan de calidad.

## 18. Decisiones consolidadas para revisión

Esta versión propone cerrar las siguientes decisiones:

- Un rol empresarial predefinido por cuenta.
    
- Un puesto principal por colaborador.
    
- Colaboradores planificables sin cuenta.
    
- Indisponibilidades registradas por responsables autorizados.
    
- Plantillas opcionales.
    
- Semana de lunes a domingo.
    
- Descanso mínimo bloqueante.
    
- Umbral de horas como advertencia operativa.
    
- Reconocimiento específico y justificado de advertencias.
    
- Un borrador abierto por semana.
    
- Guardado de conflictos operativos dentro del borrador.
    
- Publicaciones versionadas e inmutables.
    
- Semana vacía permitida con confirmación y motivo.
    
- Turnos iniciados no editables.
    
- Cambios de política inmediatos y controlados.
    
- Intercambio de dos asignaciones, sin cesión unilateral.
    
- Una solicitud pendiente por asignación.
    
- Resolución final independiente de los participantes.
    
- Revalidación al aprobar.
    
- Sin combinación automática de borradores desactualizados.
    
- Reintentos protegidos según el contrato.
    
- Avisos separados de la confirmación del cambio de negocio.
    

No quedan como alternativas abiertas dentro de esta versión. Su aprobación como línea base debe registrarse antes de implementar las partes afectadas.

Si una decisión no encaja con el negocio piloto, debe revisarse explícitamente.

## 19. Pendientes del escenario de uso

Estos puntos requieren información real o definición adicional:

- Valores de descanso mínimo y umbral de horas.
    
- Confirmación de que un puesto por colaborador resulta suficiente.
    
- Necesidad de jornadas partidas, pausas o deducciones.
    
- Personas que actuarán como aprobadores independientes.
    
- Adecuación de la semana de lunes a domingo al negocio.
    
- Límites de servicio o piloto.
    
- Política de conservación y cierre de datos.
    
- Procedimiento de primer acceso y recuperación.
    
- Límites de tamaño de motivos y otros campos.
    
- Configuración operativa y criterios de soporte.
    

Para desarrollo y pruebas se podrán utilizar valores ficticios identificados como tales. No se presentarán como condiciones legales ni empresariales reales.

## 20. Documentos relacionados y sincronización

### Documentos relacionados

- [[03 - Alcance MVP y Roadmap Comercial]]
    
- [[05 - Requisitos y Casos de Uso]]
    
- [[06 - Arquitectura y Stack Tecnológico]]
    
- [[07 - Modelo de Datos y DER]]
    
- [[08 - Navegación, Pantallas y Flujos de Usuario]]
    
- [[09 - Design System y Wireframes]]
    
- [[10 - Contratos API e Integraciones]]
    
- [[11 - Backlog MVP y Plan de Implementación]]
    
- [[12 - Pruebas, Seguridad y Operación]]
    

### Ajustes que deben reflejarse

|   |   |
|---|---|
|Documento|Ajuste principal|
|05|Criterios de aceptación de las reglas consolidadas|
|07|Motivos, versiones de política, reconocimientos e idempotencia|
|08|Flujos de semana vacía, advertencias y cambios de política|
|09|Mensajes, campos y confirmaciones correspondientes|
|10|Contratos de publicación, aprobación, políticas y errores|
|11|Tareas y decisiones pendientes actualizadas|
|12|Casos de prueba adicionales|

La aprobación de un intercambio con advertencias necesita un contrato que permita registrar su reconocimiento; no basta con enviar únicamente la versión de la solicitud.

Esta revisión de la nota 04 no significa que los demás documentos ya hayan sido actualizados.

## 21. Criterio de cierre

Esta nota estará aprobada cuando:

- Los roles y permisos sean coherentes con el MVP.
    
- Las reglas tengan un comportamiento verificable.
    
- Los estados no permitan transiciones ambiguas.
    
- Se acepten las restricciones de simplificación.
    
- Se acepten las decisiones consolidadas de esta versión.
    
- Los conflictos entre planificación e intercambios estén resueltos.
    
- Las excepciones críticas estén contempladas.
    
- Los parámetros del escenario estén definidos o tengan seguimiento explícito.
    
- Los documentos dependientes se hayan sincronizado.
    
- No se confundan validaciones operativas con garantías legales.
    

**Situación de esta versión:** contenido consolidado y preparado para revisión; implementación, pruebas y sincronización documental pendientes.