
## 1. Propósito

Definir una base visual reutilizable y los esquemas de las pantallas principales del MVP.

El diseño debe permitir que:

- El administrador configure su empresa y gestione accesos.
    
- El supervisor prepare, revise y publique horarios.
    
- El colaborador consulte sus turnos y gestione intercambios desde su teléfono.
    
- Todos distingan información oficial, cambios pendientes, advertencias y errores.
    

Esta nota traduce los comportamientos de la nota 08 a componentes, jerarquía visual y wireframes.

No incorpora funciones nuevas ni sustituye las reglas de negocio, los requisitos o los contratos API.

**Estado:** especificación de diseño. La interfaz implementada, las pruebas de accesibilidad y la validación con usuarios siguen pendientes.

## 2. Documentos relacionados

- [[03 - Alcance MVP y Roadmap Comercial]]
    
- [[04 - Roles, Procesos y Reglas de Negocio]]
    
- [[05 - Requisitos y Casos de Uso]]
    
- [[06 - Arquitectura y Stack Tecnológico]]
    
- [[07 - Modelo de Datos y DER]]
    
- [[08 - Navegación, Pantallas y Flujos de Usuario]]
    
- [[10 - Contratos API e Integraciones]]
    
- [[11 - Backlog MVP y Plan de Implementación]]
    
- [[12 - Pruebas, Seguridad y Operación]]
    

### Responsabilidad de esta nota

Define:

- Apariencia inicial.
    
- Componentes compartidos.
    
- Presentación de estados.
    
- Jerarquía de información.
    
- Distribución de controles.
    
- Adaptación móvil.
    
- Esquemas de los flujos principales.
    

Los permisos, transiciones, validaciones y datos disponibles proceden de las notas 04, 05, 08 y 10.

Si un wireframe necesita una acción o campo que no existe en esos documentos, debe resolverse la discrepancia antes de implementarlo.

## 3. Principios de diseño

### 3.1. Primero la tarea

La interfaz prioriza horarios, planificación e intercambios.

No se agregan gráficos decorativos, indicadores sin datos reales ni funciones comerciales futuras.

### 3.2. Estado explícito

El usuario debe reconocer:

- Cambios locales sin guardar.
    
- Borrador guardado.
    
- Resultado de validación.
    
- Publicación vigente.
    
- Publicación histórica.
    
- Solicitud pendiente.
    
- Intercambio aplicado.
    
- Operación cuyo resultado aún se desconoce.
    

El color ayuda, pero el texto comunica el significado.

### 3.3. Complejidad proporcional al rol

El colaborador recibe una experiencia centrada en su horario.

Administradores y supervisores acceden a funciones operativas según sus permisos.

Los componentes no deducen la autorización únicamente a partir del rol: utilizan también las acciones disponibles y restricciones devueltas por el servidor.

### 3.4. Efectos visibles antes de confirmar

Antes de publicar, aprobar o activar una política se presentan los efectos reales de la decisión.

La interfaz no muestra éxito antes de recibir confirmación.

Cerrar un diálogo después de enviar una operación no equivale a cancelarla en el servidor.

### 3.5. Profesional y sobrio

Se priorizan jerarquía, legibilidad y consistencia.

Se evitan:

- Transparencias que dificulten leer.
    
- Animaciones constantes.
    
- Fondos recargados.
    
- Exceso de tarjetas.
    
- Controles diminutos para comprimir información.
    
- Confirmaciones genéricas sin explicar consecuencias.
    

### 3.6. Diseño dentro del alcance

No se agregan filtros, exportaciones o botones sin respaldo en los contratos.

Una pantalla “próximamente” no cuenta como funcionalidad del MVP.

## 4. Identidad visual inicial

### 4.1. Nombre visible

**TurnoShift**

“SaaS” describe el modelo de negocio, pero no necesita aparecer en cada pantalla.

Se propone una marca tipográfica sencilla. No es necesario diseñar un logotipo complejo para iniciar el desarrollo.

### 4.2. Personalidad

- Clara.
    
- Cercana.
    
- Operativa.
    
- Confiable.
    

El lenguaje visible utiliza:

- Colaborador.
    
- Turno.
    
- Horario.
    
- Borrador.
    
- Publicación.
    
- Intercambio.
    
- Indisponibilidad.
    

Los identificadores técnicos pueden utilizar “employee” internamente, pero no obligan a variar el vocabulario de la interfaz.

No se muestran términos como `tenant`, `payload` o nombres de excepciones al usuario.

### 4.3. Tema

El MVP utiliza tema claro.

El modo oscuro queda fuera de esta versión.

Los colores se centralizan para mantener consistencia, sin convertir el sistema de temas en otro proyecto.

## 5. Colores

### 5.1. Paleta propuesta

|Uso|Color|Aplicación|
|---|---|---|
|Fondo general|`#F8FAFC`|Área de trabajo|
|Superficie|`#FFFFFF`|Formularios y paneles|
|Texto principal|`#0F172A`|Títulos y contenido|
|Texto secundario|`#475569`|Ayudas y metadatos|
|Primario|`#0F766E`|Acción principal|
|Primario al interactuar|`#115E59`|Interacción con acción principal|
|Borde decorativo|`#CBD5E1`|Separadores y contenedores|
|Borde de control|`#64748B`|Límites de campos|
|Información y foco|`#1D4ED8`|Enlaces, información y foco|
|Éxito|`#166534`|Resultado confirmado|
|Advertencia|`#92400E`|Situación que requiere revisión|
|Error|`#B91C1C`|Fallos y bloqueos|

### Fondos semánticos

|   |   |   |
|---|---|---|
|Tipo|Fondo|Texto|
|Información|`#EFF6FF`|`#1D4ED8`|
|Éxito|`#F0FDF4`|`#166534`|
|Advertencia|`#FFFBEB`|`#92400E`|
|Error|`#FEF2F2`|`#B91C1C`|

### 5.2. Contraste

Se recalcularon las siguientes combinaciones de colores sólidos:

