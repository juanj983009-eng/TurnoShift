
## 1. Propósito

Definir qué debe hacer el MVP de TurnoShift, quién puede realizar cada operación y cómo se comprobará su funcionamiento.

Esta especificación conecta el alcance del producto con su implementación y pruebas.

TurnoShift se plantea como un SaaS comercial de planificación de turnos. El MVP debe permitir incorporar empresas, publicar horarios, gestionar intercambios y operar un piloto controlado.

El producto registra **horas programadas**, no asistencia ni horas efectivamente trabajadas.

Los requisitos descritos son compromisos propuestos para el MVP. Este documento no demuestra que ya estén implementados, probados o aprobados comercialmente.

## 2. Documentos de referencia

- [[01 - Formulación y Visión del Producto]]
    
- [[02 - Modelo de Negocio SaaS y Validación Comercial]]
    
- [[03 - Alcance MVP y Roadmap Comercial]]
    
- [[04 - Roles, Procesos y Reglas de Negocio]]
    
- [[06 - Arquitectura y Stack Tecnológico]]
    
- [[07 - Modelo de Datos y DER]]
    
- [[08 - Navegación, Pantallas y Flujos de Usuario]]
    
- [[10 - Contratos API e Integraciones]]
    
- [[11 - Backlog MVP y Plan de Implementación]]
    
- [[12 - Pruebas, Seguridad y Operación]]
    

### Distribución de responsabilidades

|Documento|Responsabilidad|
|---|---|
|03 — Alcance|Determinar qué entra y qué queda fuera del MVP.|
|04 — Reglas de negocio|Definir permisos, restricciones y transiciones válidas.|
|05 — Requisitos|Especificar comportamientos verificables y casos de uso.|
|06 — Arquitectura|Establecer la solución técnica.|
|07 — Datos|Representar entidades, relaciones e integridad.|
|08 — Navegación|Organizar pantallas, acciones y estados de interfaz.|
|10 — API|Precisar solicitudes, respuestas y errores.|
|11 — Backlog|Ordenar la implementación.|
|12 — Pruebas y operación|Definir comprobaciones, evidencias y condiciones operativas.|

Si aparece una contradicción, debe resolverse en los documentos afectados antes de implementar ese comportamiento. No se elegirá silenciosamente la interpretación más conveniente.

## 3. Convenciones

|   |   |
|---|---|
|Identificador|Significado|
|RF|Requisito funcional.|
|RNF|Requisito no funcional.|
|CU|Caso de uso.|
|RN|Regla de negocio definida en la nota 04.|
|MVP|Capacidad del alcance definido en la nota 03.|

Los identificadores se mantienen estables para permitir trazabilidad con historias y pruebas.

No se reutilizará el identificador de un requisito retirado para una función distinta.

### Estado de los requisitos

Todos los RF de esta nota pertenecen al alcance propuesto del MVP.

Su orden de implementación no los convierte en opcionales. Una reducción del alcance requiere una decisión explícita y la actualización de los documentos relacionados.

### Interpretación de criterios

- **Bloqueo:** impide confirmar una operación.
    
- **Advertencia:** permite continuar únicamente bajo las condiciones de confirmación de la nota 04.
    
- **Previsualización:** evaluación del resultado propuesto; no modifica el horario oficial.
    
- **Confirmación:** solicitud que vuelve a comprobar las condiciones y aplica la operación.
    
- **Versión vigente:** publicación oficial actual de una semana.
    
- **Borrador desactualizado:** borrador cuya publicación base ya no coincide con la vigente.
    

## 4. Actores

|   |   |
|---|---|
|Actor|Responsabilidades|
|Operador de TurnoShift|Incorporar empresas, administrar el servicio y ejecutar procedimientos operativos autorizados.|
|Administrador|Administrar su empresa, usuarios, colaboradores, configuración y planificación.|
|Supervisor|Planificar, publicar, gestionar indisponibilidades y revisar intercambios; consultar datos operativos autorizados.|
|Colaborador|Consultar su horario publicado y participar en intercambios propios.|
|Sistema|Aplicar validaciones, controlar vigencia, registrar auditoría y procesar avisos internos.|

### Condiciones de acceso

1. El operador no es un rol empresarial ni un aprobador alternativo de intercambios.
    
2. Cada cuenta empresarial tiene un rol predefinido.
    
3. Administradores y supervisores pueden utilizar funciones personales si tienen un colaborador activo vinculado.
    
4. Una cuenta y un colaborador son entidades diferentes.
    
5. Puede existir un colaborador planificable sin cuenta.
    
6. El supervisor no administra usuarios, colaboradores, catálogos ni políticas.
    
7. Participar en un intercambio impide resolverlo como responsable, tanto para aprobarlo como para rechazarlo en la etapa de revisión.
    

## 5. Condiciones transversales

Estas condiciones forman parte de todos los requisitos aplicables.

### 5.1. Autorización y aislamiento

- El servidor verifica identidad, permisos y organización.
    
- La organización se obtiene del contexto autenticado; no se confía en un identificador empresarial enviado por el cliente.
    
- Conocer un identificador o recibir un enlace no concede acceso.
    
- Los cambios de estado o permisos deben afectar las solicitudes posteriores.
    
- Una empresa suspendida no puede seguir operando mediante sesiones previamente abiertas.
    

### 5.2. Integridad y concurrencia

- Las operaciones comprueban las versiones requeridas.
    
- No se sobrescriben cambios ajenos silenciosamente.
    
- Las decisiones críticas se coordinan también frente a cambios en semanas adyacentes, políticas e indisponibilidades.
    
- Una validación previa no garantiza que una confirmación posterior siga siendo válida.
    
- Publicar o aprobar un intercambio no puede dejar resultados parciales.
    

### 5.3. Reintentos y resultado desconocido

Las escrituras de negocio siguen el contrato de idempotencia de la nota 10.

- Repetir la misma operación con la misma clave y contenido no duplica sus efectos.
    
- Reutilizar una clave con contenido diferente produce un conflicto.
    
- Antes de devolver un resultado anterior se comprueban los permisos actuales.
    
- Si una respuesta se pierde, se consulta el resultado o se reintenta con la misma clave.
    
- No encontrar la operación no demuestra que nunca se ejecutó.
    
- La interfaz no genera automáticamente otra clave para repetir una operación cuyo resultado sigue siendo desconocido.
    

La retención prevista del resultado es de 24 horas. No se promete recuperación indefinida mediante este mecanismo.

Las operaciones de autenticación y entrega de secretos tienen las excepciones definidas en la nota 10.

### 5.4. Tiempo y periodos

- El reloj del servidor determina inicio, vencimiento y vigencia.
    
- La zona horaria de la empresa determina la presentación y los límites locales de los periodos.
    
- Las semanas empiezan el lunes.
    
- Una asignación pertenece a la semana de su inicio local.
    
- Los intervalos se interpretan como inicio incluido y fin excluido.
    
- Las horas de un turno se distribuyen entre los periodos que realmente intersecta.
    
- No se deduce asistencia ni disponibilidad confirmada a partir del horario.
    

### 5.5. Confirmaciones y evidencia

Las confirmaciones de advertencias corresponden a la evaluación concreta presentada al usuario.

Cuando cambien los datos relevantes, la previsualización debe renovarse.

El sistema conserva la decisión, su actor, motivo cuando corresponda, política utilizada y advertencias aceptadas. No confía en horas o umbrales enviados por el navegador.

### 5.6. Auditoría y avisos

- La auditoría crítica se registra junto con la operación.
    
- Si falla ese registro, la operación crítica no se confirma.
    
- Los eventos necesarios para generar avisos se guardan dentro de la misma transacción.
    
- La entrega de avisos puede completarse después.
    
