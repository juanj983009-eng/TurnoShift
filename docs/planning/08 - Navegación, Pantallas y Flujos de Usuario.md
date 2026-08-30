
## 1. Propósito

Definir la navegación, ubicación de funciones y comportamiento de las pantallas del MVP.

La experiencia debe permitir que cada usuario:

- Encuentre sus tareas principales.
    
- Distinga el horario oficial de los cambios propuestos.
    
- Comprenda las acciones pendientes.
    
- Sepa qué está guardado y qué no.
    
- Corrija errores sin sobrescribir cambios ajenos.
    
- Entienda por qué una operación está bloqueada.
    
- Utilice las funciones esenciales desde escritorio o móvil.
    

Esta nota especifica comportamiento. Los componentes visuales y wireframes corresponden a [[09 - Design System y Wireframes]].

Las pantallas descritas son una propuesta de diseño, no una interfaz ya implementada o validada.

## 2. Principios de experiencia

### 2.1. Orientación a tareas

La navegación prioriza:

1. Preparar y publicar el horario.
    
2. Consultar el turno propio.
    
3. Solicitar y resolver intercambios.
    
4. Revisar información operativa.
    

Los catálogos y la configuración apoyan esos procesos.

### 2.2. Diferenciar información oficial y propuesta

Deben distinguirse mediante texto y presentación:

- Cambios locales sin guardar.
    
- Borrador guardado.
    
- Resultado de validación.
    
- Publicación vigente.
    
- Publicación histórica.
    
- Intercambio pendiente.
    
- Intercambio aprobado.
    

Guardar no significa validar. Validar no significa publicar. Aceptar un intercambio no significa aprobarlo.

### 2.3. No depender exclusivamente del color

Estados, advertencias y bloqueos incluyen etiquetas y explicaciones comprensibles.

### 2.4. Permisos consistentes

La interfaz muestra acciones acordes con el contexto autorizado. El servidor vuelve a comprobarlas.

Una URL, un aviso o un botón visible no conceden permisos.

Las acciones disponibles de una solicitud se obtienen del servidor; no se deducen únicamente del nombre del rol.

### 2.5. Una organización por sesión

La cabecera identifica la empresa actual.

El MVP no incluye selector libre de organización, simulación de roles ni suplantación de usuarios.

### 2.6. Confirmar antes de mostrar éxito

Las escrituras no se presentan como completadas antes de recibir confirmación.

Mientras se envía una operación se evita el envío repetido desde sus controles. Esto complementa, pero no sustituye, la protección contra duplicados del servidor.

### 2.7. No inventar capacidades desde la interfaz

No se incluyen controles para funciones, filtros o transiciones que no estén definidos en requisitos y contratos.

Una nueva necesidad se registra como decisión de alcance, no como un botón agregado durante el diseño.

## 3. Estructura general de la aplicación

### Cabecera

Contiene:

- Identidad de TurnoShift.
    
- Nombre de la organización.
    
- Acceso a avisos propios.
    
- Nombre y rol del usuario.
    
- Menú de cuenta.
    
- Cierre de sesión.
    

La zona horaria aparece en las vistas donde afecta la interpretación de fechas y horas.

### Navegación principal

Se adapta al rol y a la existencia de una vinculación personal activa.

No se muestran secciones vacías para funciones futuras.

### Área de trabajo

Contiene:

- Título.
    
- Periodo o recurso consultado.
    
- Estado relevante.
    
- Acción principal.
    
- Filtros definidos.
    
- Contenido.
    
- Estados de carga, vacío, error y resultado.
    

### Vistas secundarias

Se utilizan formularios, paneles o diálogos para tareas acotadas:

- Crear o editar un colaborador.
    
- Añadir una asignación.
    
- Consultar un turno.
    
- Confirmar una cancelación.
    
- Revisar una previsualización.
    

La edición semanal y el detalle de intercambio tienen vistas dedicadas.

La previsualización no constituye otro módulo ni una copia privada del recurso.

## 4. Navegación por rol

En esta nota, **acceso personal habilitado** significa cuenta activa vinculada a un colaborador activo.

|Sección|Administrador|Supervisor|Colaborador|
|---|---|---|---|
|Inicio|Resumen operativo y preparación|Resumen operativo|Entrada en Mi horario|
|Planificación|Consultar, editar y publicar|Consultar, editar y publicar|No|
|Mi horario|Con acceso personal habilitado|Con acceso personal habilitado|Con acceso personal habilitado|
|Intercambios|Gestión y funciones personales cuando correspondan|Gestión y funciones personales cuando correspondan|Solicitudes propias|
|Equipo|Administración|Consulta limitada|No|
|Indisponibilidades|Gestión|Gestión|No|
|Reportes|Consulta y CSV|Consulta y CSV|No|
|Configuración|Administración|No|No|
|Historial operativo|Consulta autorizada|Consulta operativa autorizada|No; su historial está en las solicitudes|
|Avisos|Propios|Propios|Propios|
|Mi cuenta|Sí|Sí|Sí|

### Configuración

Agrupa:

- Organización y sede.
    
- Usuarios.
    
- Puestos.
    
- Tipos de turno.
    
- Políticas.
    

El supervisor consulta los datos y políticas necesarios dentro de planificación, validaciones y formularios autorizados. No necesita acceso al módulo administrativo de configuración.

### Falta de vinculación personal

No se confunde con falta de turnos.

Un administrador o supervisor sin vinculación activa conserva sus funciones administrativas u operativas autorizadas.

Un colaborador sin acceso personal habilitado recibe una explicación y la indicación de contactar al administrador. No puede elegir por sí mismo a qué colaborador vincularse.

### Operador de TurnoShift

Utiliza herramientas y procedimientos restringidos separados del portal empresarial.

No se incorpora una pantalla pública de superadministrador ni un acceso para aprobar intercambios de clientes.

## 5. Mapa de pantallas y rutas propuestas

Las rutas siguientes pertenecen al frontend, no a la API.

|   |   |   |
|---|---|---|
|ID|Pantalla|Ruta|
|UI-01|Iniciar sesión|`/acceso`|
|UI-02|Ayuda para recuperar acceso|`/recuperar-acceso`|
|UI-03|Establecer nueva contraseña|`/restablecer-acceso`|
|UI-04|Acceso restringido|`/acceso-restringido`|
|UI-05|Inicio operativo|`/app/inicio`|
|UI-06|Planificación semanal|`/app/planificacion`|
|UI-07|Editor de borrador|`/app/planificacion/borradores/:id`|
|UI-08|Publicación e historial semanal|`/app/planificacion/publicaciones/:id`|
|UI-09|Mi horario|`/app/mi-horario`|
|UI-10|Bandeja de intercambios|`/app/intercambios`|
|UI-11|Solicitud de intercambio|`/app/intercambios/:id`|
|UI-12|Equipo|`/app/equipo`|
|UI-13|Indisponibilidades|`/app/disponibilidad`|
|UI-14|Organización y catálogos|`/app/configuracion`|
|UI-15|Usuarios|`/app/configuracion/usuarios`|
|UI-16|Políticas|`/app/configuracion/politicas`|
|UI-17|Reportes|`/app/reportes`|
|UI-18|Historial operativo|`/app/historial`|
|UI-19|Avisos|`/app/avisos`|
|UI-20|Mi cuenta|`/app/mi-cuenta`|