|   |   |
|---|---|
|Combinación|Contraste aproximado|
|Texto principal sobre blanco|17.85:1|
|Texto secundario sobre blanco|7.58:1|
|Blanco sobre primario|5.47:1|
|Información sobre su fondo|6.16:1|
|Éxito sobre su fondo|6.81:1|
|Advertencia sobre su fondo|6.84:1|
|Error sobre su fondo|5.91:1|

La referencia de contraste mínimo es 4.5:1 para texto normal y 3:1 para texto grande bajo las condiciones del criterio. Los valores aproximados sirven para documentar la paleta; la comprobación contra el umbral no debe depender del redondeo. [W3C: contraste mínimo](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).

Las partes visuales necesarias para identificar controles y estados también requieren comprobar su contraste con los colores adyacentes. El borde decorativo no se utilizará como único límite reconocible de un campo. [W3C: contraste no textual](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html).

Estas comprobaciones no certifican la accesibilidad de la aplicación.

Todavía deben probarse estados de interacción, foco, selección, error, ampliación y contenido real.

### 5.3. Significado

El color siempre se acompaña de texto.

Ejemplos:

- “Borrador”, no solo fondo amarillo.
    
- “Publicado”, no solo punto verde.
    
- “Conflicto de horario”, no solo borde rojo.
    
- “Pendiente de aprobación”, no solo icono de reloj.
    

No se asigna un color exclusivo a cada colaborador.

Una selección visual tampoco significa aceptación de una advertencia.

## 6. Tipografía y medidas

### 6.1. Tipografía

Familia propuesta:

`system-ui, "Segoe UI", sans-serif`

Se utiliza la tipografía disponible en el dispositivo, sin depender de una descarga externa.

|   |   |
|---|---|
|Elemento|Tamaño orientativo|
|Título de página|28 px|
|Título de sección|24 px|
|Título de panel|20 px|
|Texto y campos|16 px|
|Etiquetas y tablas|14 px|
|Metadatos auxiliares|12 px|

Reglas:

- Implementar tamaños que admitan escalado.
    
- No utilizar 12 px para información esencial.
    
- Evitar párrafos en mayúsculas.
    
- Alinear el contenido general a la izquierda.
    
- Usar cifras de ancho uniforme cuando facilite comparar horas.
    
- Permitir que nombres, motivos y mensajes ocupen varias líneas.
    

### 6.2. Espaciado

Escala:

`4 · 8 · 12 · 16 · 24 · 32 · 48`

Aplicación inicial:

- 8 px entre icono y texto.
    
- 8 px entre etiqueta y campo.
    
- 16 px entre campos relacionados.
    
- 24 px entre grupos.
    
- 24–32 px entre secciones.
    

### 6.3. Formas

- Controles: radio de 8 px.
    
- Tarjetas: radio de 12 px.
    
- Diálogos: radio de 16 px.
    
- Sombras discretas para elementos superpuestos.
    

La jerarquía no depende de envolver cada elemento en una tarjeta.

### 6.4. Áreas de interacción

Objetivo interno: **44 × 44 px** para controles táctiles.

Es una decisión de producto. No debe confundirse con el criterio general de tamaño mínimo de WCAG 2.2 AA, que contempla 24 × 24 CSS px y excepciones. [W3C: tamaño mínimo de objetivos](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html).

Los controles adyacentes deben poder activarse sin pulsar accidentalmente otra acción.

## 7. Estructura adaptable

### 7.1. Escritorio

- Encabezado global.
    
- Navegación lateral.
    
- Área principal.
    
- Panel contextual cuando sea útil.
    

Referencias iniciales:

- Navegación lateral: 240 px.
    
- Formularios: hasta 720 px.
    
- Contenido general: hasta 1280 px.
    
- Planificación: espacio disponible según contenido.
    

### 7.2. Tableta

- Navegación plegable.
    
- Filtros en varias filas.
    
- Paneles debajo del contenido si falta espacio.
    
- Comparaciones apiladas cuando sea necesario.
    

### 7.3. Móvil

- Encabezado compacto.
    
- Menú accesible.
    
- Una columna principal.
    
- Formularios de ancho disponible.
    
- Agenda en lugar de cuadrícula comprimida.
    
- Acciones importantes identificables.
    

|   |   |
|---|---|
|Ancho orientativo|Comportamiento|
|Menor de 768 px|Móvil|
|768–1023 px|Intermedio|
|Desde 1024 px|Escritorio|

Estos puntos se ajustarán al probar contenido y ampliación, no solo por tipo de dispositivo.

### 7.4. Calendario

En escritorio se propone una vista semanal por colaborador.

En móvil se utiliza agenda por día o colaborador.

No se exige arrastrar turnos para completar ninguna tarea.

Una tabla amplia puede desplazarse dentro de su contenedor, pero debe existir una alternativa utilizable sin recorrer horizontalmente toda la página.

### 7.5. Previsualizaciones extensas

La revisión de una publicación o política puede ocupar una vista completa cuando los impactos no caben cómodamente en un diálogo.

En móvil no se eliminan diferencias, advertencias ni intercambios afectados para reducir altura.

La acción final no debe tapar el contenido o el elemento que tiene el foco.

## 8. Componentes reutilizables

Se conservan los identificadores DS-01 a DS-14.

|   |   |   |
|---|---|---|
|ID|Componente|Responsabilidad|
|DS-01|Estructura de aplicación|Organización, navegación y cuenta|
|DS-02|Encabezado de página|Título, contexto y acción principal|
|DS-03|Botón|Acción explícita y estado de envío|
|DS-04|Campo de formulario|Etiqueta, ayuda, error y obligatoriedad|
|DS-05|Selector con búsqueda|Opciones extensas autorizadas|
|DS-06|Etiqueta de estado|Texto obligatorio y color complementario|
|DS-07|Mensaje contextual|Información, advertencia, error o resultado|
|DS-08|Tabla o lista adaptable|Datos, encabezados y acciones|
|DS-09|Tarjeta de turno|Fechas, horas, puesto y contexto|
|DS-10|Panel o diálogo|Detalle, edición acotada y confirmación|
|DS-11|Resumen de validación|Bloqueos, advertencias y reconocimiento|
|DS-12|Comparación de intercambio|Situación original, propuesta o aplicada|
|DS-13|Estado de contenido|Carga, vacío, fallo, restricción e incertidumbre|
|DS-14|Filtros y paginación|Alcance de consulta y navegación de resultados|