- Un fallo de entrega no repite ni revierte una publicación o un intercambio ya confirmado.
    

# 6. Requisitos funcionales

## 6.1. Empresas y servicio

### RF-001 — Incorporar una empresa

**Actor:** operador.

**Descripción:** incorporar una organización mediante un procedimiento controlado.

**Criterios de aceptación:**

1. Se registran los datos mínimos de la empresa, sede, zona horaria y capacidad del servicio.
    
2. El MVP admite una sede activa por organización.
    
3. Se habilita al primer administrador mediante un mecanismo seguro de configuración inicial de contraseña.
    
4. No se entregan contraseñas predeterminadas compartidas.
    
5. El alta no deja una empresa parcialmente creada ante un fallo transaccional.
    
6. Se registra la intervención del operador.
    
7. Se informa qué configuración operativa falta antes de publicar.
    
8. Incorporar una empresa no genera automáticamente una planificación.
    

**Capacidad:** MVP-01.

### RF-002 — Gestionar el estado y límites del servicio

**Actor:** operador.

**Descripción:** activar, suspender, reactivar y ajustar la capacidad contratada o habilitada.

**Criterios de aceptación:**

1. Cada cambio registra actor y motivo.
    
2. La suspensión bloquea las solicitudes empresariales posteriores, incluidas las realizadas desde sesiones abiertas.
    
3. Suspender no elimina publicaciones, colaboradores ni historial.
    
4. Reactivar no activa automáticamente cuentas desactivadas.
    
5. Reactivar no devuelve vigencia a solicitudes vencidas o invalidadas.
    
6. Reducir una capacidad no elimina recursos existentes para forzar su cumplimiento.
    
7. Los límites se comprueban en las operaciones afectadas.
    
8. El operador no adquiere permisos para aprobar intercambios empresariales.
    

**Capacidad:** MVP-01.

### RF-003 — Gestionar la salida de una empresa

**Actores:** operador y representante autorizado.

**Descripción:** ejecutar un cierre asistido con entrega y tratamiento controlado de datos.

**Criterios de aceptación:**

1. Se verifica la autoridad del solicitante.
    
2. Se acuerdan alcance, fecha de cierre y canal de entrega.
    
3. La exportación incluye únicamente información de la empresa autorizada.
    
4. Se comprueba la entrega antes de dar por finalizado el procedimiento.
    
5. El cierre de acceso se diferencia de la eliminación de información.
    
6. La conservación o eliminación sigue una política previamente definida.
    
7. Se registra evidencia administrativa del procedimiento.
    
8. Los CSV de reportes no se presentan como una exportación completa de la empresa.
    

No se exige un portal de baja ni una eliminación automática para el MVP.

**Capacidad:** MVP-01.

## 6.2. Identidad y datos operativos

### RF-004 — Iniciar y finalizar sesión

**Actor:** usuario empresarial.

**Descripción:** acceder mediante una sesión gestionada en servidor.

**Criterios de aceptación:**

1. El servidor verifica credenciales, estado de la cuenta y estado de la organización.
    
2. Una cuenta sin contraseña configurada no puede iniciar sesión.
    
3. La sesión establece el contexto empresarial y los permisos efectivos.
    
4. Ocultar opciones en la interfaz no sustituye los controles del servidor.
    
5. Cerrar sesión invalida la sesión correspondiente en el servidor.
    
6. Se aplican los vencimientos de sesión definidos en arquitectura y seguridad.
    
7. La consulta automática de avisos no debe mantener indefinidamente una sesión sin actividad real.
    
8. Los errores de acceso no revelan contraseñas ni detalles innecesarios sobre cuentas.
    

**Capacidad:** MVP-02.

### RF-005 — Cambiar contraseña y recuperar acceso

**Actores:** usuario y responsable autorizado del procedimiento asistido.

**Descripción:** permitir el cambio de contraseña y la recuperación segura de una cuenta.

**Criterios de aceptación:**

1. Se distingue la configuración inicial de contraseña de la recuperación posterior.
    
2. Los enlaces temporales tienen vencimiento y un solo uso.
    
3. Un enlace vencido, consumido o inválido no permite establecer una contraseña.
    
4. La recuperación asistida verifica la identidad mediante el procedimiento definido.
    
5. Las contraseñas y los secretos de recuperación no aparecen en auditoría, registros técnicos ni resultados genéricos de idempotencia.
    
6. Se aplica la invalidación de sesiones prevista para el cambio o recuperación.
    
7. La operación queda auditada sin almacenar el secreto.
    
8. La pérdida de la respuesta que entrega un enlace se trata mediante su procedimiento específico, no recuperando el secreto desde el registro general de operaciones.
    

**Capacidad:** MVP-02.

### RF-006 — Administrar usuarios y roles predefinidos

**Actor:** administrador.

**Descripción:** crear y administrar las cuentas de su organización.

**Criterios de aceptación:**

1. Puede consultar, crear, modificar, activar y desactivar usuarios según los campos permitidos.
    
2. Solo puede asignar roles predefinidos.
    
3. No puede dejar a la empresa sin administrador activo.
    
4. La vinculación con colaboradores es de la misma organización y respeta la relación uno a uno.
    
5. Cambiar una vinculación se bloquea cuando existen solicitudes pendientes que lo impiden.
    
6. Las solicitudes no se transfieren a otra persona al cambiar una vinculación.
    
7. Desactivar una cuenta impide su acceso e invalida las solicitudes pendientes afectadas.
    
8. Desactivar una cuenta no desactiva automáticamente al colaborador.
    
9. Las modificaciones comprueban versiones y se auditan.
    

**Capacidad:** MVP-02.

### RF-007 — Gestionar sede, puestos y tipos de turno

**Actor:** administrador; supervisor con acceso de consulta autorizado.

**Descripción:** mantener la configuración y los catálogos utilizados para planificar.

**Criterios de aceptación:**

1. Se mantiene una sede activa por empresa.
    
2. Los puestos y tipos de turno pertenecen a esa empresa.
    
3. No se eliminan referencias históricas necesarias para interpretar publicaciones.
    
4. Modificar una plantilla de turno no modifica asignaciones existentes.
    
5. Usar una plantilla para crear una asignación no establece una actualización automática posterior.
    
6. Antes de la primera publicación, cambiar la zona horaria requiere revisar su impacto.
    
7. El cambio de zona conserva los instantes existentes y se bloquea si deja líneas de borrador fuera de la semana a la que pertenecen.
    
8. Después de la primera publicación, la zona horaria queda bloqueada en el MVP, incluso si la publicación fue vacía.
    
9. El supervisor no modifica esta configuración.
    

**Capacidad:** MVP-03.

### RF-008 — Gestionar colaboradores y vinculación de cuentas

**Actor:** administrador.

**Descripción:** administrar los colaboradores planificables y su puesto principal.

**Criterios de aceptación:**

1. Un colaborador puede existir y recibir asignaciones sin tener cuenta.
    
2. Las funciones personales requieren una cuenta vinculada y un colaborador activo.
    
3. Un colaborador tiene un puesto principal según el modelo del MVP.
    
4. No se permite una vinculación duplicada o entre organizaciones.
    
5. La desactivación se bloquea si existen asignaciones publicadas no finalizadas, solicitudes pendientes o asignaciones en borradores abiertos que lo impidan.
    
6. El cambio de puesto se bloquea cuando deja compromisos publicados, borradores o intercambios incompatibles.
    
7. La previsualización de desactivación informa los bloqueos; la confirmación vuelve a comprobarlos.
    
8. Los cambios no reescriben los datos históricos de publicaciones anteriores.
    

**Capacidad:** MVP-03.

### RF-009 — Gestionar indisponibilidades