Se conserva la ruta de UI-13, pero la etiqueta visible será **Indisponibilidades** para describir lo que realmente se registra.

El detalle de turno y el asistente de intercambio son vistas secundarias.

Los periodos y filtros no sensibles pueden representarse en parámetros de URL.

Las credenciales no se incluyen en rutas ni parámetros de consulta. El enlace temporal de recuperación sigue el tratamiento específico de UI-03.

# 6. Acceso y cuenta

## UI-01 — Iniciar sesión

### Campos

- Código de empresa.
    
- Correo.
    
- Contraseña.
    

### Acciones

- Ingresar.
    
- Mostrar u ocultar contraseña.
    
- Abrir ayuda de recuperación.
    

### Comportamiento

1. Comprobar campos obligatorios.
    
2. Enviar las credenciales.
    
3. Esperar el resultado.
    
4. Resolver el contexto autenticado.
    
5. Abrir la pantalla inicial autorizada.
    

Una cuenta sin contraseña configurada no puede utilizar una contraseña provisional inventada.

### Estados

- Credenciales inválidas: mensaje genérico.
    
- Demasiados intentos: indicación de espera.
    
- Error temporal: opción de recuperación.
    
- Sesión existente: resolver su contexto antes de redirigir.
    
- Restricción de acceso: mostrar únicamente el motivo que el servidor permita revelar.
    

No se confirma públicamente la existencia de cuentas ni se distingue una suspensión empresarial a partir de credenciales no verificadas.

### Respuesta perdida

Si no se sabe si el acceso terminó correctamente, se consulta la sesión antes de volver a enviar credenciales.

### Fuera del MVP

- Registro empresarial autoservicio.
    
- Acceso mediante redes sociales.
    
- Opción “Recordarme”.
    
- Selector de roles.
    
- Acceso del operador desde este formulario.
    

## UI-02 — Ayuda para recuperar acceso

La recuperación es asistida.

La pantalla explica:

- A quién contactar según el procedimiento habilitado.
    
- Qué información mínima proporcionar.
    
- Que nunca debe enviarse la contraseña.
    
- Que se verificará la identidad.
    
- Que el enlace temporal tiene vigencia limitada.
    

No afirma que se envió un correo si no existe esa integración.

No incorpora tickets, chat ni un formulario público que confirme cuentas existentes.

El canal y responsable reales deben estar definidos antes del piloto.

## UI-03 — Establecer nueva contraseña

Sirve para configuración inicial y recuperación.

### Campos

- Nueva contraseña.
    
- Confirmación de contraseña.
    

La confirmación repetida se utiliza para validación de interfaz; no es otro dato persistido.

### Comportamiento

1. Obtener el mecanismo temporal mediante el flujo seguro definido.
    
2. Inspeccionarlo sin consumirlo.
    
3. Mostrar el propósito permitido: configuración inicial o recuperación.
    
4. Solicitar la nueva contraseña.
    
5. Confirmar.
    
6. Informar el resultado y dirigir al acceso.
    

El enlace se consume únicamente al confirmar correctamente el cambio.

### Estados

- Comprobando enlace.
    
- Enlace válido.
    
- Enlace no válido o no utilizable.
    
- Contraseña que no cumple las condiciones.
    
- Confirmaciones diferentes.
    
- Error temporal.
    
- Cambio completado.
    
- Resultado todavía desconocido.
    

No se deduce públicamente por qué un enlace es inválido si el servidor no devuelve ese detalle.

### Protección del enlace

El mecanismo previsto puede transportar el secreto en el fragmento del enlace y enviarlo después al servidor en el cuerpo de la solicitud.

No se conserva en analítica, registros, almacenamiento persistente de formularios ni rutas de retorno. Se retira de la URL una vez capturado para el flujo.

No se muestra el perfil de la cuenta a partir de la mera inspección.

### Respuesta perdida

No se afirma que la contraseña cambió solo porque el enlace dejó de ser válido.

Se aplica la recuperación específica de seguridad: comprobar lo permitido, intentar una nueva autenticación o iniciar otro procedimiento asistido.

No se recupera el secreto desde el registro general de operaciones.

## UI-04 — Acceso restringido

Representa restricciones confirmadas por el servidor:

- Sesión vencida.
    
- Acceso empresarial suspendido.
    
- Cuenta desactivada.
    
- Permiso insuficiente.
    

No muestra datos protegidos detrás del mensaje.

### Acciones

Según el contexto:

- Volver a iniciar sesión.
    
- Cerrar sesión.
    
- Volver a una sección autorizada.
    
- Consultar cómo contactar al responsable.
    

No ofrece pago, reactivación automática ni cambio de empresa.

La falta de vinculación personal se explica en las vistas personales; no bloquea innecesariamente otras funciones autorizadas.

## UI-20 — Mi cuenta

Muestra:

- Nombre.
    
- Correo.
    
- Empresa.
    
- Rol.
    
- Situación de vinculación personal.
    
- Cambio de contraseña.
    
- Cierre de sesión.
    

### Cambio de contraseña

Solicita:

- Contraseña actual.
    
- Nueva contraseña.
    
- Confirmación.
    

Después del cambio se requiere nueva autenticación conforme al contrato.

### Restricciones

El usuario no modifica libremente rol, correo operativo ni vinculación. Esos cambios pertenecen a la administración autorizada.

Cerrar sesión debe invalidar la sesión del servidor. Ante una respuesta incierta, la aplicación no continúa operando como si el cierre estuviera confirmado.

# 7. Inicio operativo e incorporación

## UI-05 — Inicio operativo

Es un resumen de tareas, no un panel comercial complejo.

### Administrador

Muestra información disponible del sistema:

- Estado de publicación semanal.
    
- Borrador abierto.
    
- Solicitudes pendientes de revisión.
    
- Configuración indispensable incompleta.
    
- Accesos a equipo, usuarios y políticas.
    

### Supervisor

Muestra:

- Publicación semanal.
    
- Borrador disponible.
    
- Solicitudes pendientes.
    
- Acceso a indisponibilidades.
    

La cantidad de solicitudes pendientes no significa que el usuario pueda resolverlas todas: su participación puede impedir la revisión independiente.

### Colaborador

Su entrada principal es **Mi horario**, sin un panel adicional.

### Límites

No muestra:

- Asistencia.
    
- Ingresos del SaaS.
    
- Datos de otras empresas.
    
- Ahorro estimado sin medición.
    
- Métricas de adopción no instrumentadas.
    

## 7.1. Preparación inicial acompañada

El administrador dispone de una guía de preparación:

1. Revisar organización, sede y zona horaria.
    
2. Activar las políticas necesarias.
    
3. Preparar puestos y colaboradores para las asignaciones.
    