### 8.1. Botones

Cada decisión tiene una acción principal reconocible.

|   |   |
|---|---|
|Contexto|Acción|
|Formulario de asignación|Guardar turno|
|Borrador|Revisar publicación|
|Confirmación de publicación|Publicar horario|
|Respuesta del compañero|Aceptar propuesta|
|Detalle para el responsable|Revisar aprobación|
|Confirmación de aprobación|Aprobar intercambio|
|Política|Revisar impacto|
|Confirmación de política|Activar política|

Los botones de impacto utilizan verbos específicos: “Descartar borrador”, no “Aceptar”.

Durante el envío:

- Se evita repetir la misma intención.
    
- Se indica qué operación está ocurriendo.
    
- Se anuncia el estado.
    
- No se presenta éxito anticipado.
    

Si una acción está bloqueada, se explica la causa cerca del control. No se deja únicamente un botón gris sin explicación.

### 8.2. Formularios

Todos los campos tienen etiquetas visibles.

El texto de ejemplo dentro de un campo no sustituye su etiqueta.

Los errores:

- Identifican qué corregir.
    
- Aparecen junto al campo.
    
- Se resumen cuando son varios.
    
- Conservan valores no sensibles cuando sea seguro.
    

Las contraseñas y secretos temporales no se guardan en mecanismos de recuperación de formularios.

Los campos de tiempo muestran la zona horaria y utilizan la precisión admitida por la API.

### 8.3. Diálogos

Se reservan para decisiones acotadas.

Deben tener nombre accesible, gestión de foco, salida clara y devolución del foco al elemento apropiado al cerrarse. El contenido exterior no debe seguir siendo interactivo mientras el diálogo modal está activo. [W3C: patrón de diálogo modal](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/).

Cerrar una previsualización antes de confirmar no aplica cambios.

Si la operación ya fue enviada, cerrar la presentación no cancela el procesamiento ni elimina su referencia de seguimiento.

### 8.4. Iconos

- Estilo consistente.
    
- Texto junto al icono en acciones importantes.
    
- Nombre accesible para botones de solo icono.
    
- Ayuda adicional cuando el significado no sea evidente.
    
- Sin depender del icono para comunicar un estado.
    

La biblioteca concreta sigue pendiente de selección y revisión de licencia.

### 8.5. Advertencias y confirmaciones

DS-11 presenta cada advertencia con:

- Colaborador.
    
- Periodo.
    
- Valor calculado.
    
- Umbral configurado.
    
- Explicación.
    
- Casilla de reconocimiento sin marcar.
    

No existe una casilla permanente para ignorar advertencias futuras.

El motivo de la decisión se registra por separado. Una justificación puede acompañar el conjunto de advertencias reconocidas; no sustituye sus aceptaciones.

Las confirmaciones se invalidan cuando cambia la evaluación.

La publicación vacía utiliza una confirmación distinta: no se representa como una advertencia ficticia de horas.

### 8.6. Tablas, listas y selectores

- Solo incluyen campos autorizados.
    
- Sus filtros coinciden con el contrato.
    
- La paginación indica qué parte de los resultados está visible.
    
- Filtrar una página no debe aparentar que se filtró todo el conjunto.
    
- Las acciones de cada fila tienen contexto identificable.
    
- Los motivos extensos se consultan sin recortarlos de forma irrecuperable.
    

### 8.7. Resultado desconocido

DS-13 diferencia:

- Enviando.
    
- Resultado confirmado.
    
- Rechazo confirmado.
    
- Resultado aún desconocido.
    

Un mensaje de incertidumbre no utiliza el estilo de “operación fallida” como si se supiera que no hubo cambios.

El seguimiento de la operación pertenece al flujo de aplicación, no al estado local de un botón que puede desaparecer.

## 9. Estados y lenguaje de interfaz

### 9.1. Horarios

|   |   |
|---|---|
|Situación|Presentación|
|Sin publicación|“Esta semana todavía no tiene un horario publicado.”|
|Cambios locales|“Tienes cambios sin guardar.”|
|Borrador guardado|“Borrador guardado. Todavía no es el horario oficial.”|
|Borrador con bloqueos|“Guardado. Hay conflictos que impiden publicar.”|
|Evaluación favorable|“No se detectaron bloqueos en esta evaluación.”|
|Publicación vigente|“Horario publicado · Versión N”|
|Publicación vacía|“Semana publicada sin turnos”|
|Sin asignaciones propias|“No tienes turnos asignados en esta semana.”|
|Publicación histórica|“Versión histórica · Consultar horario vigente”|
|Borrador desactualizado|“La publicación base cambió. Revisa antes de continuar.”|

Una celda vacía no se etiqueta automáticamente como “Disponible”, “Descanso” o “Ausente”.

### 9.2. Intercambios

|   |   |
|---|---|
|Estado interno|Etiqueta visible|
|`PENDIENTE_COMPANERO`|Esperando al compañero|
|`PENDIENTE_SUPERVISOR`|Pendiente de aprobación|
|`APROBADA`|Intercambio aprobado|
|`RECHAZADA`|Solicitud rechazada|
|`CANCELADA`|Solicitud cancelada|
|`VENCIDA`|Solicitud vencida|
|`INVALIDADA`|Solicitud invalidada|

El detalle indica quién puede actuar y qué efecto tendrá la acción.

Un responsable participante no puede aprobar ni rechazar como revisor independiente. Conserva únicamente las acciones personales que le correspondan.

### 9.3. Mensajes importantes

**Después de aceptar una propuesta:**