**Actores:** administrador o supervisor.

**Descripción:** registrar, modificar y cancelar intervalos de indisponibilidad.

**Criterios de aceptación:**

1. El intervalo debe ser válido y pertenecer a un colaborador de la organización.
    
2. Crear o modificar una indisponibilidad se bloquea si intersecta una asignación publicada.
    
3. La comprobación se coordina con publicaciones e intercambios concurrentes.
    
4. No se utiliza para modificar retroactivamente periodos iniciados.
    
5. Los motivos tienen acceso restringido y no se muestran a compañeros al buscar intercambios.
    
6. Su efecto se considera en validaciones posteriores.
    
7. La ausencia de registros no se presenta como disponibilidad confirmada.
    
8. Los cambios y cancelaciones quedan trazados.
    

El MVP no incluye solicitudes personales de vacaciones ni un flujo adicional de aprobación de ausencias.

**Capacidad:** MVP-03.

### RF-010 — Configurar políticas de planificación

**Actor:** administrador.

**Descripción:** establecer las políticas de descanso mínimo y advertencia de horas semanales.

**Criterios de aceptación:**

1. La política activa se consulta desde planificación y validación.
    
2. Cada activación crea una versión inmutable y tiene efecto inmediato.
    
3. No se programa una activación futura en el MVP.
    
4. Antes de activar se evalúa el efecto sobre la planificación publicada futura.
    
5. Un nuevo mínimo de descanso que introduce conflictos bloquea la activación.
    
6. La evaluación incluye asignaciones adyacentes relevantes, aunque alguna ya haya comenzado.
    
7. Las advertencias permitidas requieren aceptación específica y motivo.
    
8. La confirmación vuelve a comprobar la política base y el contexto evaluado.
    
9. Activar una política no altera publicaciones históricas.
    
10. Los borradores e intercambios se revalidan con la política vigente al confirmarse.
    

Los valores reales deben acordarse para el piloto. No se presentan como certificación de cumplimiento laboral.

**Capacidad:** MVP-05.

## 6.3. Planificación y publicación

### RF-011 — Crear, consultar y descartar borradores

**Actores:** administrador o supervisor.

**Descripción:** preparar una planificación semanal sin alterar el horario oficial.

**Criterios de aceptación:**

1. Existe como máximo un borrador abierto por semana y ámbito de planificación.
    
2. Puede crearse para una semana sin publicación o desde su publicación vigente.
    
3. El borrador registra su publicación base cuando existe.
    
4. Consultar una semana inexistente no crea registros.
    
5. Descartar un borrador no elimina ni cambia una publicación.
    
6. Si cambia la publicación vigente, el borrador anterior se identifica como desactualizado.
    
7. No se fusionan cambios automáticamente.
    
8. Reiniciar o reconstruir el borrador requiere una acción explícita que advierta sobre el trabajo que no se conservará.
    

**Capacidad:** MVP-04.

### RF-012 — Gestionar asignaciones del borrador

**Actores:** administrador o supervisor.

**Descripción:** añadir, modificar y retirar líneas mediante guardado explícito.

**Criterios de aceptación:**

1. Se distingue entre cambios locales sin guardar, borrador guardado, validación y publicación.
    
2. Cada escritura comprueba y actualiza la versión del borrador.
    
3. Se rechazan datos estructuralmente inválidos, referencias ajenas e intervalos incorrectos.
    
4. Los conflictos operativos que la nota 04 permite mantener en borrador se muestran como pendientes; no se confunden con una publicación válida.
    
5. Un guardado exitoso no implica que el borrador pueda publicarse.
    
6. Editar el contenido invalida la evaluación anterior.
    
7. Las asignaciones iniciadas se conservan sin modificación.
    
8. Se admite cruce de medianoche sin trasladar silenciosamente la identidad de una asignación a otra semana.
    
9. Las líneas nuevas tienen identidad de borrador independiente de la asignación oficial que se creará al publicar.
    
10. Las fechas de turnos e indisponibilidades respetan la precisión de minutos establecida en el contrato.
    

**Capacidad:** MVP-04.

### RF-013 — Validar una planificación

**Actores:** administrador o supervisor.

**Descripción:** evaluar si el borrador puede convertirse en horario oficial.

**Criterios de aceptación:**

1. Se identifican bloqueos y advertencias por separado.
    
2. Los mensajes indican el colaborador, intervalo o condición relevante.
    
3. Se verifican actividad, puesto, sede, intervalos, indisponibilidad, solapamiento y descanso.
    
4. La simulación sustituye la publicación de la semana evaluada; no suma ambas como si fueran turnos distintos.
    
5. Se consideran las publicaciones de periodos adyacentes.
    
6. Los borradores de otras semanas no se tratan como horarios oficiales.
    
7. Las horas se distribuyen entre periodos antes de evaluar umbrales.
    
8. Se conservan las asignaciones iniciadas que deban permanecer.
    
9. Una evaluación puede completarse correctamente y concluir que no se puede publicar.
    
10. La confirmación final repite las comprobaciones necesarias.
    

**Capacidad:** MVP-05.

### RF-014 — Publicar una planificación

**Actores:** administrador o supervisor.

**Descripción:** convertir un borrador válido en una publicación oficial completa.

**Criterios de aceptación:**

1. Se comprueban autorización, configuración, versión del borrador y publicación base.
    
2. La previsualización muestra cambios, bloqueos, advertencias e intercambios afectados.
    
3. El usuario confirma exactamente la evaluación presentada.
    
4. Las advertencias requieren aceptación individual y motivo; no existe una autorización genérica para ignorarlas.
    
5. Se admite una publicación vacía únicamente con confirmación explícita y motivo.
    
6. Una publicación vacía no puede retirar asignaciones iniciadas que deban conservarse.
    
7. Una publicación inicial ordinaria sin advertencias no exige motivo; las revisiones sí.
    
8. Se registra la decisión y su evidencia junto con la nueva publicación.
    
9. Publicación, cierre del borrador, invalidaciones, liberación de reservas, auditoría y eventos de aviso se confirman atómicamente.
    
10. Un fallo no deja una parte del horario publicada.
    
11. Un reintento de la misma operación no crea otra versión.
    

**Capacidad:** MVP-06.

### RF-015 — Revisar una planificación publicada

**Actores:** administrador o supervisor.

**Descripción:** modificar el horario oficial mediante un nuevo borrador y una nueva publicación.

**Criterios de aceptación:**

1. La publicación actual permanece vigente durante la edición.
    
2. No se editan directamente publicaciones históricas.
    
3. Las asignaciones iniciadas no se modifican ni retiran.
    
4. La revisión requiere motivo.
    
5. La previsualización muestra las solicitudes que quedarían invalidadas.
    
6. Cambiar una revisión referenciada por un intercambio invalida la solicitud afectada.
    
7. Un cambio no relacionado dentro de la misma semana no invalida automáticamente todos los intercambios.
    
8. Si cambia la publicación base, el borrador no puede publicarse sin resolver su desactualización.
    
9. Se conserva el historial de publicaciones y sus decisiones.
    

**Capacidad:** MVP-06.

### RF-016 — Consultar planificación y horario personal

**Actor:** usuario empresarial según permisos.

**Descripción:** consultar el horario oficial y las referencias de vigencia.

**Criterios de aceptación:**

1. Administradores y supervisores pueden consultar la planificación empresarial autorizada.
    
2. Las funciones personales muestran únicamente asignaciones publicadas del colaborador vinculado.
    
3. Los borradores no aparecen como horario oficial del colaborador.
    
4. Se muestran fechas, horas y zona horaria comprensibles.
    
5. Se distingue entre semana no publicada, semana publicada vacía y semana publicada sin asignaciones propias.
    