4. Habilitar cuentas para quienes utilizarán funciones personales.
    
5. Crear y revisar la primera planificación.
    

La guía distingue:

- Requisitos obligatorios de publicación.
    
- Requisitos aplicables a las asignaciones incluidas.
    
- Recomendaciones.
    
- Funciones opcionales.
    

Las plantillas no son obligatorias para introducir turnos manuales.

No todos los colaboradores necesitan cuenta para ser planificados.

Una publicación vacía no exige inventar colaboradores o turnos, pero sí cumplir la configuración general y las confirmaciones exigidas.

No registrar indisponibilidades no significa que se haya confirmado la disponibilidad de todo el equipo.

# 8. Planificación semanal

## UI-06 — Planificación semanal

### Objetivo

Consultar el horario oficial y acceder al borrador o historial.

### Cabecera

- Semana.
    
- Anterior y siguiente.
    
- “Semana actual”.
    
- Zona horaria.
    
- Estado de publicación.
    
- Número y fecha de la publicación vigente.
    
- Indicación de borrador abierto, si existe.
    

Consultar una semana sin registros no crea automáticamente una planificación ni un borrador.

### Presentación

En escritorio se propone una cuadrícula por colaborador y día.

Los filtros visuales de colaborador o puesto no modifican el alcance de una publicación: publicar sigue afectando a la semana completa.

Si se filtra una colección paginada, la interfaz no presenta una página filtrada como si fuera el conjunto completo. Solo se ofrecen filtros respaldados por los datos y el contrato.

### Acciones

- Crear borrador.
    
- Continuar borrador.
    
- Revisar la publicación mediante un borrador.
    
- Consultar historial.
    
- Abrir detalle de turno.
    

### Estados

- Sin publicación ni borrador.
    
- Sin publicación, con borrador.
    
- Publicada con asignaciones.
    
- Publicada vacía.
    
- Publicada con borrador abierto.
    
- Borrador desactualizado.
    

La cuadrícula oficial no cambia al guardar un borrador.

## UI-07 — Editor de borrador

### Identificación permanente

Muestra una etiqueta visible:

> Borrador — estos cambios todavía no son el horario oficial.

Incluye:

- Semana.
    
- Publicación base, cuando existe.
    
- Versión del borrador.
    
- Último guardado confirmado.
    
- Estado de cambios locales.
    
- Aviso de desactualización.
    

No se muestra un autor de último cambio si ese dato no está disponible en el contrato.

### Acciones

- Añadir asignación.
    
- Editar una asignación permitida.
    
- Retirar una asignación permitida.
    
- Guardar el formulario.
    
- Validar.
    
- Revisar publicación.
    
- Descartar borrador.
    

La acción “Revisar publicación” abre la previsualización; no publica directamente.

### Guardado explícito

Los formularios guardan sus cambios expresamente.

- No hay autoguardado implícito.
    
- “Guardado” aparece después de confirmar el servidor.
    
- Cada escritura utiliza la versión vigente conocida del borrador.
    
- El borrador guardado es compartido entre responsables autorizados.
    
- No se crean copias privadas ocultas.
    

Si existe un formulario modificado sin guardar, antes de validar o previsualizar se pide guardarlo o descartarlo. No se publica ignorando silenciosamente esos cambios locales.

### Formulario de asignación

Contiene:

- Colaborador.
    
- Plantilla de turno opcional.
    
- Fecha y hora de inicio.
    
- Fecha y hora de fin.
    
- Puesto según los campos y reglas del contrato.
    

Muestra:

- Zona horaria.
    
- Duración programada.
    
- Ambas fechas si cruza medianoche.
    
- Conflictos detectados.
    

La plantilla ayuda a introducir datos. Modificarla después no actualiza asignaciones existentes.

Se utiliza precisión de minutos. No se redondean silenciosamente segundos u otros valores que el contrato rechaza.

### Guardado y conflictos

Los datos estructuralmente inválidos no se guardan.

Los conflictos operativos permitidos en borrador se muestran como pendientes de resolución. El mensaje debe indicar:

> Borrador guardado. Todavía hay conflictos que impiden publicarlo.

No todos los errores de planificación tienen que impedir conservar el borrador, pero sí su publicación cuando son bloqueantes.

### Turnos iniciados y pertenencia semanal

Las asignaciones iniciadas se muestran como no modificables.

La semana de pertenencia se determina por el inicio local. No se cambia silenciosamente la identidad de una asignación publicada para trasladarla a otra semana.

Si una acción exige un procedimiento no definido, no se ofrece un botón que lo improvise.

## 8.1. Validación del borrador

El panel distingue:

- Bloqueos.
    
- Advertencias.
    
- Resultado sin conflictos detectados.
    

Cada elemento identifica:

- Explicación.
    
- Colaborador o recurso afectado.
    
- Periodo relevante.
    
- Acción para localizarlo, cuando esté autorizada.
    

La validación considera la publicación que será sustituida y las semanas adyacentes.

No se muestra como garantizada una publicación solo porque una validación anterior fue favorable.

Editar el borrador invalida la evaluación anterior. Los cambios externos también pueden obligar a repetirla al confirmar.

## 8.2. Previsualización y publicación

### Contenido

La previsualización muestra:

- Semana completa que se publicará.
    
- Publicación base.
    
- Política evaluada.
    
- Asignaciones agregadas, modificadas y retiradas.
    
- Colaboradores afectados.
    
- Bloqueos.
    
- Advertencias.
    
- Intercambios que quedarían invalidados.
    
- Condición de horario vacío.
    
- Confirmaciones y motivo exigidos.
    

Los filtros de la cuadrícula no ocultan impactos de la decisión.

La información relevante puede organizarse en secciones desplegables, pero no se omite silenciosamente para reducir el resumen.

### Bloqueos

Se impide confirmar y se indica qué debe corregirse.

Un motivo escrito no permite ignorar un bloqueo.

### Advertencias

Cada advertencia muestra:

- Colaborador.
    
- Periodo.
    
- Horas programadas calculadas.
    
- Umbral configurado.
    
- Explicación.
    

El usuario reconoce cada advertencia concreta. No existe una aceptación permanente ni un “aceptar cualquier advertencia futura”.

Las aceptaciones comienzan sin marcar y se invalidan si cambia la evaluación.

El cliente remite las referencias de las advertencias reconocidas; el servidor conserva sus propios cálculos.

### Motivo

|   |   |
|---|---|
|Situación|Motivo obligatorio|
|Primera publicación con turnos y sin advertencias|No|
|Revisión mediante borrador|Sí|
|Advertencias reconocidas|Sí|
|Publicación vacía|Sí|

### Publicación vacía

Es una opción definida del MVP, no una decisión pendiente.

Se solicita una confirmación independiente:

> Confirmo que esta semana se publicará sin asignaciones.

Además se exige motivo.

No se permite utilizarla para retirar turnos iniciados que deban conservarse.

El resultado se identifica como **Semana publicada sin turnos**, no como semana pendiente ni disponibilidad confirmada.

### Confirmación