> Aceptaste la propuesta. Los horarios todavía no cambiaron: falta la aprobación de un responsable independiente.

**Ante un conflicto de versión:**

> Otra operación modificó estos datos. Revisa la versión actual antes de continuar.

**Ante resultado desconocido:**

> No pudimos confirmar el resultado. La operación podría haberse completado.

Solo se añadirá “Estamos comprobándolo” mientras exista una comprobación activa.

**Ante una advertencia de horas:**

> Se supera el umbral semanal configurado para este colaborador. Revisa el periodo y justifica la decisión si continúas.

**Ante un reporte modificado:**

> Los datos cambiaron desde tu consulta. Actualiza y revisa el reporte antes de exportar.

### 9.4. Vacíos, fallos y restricciones

Se distinguen:

- No existe información.
    
- No hay resultados para los filtros.
    
- No hay publicación.
    
- No hay turnos propios.
    
- No existe vinculación personal activa.
    
- No hay permiso.
    
- Falló la consulta.
    
- El recurso dejó de estar disponible.
    

Un error de red no se representa como agenda vacía.

## 10. Convenciones de los wireframes

Los esquemas muestran distribución y jerarquía, no medidas exactas.

### Notación

- `[Acción]`: botón.
    
- `(Valor ▼)`: selector.
    
- `☐`: confirmación sin marcar.
    
- `[Campo]`: entrada de datos.
    
- Separadores: regiones de contenido.
    

Las instrucciones escritas debajo de cada esquema forman parte de su especificación.

### Datos ficticios

- Empresa: Café Demo.
    
- Colaboradores: Ana y Bruno.
    
- Responsable independiente: Lucía.
    
- Puesto: Barista.
    
- Semana: 31 de agosto al 6 de septiembre de 2026.
    
- Zona: America/Lima.
    

Las cifras y nombres de los esquemas son ilustrativos. No son datos de una empresa real, políticas aprobadas ni resultados de una API ejecutada.

Los estados de carga de la aplicación no utilizan estos datos como contenido provisional.

## 11. WF-01 — Acceso

**Pantalla:** UI-01.

```
TurnoShift

Ingresa a tu cuenta

Código de empresa
[________________________________]

Correo
[________________________________]

Contraseña
[________________________] [Ver]

[Ingresar]

¿Necesitas recuperar el acceso?
```

### Reglas

- Sin registro empresarial público.
    
- Sin acceso social.
    
- Sin selector de rol.
    
- Sin acceso del operador.
    
- Error de credenciales genérico.
    
- Mostrar la contraseña no borra el campo.
    
- La pantalla de destino depende del contexto autorizado.
    

### Variantes relacionadas

**UI-02:** instrucciones del procedimiento asistido, sin simular envío de correo.

**UI-03:** nueva contraseña y confirmación; inspección del enlace sin consumirlo y resultado seguro.

**UI-04:** restricción confirmada por el servidor, sin datos protegidos detrás.

No se confirma públicamente la existencia de una cuenta a partir de un intento de acceso.

Si la respuesta del acceso se pierde, se comprueba la sesión antes de volver a enviar credenciales.

## 12. WF-02 — Planificación semanal

**Pantalla:** UI-06.

```
TurnoShift · Café Demo                 Avisos · Lucía

Planificación
31 ago.–6 sep. de 2026 · America/Lima

[Anterior] [Semana actual] [Siguiente]

Horario publicado · Versión 3
[Crear borrador] [Ver historial]

Colaborador (Todos ▼)

Colaborador | Lun 31 | Mar 1 | Mié 2 | ... | Dom 6
------------|--------|-------|-------|-----|------
Ana         | 08–16  | —     | 08–16 | ... | —
Bruno       | —      | 08–16 | —     | ... | —

— Sin asignación en esta vista.
```

### Reglas

- El esquema abrevia columnas; la vista completa incluye los siete días.
    
- Se permite filtrar la presentación únicamente con soporte real de datos.
    
- La acción disponible cambia a “Continuar borrador” si ya existe uno.
    
- Consultar una semana no crea registros.
    
- La vista oficial permanece separada del editor.
    
- Cada turno puede abrirse mediante teclado.
    
- Los turnos nocturnos muestran también la fecha final.
    
- No existe selector de varias sedes.
    

La navegación lateral utiliza el rol actual. “Mi horario” aparece cuando corresponde al acceso personal.

### Variantes obligatorias

- Sin publicación ni borrador.
    
- Borrador sin publicación.
    
- Publicación vigente.
    
- Publicación vacía.
    
- Publicación con borrador.
    
- Borrador desactualizado.
    

Un filtro de la cuadrícula no reduce el alcance de una publicación: la decisión sigue afectando la semana completa.

## 13. WF-03 — Editor de borrador

**Pantalla:** UI-07.

```
Editar planificación                              [Volver]

31 ago.–6 sep. de 2026 · America/Lima
BORRADOR · Basado en publicación 3

Estos cambios todavía no son el horario oficial.
Último guardado confirmado: 10:42

[Agregar turno] [Validar] [Revisar publicación]

----------------------------------------------------------
Planificación propuesta       | Editar turno
                              |
Turno seleccionado            | Colaborador (Ana ▼)
                              | Puesto: Barista
                              | Plantilla (Opcional ▼)
                              |
                              | Inicio
                              | [31/08/2026] [08:00]
                              | Fin
                              | [31/08/2026] [16:00]
                              |
                              | Duración programada: 8 h
                              |
                              | Cambios sin guardar
                              | [Guardar turno] [Cancelar]
----------------------------------------------------------

Validación
Bloqueos: 1 · Advertencias: 1

Conflicto de horario de Ana.
[Revisar asignaciones]

Se supera un umbral semanal.
[Ver detalle]

[Descartar borrador]
```

### Reglas

- Guardado explícito.
    
- No se muestra “guardado” antes de confirmación.
    
- El puesto respeta la compatibilidad con el colaborador.
    
- El tipo de turno es opcional.
    
- Los datos estructuralmente inválidos no se guardan.
    