6. No tener un colaborador activo vinculado produce un estado de acceso personal no disponible; no se disfraza como agenda vacía.
    
7. La consulta del próximo turno puede indicar legítimamente que no existe uno.
    
8. Tras un intercambio, el detalle personal respeta al propietario actual de la asignación.
    
9. El historial de una solicitud sigue siendo consultable por sus participantes autorizados aunque haya cambiado la asignación.
    

**Capacidad:** MVP-06.

## 6.4. Intercambios

### RF-017 — Consultar oportunidades y solicitar un intercambio

**Actor:** usuario con colaborador activo vinculado.

**Descripción:** proponer el intercambio de dos asignaciones publicadas futuras.

**Criterios de aceptación:**

1. El solicitante selecciona una asignación propia.
    
2. La contraparte es otra persona elegible de la misma empresa, sede y puesto compatible.
    
3. Ambos participantes necesitan cuentas y colaboradores activos.
    
4. La búsqueda muestra solo los datos necesarios para elegir una oportunidad.
    
5. Consultar candidatos no reserva turnos.
    
6. Registrar la solicitud comprueba las revisiones vigentes de ambas asignaciones.
    
7. Cada solicitud pendiente reserva exactamente sus dos asignaciones.
    
8. Una asignación no puede intervenir en otra solicitud pendiente, independientemente del lado que ocupe.
    
9. La solicitud nace en `PENDIENTE_COMPANERO`.
    
10. Crear la solicitud no cambia el horario publicado.
    

La consulta de oportunidades usa un periodo acotado conforme a la nota 10.

**Capacidad:** MVP-07.

### RF-018 — Responder o cancelar una solicitud

**Actores:** destinatario o solicitante, según la acción.

**Descripción:** permitir la respuesta del compañero y la cancelación por el solicitante.

**Criterios de aceptación:**

1. Solo el destinatario puede aceptar o rechazar desde `PENDIENTE_COMPANERO`.
    
2. Aceptar produce `PENDIENTE_SUPERVISOR`.
    
3. Aceptar no reasigna turnos ni libera sus reservas.
    
4. Solo el solicitante puede cancelar su solicitud mientras esté pendiente.
    
5. El motivo del rechazo del compañero o de la cancelación es opcional.
    
6. Cada transición comprueba estado, versión, actor y vigencia.
    
7. Rechazar o cancelar libera ambas reservas.
    
8. Una solicitud terminal no vuelve a abrirse.
    
9. Las acciones repetidas o concurrentes no generan transiciones duplicadas.
    

**Capacidad:** MVP-07.

### RF-019 — Resolver un intercambio

**Actor:** administrador o supervisor independiente de los participantes.

**Descripción:** aprobar o rechazar una solicitud aceptada por el compañero.

**Criterios de aceptación:**

1. La solicitud debe estar en `PENDIENTE_SUPERVISOR` y conservar su vigencia.
    
2. Un participante no puede aprobarla ni rechazarla en calidad de responsable.
    
3. La aprobación requiere una previsualización del resultado.
    
4. La simulación retira ambas asignaciones originales antes de evaluar las reasignaciones.
    
5. Se comprueban participantes, revisiones, reservas, puestos, horarios, indisponibilidades, descanso y política vigente.
    
6. Se consideran todas las semanas afectadas y sus límites adyacentes.
    
7. Las advertencias permitidas requieren aceptación específica y motivo.
    
8. La confirmación vuelve a validar el contexto de la previsualización.
    
9. Se intercambian los colaboradores, no las fechas ni la duración de los turnos.
    
10. Ambas reasignaciones, publicaciones afectadas, estado, liberación de reservas, auditoría y eventos de aviso se confirman en una sola transacción.
    
11. Si participan dos semanas, sus publicaciones cambian conjuntamente.
    
12. Los borradores cuya base cambió quedan desactualizados.
    
13. Rechazar exige motivo y no cambia el horario.
    
14. Aprobar no requiere una segunda publicación manual.
    

**Capacidad:** MVP-07.

### RF-020 — Gestionar vigencia e historial de solicitudes

**Actores:** sistema y usuarios autorizados.

**Descripción:** impedir operaciones sobre solicitudes vencidas o incompatibles y conservar su trazabilidad.

**Criterios de aceptación:**

1. La solicitud vence al comenzar el primero de sus dos turnos.
    
2. El vencimiento se aplica aunque la tarea de mantenimiento aún no haya actualizado el estado persistido.
    
3. Una consulta puede presentar la vigencia efectiva sin realizar una escritura de negocio.
    
4. Los cambios incompatibles producen invalidación conforme a la nota 04.
    
5. Reactivar una cuenta o empresa no reabre solicitudes terminales.
    
6. Las transiciones terminales liberan ambas reservas al persistirse.
    
7. Se conserva el historial de estados, actores, fechas y motivos correspondientes.
    
8. Los participantes y responsables solo consultan información autorizada.
    
9. Una acción rechazada puede registrar que la solicitud venció o se invalidó; eso no significa que se haya aprobado el intercambio.
    

**Estados:**

- `PENDIENTE_COMPANERO`
    
- `PENDIENTE_SUPERVISOR`
    
- `APROBADA`
    
- `RECHAZADA`
    
- `CANCELADA`
    
- `VENCIDA`
    
- `INVALIDADA`
    

**Capacidad:** MVP-07.

## 6.5. Reportes, avisos y auditoría

### RF-021 — Consultar reportes de programación

**Actores:** administrador o supervisor.

**Descripción:** consultar horas programadas e intercambios dentro de un periodo acotado.

**Criterios de aceptación:**

1. Los reportes operativos de horas usan las publicaciones vigentes.
    
2. El historial de publicaciones se consulta por separado; no se incorpora un constructor de reportes históricos.
    
3. Los intervalos se distribuyen entre los periodos que intersectan.
    
4. Se calculan horas programadas, no asistencia, pagos ni horas realmente trabajadas.
    
5. El reporte de intercambios selecciona solicitudes creadas en el periodo y muestra su estado efectivo al generar la consulta.
    
6. Los filtros y recursos pertenecen a la organización autorizada.
    
7. El periodo máximo es de 92 días, según la nota 10.
    
8. Se incluyen periodo, zona horaria, fecha de generación y referencias de origen.
    
9. La respuesta incluye una huella de los datos fuente para comprobar una exportación posterior.
    
10. Los resúmenes del panel representan datos reales del sistema, no ahorros ni rentabilidad inventados.
    

**Capacidad:** MVP-08.

### RF-022 — Exportar reportes CSV

**Actores:** administrador o supervisor.

**Descripción:** exportar el reporte consultado manteniendo filtros y coherencia de datos.

**Criterios de aceptación:**

1. La exportación aplica los mismos permisos, filtros y periodo de la consulta.
    
2. El cliente envía la huella de origen del reporte revisado.
    
3. Si los datos relevantes cambiaron, se solicita actualizar el reporte antes de exportar.
    
4. La comprobación y la generación utilizan una lectura consistente.
    
5. El archivo identifica empresa, periodo, zona horaria, generación y significado de los datos.
    
6. Los campos se generan de forma segura para reducir riesgos al abrirlos en una hoja de cálculo.
    
7. No se incluyen secretos, motivos privados ni información ajena al reporte.
    
8. El CSV de horas se identifica como programación, no como registro de trabajo efectivo.
    

**Capacidad:** MVP-08.

### RF-023 — Consultar avisos internos

**Actor:** usuario destinatario.

**Descripción:** consultar avisos asociados a publicaciones e intercambios.

**Criterios de aceptación:**

1. Cada usuario accede únicamente a sus avisos.
    
2. Los eventos necesarios se registran junto con la operación que los origina.
    