1. El usuario revisa la evaluación.
    
2. Completa las confirmaciones requeridas.
    
3. Confirma la intención.
    
4. El servidor comprueba versiones, contexto y tiempo.
    
5. La interfaz espera el resultado.
    
6. Tras el éxito, consulta la publicación vigente y actualiza las vistas afectadas.
    

Si cambió el contexto, se presenta una evaluación nueva y se solicita otra decisión.

## UI-08 — Publicación e historial semanal

### Información

Muestra:

- Semana y asignaciones.
    
- Número de publicación.
    
- Fecha.
    
- Autor de la decisión.
    
- Motivo, cuando corresponde.
    
- Origen: inicial, revisión o intercambio.
    
- Política y zona utilizadas.
    
- Advertencias reconocidas, según permisos.
    

Autor, motivo y evidencia proceden de la decisión registrada. No son campos editables de la publicación.

### Versión vigente

Se identifica como horario oficial actual de esa semana.

### Versión histórica

Incluye un aviso permanente:

> Versión histórica — puede diferir del horario vigente.

Permite volver a la versión actual.

No ofrece:

- Edición directa.
    
- Eliminación.
    
- Restauración automática.
    
- Exportación histórica no definida en el contrato.
    

El historial semanal y la auditoría operativa tienen propósitos distintos.

## 8.3. Conflictos del borrador

### Otra edición del mismo borrador

Se informa que existe una versión más reciente.

La aplicación no sustituye la versión esperada y reenvía automáticamente los cambios locales.

Se conserva el contexto no sensible mientras el usuario revisa lo ocurrido. No se promete una comparación automática de cualquier campo.

### Publicación base desactualizada

Puede ocurrir por una revisión publicada o un intercambio aprobado.

La interfaz:

1. Señala que el borrador parte de una publicación anterior.
    
2. Permite consultar su contenido.
    
3. Bloquea la publicación.
    
4. Ofrece revisar la versión vigente.
    
5. Permite descartar el borrador y crear otro desde la base actual, con confirmaciones explícitas.
    

No se presenta una “regeneración” como operación atómica si solo existen descarte y creación por separado.

Si otra persona crea un borrador durante ese proceso, se abre el existente y se informa el conflicto.

No hay fusión automática en el MVP.

# 9. Mi horario

## UI-09 — Mi horario

### Prioridades

- Próximo turno.
    
- Agenda de la semana.
    
- Detalle de asignaciones.
    
- Acceso a solicitudes propias.
    

### Datos de cada turno

- Día y fecha de inicio.
    
- Día y fecha de fin cuando difieran.
    
- Horas.
    
- Puesto.
    
- Sede.
    
- Zona horaria.
    
- Condición relevante para solicitar intercambio.
    

### Acciones

- Consultar detalle.
    
- Iniciar solicitud sobre un turno elegible.
    
- Abrir una solicitud propia relacionada.
    

### Estados separados

|   |   |
|---|---|
|Situación|Presentación|
|No existe publicación|“Esta semana todavía no tiene un horario publicado.”|
|Publicación sin asignaciones para nadie|“Esta semana se publicó sin turnos.”|
|Hay publicación, pero no turnos propios|“No tienes turnos asignados en esta semana.”|
|No hay próximo turno|“No tienes un próximo turno publicado.”|
|No existe vinculación personal activa|Explicación de acceso personal no disponible y contacto con el administrador.|

### Restricciones

No permite:

- Editar horarios.
    
- Registrar asistencia.
    
- Declarar disponibilidad.
    
- Consultar el calendario completo de compañeros.
    
- Leer motivos privados de indisponibilidad.
    

Después de un intercambio, un turno que ya no pertenece al usuario puede dejar de ser accesible desde el detalle personal. Su participación en la solicitud conserva el acceso autorizado al historial de esa solicitud.

# 10. Intercambios

## UI-10 — Bandeja de intercambios

### Vista personal

Organiza las solicitudes propias en:

- Recibidas.
    
- Enviadas.
    
- Historial.
    

Los filtros y la paginación corresponden a las capacidades del contrato. No se filtra solo la página cargada para aparentar un historial completo.

### Vista de gestión

Disponible para administrador y supervisor:

- Pendientes de aprobación.
    
- Otras solicitudes autorizadas.
    
- Historial.
    

Cuando tiene acceso personal habilitado, el responsable distingue:

- **Mis solicitudes**.
    
- **Gestión de solicitudes**.
    

Esto no cambia su rol.

### Información del listado

- Participantes.
    
- Resumen de ambos turnos.
    
- Estado.
    
- Fecha de creación.
    
- Vencimiento.
    
- Acción disponible para el usuario.
    

### Etiquetas

|   |   |
|---|---|
|Estado interno|Etiqueta|
|`PENDIENTE_COMPANERO`|Esperando al compañero|
|`PENDIENTE_SUPERVISOR`|Pendiente de aprobación|
|`APROBADA`|Aprobada|
|`RECHAZADA`|Rechazada|
|`CANCELADA`|Cancelada|
|`VENCIDA`|Vencida|
|`INVALIDADA`|Invalidada|

La cuenta atrás es orientativa. El servidor determina el vencimiento efectivo.

## 10.1. Asistente de solicitud

Se abre desde un turno propio y no constituye una pantalla adicional del catálogo.

### Paso 1 — Confirmar turno de origen

Mostrar fecha, horario, puesto y condición de elegibilidad.

### Paso 2 — Buscar oportunidad

Solicitar un periodo acotado, con máximo de 92 días según la API.

Mostrar candidatos paginados con:

- Nombre visible del compañero.
    
- Puesto.
    
- Fecha y horas.
    
- Información mínima necesaria para comparar.
    

No mostrar correo, calendario completo ni motivos privados.

Consultar candidatos no reserva asignaciones.

### Paso 3 — Revisar propuesta

Mostrar:

- Turnos actuales de ambos participantes.
    
- Quién recibiría cada turno.
    
- Ambas semanas cuando sean diferentes.
    
- Necesidad de aceptación y aprobación independiente.
    

Se mantienen las fechas y horas de los turnos; cambia la persona asignada.

### Paso 4 — Enviar

El servidor vuelve a comprobar elegibilidad y revisiones, y reserva ambos turnos si registra la solicitud.

Mensaje de éxito:

> Solicitud enviada. Los horarios todavía no han cambiado.

Si el candidato dejó de estar disponible, se actualizan los resultados. No se selecciona automáticamente otro.

## UI-11 — Detalle de solicitud

### Contenido

- Estado y vigencia.
    
- Participantes.
    
- Turnos y revisiones de origen.
    
- Resultado propuesto o cambio aplicado.
    
- Semanas afectadas.
    
- Historial de eventos.
    
- Motivos permitidos.
    
- Acciones autorizadas para el usuario actual.
    

En una solicitud aprobada, el cambio aplicado se presenta como histórico de esa decisión; no se afirma que nunca pudo existir una revisión posterior.

### Acciones por contexto