- Los conflictos operativos admitidos pueden conservarse en borrador, pero bloquean publicar cuando corresponda.
    
- Las asignaciones iniciadas no se modifican.
    
- Los conflictos de semanas adyacentes también se explican.
    
- Editar invalida la evaluación anterior.
    
- Antes de validar o previsualizar se resuelven los cambios locales sin guardar.
    
- No se exige arrastrar turnos.
    

En móvil, el formulario ocupa una vista de una columna.

### Variante: borrador desactualizado

```
La publicación base cambió.

Este borrador no puede publicarse en su estado actual.
Puedes consultar su contenido y revisar el horario vigente.

[Ver publicación vigente]
[Descartar borrador]
```

Descartar y crear otro borrador son operaciones diferenciadas. No se presenta una fusión o regeneración automática que la API no implementa.

No se actualiza la versión esperada y se reenvía la edición sin una decisión del usuario.

## 14. WF-04 — Confirmación de publicación

**Pantalla:** previsualización asociada a UI-07.

```
Revisar publicación                              [Cerrar]

31 ago.–6 sep. de 2026 · America/Lima
Se reemplazará la publicación vigente.

Cambios de toda la semana
[Ver agregados, modificaciones y retiros]
[Ver colaboradores afectados]

Bloqueos: ninguno en esta evaluación

Advertencias
Ana · 31 ago.–6 sep.
Programado: 44 h · Umbral configurado: 40 h
☐ Revisé esta advertencia.

Intercambios afectados
[Ver solicitudes que se invalidarán]

Motivo del cambio — obligatorio
[________________________________________________]
[________________________________________________]

[Volver al borrador] [Publicar horario]
```

Las 44 y 40 horas son datos ficticios para ilustrar el componente, no parámetros recomendados para el piloto.

### Información obligatoria

La previsualización utiliza datos del servidor:

- Publicación base.
    
- Semana.
    
- Política evaluada.
    
- Asignaciones agregadas, modificadas y retiradas.
    
- Colaboradores afectados.
    
- Bloqueos.
    
- Advertencias.
    
- Solicitudes que se invalidarán.
    
- Requisitos de confirmación.
    

No se omiten impactos relevantes porque exista un filtro en la cuadrícula o porque el resumen sea largo.

### Advertencias

- Se presentan individualmente.
    
- Las casillas empiezan sin marcar.
    
- Se identifica colaborador, periodo, cantidad y umbral.
    
- Se requiere motivo cuando hay advertencias.
    
- La confirmación corresponde a esa evaluación.
    
- Una evaluación distinta invalida las aceptaciones anteriores.
    

### Motivo

|   |   |
|---|---|
|Situación|Obligatorio|
|Primera publicación con turnos y sin advertencias|No|
|Revisión mediante borrador|Sí|
|Advertencias reconocidas|Sí|
|Publicación vacía|Sí|

### Variante: publicación vacía

```
Publicar semana sin turnos

La nueva publicación no tendrá asignaciones.
Esto no confirma disponibilidad del equipo.

☐ Confirmo que esta semana se publicará sin turnos.

Motivo — obligatorio
[________________________________________________]

[Volver al borrador] [Publicar semana vacía]
```

La confirmación vacía es independiente de las advertencias.

No permite retirar asignaciones iniciadas que deban conservarse.

### Confirmación y resultado

- El servidor vuelve a validar.
    
- Un cambio relevante obliga a revisar nuevamente.
    
- El éxito muestra la nueva publicación.
    
- La entrega retrasada de avisos no se presenta como reversión de la publicación.
    
- Una respuesta perdida se muestra como resultado desconocido.
    

Si se necesita más espacio, esta revisión utiliza una vista completa en lugar de un diálogo comprimido.

## 15. WF-05 — Mi horario en móvil

**Pantalla:** UI-09.

```
[Menú] TurnoShift                  [Avisos]
Café Demo

Mi horario
America/Lima

Próximo turno
Lunes 31 de agosto
08:00–16:00 · Barista
Publicado

[Ver detalle]
[Solicitar intercambio]

[Anterior] 31 ago.–6 sep. [Siguiente]

Lunes 31
08:00–16:00 · Barista

Martes 1
Sin turno asignado

Miércoles 2
08:00–16:00 · Barista

[Ver mis solicitudes]
```

### Reglas

- Solo aparecen asignaciones propias publicadas.
    
- El próximo turno se obtiene del dato autorizado correspondiente.
    
- Una solicitud pendiente no modifica el turno oficial.
    
- El control de intercambio aparece cuando esté permitido.
    
- Los turnos que terminan otro día muestran ambas fechas.
    
- No hay controles de asistencia, nómina o declaración de disponibilidad.
    

### Variantes

1. No existe publicación semanal.
    
2. La semana se publicó sin turnos.
    
3. Hay publicación, pero no asignaciones propias.
    
4. No existe próximo turno.
    
5. No existe vinculación personal activa.
    
6. Falló la consulta.
    

Esos estados no comparten un único mensaje genérico de “sin datos”.

Si un turno deja de pertenecer al usuario después de un intercambio, el detalle personal respeta la propiedad actual. El historial autorizado de su solicitud se consulta por separado.

## 16. WF-06 — Propuesta y aprobación de intercambio

**Pantallas:** UI-10 y UI-11.

### Variante: responsable independiente

```
Solicitud de intercambio
Pendiente de aprobación

Ana propuso · Bruno aceptó
Falta una decisión independiente.

HORARIO ORIGINAL              RESULTADO PROPUESTO

Ana                           Ana
Lun 31 ago.                   Mar 1 sep.
08:00–16:00                   08:00–16:00

Bruno                         Bruno
Mar 1 sep.                    Lun 31 ago.
08:00–16:00                   08:00–16:00

Barista · America/Lima
Vence al iniciar el primero de los dos turnos.

Historial
Ana envió la propuesta.
Bruno aceptó.

[Rechazar solicitud] [Revisar aprobación]
```

La variante corresponde a Lucía como responsable independiente.