3. Su entrega admite reintentos sin duplicar avisos equivalentes para el destinatario.
    
4. Un fallo de entrega no repite la operación de negocio.
    
5. El usuario puede marcar un aviso como leído.
    
6. Repetir esa acción conserva la primera fecha de lectura.
    
7. Leer un aviso no equivale a aceptar un intercambio ni confirmar recepción del horario.
    
8. Abrir su enlace vuelve a comprobar los permisos actuales.
    
9. Si el recurso ya no está disponible, se muestra un estado comprensible sin filtrar información.
    

No se incluyen correo automático, SMS, WhatsApp ni chat.

**Capacidad:** MVP-09.

### RF-024 — Consultar auditoría autorizada

**Actores:** administrador y supervisor dentro de su ámbito permitido.

**Descripción:** consultar los eventos de negocio relevantes para explicar cambios y decisiones.

**Criterios de aceptación:**

1. Se identifica el actor, fecha, operación y recurso afectado.
    
2. Se incluyen motivos y cambios relevantes según el tipo de evento.
    
3. Las decisiones de publicación, aprobación y activación de políticas conservan su evidencia.
    
4. Las advertencias aceptadas pueden relacionarse con la decisión correspondiente.
    
5. El supervisor no obtiene acceso indiscriminado a eventos administrativos.
    
6. No se exponen contraseñas, enlaces secretos ni contenido técnico sin filtrar.
    
7. La auditoría no admite edición o borrado mediante operaciones ordinarias de usuario.
    
8. Los eventos se diferencian de los registros técnicos de diagnóstico.
    

**Capacidad:** MVP-09.

# 7. Requisitos no funcionales

## RNF-001 — Aislamiento entre empresas

Ninguna operación debe leer, modificar o exportar información de otra organización sin autorización específica del procedimiento de plataforma.

**Verificación mínima:**

- Pruebas con al menos dos empresas.
    
- Manipulación de identificadores en consultas y escrituras.
    
- Acceso a publicaciones, borradores, intercambios, avisos y auditoría.
    
- Exportaciones y tareas internas.
    
- Intentos de recuperar resultados de operaciones de otro usuario o empresa.
    

## RNF-002 — Seguridad de credenciales y sesiones

Las credenciales y sesiones deben protegerse conforme a las decisiones de las notas 06 y 12.

**Verificación mínima:**

- Contraseñas no almacenadas en texto claro.
    
- Transporte cifrado en el entorno publicado.
    
- Sesiones revocables y vencimiento comprobado en servidor.
    
- Protección de solicitudes autenticadas contra falsificación.
    
- Limitación de intentos de acceso y recuperación.
    
- Enlaces de un solo uso y vencimiento efectivo.
    
- Ausencia de secretos en registros, auditoría y resultados recuperables.
    
- Suspensión y desactivación efectivas sobre sesiones existentes.
    

Los parámetros de seguridad deben quedar configurados y probados; no basta con nombrar una tecnología.

## RNF-003 — Consistencia transaccional

Las decisiones críticas se aplican íntegramente o no se aplican.

**Verificación mínima:**

- Fallo entre pasos de una publicación.
    
- Fallo entre las dos reasignaciones de un intercambio.
    
- Fallo al registrar auditoría o eventos de aviso.
    
- Activación de política concurrente con planificación.
    
- Reintento después de perder la respuesta.
    
- Persistencia consistente de la decisión y del resultado de la operación.
    

No deben existir publicaciones parciales, reservas huérfanas ni intercambios aprobados sin ambas reasignaciones.

## RNF-004 — Concurrencia

Las operaciones simultáneas no deben ocasionar pérdida silenciosa de cambios ni planificaciones incompatibles.

**Verificación mínima:**

- Dos ediciones del mismo borrador.
    
- Dos solicitudes que compiten por una asignación.
    
- Aprobación frente a cancelación.
    
- Publicaciones de semanas adyacentes.
    
- Intercambio frente a revisión.
    
- Indisponibilidad frente a publicación.
    
- Cambio de política frente a aprobación.
    
- Confirmación de una previsualización desactualizada.
    

Los conflictos deben producir respuestas controladas y una vía clara para recargar y revisar.

## RNF-005 — Exactitud temporal

Los cálculos deben utilizar instantes y límites de periodo consistentes.

**Verificación mínima:**

- Cruce de medianoche y de semana.
    
- Turnos que intersectan el inicio o fin de un reporte.
    
- Intervalos adyacentes sin solapamiento.
    
- Descanso exactamente igual al mínimo.
    
- Vencimiento exactamente al iniciar un turno.
    
- Horas locales ambiguas o inexistentes cuando correspondan.
    
- Cambio de zona antes de la primera publicación.
    
- Bloqueo del cambio de zona después de publicar.
    
- Rechazo de precisión temporal no admitida por el contrato.
    

Los cálculos no dependen del reloj del navegador ni redondean silenciosamente datos inválidos.

## RNF-006 — Rendimiento inicial

Se adopta el escenario propuesto de la nota 12:

- Cinco organizaciones ficticias.
    
- Hasta 50 colaboradores por organización.
    
- Seis meses de historial representativo.
    
- Veinte sesiones concurrentes.
    
- Mezcla documentada de consultas y escrituras.
    
- Escenarios de competencia dentro de una misma organización.
    
- Reportes dentro del máximo de 92 días.
    

**Objetivos propuestos:**

|   |   |
|---|---|
|Operación|Objetivo|
|Consultas habituales de API|Percentil 95 menor de 2 segundos.|
|Guardados y comandos habituales|Percentil 95 menor de 5 segundos.|
|Reporte acotado|Menor de 10 segundos en el escenario ensayado.|
|Operaciones concurrentes|Sin pérdida de integridad ni efectos duplicados.|

Antes de medir deben fijarse infraestructura, volumen exacto, duración de carga, distribución de operaciones y punto de medición.

Estos valores son objetivos de prueba, no un SLA ni capacidad comercial demostrada.

## RNF-007 — Recuperación de información

Antes de incorporar datos reales deben existir respaldos y una restauración comprobada.

**Objetivos propuestos, alineados con la nota 12:**

|   |   |
|---|---|
|Objetivo|Valor|
|RPO: pérdida máxima de datos buscada|Hasta 4 horas.|
|RTO: recuperación del servicio buscada|Hasta 8 horas desde la interrupción.|

**Verificación mínima:**

- Disponibilidad y antigüedad de copias recuperables.
    
- Restauración real, no solo validación del archivo.
    
- Acceso a las claves y recursos necesarios.
    
- Integridad de publicaciones, solicitudes y reservas.
    
- Invalidación de sesiones y enlaces restaurados.
    
- Revisión de permisos y suspensiones posteriores al respaldo.
    
- Conciliación de avisos y operaciones cuyo resultado pudo perderse.
    
- Registro del tiempo completo de recuperación.
    

Restaurar una base compartida afecta a todas las empresas contenidas en ella.

Los objetivos requieren aceptación según presupuesto, infraestructura y capacidad humana de atención.

## RNF-008 — Usabilidad y acceso móvil

Los procesos principales deben ser utilizables en escritorio y móvil.

**Verificación mínima:**

- Anchos de referencia de 360, 768 y 1280 píxeles.
    
- Navegación por teclado y foco visible.
    
- Etiquetas y errores comprensibles.
    
- Información que no dependa exclusivamente del color.
    
- Alternativa de agenda para consultar horarios en móvil.
    
- Distinción entre guardado, validado y publicado.
    
- Confirmaciones comprensibles para advertencias y publicaciones vacías.
    
- Recuperación clara ante versiones desactualizadas y respuestas perdidas.
    
- Pruebas con usuarios representativos.
    