|   |   |
|---|---|
|Usuario y estado|Acciones|
|Destinatario en espera de respuesta|Aceptar o rechazar|
|Solicitante mientras la solicitud esté pendiente|Cancelar|
|Administrador o supervisor independiente en revisión|Previsualizar aprobación o rechazar|
|Participante con rol administrativo|Acciones personales que le correspondan; no resolver como responsable|
|Usuario autorizado ante estado terminal|Consultar|

La prohibición de revisión independiente incluye **aprobar y rechazar** en la etapa de supervisión.

Un supervisor destinatario sí puede rechazar como compañero en la etapa inicial.

### Aceptación del compañero

Mensaje:

> Aceptar enviará la solicitud a revisión. El intercambio todavía no se aplicará.

### Motivos

- Rechazo del compañero: opcional.
    
- Cancelación del solicitante: opcional.
    
- Rechazo del responsable independiente: obligatorio.
    
- Aprobación con advertencias: obligatorio.
    
- Aprobación sin advertencias: según los requisitos de confirmación devueltos por el servidor.
    

No se obliga a revelar información sensible en motivos opcionales.

### Aprobación

1. Solicitar la previsualización.
    
2. Mostrar turnos actuales y propuestos.
    
3. Mostrar todas las semanas afectadas.
    
4. Presentar bloqueos y advertencias.
    
5. Recoger reconocimientos específicos y motivo cuando corresponda.
    
6. Confirmar.
    
7. Esperar el resultado.
    
8. Actualizar solicitud, horarios y referencias a borradores afectados.
    

La previsualización no mantiene bloqueada la planificación mientras se lee.

No se ofrece una segunda acción manual para publicar el intercambio: la aprobación válida ya actualiza las publicaciones.

### Sin responsable independiente

Mostrar:

> Este intercambio necesita otro administrador o supervisor autorizado que no participe.

No se ofrece autoaprobación ni intervención del operador como excepción.

### Vencimiento o invalidación

Se retiran las acciones incompatibles y se explica la causa permitida.

Una respuesta de error puede informar que la solicitud quedó vencida o invalidada. Se recarga su estado sin presentar el intercambio como aplicado.

Puede ofrecerse volver al horario para iniciar otra solicitud elegible, pero no reabrir la anterior.

# 11. Equipo, indisponibilidades y configuración

## UI-12 — Equipo

### Listado

- Código.
    
- Nombre.
    
- Puesto.
    
- Estado.
    
- Vinculación de cuenta, solo cuando esté autorizada.
    

### Administrador

Puede crear, editar, activar y solicitar desactivación.

La vinculación se administra desde Usuarios, mediante un acceso contextual.

Crear un colaborador no crea una cuenta. Crear una cuenta no crea un colaborador.

### Supervisor

Consulta únicamente los datos operativos permitidos. No accede a secretos ni información administrativa innecesaria.

### Desactivación

1. Solicitar la previsualización de compromisos.
    
2. Mostrar los bloqueos.
    
3. Ofrecer enlaces autorizados para revisarlos.
    
4. Confirmar solo cuando proceda.
    
5. Revalidar en el servidor.
    

Los compromisos no se eliminan ni reasignan automáticamente.

Si hay turnos iniciados, no se ofrece retirarlos para forzar la desactivación.

### Cambio de puesto

Los conflictos se presentan con explicación. No se promete que cambiar el catálogo resolverá asignaciones existentes.

## UI-13 — Indisponibilidades

### Contenido

- Colaborador.
    
- Inicio y fin.
    
- Estado.
    
- Motivo dentro del ámbito autorizado.
    

### Acciones

- Registrar.
    
- Editar.
    
- Cancelar.
    
- Consultar la información de trazabilidad disponible.
    

No se incorpora un historial independiente que requiera contratos nuevos; la trazabilidad adicional utiliza el historial autorizado existente.

### Conflictos

Una indisponibilidad incompatible con un turno publicado no se guarda como contradicción pendiente.

Se indican los recursos autorizados que requieren revisión. La pantalla no modifica automáticamente la planificación.

Tampoco se habilitan modificaciones retroactivas de periodos iniciados.

### Mensaje permanente

> Aquí se registran periodos de indisponibilidad. La ausencia de registros no confirma disponibilidad.

No se incluyen solicitudes de vacaciones ni aprobación de ausencias.

## UI-14 — Organización y catálogos

Agrupa:

- Organización.
    
- Sede.
    
- Puestos.
    
- Tipos o plantillas de turno.
    

### Límites

- La capacidad del servicio es de consulta para el administrador.
    
- No se ofrece crear una segunda sede.
    
- No se elimina información histórica mediante controles ordinarios.
    
- Modificar plantillas no actualiza turnos existentes.
    

### Cambio de zona horaria

Antes de la primera publicación:

1. Elegir la zona propuesta.
    
2. Solicitar evaluación de impacto.
    
3. Revisar borradores afectados.
    
4. Resolver bloqueos de pertenencia semanal.
    
5. Confirmar con la versión y evaluación correspondientes.
    

Se explica que los instantes existentes se conservan; no se mantiene automáticamente la misma hora local.

Después de la primera publicación, incluso si fue vacía, el campo queda bloqueado para cambios en el MVP.

Si cambia el contexto durante la revisión, se solicita una nueva evaluación.

## UI-15 — Usuarios

### Listado

- Nombre.
    
- Correo.
    
- Rol.
    
- Estado.
    
- Colaborador vinculado.
    

### Acciones

- Crear cuenta.
    
- Editar campos permitidos.
    
- Asignar rol predefinido.
    
- Activar o desactivar.
    
- Vincular o desvincular cuando las reglas lo permitan.
    
- Emitir enlace de configuración inicial.
    
- Emitir enlace de recuperación asistida.
    

### Creación y acceso inicial

Crear la cuenta y emitir el enlace son acciones diferenciadas.

Si se crea la cuenta pero falla la emisión del enlace, se informa esa situación. No se repite el alta para intentar recuperar el enlace.

El enlace se muestra únicamente al administrador autorizado para entregarlo mediante el procedimiento acordado.

No se promete recuperar posteriormente el secreto desde el historial. Emitir otro requiere una acción consciente e invalida el anterior según el contrato.

### Confirmaciones

Se solicitan para:

- Desactivación.
    
- Cambios de permisos.
    
- Cambios de vinculación.
    
- Emisión de enlaces sensibles.
    

Se explica que desactivar una cuenta no desactiva al colaborador y puede invalidar solicitudes pendientes.

El último administrador activo no puede perder su capacidad administrativa.

Un cambio de vinculación bloqueado por solicitudes pendientes no permite transferirlas a otra persona.

## UI-16 — Políticas

### Contenido

- Política activa o estado sin política.
    
- Descanso mínimo.
    
- Umbral semanal de horas programadas.
    
- Versión.
    
- Fecha de activación.
    
- Historial autorizado.
    
- Evidencia de la decisión cuando esté disponible.
    

### Activación

1. Introducir los valores propuestos.
    
2. Solicitar la previsualización.
    
3. Revisar su impacto sobre la programación publicada futura.
    