**“Revisar aprobación” no aplica el intercambio.** Obtiene la previsualización actual.

### Confirmación final

```
Revisar aprobación

[Comparación actualizada de ambos turnos]
[Semanas afectadas]

Bloqueos y advertencias de esta evaluación
[Reconocimientos individuales cuando correspondan]

Motivo
[Campo obligatorio si lo requiere la evaluación]

[Volver a la solicitud] [Aprobar intercambio]
```

### Variantes por usuario

|   |   |
|---|---|
|Contexto|Controles|
|Destinatario antes de responder|Aceptar propuesta / Rechazar|
|Solicitante mientras siga pendiente|Cancelar solicitud|
|Responsable independiente en revisión|Revisar aprobación / Rechazar con motivo|
|Responsable que también participa|Solo acciones personales correspondientes|
|Solicitud terminal|Consulta de resultado e historial|

Un participante no puede aprobar ni rechazar en calidad de responsable independiente.

El destinatario con rol de supervisor sí puede rechazar como compañero durante la primera etapa.

### Propuesta inicial

El asistente reutiliza DS-09 y DS-12:

1. Turno propio.
    
2. Candidatos permitidos.
    
3. Comparación.
    
4. Envío.
    

Los candidatos muestran únicamente datos mínimos. No exponen calendarios completos ni motivos de indisponibilidad.

Consultar candidatos no reserva turnos.

### Reglas de aprobación

- Comparar ambas personas y ambos turnos.
    
- Mostrar las dos semanas si son diferentes.
    
- Reconocer advertencias específicas.
    
- Revalidar al confirmar.
    
- No mostrar una segunda publicación manual.
    
- No representar una sola reasignación como éxito.
    
- Mostrar resultado desconocido si se pierde la respuesta.
    

En móvil las comparaciones se apilan manteniendo nombre, fecha y horario.

### Solicitud terminada

Se diferencia entre:

- Lo propuesto.
    
- Lo aplicado por esa decisión.
    
- El horario vigente actual, si hubo cambios posteriores.
    

Una solicitud invalidada o vencida no ofrece un botón para reabrirla.

## 17. WF-07 — Equipo y desactivación

**Pantalla:** UI-12.

```
Equipo                              [Crear colaborador]

[Controles de consulta definidos en el contrato]

Nombre | Puesto  | Estado | Vinculación | Acción
-------|---------|--------|-------------|-------
Ana    | Barista | Activo | Con cuenta  | [Ver]
Bruno  | Barista | Activo | Con cuenta  | [Ver]

[Anterior] [Siguiente]
```

La vinculación y los controles administrativos solo aparecen para perfiles autorizados.

No se dibujan búsquedas o filtros nuevos sin soporte contractual.

### Desactivación bloqueada

```
No se puede desactivar a Ana todavía.

Compromisos detectados:
• Asignaciones publicadas no finalizadas.
• Solicitudes pendientes.
• Asignaciones en borradores abiertos.

[Revisar recursos autorizados] [Cerrar]
```

La lista real utiliza los compromisos encontrados, no todos los tipos de bloqueo indiscriminadamente.

### Reglas

- La consulta previa no garantiza que las condiciones sigan iguales al confirmar.
    
- No se eliminan ni reasignan compromisos automáticamente.
    
- No se retiran turnos iniciados para forzar la desactivación.
    
- La desactivación de una cuenta y la del colaborador se presentan como acciones diferentes.
    
- La vinculación se administra mediante el flujo de Usuarios.
    
- El supervisor recibe la variante de consulta, sin acciones administrativas.
    

## 18. WF-08 — Reporte de horas programadas

**Pantalla:** UI-17.

```
Reportes
(Horas programadas ▼)

Calculadas a partir de publicaciones vigentes.

Desde [________] Hasta [________]
Colaborador (Todos ▼)
[Consultar]

Resultado del periodo consultado
America/Lima · Generado: fecha y hora

Colaborador | Horas programadas
------------|------------------
Ana         | Valor calculado
Bruno       | Valor calculado

[Exportar CSV]

No representa asistencia ni horas trabajadas.
```

Los textos “Valor calculado” son marcadores del esquema documental. La aplicación muestra resultados reales o un estado de carga.

### Reglas

- Máximo de 92 días.
    
- Periodo y zona claramente identificados.
    
- Los borradores no se suman.
    
- Los segmentos de turnos se distribuyen según el periodo consultado.
    
- Solo se muestran columnas que realmente entregue el contrato.
    
- No se agregan nómina, costes o ahorros.
    

### Exportación

“Exportar CSV” queda disponible después de consultar correctamente.

Si cambian los filtros, debe ejecutarse otra consulta antes de exportar.

La solicitud utiliza los mismos filtros y la referencia del resultado revisado.

### Variante: origen modificado

```
Los datos cambiaron desde tu consulta.

Actualiza y revisa el reporte antes de exportarlo.

[Actualizar reporte]
```

No se descarga silenciosamente otra versión.

### Variante: intercambios

Reutiliza el mismo esquema con los datos definidos para ese reporte.

Debe mostrar:

> Solicitudes creadas en el periodo seleccionado. Estados evaluados al generar el reporte.

No se presenta como reporte de solicitudes resueltas en ese periodo.

El MVP tiene dos reportes operativos: horas programadas e intercambios.

## 19. Composición de las demás pantallas