No se declara certificación de accesibilidad sin una evaluación específica.

## RNF-009 — Manejo de errores

Los errores deben explicar el problema y el siguiente paso sin exponer información interna.

**Escenarios mínimos:**

- Sesión vencida.
    
- Permiso insuficiente.
    
- Recurso no accesible.
    
- Acceso personal no disponible.
    
- Conflicto de versión.
    
- Previsualización desactualizada.
    
- Bloqueo de negocio.
    
- Advertencias no confirmadas correctamente.
    
- Resultado desconocido después de un problema de conexión.
    
- Datos del reporte modificados antes de exportar.
    
- Fallo temporal del servicio.
    

La interfaz distingue un rechazo confirmado de una operación cuyo resultado todavía no se conoce.

No se muestran consultas SQL, trazas internas ni secretos.

## RNF-010 — Observabilidad

Debe ser posible investigar fallos sin acceder indiscriminadamente a información personal.

**Verificación mínima:**

- Identificadores de correlación.
    
- Registro de fallos de tareas internas.
    
- Comprobación de salud del servicio.
    
- Detección de respaldos fallidos.
    
- Visibilidad de avisos pendientes y reintentos.
    
- Diagnóstico de errores transaccionales y conflictos.
    
- Separación entre auditoría de negocio y registros técnicos.
    
- Ausencia de secretos en los registros.
    

## RNF-011 — Mantenibilidad

Las reglas de negocio deben probarse sin depender exclusivamente de interacción manual.

**Verificación mínima:**

- Pruebas automatizadas de reglas, estados y permisos.
    
- Pruebas de integración sobre restricciones y transacciones.
    
- Cambios del esquema versionados.
    
- Contrato API verificable.
    
- Dependencias entre módulos documentadas.
    
- Ausencia de reglas críticas duplicadas y contradictorias.
    
- Instrucciones reproducibles para ejecutar las pruebas.
    

La cobertura porcentual no sustituye la lista de escenarios críticos.

## RNF-012 — Despliegue y configuración

El proyecto debe poder instalarse en un entorno limpio siguiendo instrucciones versionadas.

**Verificación mínima:**

- Configuración externa al código.
    
- Secretos fuera del repositorio.
    
- Creación o actualización controlada del esquema.
    
- Comprobación posterior al despliegue.
    
- Procedimiento para un despliegue fallido.
    
- Separación entre demostración y producción.
    
- Respaldo previo cuando el cambio lo requiera.
    
- Validación de compatibilidad entre aplicación y datos.
    

Retroceder la aplicación no implica que sea seguro revertir automáticamente la base de datos.

La selección técnica corresponde a la nota 06.

## RNF-013 — Privacidad y conservación

Solo se recopila la información necesaria para el alcance.

**Verificación mínima:**

- Inventario de datos y finalidad.
    
- Acceso restringido a motivos de indisponibilidad.
    
- Proyecciones mínimas para oportunidades de intercambio.
    
- Exportaciones con autorización y alcance definido.
    
- Procedimiento de soporte delimitado.
    
- Política de conservación, cierre y eliminación.
    
- Tratamiento definido para respaldos y datos históricos.
    
- Separación entre información ficticia y real.
    

Las condiciones aplicables deben resolverse antes de incorporar datos reales. No se inventan plazos legales desde esta especificación.

# 8. Catálogo de casos de uso

|   |   |   |   |
|---|---|---|---|
|Caso|Objetivo|Actor principal|Requisitos|
|CU-01|Incorporar y administrar una empresa|Operador|RF-001, RF-002|
|CU-02|Acceder y recuperar acceso|Usuario / responsable autorizado|RF-004, RF-005|
|CU-03|Administrar usuarios|Administrador|RF-006|
|CU-04|Preparar datos operativos|Administrador / supervisor según acción|RF-007 a RF-010|
|CU-05|Preparar un borrador semanal|Administrador o supervisor|RF-011 a RF-013|
|CU-06|Publicar una planificación|Administrador o supervisor|RF-013, RF-014|
|CU-07|Revisar un horario publicado|Administrador o supervisor|RF-011 a RF-015|
|CU-08|Consultar el horario|Usuario empresarial|RF-016|
|CU-09|Solicitar un intercambio|Usuario con colaborador activo vinculado|RF-017, RF-020|
|CU-10|Responder o cancelar una solicitud|Participante correspondiente|RF-018, RF-020|
|CU-11|Resolver un intercambio|Administrador o supervisor independiente|RF-019, RF-020|
|CU-12|Consultar y exportar reportes|Administrador o supervisor|RF-021, RF-022|
|CU-13|Consultar avisos y trazabilidad|Usuario según permiso|RF-023, RF-024|
|CU-14|Cerrar el servicio y entregar datos|Operador y representante autorizado|RF-003|

Los casos agrupan operaciones relacionadas. El backlog puede descomponerlos sin ampliar su alcance.

# 9. Especificación de casos de uso

## CU-01 — Incorporar y administrar una empresa

**Actor:** operador.

**Precondiciones:** operador autorizado y condiciones iniciales identificadas.

**Flujo principal:**

1. Registra los datos mínimos de la empresa.
    
2. Configura sede, zona horaria y capacidad.
    
3. Habilita al primer administrador.
    
4. El sistema valida y registra el alta.
    
5. Se entrega el mecanismo seguro de acceso inicial.
    
6. Se informa la configuración pendiente.
    

**Variantes:** suspensión, reactivación y ajuste de capacidad con motivo.

**Excepciones:** datos inválidos, capacidad incoherente, fallo del alta o acción no autorizada.

**Resultado:** empresa incorporada o servicio actualizado con trazabilidad, sin eliminar información ni conceder permisos empresariales al operador.

## CU-02 — Acceder y recuperar acceso

**Actores:** usuario y responsable autorizado cuando corresponda.

**Flujo de acceso:**

1. El usuario presenta credenciales.
    
2. El servidor valida cuenta, organización y contraseña.
    
3. Establece una sesión con permisos efectivos.
    
4. La interfaz muestra las funciones autorizadas.
    

**Flujo de recuperación:**

1. Se aplica la verificación de identidad del procedimiento.
    
2. El responsable habilita un enlace temporal cuando corresponde.
    
3. El usuario establece una nueva contraseña.
    
4. El sistema consume el enlace, aplica la política de sesiones y registra el evento.
    

**Variantes:** configuración inicial, cambio de contraseña autenticado y cierre de sesión.

**Excepciones:** credenciales inválidas, cuenta inactiva, organización suspendida, enlace vencido o consumido y verificación insuficiente.

**Resultado:** acceso autorizado o rechazo controlado sin exposición de secretos.

## CU-03 — Administrar usuarios

**Actor:** administrador.

**Precondiciones:** empresa habilitada y permisos actuales.

**Flujo principal:**

1. Consulta usuarios.
    
2. Crea o modifica una cuenta.
    
3. Asigna un rol predefinido.
    
4. Vincula un colaborador cuando corresponde.
    
5. El sistema comprueba pertenencia, versión y restricciones.
    
6. Confirma y audita.
    

**Variantes:** activar o desactivar una cuenta.

**Excepciones:** vinculación duplicada, solicitudes pendientes que impiden cambiarla, recurso ajeno, versión desactualizada o pérdida del último administrador activo.

**Resultado:** cuenta actualizada sin transferir solicitudes ni romper el acceso administrativo.

## CU-04 — Preparar datos operativos

**Actores:** administrador; supervisor para consultas e indisponibilidades autorizadas.

**Flujo principal:**

1. El administrador configura sede y catálogos.
    
2. Registra colaboradores y puestos.
    
3. Vincula cuentas cuando se necesiten funciones personales.
    