4. Resolver bloqueos.
    
5. Reconocer cada advertencia permitida.
    
6. Registrar el motivo requerido.
    
7. Confirmar.
    
8. Mostrar la nueva versión activa.
    

La propuesta aún no activa no se presenta como política vigente ni con un identificador definitivo inventado.

Editar los valores invalida la evaluación anterior.

La activación es inmediata. No se muestra calendario para programarla.

La interfaz explica que la política no reescribe publicaciones históricas, pero se utilizará en las confirmaciones posteriores que correspondan.

No se muestra “cumplimiento legal garantizado”.

# 12. Reportes, historial y avisos

## UI-17 — Reportes

### Reportes incluidos

1. Horas programadas.
    
2. Resumen de intercambios.
    

La cuadrícula semanal y el historial de publicaciones permanecen en Planificación. No se duplican como reportes adicionales ni se les atribuyen exportaciones no definidas.

### Filtros

- Periodo.
    
- Colaborador, cuando el contrato del reporte lo admita.
    

No se añaden filtros por puesto, resolución u otros campos sin soporte contractual.

El periodo máximo es de 92 días.

La interfaz puede mostrar fechas inclusivas comprensibles para el usuario, convirtiéndolas correctamente al límite final exclusivo de la API en la zona de la empresa.

### Explicaciones obligatorias

**Horas programadas:**

> Se calculan a partir de las publicaciones vigentes y del tiempo comprendido dentro del periodo seleccionado.

**Intercambios:**

> Incluye solicitudes creadas en el periodo. Su estado corresponde al momento de generar el reporte.

No se interpreta el periodo de intercambios como fecha de aprobación o resolución.

### Metadatos

- Empresa.
    
- Periodo.
    
- Zona horaria.
    
- Momento de generación.
    
- Referencia de las fuentes.
    

Las huellas técnicas se conservan para la exportación, sin exigir que el usuario las lea.

### Exportación CSV

1. Consultar el reporte.
    
2. Revisar el resultado.
    
3. Solicitar CSV con los mismos filtros y referencia de origen.
    
4. Esperar la generación.
    
5. Descargar si la fuente sigue siendo compatible.
    

Cambiar filtros deja pendiente una nueva consulta. No se exporta el resultado anterior con las etiquetas de los filtros nuevos.

Si cambió la información relevante:

> Los datos cambiaron desde tu consulta. Actualiza el reporte y revísalo antes de exportar.

No se descarga silenciosamente una versión distinta.

El CSV no representa una exportación completa de la empresa.

## UI-18 — Historial operativo

### Acceso

- Administrador: eventos empresariales autorizados.
    
- Supervisor: eventos operativos dentro de su alcance.
    
- Colaborador: sin acceso a esta sección; consulta los eventos permitidos en sus solicitudes.
    

### Presentación

- Fecha.
    
- Actor.
    
- Acción.
    
- Recurso.
    
- Motivo.
    
- Cambio relevante.
    

Los detalles de decisiones y advertencias se consultan a través de publicaciones, políticas o intercambios cuando estén autorizados.

No hay una pantalla para editar los registros de evidencia.

No se exponen secretos ni estructuras internas completas mediante una opción “avanzada”.

## UI-19 — Avisos

### Contenido

- Tipo de evento.
    
- Resumen.
    
- Fecha.
    
- Estado de lectura.
    
- Enlace autorizado.
    

### Acciones

- Abrir el recurso.
    
- Marcar como leído.
    

Marcar como leído es una función del MVP, no una opción pendiente.

Repetir la acción conserva la primera fecha de lectura.

No se incorpora “marcar todos” sin un contrato que lo respalde.

### Actualización

La bandeja consulta al abrirse y utiliza actualización periódica según la implementación acordada.

Esa actividad automática no mantiene indefinidamente una sesión que debería vencer por inactividad.

No se promete actualización instantánea entre todos los dispositivos.

### Casos especiales

- Recurso que dejó de ser accesible.
    
- Solicitud ya resuelta.
    
- Permiso revocado.
    
- Aviso antiguo.
    
- Entrega retrasada.
    

Leer un aviso no acepta un turno ni confirma formalmente su recepción.

# 13. Flujos prioritarios de extremo a extremo

## F-01 — Primera publicación

1. Administrador accede.
    
2. Completa la preparación indispensable.
    
3. Registra colaboradores si publicará asignaciones.
    
4. Crea un borrador semanal.
    
5. Introduce y guarda los turnos.
    
6. Valida.
    
7. Revisa la previsualización completa.
    
8. Resuelve bloqueos y completa confirmaciones.
    
9. Publica.
    
10. Un usuario con acceso personal consulta su horario.
    

**Resultado:** primera planificación oficial accesible.

La variante vacía exige confirmación y motivo, sin turnos ficticios.

## F-02 — Revisión de una semana publicada

1. Responsable consulta la publicación vigente.
    
2. Crea un borrador desde ella.
    
3. Modifica únicamente asignaciones permitidas.
    
4. Guarda.
    
5. Revisa diferencias e intercambios afectados.
    
6. Registra el motivo y confirmaciones requeridas.
    
7. Publica.
    
8. Los usuarios consultan la nueva versión.
    

**Resultado:** actualización oficial con historia preservada.

## F-03 — Intercambio completo

1. Usuario selecciona un turno propio.
    
2. Busca candidatos.
    
3. Revisa y envía.
    
4. El compañero acepta.
    
5. Un responsable independiente solicita la previsualización.
    
6. Revisa resultado y advertencias.
    
7. Confirma.
    
8. El servidor aplica ambas reasignaciones.
    
9. Los participantes consultan sus horarios actuales.
    

**Resultado:** intercambio íntegro, incluida la actualización conjunta si afecta dos semanas.

## F-04 — Conflicto concurrente

1. Un responsable prepara cambios.
    
2. Otra operación modifica el borrador o su publicación base.
    
3. El primer usuario intenta guardar o publicar.
    
4. El servidor rechaza la versión desactualizada.
    
5. La interfaz explica qué clase de conflicto ocurrió.
    
6. El usuario consulta el estado actual.
    
7. Decide cómo continuar sin reenvío automático.
    

**Resultado:** ningún cambio ajeno se sobrescribe silenciosamente.

## F-05 — Desactivación con compromisos

1. Administrador solicita previsualizar la desactivación de un colaborador.
    
2. Revisa compromisos pendientes.
    
3. Accede a los recursos autorizados.
    
4. Resuelve lo permitido mediante sus flujos normales.
    
5. Espera cuando un compromiso iniciado no puede modificarse.
    
6. Vuelve a evaluar.
    
7. Confirma cuando las reglas lo permitan.
    

**Resultado:** desactivación consistente sin eliminación automática de compromisos.

## F-06 — Recuperación de acceso

1. Usuario consulta la ayuda.
    
2. El responsable verifica identidad.
    
3. Emite y entrega un enlace temporal.
    
4. El usuario establece la contraseña.
    
5. Inicia una nueva sesión.
    
6. El acceso anterior queda invalidado según la política.
    