|   |   |
|---|---|
|Pantalla|Composición y estados esenciales|
|UI-02 — Ayuda de recuperación|Instrucciones breves, responsable y canal habilitado; sin prometer correo automático|
|UI-03 — Nueva contraseña|Formulario, inspección del enlace, resultado confirmado o incierto|
|UI-04 — Acceso restringido|Mensaje seguro y siguiente acción, sin datos protegidos detrás|
|UI-05 — Inicio operativo|Tareas y preparación según rol; contadores respaldados por datos|
|UI-08 — Publicación e historial|Solo lectura, origen, versión, decisión y advertencias autorizadas|
|UI-10 — Bandeja de intercambios|Ámbito personal o de gestión, estados, paginación y acciones actuales|
|UI-13 — Indisponibilidades|Lista y formulario; motivos restringidos y bloqueos contra publicaciones|
|UI-14 — Organización y catálogos|Secciones administrativas; evaluación de zona y límites del MVP|
|UI-15 — Usuarios|Cuenta, rol, vínculo y emisión separada de enlaces temporales|
|UI-16 — Políticas|Valores, previsualización, advertencias, motivo y activación inmediata|
|UI-18 — Historial operativo|Eventos legibles, actor, fecha, motivo y recursos autorizados|
|UI-19 — Avisos|Avisos propios, lectura y acceso autorizado al recurso|
|UI-20 — Mi cuenta|Identidad de consulta, cambio de contraseña y cierre de sesión|

### 19.1. Publicación e historial

La etiqueta histórica permanece visible.

Autor, motivo y advertencias proceden de la decisión registrada. No son campos editables.

No se ofrece eliminación, restauración automática ni exportación histórica no definida.

### 19.2. Indisponibilidades

El título visible es **Indisponibilidades**.

Se indica que ausencia de registros no confirma disponibilidad.

No se añade un proceso de vacaciones ni una aprobación de ausencias.

### 19.3. Cambio de zona

Antes de la primera publicación se muestra la revisión de impacto y sus bloqueos.

Después de publicar, incluso una semana vacía, el campo queda de solo lectura.

Se explica que cambiar la zona conserva los instantes, no necesariamente la misma hora local.

### 19.4. Usuarios y enlaces

Crear la cuenta y emitir el enlace inicial se muestran como pasos diferentes.

Si el alta funciona y la emisión falla, no se presenta todo como fallido ni se repite la creación.

Un enlace perdido no se recupera desde el historial de operaciones. La emisión de otro es una acción consciente.

### 19.5. Políticas

Utiliza DS-04, DS-10 y DS-11.

Orden visual:

1. Valores propuestos.
    
2. Impacto.
    
3. Bloqueos.
    
4. Advertencias individuales.
    
5. Motivo requerido.
    
6. Confirmación.
    

No se muestra una política propuesta como activa.

No hay programación de activación futura ni sello de cumplimiento legal.

### 19.6. Avisos

Marcar como leído está incluido.

La lectura no acepta un intercambio ni confirma formalmente un turno.

No se agrega “marcar todos” sin contrato.

Un aviso no concede acceso al recurso enlazado.

## 20. Accesibilidad e interacción

Se mantiene WCAG 2.2 AA como objetivo de referencia, sin afirmar conformidad antes de una evaluación.

### Lista de diseño y prueba

- Navegación por teclado.
    
- Foco visible y no oculto por paneles.
    
- Orden de lectura y foco coherente.
    
- Etiquetas asociadas a campos.
    
- Errores relacionados con sus entradas.
    
- Estados importantes anunciados.
    
- Color acompañado de texto.
    
- Contenido utilizable con ampliación.
    
- Diálogos con foco gestionado.
    
- Acciones disponibles sin arrastrar.
    
- Tablas con encabezados comprensibles.
    
- Agenda alternativa.
    
- Movimiento discreto.
    
- Nombres y motivos largos sin pérdida de información.
    
- Confirmaciones accesibles también en móvil.
    

La vista semanal prioriza estructura semántica y controles identificables.

No se adopta una cuadrícula interactiva compleja sin implementar y probar su navegación completa.

### Anuncios de estado

Se anuncian cambios relevantes:

- Guardado confirmado.
    
- Error de validación.
    
- Resultado de publicación.
    
- Nueva necesidad de revisar.
    
- Resultado desconocido.
    

No se anuncia cada actualización de reloj, consulta periódica o cambio menor que interrumpa la lectura.

### Confirmaciones accesibles

Las casillas de advertencias incluyen el contexto suficiente para entender qué se reconoce.

Un botón deshabilitado no es la única explicación del bloqueo.

No se exige llegar al final de un desplazamiento como supuesto sustituto de leer o comprender.

## 21. Carga, errores y protección de datos

### 21.1. Durante la carga

- Mantener una estructura estable.
    
- Identificar el contenido que sigue cargando.
    
- No presentar cifras ficticias como reales.
    
- No mostrar datos de otra sesión.
    
- No habilitar confirmaciones que dependan de una evaluación incompleta.
    

Los impactos de una publicación no se recortan silenciosamente para acelerar la presentación.

### 21.2. Ante errores de consulta

- Conservar filtros cuando sea seguro.
    
- Ofrecer reintento.
    
- Diferenciar fallo de ausencia de datos.
    
- Mostrar referencia de soporte cuando exista.
    
- No mostrar excepciones internas.
    

### 21.3. Ante resultado desconocido

El componente informa incertidumbre y el flujo conserva la intención original.

Debe poder distinguir:

- Se recuperó un éxito.
    
- Se recuperó un rechazo.
    
- Todavía no existe un resultado recuperable visible.
    

Un resultado no encontrado no equivale a “nunca se ejecutó”.

Los reintentos permitidos utilizan la misma clave y contenido. No se genera otra intención automáticamente.

La ventana prevista de recuperación es de 24 horas según la nota 10; fuera de ella no se promete deduplicación.

Después de recuperar el resultado se consulta el recurso vigente, porque podría haber cambiado posteriormente.

No se promete conservar este seguimiento después de cerrar el navegador.

### 21.4. Seguridad y secretos

Los flujos de acceso, contraseña y emisión de enlaces siguen sus reglas específicas, no el registro genérico de operaciones.

No se almacenan secretos en:

- Mensajes de error.
    
- Historial visible.
    
- Analítica.
    
- Recuperación de formularios.
    
- Resultados reutilizables de operaciones.
    

### 21.5. Sesión y permisos

Ante vencimiento o pérdida de acceso:

- Se retira el contenido protegido.
    
- Se actualizan navegación y acciones.
    
- Se solicita autenticación cuando corresponda.
    