4. El responsable registra indisponibilidades.
    
5. El administrador previsualiza una política.
    
6. Revisa bloqueos y advertencias.
    
7. Confirma la activación válida.
    
8. El sistema conserva su versión y evidencia.
    

**Variantes:** modificar catálogos, cambiar zona antes de publicar o previsualizar la desactivación de un colaborador.

**Excepciones:** compromisos incompatibles, indisponibilidad contra un turno publicado, nueva política con bloqueos, cambio de zona prohibido o evaluación desactualizada.

**Resultado:** datos operativos consistentes, sin alterar publicaciones históricas.

## CU-05 — Preparar un borrador semanal

**Actor:** administrador o supervisor.

**Precondiciones:** acceso autorizado a planificación.

**Flujo principal:**

1. Selecciona la semana.
    
2. Crea o abre su borrador.
    
3. Añade, modifica o retira asignaciones permitidas.
    
4. Guarda explícitamente los cambios.
    
5. El sistema comprueba datos y versión.
    
6. Solicita la validación operativa.
    
7. Revisa bloqueos y advertencias.
    
8. Corrige y vuelve a guardar cuando sea necesario.
    

**Variantes:** descartar el borrador o resolver su desactualización mediante una acción explícita.

**Excepciones:** datos inválidos, asignación iniciada, conflicto concurrente o publicación base modificada.

**Resultado:** borrador persistido y separado del horario oficial. Puede seguir teniendo conflictos pendientes de resolver.

## CU-06 — Publicar una planificación

**Actor:** administrador o supervisor.

**Precondiciones:** borrador abierto, base vigente y configuración obligatoria completa.

**Flujo principal:**

1. Solicita la previsualización.
    
2. El sistema evalúa el contexto actual.
    
3. Presenta cambios, bloqueos, advertencias e intercambios afectados.
    
4. El responsable revisa el resultado.
    
5. Acepta las advertencias concretas, si existen.
    
6. Confirma el horario vacío cuando corresponda.
    
7. Introduce el motivo exigido por la operación.
    
8. Envía la confirmación con las versiones y la referencia de evaluación.
    
9. El sistema revalida y publica atómicamente.
    
10. La interfaz muestra la nueva versión vigente.
    

**Excepciones:** bloqueo, confirmación incompleta, advertencias diferentes, base desactualizada, turno iniciado durante el proceso o fallo transaccional.

**Respuesta perdida:** se recupera el resultado o se reintenta con la misma clave; no se publica otra versión por iniciativa automática.

**Resultado:** publicación completa con evidencia, o ausencia de una nueva publicación parcial.

## CU-07 — Revisar un horario publicado

**Actor:** administrador o supervisor.

**Precondiciones:** publicación existente.

**Flujo principal:**

1. Crea un borrador desde la publicación vigente.
    
2. Modifica las asignaciones futuras permitidas.
    
3. Guarda los cambios.
    
4. Solicita la previsualización.
    
5. Revisa los intercambios afectados.
    
6. Registra el motivo.
    
7. Ejecuta la confirmación de CU-06.
    

**Excepciones:** turno iniciado, intercambio que cambió la publicación base, otro responsable que publicó primero o nuevas incompatibilidades.

**Resultado:** nueva publicación con historial preservado e invalidaciones consistentes, o rechazo de la revisión sin aplicarla parcialmente.

## CU-08 — Consultar el horario

**Actor:** usuario empresarial.

**Flujo principal:**

1. Accede a planificación empresarial o a su horario personal.
    
2. Selecciona el periodo.
    
3. El sistema comprueba el ámbito autorizado.
    
4. Obtiene la publicación vigente.
    
5. Muestra asignaciones, zona horaria y referencia de vigencia.
    
6. Permite consultar detalles autorizados.
    

**Estados alternativos:**

- Semana sin publicación.
    
- Semana publicada vacía.
    
- Semana publicada sin turnos propios.
    
- Sin próximo turno.
    
- Funciones personales no disponibles por falta de vinculación activa.
    

**Excepciones:** sesión vencida, empresa suspendida o recurso no accesible.

**Resultado:** consulta del horario oficial sin exposición de borradores ni datos personales innecesarios.

## CU-09 — Solicitar un intercambio

**Actor:** usuario con colaborador activo vinculado.

**Precondiciones:** asignación propia publicada y futura.

**Flujo principal:**

1. Selecciona su asignación.
    
2. Consulta oportunidades en un periodo acotado.
    
3. Revisa la información mínima de un turno candidato.
    
4. Envía la solicitud con las referencias de ambas asignaciones.
    
5. El sistema revalida elegibilidad y versiones.
    
6. Reserva ambos turnos.
    
7. Registra `PENDIENTE_COMPANERO` y el evento de aviso.
    

**Excepciones:** falta de candidatos, revisión modificada, reserva concurrente, cuenta inactiva o turno iniciado.

**Resultado:** solicitud pendiente; el horario oficial no cambia.

## CU-10 — Responder o cancelar una solicitud

**Actores:** destinatario o solicitante, según acción.

**Flujo de respuesta:**

1. El destinatario consulta la solicitud.
    
2. Revisa ambos turnos.
    
3. Acepta o rechaza.
    
4. El sistema comprueba actor, estado, versión y vigencia.
    
5. Registra la transición y los eventos correspondientes.
    

**Flujo de cancelación:**

1. El solicitante abre una solicitud propia pendiente.
    
2. Confirma la cancelación.
    
3. El sistema revalida y registra `CANCELADA`.
    
4. Libera ambas reservas.
    

**Excepciones:** actor incorrecto, solicitud terminal, vencimiento, invalidación o acción concurrente.

**Resultado:** transición válida sin reasignación anticipada. Aceptar produce `PENDIENTE_SUPERVISOR`, no `APROBADA`.

## CU-11 — Resolver un intercambio

**Actor:** administrador o supervisor que no participa.

**Precondiciones:** solicitud vigente en `PENDIENTE_SUPERVISOR`.

**Flujo de aprobación:**

1. El responsable solicita la previsualización.
    
2. El sistema comprueba su independencia.
    
3. Simula ambas reasignaciones.
    
4. Revisa políticas, reservas, revisiones y periodos afectados.
    
5. Presenta bloqueos y advertencias.
    
6. El responsable acepta las advertencias concretas e indica motivo cuando corresponda.
    
7. Confirma la evaluación.
    
8. El sistema revalida y aplica la decisión atómicamente.
    
9. Actualiza una o dos publicaciones, según las semanas afectadas.
    
10. Libera reservas y registra auditoría y eventos de aviso.
    

**Flujo de rechazo:**

1. El responsable indica el motivo.
    
2. El sistema comprueba independencia, versión, estado y vigencia.
    
3. Registra `RECHAZADA` y libera las reservas sin cambiar el horario.
    

**Excepciones:** responsable participante, solicitud cancelada o vencida, revisión modificada, nuevo conflicto o fallo transaccional.

**Resultado:** intercambio completo o rechazo trazable; nunca una única reasignación aplicada.

Si no existe otro responsable elegible, el operador de plataforma no puede suplirlo.

## CU-12 — Consultar y exportar reportes

**Actor:** administrador o supervisor.

**Flujo principal:**

1. Selecciona el reporte y un periodo permitido.
    
2. Aplica los filtros disponibles.
    
3. El sistema obtiene una lectura consistente.
    
4. Presenta resultados, metadatos y referencia de origen.
    
5. El usuario solicita CSV del reporte consultado.
    
6. El sistema verifica permisos, filtros y huella de origen.
    
7. Genera el archivo si los datos relevantes coinciden.
    

**Alternativa:** si cambió el origen, informa el conflicto y solicita actualizar el reporte antes de exportar.