**Resultado:** recuperación sin revelar contraseñas existentes.

## F-07 — Exportación después de consultar

1. Responsable selecciona reporte y periodo.
    
2. Consulta.
    
3. Revisa resultados.
    
4. Solicita CSV.
    
5. El sistema comprueba la fuente.
    
6. Descarga o informa que debe actualizarse el reporte.
    
7. Si hubo cambios, el usuario vuelve a consultar antes de exportar.
    

**Resultado:** archivo coherente con la información revisada.

## F-08 — Respuesta perdida de una operación

1. El usuario confirma una intención.
    
2. La respuesta no llega.
    
3. La interfaz muestra resultado desconocido.
    
4. Conserva la referencia de esa intención.
    
5. Consulta el resultado recuperable o reintenta exactamente la misma operación dentro de las condiciones permitidas.
    
6. Distingue éxito, rechazo y ausencia de resultado recuperable.
    
7. Consulta después el estado vigente del recurso.
    

**Resultado:** recuperación sin asumir fracaso ni crear otra intención automáticamente.

## F-09 — Activación de política

1. Administrador introduce valores.
    
2. Solicita evaluación.
    
3. Revisa programación afectada.
    
4. Resuelve bloqueos.
    
5. Reconoce advertencias y registra motivo.
    
6. Confirma.
    
7. El servidor revalida.
    
8. Se muestra la nueva política activa o la necesidad de revisar nuevamente.
    

**Resultado:** política activada con decisión trazable, sin reescribir el historial.

# 14. Estados transversales de interfaz

|   |   |
|---|---|
|Estado|Comportamiento|
|Carga inicial|Mostrar progreso sin reutilizar datos de otra sesión.|
|Sin datos|Explicar la situación y la acción autorizada.|
|Sin resultados|Conservar filtros y permitir ajustarlos.|
|Cambios sin guardar|Indicar que siguen siendo locales.|
|Guardado confirmado|Mostrar que el borrador se conservó, sin confundirlo con publicación.|
|Validación bloqueante|Localizar el impedimento y su corrección.|
|Advertencia|Solicitar reconocimiento específico solo si la regla permite continuar.|
|Previsualización desactualizada|Solicitar una evaluación nueva.|
|Conflicto de versión|No sobrescribir ni reenviar con versiones actualizadas automáticamente.|
|Enviando|Impedir envíos repetidos y esperar confirmación.|
|Resultado desconocido|Consultar o recuperar la misma intención.|
|Rechazo confirmado|Explicar el motivo y permitir una decisión nueva.|
|Sesión vencida|Retirar información protegida y solicitar acceso.|
|Permiso perdido|Actualizar navegación y acciones.|
|Empresa suspendida|Mostrar restricción sin contenido empresarial de fondo.|
|Éxito confirmado|Informar brevemente y actualizar los datos afectados.|

## 14.1. Identidad de una operación

Las escrituras de negocio siguen la idempotencia de la nota 10.

La aplicación conserva la clave y el contenido de la intención en el controlador del flujo, no únicamente en el botón.

- Reintento de la misma intención: misma clave y contenido.
    
- Cambio de datos, motivo, versiones o confirmaciones: otra intención, después de resolver el resultado anterior.
    
- Una clave no se comparte entre usuarios ni empresas.
    

No se guardan contraseñas ni enlaces secretos mediante este mecanismo.

## 14.2. Recuperar el resultado

Recuperar un registro no equivale necesariamente a recuperar un éxito.

La interfaz interpreta el resultado de la operación original:

- Exitosa.
    
- Rechazada.
    
- Sin resultado recuperable visible.
    

Un resultado no encontrado no demuestra que nunca se ejecutó: podría seguir sin confirmarse, haber revertido o estar fuera de retención.

Dentro de la ventana prevista de 24 horas pueden aplicarse reintentos limitados con la misma intención. Fuera de ella no se promete deduplicación.

Una respuesta recuperada puede corresponder a una versión que después cambió. Por eso se consulta el estado vigente antes de permitir decisiones posteriores.

## 14.3. Límites de recuperación local

El MVP no promete conservar trabajo ni operaciones pendientes después de cerrar el navegador.

Si se perdió la referencia necesaria, no se reconstruye una nueva operación a ciegas. Se revisa el recurso y, si sigue sin poder determinarse lo ocurrido, se solicita ayuda mediante el procedimiento operativo.

No se repiten automáticamente comandos después de una restauración del servicio sin conciliar su resultado.

Las operaciones de seguridad siguen sus flujos específicos, no el registro genérico de resultados.

# 15. Formularios y navegación segura

## 15.1. Campos

- Etiquetas visibles.
    
- Obligatoriedad explícita.
    
- Ayudas breves.
    
- Unidades junto a valores.
    
- Fechas y zona horaria comprensibles.
    
- Errores próximos al campo.
    
- Resumen de errores cuando sea necesario.
    
- Conservación temporal de valores solo cuando sea segura.
    

Las contraseñas y secretos temporales no se conservan para reintentos indiscriminados.

## 15.2. Cambios sin guardar

Salir de un formulario modificado requiere advertencia cuando la aplicación pueda interceptar la navegación.

No se garantiza impedir el cierre del navegador ni recuperar el formulario después.

El MVP no tiene modo offline.

Ante vencimiento de sesión o pérdida de permisos, proteger los datos tiene prioridad sobre mantenerlos visibles.

## 15.3. Confirmaciones

Se utilizan para decisiones con impacto:

- Publicar.
    
- Reconocer advertencias.
    
- Confirmar semana vacía.
    
- Descartar un borrador.
    
- Aceptar, rechazar, cancelar o aprobar un intercambio.
    
- Desactivar recursos.
    
- Cambiar permisos o vinculaciones.
    
- Emitir enlaces sensibles.
    
- Activar políticas.
    
- Cambiar zona horaria cuando esté permitido.
    

El texto explica el efecto real. No utiliza un “¿Estás seguro?” sin contexto para decisiones complejas.

## 15.4. Enlaces directos

Antes de mostrar una ruta protegida:

1. Resolver sesión.
    
2. Comprobar acceso.
    
3. Consultar el recurso.
    
4. Mostrar el contenido permitido.
    

Las rutas de retorno tras iniciar sesión deben ser internas y autorizadas.

No se conserva una página de otra empresa como contenido provisional durante el acceso.

## 15.5. Actualización de vistas

Después de una escritura confirmada se actualizan las consultas afectadas.

Por ejemplo, una aprobación puede afectar solicitud, horario personal, planificación semanal y condición de borradores.

Esto no exige sincronización instantánea entre todos los navegadores. Al volver a una vista o ejecutar una acción, deben comprobarse los datos actuales.

# 16. Comportamiento móvil

## 16.1. Colaborador

Se priorizan:

- Próximo turno.
    
- Agenda semanal.
    
- Solicitudes recibidas.
    
- Solicitudes propias.
    
- Avisos.
    

Las acciones principales no dependen de desplazamiento horizontal.

## 16.2. Administrador y supervisor