- No se promete recuperar cambios locales.
    
- No se conservan datos de otra cuenta como contenido provisional.
    

La consulta automática de avisos no debe mantener una sesión indefinidamente activa.

## 22. Organización del diseño para implementación

El frontend centraliza:

- Colores.
    
- Tipografía.
    
- Espaciados.
    
- Radios.
    
- Elevaciones.
    
- Componentes DS-01 a DS-14.
    
- Etiquetas de estados.
    
- Mensajes recurrentes.
    
- Formatos de fechas, horas y duraciones.
    

### Separación de responsabilidades

Los componentes presentan datos y acciones.

Los controladores de cada flujo gestionan:

- Carga.
    
- Formularios.
    
- Versiones conocidas.
    
- Previsualizaciones.
    
- Confirmaciones.
    
- Seguimiento de operaciones.
    
- Actualización de consultas.
    

El servidor decide autorización y validez de negocio.

Ejemplo:

> El componente muestra “Aprobar intercambio” cuando corresponde al flujo. El servidor comprueba que el actor sea independiente y que la solicitud siga siendo válida.

### Reutilización sin proyecto adicional

No se construye una biblioteca pública de componentes, un generador de pantallas ni un sistema completo de temas.

La reutilización se limita a lo necesario para mantener coherencia y reducir trabajo repetido.

### Actualización después de cambios

Una publicación o intercambio confirmado actualiza las consultas afectadas.

No se promete sincronización instantánea entre todos los dispositivos.

Las vistas deben comprobar el estado actual al consultarse o antes de una nueva decisión.

## 23. Validación con usuarios

### Perfiles

- Responsable que planifica.
    
- Colaborador que consulta.
    
- Administrador que configura.
    

### Tareas iniciales

1. Encontrar el próximo turno.
    
2. Entender un turno que termina al día siguiente.
    
3. Distinguir cambios locales, borrador y publicación.
    
4. Guardar un borrador con conflictos y explicar qué falta.
    
5. Revisar advertencias individuales.
    
6. Explicar el efecto de publicar una semana vacía.
    
7. Proponer y responder un intercambio.
    
8. Identificar por qué un participante no puede resolverlo como responsable.
    
9. Comprender cuándo cambió realmente el horario.
    
10. Recuperarse de un conflicto de versión.
    
11. Interpretar una respuesta perdida sin repetir a ciegas.
    
12. Consultar y exportar un reporte.
    
13. Entender el bloqueo de una desactivación.
    
14. Distinguir crear una cuenta de emitir su enlace.
    

### Evidencia

Registrar:

- Si completó la tarea.
    
- Ayuda necesaria.
    
- Interpretaciones equivocadas.
    
- Errores.
    
- Elementos difíciles de localizar.
    
- Cambios de diseño justificados por la observación.
    

Los escenarios complejos pueden distribuirse entre sesiones. No es necesario someter a todos los participantes a toda la lista.

Las tareas no se consideran validadas hasta ejecutarlas con un prototipo o la aplicación.

## 24. Criterios de cierre del diseño del MVP

Se distinguen dos niveles.

### 24.1. Especificación lista para implementar

- Las veinte pantallas tienen composición y referencias.
    
- Los componentes reutilizables tienen responsabilidades.
    
- Los ocho wireframes principales coinciden con la nota 08.
    
- Las variantes críticas están descritas.
    
- Los permisos y acciones coinciden con la API.
    
- No se introducen controles de funciones excluidas.
    
- Borrador, publicación e historial se distinguen.
    
- Advertencias y publicación vacía tienen confirmaciones diferentes.
    
- Aceptación y aprobación de intercambio son pasos distintos.
    
- Se contempla la respuesta incierta.
    
- Los reportes y sus exportaciones mantienen coherencia.
    
- Existe alternativa móvil.
    
- Los pendientes tienen ubicación y momento de resolución.
    

Este nivel permite avanzar al desarrollo sin afirmar que la experiencia ya funciona.

### 24.2. Interfaz implementada y verificada

- Componentes revisados con contenido realista.
    
- Estados de carga, vacío, error y conflicto probados.
    
- Contraste comprobado en los estados implementados.
    
- Navegación por teclado verificada.
    
- Ampliación y adaptación móvil comprobadas.
    
- Formularios y diálogos revisados con tecnologías de asistencia.
    
- Confirmaciones probadas frente a cambios concurrentes.
    
- Recuperación de respuestas perdidas comprobada.
    
- Tareas ejecutadas con usuarios representativos.
    
- Problemas encontrados corregidos o registrados con criterio de aceptación.
    

**Situación actual:** propuesta documental consolidada. No se han realizado aquí esas pruebas sobre una interfaz implementada.

## 25. Decisiones que siguen abiertas

|   |   |
|---|---|
|Pendiente|Momento de resolución|
|Validación de identidad visual|Antes de cerrar la presentación visual del MVP|
|Biblioteca de iconos|Antes de incorporarla al frontend|
|Densidad de la planificación|Durante pruebas con volumen representativo|
|Ajustes de tamaños y adaptación|Durante implementación y pruebas visuales|
|Límites de campos pendientes en el contrato|Antes de implementar el formulario correspondiente|
|Detalles visuales de casos poco frecuentes|Al construir el flujo, sin cambiar sus reglas|
|Cambios derivados de pruebas de tareas|Durante validación de experiencia|
|Necesidades de presentación académica|En la nota 14|

Estos pendientes no autorizan ampliar funciones ni reinterpretar reglas ya definidas.

## 26. Siguiente documento

Continuar con:

[[11 - Backlog MVP y Plan de Implementación]]

La nota 10 ya define los contratos que esta revisión toma como referencia.

El siguiente paso es comprobar que el backlog incluya:

- Componentes compartidos.
    
- Pantallas y variantes.
    
- Confirmaciones específicas.
    
- Manejo de concurrencia.
    
- Recuperación de operaciones inciertas.
    
- Exportación consistente.
    
- Verificación móvil y de accesibilidad.
    