**Excepciones:** periodo inválido, volumen fuera del límite, recurso no autorizado o fallo de generación.

**Resultado:** reporte o CSV coherente, identificado como información programada cuando corresponda.

## CU-13 — Consultar avisos y trazabilidad

**Actor:** usuario según permiso.

**Flujo de avisos:**

1. Consulta sus avisos.
    
2. Abre uno.
    
3. El sistema comprueba el acceso actual al recurso relacionado.
    
4. Presenta la información permitida.
    
5. El usuario puede marcarlo como leído.
    

**Flujo de auditoría:**

1. El responsable accede a la consulta autorizada.
    
2. Aplica los filtros disponibles.
    
3. Revisa eventos, actores, motivos y referencias de decisión permitidas.
    

**Excepciones:** recurso no disponible, pérdida de permisos o fallo temporal.

**Resultado:** información contextual sin conceder permisos adicionales ni ejecutar acciones de negocio por leer un aviso.

## CU-14 — Cerrar el servicio y entregar datos

**Actores:** operador y representante autorizado.

**Precondiciones:** identidad verificada y política de cierre definida.

**Flujo principal:**

1. Se registra la solicitud.
    
2. Se acuerdan alcance, fecha y condiciones.
    
3. Se prepara la exportación autorizada.
    
4. Se entrega mediante un canal definido.
    
5. Se verifica la entrega.
    
6. Se cierra el acceso según lo acordado.
    
7. Se aplica posteriormente la política de conservación o eliminación.
    
8. Se conserva evidencia administrativa mínima.
    

**Excepciones:** autoridad insuficiente, exportación fallida, entrega no verificada o política pendiente.

**Resultado:** salida controlada sin eliminación accidental ni exposición de información.

# 10. Trazabilidad por capacidad

|   |   |   |
|---|---|---|
|Capacidad del MVP|Requisitos principales|Casos|
|MVP-01 — Empresas|RF-001 a RF-003|CU-01, CU-14|
|MVP-02 — Acceso y usuarios|RF-004 a RF-006|CU-02, CU-03|
|MVP-03 — Datos operativos|RF-007 a RF-009|CU-04|
|MVP-04 — Planificación|RF-011, RF-012|CU-05|
|MVP-05 — Validación|RF-010, RF-013|CU-04 a CU-07|
|MVP-06 — Publicación y consulta|RF-014 a RF-016|CU-06 a CU-08|
|MVP-07 — Intercambios|RF-017 a RF-020|CU-09 a CU-11|
|MVP-08 — Reportes|RF-021, RF-022|CU-12|
|MVP-09 — Avisos y auditoría|RF-023, RF-024|CU-13 y procesos críticos|

Los RNF se aplican transversalmente.

### Enlace con implementación y pruebas

Cada historia debe indicar:

- RF y RNF aplicables.
    
- CU relacionado, cuando corresponda.
    
- Reglas de la nota 04 que implementa.
    
- Contratos de la nota 10 utilizados.
    
- Criterios de aceptación que cubre.
    
- Evidencia de prueba requerida.
    

Una historia terminada no implica que todo el requisito esté cubierto si faltan otros criterios.

# 11. Exclusiones explícitas

Esta especificación no incorpora:

- Asistencia, marcaciones ni control de presencia.
    
- Nómina, pagos a trabajadores ni cálculo salarial.
    
- Generación automática u optimización de horarios.
    
- Inteligencia artificial para asignar turnos.
    
- Operación multisede completa.
    
- Aplicación móvil nativa.
    
- Funcionamiento offline.
    
- Cobros o suscripciones automáticas.
    
- Roles personalizados.
    
- Chat, SMS, WhatsApp o correo automático de avisos.
    
- Solicitudes de vacaciones con un nuevo flujo de aprobación.
    
- Intercambios unilaterales, cesión de turnos o bolsa de vacantes.
    
- Constructor de reportes históricos.
    
- Activación programada de políticas.
    
- Migración automática de zona horaria tras publicar.
    
- Portal automático de cierre y eliminación empresarial.
    

La configuración inicial y recuperación segura de acceso sí pertenecen al MVP, mediante el mecanismo asistido definido.

# 12. Pendientes para cerrar la especificación

## 12.1. Decisiones ya expresadas en la planificación consolidada

No deben volver a tratarse como comportamiento libre durante el desarrollo:

- Sesiones gestionadas en servidor.
    
- Recuperación mediante enlaces temporales y procedimiento asistido.
    
- Guardado explícito de borradores.
    
- Publicación vacía con confirmación y motivo.
    
- Aceptación específica de advertencias.
    
- Revisión independiente de intercambios.
    
- Publicaciones inmutables y versiones vigentes.
    
- Reintentos de negocio con idempotencia.
    
- Reportes acotados y exportación vinculada a la consulta.
    
- Políticas con activación inmediata y evaluación de impacto.
    

Son decisiones propuestas de diseño; no evidencia de implementación.

## 12.2. Pendientes reales

|   |   |   |
|---|---|---|
|Pendiente|Documento responsable|Momento límite|
|Valores reales de descanso y umbral de horas para el piloto|04 y 13|Antes de configurar una empresa real.|
|Límites de longitud, tamaño de solicitudes y otros detalles pendientes del contrato|10|Antes de implementar el contrato afectado.|
|Parámetros operativos de seguridad y recuperación asistida|06 y 12|Antes de probar el acceso con usuarios reales.|
|Infraestructura y definición exacta del ensayo de rendimiento|06 y 12|Antes de medir y aceptar los objetivos.|
|Validación práctica de respaldo, RPO y RTO|12|Antes del piloto con datos reales.|
|Conservación, exportación completa y eliminación|12 y 13|Antes de incorporar datos reales.|
|Responsables, horario y condiciones de soporte|13|Antes de ofrecer el piloto.|
|Trazabilidad detallada de historias y pruebas con esta revisión|11 y 12|Antes de cerrar cada incremento.|
|Obligaciones académicas todavía no confirmadas|14|Antes de comprometer los entregables correspondientes.|

Una decisión pendiente no autoriza a inventar su comportamiento. Debe resolverse antes de ejecutar la historia o condición operativa que dependa de ella.

# 13. Criterio de cierre

Esta nota estará lista como especificación base cuando:

- Cada capacidad del MVP tenga requisitos asociados.
    
- Cada requisito tenga criterios verificables.
    
- Los permisos coincidan con la nota 04.
    
- Los datos y contratos puedan representar los comportamientos descritos.
    
- Los procesos críticos contemplen concurrencia, fallos y reintentos.
    
- Se distingan bloqueos, advertencias y confirmaciones.
    
- No se presenten funciones futuras como parte del MVP.
    
- Los objetivos de calidad estén aceptados o ajustados explícitamente.
    
- Los pendientes tengan responsable y momento límite.
    
- El backlog y las pruebas utilicen estos identificadores estables.
    

La especificación no se considera implementada por estar documentada.

La preparación para un piloto requiere además las evidencias y condiciones de las notas 12 y 13.

# 14. Documentos relacionados para continuar

- [[06 - Arquitectura y Stack Tecnológico]]
    
- [[07 - Modelo de Datos y DER]]
    
- [[08 - Navegación, Pantallas y Flujos de Usuario]]
    
- [[09 - Design System y Wireframes]]
    
- [[10 - Contratos API e Integraciones]]
    
- [[11 - Backlog MVP y Plan de Implementación]]
    
- [[12 - Pruebas, Seguridad y Operación]]
    
- [[13 - Lanzamiento Piloto y Evolución Comercial]]
    

La siguiente revisión debe comprobar que las pantallas permiten cumplir estos requisitos sin introducir pasos, permisos o funciones adicionales.