La planificación dispone de una agenda por día o colaborador.

No se comprime toda la cuadrícula hasta hacerla ilegible.

Los formularios y previsualizaciones pueden ocupar una vista completa.

Las advertencias, diferencias e intercambios afectados siguen siendo revisables en móvil; no se ocultan para abreviar.

## 16.3. Navegación y controles

- Menú lateral en escritorio.
    
- Menú compacto en móvil.
    
- Organización identificable.
    
- Acciones táctiles sin depender de pasar el cursor.
    
- Foco y etiquetas comprensibles.
    
- Orden lógico de lectura.
    
- Confirmaciones accesibles sin recortes de contenido.
    

Los tamaños, colores y componentes concretos se definen en la nota 09.

# 17. Trazabilidad de pantallas

|   |   |   |
|---|---|---|
|Pantallas|Requisitos principales|Casos de uso|
|UI-01 a UI-04, UI-20|RF-004, RF-005|CU-02|
|UI-05|Entrada a procesos y resúmenes operativos incluidos|Acceso operativo|
|UI-06 a UI-08|RF-011 a RF-016|CU-05 a CU-08|
|UI-09|RF-016, RF-017|CU-08, CU-09|
|UI-10, UI-11|RF-017 a RF-020|CU-09 a CU-11|
|UI-12|RF-008|CU-04|
|UI-13|RF-009|CU-04|
|UI-14|RF-007 y consulta empresarial autorizada|CU-04|
|UI-15|RF-006 y emisión autorizada de enlaces de RF-005|CU-02, CU-03|
|UI-16|RF-010|CU-04|
|UI-17|RF-021, RF-022|CU-12|
|UI-18|RF-024|CU-13|
|UI-19|RF-023|CU-13|

RF-001, RF-002 y RF-003 se atienden mediante herramientas y procedimientos restringidos del operador.

No necesitan pantallas adicionales dentro del portal empresarial.

Los requisitos no funcionales de seguridad, concurrencia, exactitud temporal, usabilidad y recuperación se aplican transversalmente.

# 18. Pantallas fuera del MVP

No se incorporan:

- Landing comercial completa.
    
- Registro empresarial autoservicio.
    
- Selección y compra automática de planes.
    
- Pago con tarjeta.
    
- Facturación.
    
- Administración pública de plataforma.
    
- Asistencia y marcaciones.
    
- Nómina.
    
- Chat.
    
- Inteligencia artificial u optimización automática.
    
- Operación multisede completa.
    
- Solicitudes de vacaciones con otro flujo de aprobación.
    
- Constructor de reportes.
    
- Programación futura de políticas.
    
- Migración automática de zona tras publicar.
    
- Portal automático de baja y eliminación de empresa.
    

No se ocupan menús con pantallas “próximamente”.

La comercialización inicial puede apoyarse en demostraciones y una oferta asistida.

# 19. Verificación de experiencia

Antes de cerrar los wireframes debe comprobarse mediante tareas observables que:

1. Un colaborador encuentra su próximo turno y entiende ambas fechas de un turno nocturno.
    
2. Un responsable distingue cambios locales, borrador guardado y publicación.
    
3. Guardar con conflictos no se interpreta como permiso para publicar.
    
4. Una semana no publicada se distingue de una publicación vacía.
    
5. La confirmación vacía no permite retirar turnos iniciados.
    
6. Las advertencias se revisan individualmente y no sobreviven a una evaluación distinta.
    
7. El usuario entiende qué solicitudes invalidará una revisión.
    
8. Un participante con rol de supervisor no puede resolver su propio intercambio.
    
9. Aceptar como compañero no parece una aprobación final.
    
10. Un conflicto de versión no provoca sobrescritura silenciosa.
    
11. Una respuesta perdida no desencadena otra operación con una clave nueva.
    
12. Crear una cuenta se distingue de emitir su enlace inicial.
    
13. Cambiar políticas o zona requiere revisar el impacto correspondiente.
    
14. Los reportes explican qué periodo y datos representan.
    
15. Un CSV con origen modificado exige actualizar y revisar.
    
16. Leer un aviso no se interpreta como aceptación.
    
17. Las versiones históricas están identificadas permanentemente.
    
18. Las tareas esenciales pueden completarse en móvil.
    
19. No aparecen controles para funciones excluidas.
    
20. Los estados de falta de permisos o vinculación explican cómo continuar.
    

Las pruebas y sus evidencias se concretan en la nota 12.

# 20. Decisiones propuestas

- Portal empresarial único con navegación por permisos.
    
- Veinte pantallas principales, conservando sus identificadores.
    
- Herramienta del operador separada.
    
- Mi horario como entrada del colaborador.
    
- Planificación oficial separada del borrador.
    
- Guardado explícito.
    
- Validación distinta de previsualización y publicación.
    
- Publicación vacía con confirmación y motivo.
    
- Advertencias reconocidas de forma específica.
    
- Resolución de intercambios por un responsable independiente.
    
- Sin fusión automática de borradores.
    
- Recuperación de operaciones inciertas sin asumir fracaso.
    
- Dos reportes operativos conforme a la API.
    
- CSV vinculado al resultado consultado.
    
- Lectura de avisos sin efectos sobre decisiones de negocio.
    
- Agenda alternativa en móvil.
    
- Sin controles comerciales automáticos ni funciones futuras en la navegación.
    

Estas decisiones requieren contraste durante el diseño y las pruebas. No representan una experiencia ya validada con usuarios.

# 21. Documentos relacionados

- [[03 - Alcance MVP y Roadmap Comercial]]
    
- [[04 - Roles, Procesos y Reglas de Negocio]]
    
- [[05 - Requisitos y Casos de Uso]]
    
- [[06 - Arquitectura y Stack Tecnológico]]
    
- [[07 - Modelo de Datos y DER]]
    
- [[09 - Design System y Wireframes]]
    
- [[10 - Contratos API e Integraciones]]
    
- [[11 - Backlog MVP y Plan de Implementación]]
    
- [[12 - Pruebas, Seguridad y Operación]]
    
- [[13 - Lanzamiento Piloto y Evolución Comercial]]
    

# 22. Criterio de cierre

Esta nota estará lista como base de diseño cuando:

- Cada rol tenga una navegación comprensible.
    
- Todas las funciones del portal tengan ubicación.
    
- Las operaciones del operador estén separadas.
    
- Las acciones coincidan con permisos y contratos.
    
- Las pantallas no requieran endpoints o campos inventados.
    
- Borradores, publicaciones e historial se distingan.
    
- Las confirmaciones reflejen la evaluación realmente revisada.
    
- Los flujos contemplen versiones desactualizadas y resultados inciertos.
    
- Se respete la privacidad en candidatos, motivos y avisos.
    
- Exista una alternativa móvil viable.
    
- Los wireframes puedan elaborarse sin decidir nuevos procesos de negocio.
    
- Los cambios necesarios en diseño, backlog y pruebas queden identificados.
    

La revisión documental no demuestra que la interfaz esté implementada, que el MVP esté terminado o que el piloto pueda comenzar.