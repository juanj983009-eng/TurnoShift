
## 1. Propósito

Trasladar la planificación al desarrollo sin perder decisiones, duplicar fuentes ni cargar información innecesaria en cada tarea.

Esta nota establece:

- Dónde conservar cada tipo de información.
    
- Qué documentación acompañará al código.
    
- Qué contexto consultar en cada tarea.
    
- Cómo trabajar con Antigravity.
    
- Cuándo evaluar un MCP.
    
- Cómo registrar avances y decisiones.
    
- Qué falta para comenzar.
    
- Qué condiciones pueden esperar hasta el piloto.
    

La prioridad es construir un MVP funcional y coherente que pueda evolucionar hacia un SaaS comercial.

**Estado:** planificación consolidada como propuesta. Esta nota no crea un repositorio, configura herramientas ni acredita implementación.

## 2. Método de trabajo propuesto

Se utilizan tres elementos:

1. **Obsidian:** planificación, decisiones y seguimiento.
    
2. **Repositorio:** código, contratos, migraciones, pruebas y referencias documentales identificadas.
    
3. **Antigravity:** ejecución de tareas delimitadas con el contexto pertinente.
    

### Decisión operativa inicial

Comenzar con Markdown y acceso a archivos del proyecto.

No instalar un MCP como requisito previo.

El contexto mínimo se organiza mediante:

- Índice.
    
- Resumen estable.
    
- Reglas de desarrollo.
    
- Tarea activa.
    
- Estado real.
    
- Referencias a decisiones y documentos.
    

El objetivo no es que la herramienta recuerde toda la conversación, sino que pueda encontrar la información necesaria y comprobar su vigencia.

## 3. Qué aporta y qué no aporta MCP

Antigravity documenta soporte para MCP en distintas variantes del producto. La configuración concreta debe comprobarse para la instalación utilizada antes de modificarla. [Documentación de Antigravity sobre MCP](https://antigravity.google/docs/mcp/).

MCP permite conectar aplicaciones con herramientas y fuentes de contexto. No sustituye la gestión de contexto, la autorización ni la identificación de versiones. [Arquitectura de MCP](https://modelcontextprotocol.io/docs/learn/architecture).

### Puede ayudar a

- Buscar notas.
    
- Leer contenido autorizado.
    
- Recuperar secciones.
    
- Reducir copias manuales.
    
- Consultar fuentes fuera del repositorio.
    

### No garantiza

- Memoria permanente.
    
- Lectura automática de todas las reglas.
    
- Uso de la versión correcta.
    
- Ausencia de consumo por el contenido recuperado.
    
- Seguridad por el hecho de ejecutarse localmente.
    
- Ahorro de un porcentaje determinado de tokens.
    

**Criterio del proyecto:** evaluar el conector cuando resuelva una dificultad observada, no incorporarlo únicamente para “tener memoria”.

## 4. Por qué comenzar con Markdown

Obsidian almacena las notas como archivos Markdown en una carpeta local. Esto permite utilizarlas desde otras herramientas con los permisos correspondientes. [Obsidian: almacenamiento de datos](https://obsidian.md/help/data-storage).

Para TurnoShift, una transferencia controlada al repositorio permite:

- Vincular documentación y código.
    
- Revisar cambios.
    
- Consultar contexto sin abrir Obsidian.
    
- Evitar exponer toda la bóveda.
    
- Continuar trabajando sin un conector.
    

La ruta de la carpeta de planificación ya está identificada:

`D:\Obsidian_bovedas\Universidad-y-Portafolio\Universidad-y-Portafolio\Portafolio\TurnoShift`

La ubicación del repositorio de código todavía debe confirmarse.

La carpeta de esta conversación no se considera automáticamente el repositorio de TurnoShift.

## 5. Responsabilidad de cada ubicación

|Información|Ubicación principal|
|---|---|
|Visión, problema y modelo comercial|Obsidian|
|Alcance y reglas|Obsidian, con referencia identificada en el repositorio|
|Decisiones del producto|Obsidian|
|Código y migraciones|Repositorio|
|OpenAPI|Repositorio|
|Pruebas automatizadas|Repositorio|
|Evidencias de ejecución|Ubicación definida y referenciada|
|Estado real de implementación|Registro breve del repositorio|
|Seguimiento comercial|Registro restringido separado del contexto cotidiano|
|Secretos|Almacenamiento seguro, nunca documentación de contexto|

### Intención frente a realidad

- Los documentos describen lo que se pretende construir.
    
- El código muestra lo implementado.
    
- Las pruebas aportan evidencia sobre lo verificado.
    
- Los resultados del piloto muestran uso y aprendizaje.
    
- Los pagos confirmados muestran ingresos.
    

No se sustituye una categoría por otra.

Una discrepancia entre documentación y código debe investigarse. No se cambia la documentación automáticamente para justificar cualquier comportamiento implementado.

## 6. Transferencia y control de versiones documentales

### Procedimiento

1. Identificar la nota de origen.
    
2. Seleccionar el contenido que necesita el desarrollo.
    
3. Registrar su revisión y estado.
    
4. Incorporar una copia controlada o una referencia accesible.
    
5. Verificar que el contenido transferido corresponde al original seleccionado.
    
6. Utilizar esa revisión durante la tarea.
    
7. Reconciliar cambios posteriores antes de actualizarla.
    

### Identificación de la referencia

El índice debe permitir conocer:

- Documento.
    
- Ruta de origen.
    
- Revisión adoptada.
    
- Fecha de incorporación.
    
- Estado de decisión.
    
- Huella de contenido cuando sea útil para comprobar coincidencia.
    

No se exige reescribir las quince notas solo para uniformar sus encabezados. Si una nota no tiene metadatos de versión, su referencia puede identificarse en el índice mediante contenido y fecha de incorporación.

### Reglas

- No mantener dos copias editables como autoridades independientes.
    
- No sincronizar bidireccionalmente de forma automática al inicio.
    
- No considerar aprobada una versión solo por ser la más reciente.
    
- No sobrescribir cambios ajenos.
    
- No actualizar una copia durante una tarea sin revisar su impacto.
    
- No copiar la bóveda completa.
    

La carpeta del repositorio será una referencia identificada para implementar, no otra planificación desconectada.

## 7. Paquete mínimo de contexto del repositorio

Las siguientes son **rutas relativas propuestas**. No representan archivos creados.

|   |   |
|---|---|
|Ruta propuesta|Función|
|`docs/context/INDEX.md`|Qué leer y dónde encontrarlo|
|`docs/context/PROJECT_CONTEXT.md`|Resumen estable del producto|
|`docs/context/DEVELOPMENT_RULES.md`|Límites de trabajo y verificación|
|`docs/context/CURRENT_TASK.md`|Objetivo y aceptación de la tarea activa|
|`docs/context/STATE.md`|Estado real, bloqueos y siguiente paso|
|`docs/context/DECISIONS.md`|Referencias a decisiones vigentes y pendientes|
|`docs/planning/`|Copias identificadas necesarias para el desarrollo|
|`docs/api/openapi.yaml`|Contrato técnico cuando se prepare|

No se presupone que Antigravity lea automáticamente esos nombres.

La configuración de instrucciones depende de la variante utilizada y se comprobará al preparar el entorno.

### Evitar documentación duplicada

- `INDEX.md` orienta; no resume todos los documentos.
    
- `PROJECT_CONTEXT.md` describe el producto; no contiene todo el backlog.
    
- `CURRENT_TASK.md` delimita trabajo; no repite todas las reglas.
    
- `STATE.md` registra hechos; no vuelve a narrar la conversación.
    
- `DECISIONS.md` referencia decisiones; no crea otra autoridad contradictoria.
    

## 8. Contexto estable del producto

El resumen estable debe incluir únicamente información que ayude a orientar cualquier tarea.

### Producto

TurnoShift es un sistema web de planificación de turnos e intercambios, planteado como SaaS.

El segmento inicial propuesto son restaurantes y cafeterías con una operación suficientemente acotada para un piloto. La demanda, el precio y los ingresos siguen pendientes de validación.

### MVP

- Organizaciones aisladas.
    
- Una sede activa por empresa.
    
- Incorporación asistida.
    
- Roles predefinidos.
    
- Usuarios y colaboradores separados.
    
- Indisponibilidades administradas.
    
- Políticas versionadas.
    
- Planificación manual.
    
- Borradores y publicaciones.
    
- Horario personal.
    
- Intercambios con revisión independiente.
    
- Reportes de horas programadas e intercambios.
    
- CSV consistente.
    
- Avisos y auditoría.
    
- Recuperación de acceso.
    
- Operación y salida asistida.
    

### Exclusiones principales

- Asistencia.
    
- Nómina.
    
- Generación automática de horarios.
    
- Aplicación móvil nativa.
    
- Modo offline.
    
- Multisede completa.
    
- Cobros automáticos.
    
- Chat y avisos externos.
    
- Roles personalizados.
    
- Constructor de reportes.
    

### Arquitectura de referencia

La nota 06 propone:

- Monolito modular.
    
- Java y Spring Boot.
    
- SQL Server.
    
- React y TypeScript.
    
- Sesiones gestionadas en servidor, no JWT autónomo.
    
- Migraciones versionadas.
    
- Auditoría y eventos de aviso asociados a las transacciones.
    
- Control de versiones y coordinación de operaciones críticas.
    

Las líneas de versiones documentadas en la nota 06 son una propuesta de partida. Su combinación exacta se fija mediante la prueba de compatibilidad y queda registrada antes de instalar o actualizar dependencias.

No se sustituyen tecnologías durante una tarea por preferencia de la herramienta.

## 9. Reglas que deben permanecer visibles

El resumen sirve como recordatorio. La implementación consulta además las reglas completas de la parte afectada.

### 9.1. Producto

- No ampliar alcance sin decisión explícita.
    
- No inventar requisitos académicos.
    
- No presentar hipótesis comerciales como resultados.
    
- Horas programadas no son horas trabajadas.
    

### 9.2. Acceso y datos

- La empresa se determina desde la sesión.
    
- Los permisos se comprueban en servidor.
    
- Usuario y colaborador son entidades diferentes.
    
- Un colaborador puede existir sin cuenta.
    
- Las funciones personales requieren vinculación activa.
    
- No se exponen datos ajenos ni motivos privados innecesarios.
    
- El operador no suplanta al responsable independiente.
    

### 9.3. Planificación

- Guardar no significa validar ni publicar.
    
- Los cambios locales se distinguen del borrador guardado.
    
- El borrador no altera el horario oficial.
    
- Las publicaciones y revisiones históricas son inmutables.
    
- Los turnos iniciados se conservan.
    
- Se consideran semanas adyacentes.
    
- No hay fusión automática de borradores.
    
- Una publicación vacía requiere confirmación y motivo.
    
- La primera publicación, incluso vacía, bloquea cambios posteriores de zona.
    

### 9.4. Confirmaciones

- La previsualización no aplica la decisión.
    
- Confirmar exige volver a comprobar datos, tiempo y permisos.
    
- Las advertencias se reconocen individualmente.
    
- El reconocimiento corresponde a la evaluación concreta.
    
- Un motivo no permite ignorar un bloqueo.
    
- La evidencia registra los cálculos del servidor.
    

### 9.5. Intercambios

- Participan dos personas y dos asignaciones elegibles.
    
- Las dos asignaciones quedan reservadas.
    
- Aceptar no cambia horarios.
    
- Un participante no puede aprobar ni rechazar como responsable independiente.
    
- Aprobar aplica ambos cambios o ninguno.
    
- Si afecta dos semanas, ambas publicaciones cambian conjuntamente.
    
- Una solicitud terminal no se reabre.
    
- El vencimiento no depende únicamente del mantenimiento periódico.
    

### 9.6. Reportes y avisos

- Los reportes operativos utilizan publicaciones vigentes.
    
- El periodo del reporte de intercambios selecciona por creación.
    
- Sus estados se evalúan al generar.
    
- El CSV exige la misma consulta y una fuente compatible.
    
- Los CSV operativos no son la salida completa de la empresa.
    
- Leer un aviso no acepta un turno ni un intercambio.
    

### 9.7. Calidad

- No mostrar éxito antes de confirmar.
    
- No duplicar efectos por reintentos.
    
- Resultado desconocido no significa fracaso.
    
- No encontrar una operación no demuestra que nunca se ejecutó.
    
- No afirmar pruebas no ejecutadas.
    
- No almacenar secretos en contexto.
    
- No usar datos reales para desarrollo ordinario.
    

## 10. Contexto específico por tarea

No se cargan las quince notas completas en cada cambio.

### Paquete de lectura

1. Resumen estable.
    
2. Tarea activa.
    
3. Estado real.
    
4. Reglas pertinentes.
    
5. Contrato.
    
6. Modelo relacionado.
    
7. Pantallas y componentes.
    
8. Pruebas esperadas.
    
9. Código existente que se modificará.
    

### Matriz orientativa

|   |   |
|---|---|
|Trabajo|Referencias principales|
|Entorno y compatibilidad|06, 11 y secciones operativas de 12|
|Acceso inicial y recuperación|04, 06, 07, 10 y 12|
|Usuarios y colaboradores|04, 05, 07, 08 y 10|
|Borradores y publicación|04, 07, 08, 10 y 12|
|Políticas|04, 07, 08, 10 y 12|
|Intercambios|04, 05, 07, 10 y 12|
|Componentes visuales|08, 09 y contrato correspondiente|
|Reportes y CSV|04, 07, 10 y 12|
|Respaldo y restauración|06, 11 y 12|
|Salida asistida|05, 11, 12 y 13|
|Evidencia académica|11, 12 y 14|
|Validación comercial|02 y 13|

La lectura se acota por secciones, sin omitir condiciones relacionadas.

Ejemplo: aprobar un intercambio exige revisar también publicaciones, políticas, reservas e indisponibilidad. Leer únicamente el endpoint de aprobación no basta.

## 11. Ficha de tarea activa

Cada tarea debe indicar:

### Identificación

- ID de historia o subtarea.
    
- Objetivo.
    
- Estado.
    
- Incremento.
    
- Revisión documental utilizada.
    

### Alcance

- Qué incluye.
    
- Qué excluye.
    
- Actor autorizado.
    
- Resultado esperado.
    

### Contexto

- Documentos y secciones.
    
- Contrato.
    
- Datos relacionados.
    
- Pantallas.
    
- Código existente relevante.
    

### Dependencias

- Capacidades disponibles.
    
- Decisiones pendientes.
    
- Supuestos permitidos.
    
- Bloqueos.
    

### Aceptación y verificación

- Criterios observables.
    
- Pruebas requeridas.
    
- Evidencias.
    
- Condiciones para no dar la tarea por terminada.
    

### Límites de ejecución

- Carpeta autorizada.
    
- Entorno.
    
- Cambios permitidos.
    
- Acciones que requieren otra aprobación.
    

### Entrega

- Resultado.
    
- Archivos modificados.
    
- Pruebas ejecutadas.
    
- Resultados.
    
- Limitaciones.
    
- Estado actualizado.
    
- Siguiente paso.
    

No se utiliza “haz todo lo que falta” como sustituto de una tarea delimitada.

## 12. Ejemplo de delimitación: HU-14

### Objetivo

Publicar un borrador mediante una decisión consistente, con confirmaciones específicas, historial y recuperación de resultados.

### Contexto necesario

- RF-014.
    
- Reglas de publicación y turnos iniciados.
    
- Modelo de borradores, asignaciones, revisiones y publicaciones.
    
- Decisiones y reconocimientos de advertencias.
    
- Contratos de previsualización y publicación.
    
- Invalidaciones de solicitudes.
    
- Auditoría y eventos.
    
- Concurrencia e idempotencia.
    
- UI-07, UI-08 y confirmación WF-04.
    
- Pruebas PF y PC relacionadas.
    

### Decisiones ya definidas

- Publicación vacía permitida con confirmación y motivo.
    
- Motivo obligatorio para revisión.
    
- Advertencias reconocidas específicamente.
    
- Revalidación al confirmar.
    
- Conservación de turnos iniciados.
    
- Ausencia de fusión automática.
    
- Recuperación de la misma intención ante respuesta perdida.
    

Estas reglas no vuelven a plantearse como pendientes salvo que exista una objeción concreta.

### Fuera de alcance

- Asistencia.
    
- Cobros.
    
- Planificación automática.
    
- Rediseño general.
    
- Cambio de autenticación.
    
- Migración a microservicios.
    

### Condiciones previas

- Borrador disponible.
    
- Evaluador de reglas disponible.
    
- Contrato suficiente.
    
- Modelos de decisión y resultado preparados.
    
- Pruebas de integración ejecutables.
    

La historia no termina por crear una fila de publicación: debe cumplir todos sus efectos y casos de aceptación.

## 13. Forma de trabajo con Antigravity

### Inicio

1. Leer índice, estado y tarea.
    
2. Comprobar la revisión de referencia.
    
3. Inspeccionar el código existente.
    
4. Identificar cambios ajenos.
    
5. Comprobar dependencias.
    
6. Señalar discrepancias que afecten la tarea.
    
7. Ejecutar el cambio delimitado cuando esté autorizado.
    

### Desarrollo

- Preservar cambios ajenos.
    
- Mantener contratos y pruebas alineados.
    
- No cambiar tecnología sin decisión.
    
- No instalar dependencias innecesarias.
    
- Consultar fuentes oficiales cuando una característica actual lo requiera.
    
- Separar los secretos de los archivos de ejemplo.
    
- No introducir datos reales por comodidad.
    
- No ejecutar operaciones de producción desde una tarea de desarrollo.
    

### Cierre

- Explicar qué cambió.
    
- Indicar qué se probó.
    
- Separar éxito, fallo y verificación pendiente.
    
- Registrar decisiones nuevas.
    
- Actualizar el estado real.
    
- Identificar el siguiente trabajo.
    

Los pasos ordinarios de una tarea autorizada no requieren una aprobación por cada archivo. Una ampliación material de alcance o una acción de riesgo sí debe tratarse expresamente.

## 14. Uso eficiente del contexto

### Prácticas

- Una historia o subtarea principal.
    
- Índice corto.
    
- Referencias a secciones concretas.
    
- Resultados de herramientas acotados.
    
- Lectura del código afectado.
    
- Resumen factual al terminar.
    
- Decisiones resueltas conservadas sin volver a narrar su discusión.
    
- Separación entre trabajo comercial y técnico cotidiano.
    

### Evitar

- Copiar toda la conversación.
    
- Cargar PDF y Markdown equivalentes para la misma tarea.
    
- Pegar registros completos sin filtrar.
    
- Repetir toda la arquitectura.
    
- Depender de memoria conversacional.
    
- Acortar una regla hasta volverla ambigua.
    
- Crear varios archivos que repitan el mismo resumen.
    

### Medición

Si la herramienta muestra consumo, comparar tareas semejantes:

- Contexto inicial.
    
- Archivos consultados.
    
- Relecturas.
    
- Consumo observado.
    
- Tiempo de preparación.
    
- Errores por falta de contexto.
    
- Correcciones necesarias.
    

No se promete un porcentaje de ahorro sin datos comparables.

## 15. Cuándo evaluar un MCP

Evaluarlo si:

- La documentación no puede consultarse de forma práctica desde el entorno.
    
- La transferencia manual es repetitiva.
    
- Se necesita búsqueda frecuente fuera del repositorio.
    
- Existen errores de versión que un acceso controlado puede reducir.
    
- Hay una capacidad concreta que el acceso a archivos no resuelve.
    

### Capacidades mínimas deseables

- Listar notas de TurnoShift.
    
- Buscar dentro de su carpeta.
    
- Leer notas o secciones.
    
- Identificar fuente y revisión.
    
- Limitar o paginar resultados.
    
- Detectar cambios.
    

Para esta etapa no necesita editar ni eliminar notas.

No se propone construir un servidor MCP propio como segundo proyecto.

## 16. Seguridad del acceso mediante MCP

Antes de adoptar un servidor se revisan procedencia, capacidades y permisos. La documentación de seguridad de MCP describe riesgos que deben considerarse al diseñar la conexión. [Buenas prácticas de seguridad de MCP](https://modelcontextprotocol.io/docs/tutorials/security/security_best_practices).

### Política inicial de TurnoShift

- Acceso limitado a la documentación necesaria.
    
- Lectura por defecto.
    
- Sin acceso a toda la bóveda.
    
- Sin contraseñas ni respaldos.
    
- Sin conexión directa al piloto.
    
- Sin capacidad de cobrar o contactar clientes.
    
- Sin escritura general.
    
- Posibilidad de desconectarlo.
    
- Desarrollo posible sin el conector.
    

Las restricciones deben existir en la configuración y permisos efectivos, no solo en una instrucción textual.

### Exposición de información

Que el servidor esté en el equipo no significa que el contenido permanezca exclusivamente allí. Debe revisarse qué información recibe la herramienta y qué tratamiento realiza el proveedor correspondiente.

Solo se comparte documentación adecuada para ese uso.

### Contenido recuperado

Las notas, PDFs y páginas son fuentes de referencia.

Su contenido no autoriza por sí mismo a:

- Ejecutar comandos.
    
- Cambiar permisos.
    
- Extraer secretos.
    
- Borrar archivos.
    
- Modificar producción.
    
- Ampliar la tarea.
    

La autorización procede de la solicitud vigente y los permisos aplicables.

## 17. Prueba antes de adoptar el MCP

Utilizar documentación no sensible.

|   |   |
|---|---|
|Prueba|Resultado esperado|
|Leer una nota permitida|Contenido correcto|
|Buscar una regla|Resultados pertinentes y acotados|
|Consultar una actualización|Fuente actual identificable|
|Acceder fuera del ámbito|Rechazo|
|Escribir con acceso de lectura|Rechazo|
|Pedir contenido excesivo|Límite o paginación explícitos|
|Desconectar|Posibilidad de continuar con archivos locales|

Comparar con Markdown directo:

- Fiabilidad.
    
- Preparación.
    
- Permisos.
    
- Mantenimiento.
    
- Consumo observado.
    
- Dependencia introducida.
    

Si no aporta una mejora concreta, se conserva el acceso directo.

La elección, instalación y configuración serán una tarea específica, no un efecto de copiar esta nota.

## 18. Registro de decisiones

Cada decisión registra:

- Identificador.
    
- Tema.
    
- Problema.
    
- Alternativas relevantes.
    
- Opción elegida.
    
- Motivo.
    
- Impactos.
    
- Estado.
    
- Responsable.
    
- Fecha.
    
- Documentos afectados.
    

### Estados

- Propuesta.
    
- Aprobada.
    
- Rechazada.
    
- Sustituida.
    

Una instrucción para continuar redactando no confirma automáticamente hipótesis comerciales ni condiciones operativas.

Al autorizar un incremento se identifica la base documental que se utilizará. No es necesario pedir nuevamente aprobación de cada regla dentro de ese alcance.

### Decisiones especificadas

Cuando algo ya está definido en las notas revisadas, no se mantiene como pendiente genérico.

Si se cuestiona, se registra la objeción concreta y qué trabajo afecta.

## 19. Registro del estado real

`STATE.md` debe permitir retomar el trabajo sin reconstruir la conversación.

Incluye:

- Revisión actual.
    
- Funciones implementadas.
    
- Funciones verificadas.
    
- Trabajo parcial.
    
- Pruebas fallidas o no ejecutadas.
    
- Bloqueos.
    
- Decisiones pendientes.
    
- Próxima tarea.
    
- Referencias relevantes.
    

### Reglas

- Breve y factual.
    
- Sin duplicar el backlog.
    
- Sin porcentajes inventados.
    
- Sin identificadores de cambios inexistentes.
    
- Sin secretos.
    
- Sin afirmar que una historia terminó cuando solo terminó una subtarea.
    

El estado informa; las evidencias sostienen sus afirmaciones.

## 20. Retomar después de una interrupción

1. Leer estado y tarea.
    
2. Comprobar el repositorio real.
    
3. Identificar cambios posteriores.
    
4. Revisar decisiones aplicables.
    
5. Determinar qué pruebas necesitan repetirse.
    
6. Ejecutar la comprobación necesaria.
    
7. Continuar desde lo existente.
    

No se reinicia el proyecto porque cambie la conversación.

Tampoco se supone que nada cambió durante la interrupción.

Si falta información, se recupera de archivos y evidencias antes de volver a pedir que se redacte toda la planificación.

## 21. Control de cambios

### Corrección de una omisión o contradicción

1. Identificar el incumplimiento.
    
2. Localizar documentos afectados.
    
3. Conservar identificadores.
    
4. Corregir la discrepancia.
    
5. Actualizar pruebas y referencias pertinentes.
    
6. Explicar el impacto.
    

### Detalle compatible de implementación

Se resuelve dentro de la tarea y se documenta donde corresponda.

No requiere reabrir el modelo comercial.

### Cambio de regla o alcance

1. Describir la necesidad.
    
2. Evaluar datos, API, pantallas y pruebas.
    
3. Identificar trabajo afectado.
    
4. Obtener la decisión necesaria.
    
5. Actualizar la referencia.
    
6. Implementar.
    

### Límite a la reescritura

No se reescriben notas porque existan o porque se llegue a ellas en el índice.

Solo se modifican por una discrepancia, una decisión nueva o una necesidad de implementación comprobada.

Entregar una nota completa para facilitar su copia no implica que todo su contenido haya cambiado.

## 22. Inventario de planificación

La carpeta identificada contiene las quince notas:

1. [[01 - Formulación y Visión del Producto]]
    
2. [[02 - Modelo de Negocio SaaS y Validación Comercial]]
    
3. [[03 - Alcance MVP y Roadmap Comercial]]
    
4. [[04 - Roles, Procesos y Reglas de Negocio]]
    
5. [[05 - Requisitos y Casos de Uso]]
    
6. [[06 - Arquitectura y Stack Tecnológico]]
    
7. [[07 - Modelo de Datos y DER]]
    
8. [[08 - Navegación, Pantallas y Flujos de Usuario]]
    
9. [[09 - Design System y Wireframes]]
    
10. [[10 - Contratos API e Integraciones]]
    
11. [[11 - Backlog MVP y Plan de Implementación]]
    
12. [[12 - Pruebas, Seguridad y Operación]]
    
13. [[13 - Lanzamiento Piloto y Evolución Comercial]]
    
14. [[14 - Gestión Académica y Entregables]]
    
15. [[15 - Contexto de Desarrollo y Cierre de Planificación]]
    

### Alcance de la comprobación

Se ha comprobado la existencia de los archivos y se han revisado contenidos para las actualizaciones de esta secuencia.

Esto no equivale a:

- Certificar cada enlace de Obsidian.
    
- Verificar una copia futura dentro del repositorio.
    
- Actualizar los PDFs iniciales.
    
- Aprobar todas las propuestas.
    
- Probar software.
    

La copia final de esta nota seguirá dependiendo de la acción del usuario.

## 23. Comprobación de entrada al desarrollo

No se plantea otra revisión general de las quince notas.

Antes de cada incremento se comprueba únicamente la parte necesaria.

### Para I0

- Carpeta de código confirmada.
    
- Repositorio existente o creación autorizada.
    
- Estado previo inspeccionado.
    
- Arquitectura de referencia identificada.
    
- Prueba de compatibilidad delimitada.
    
- Contexto mínimo seleccionado.
    
- Datos ficticios.
    
- Verificaciones iniciales definidas.
    

### Para funciones posteriores

Comprobar progresivamente:

- Permisos.
    
- Datos y contrato.
    
- Reglas relacionadas.
    
- Previsualización.
    
- Evidencia.
    
- Concurrencia.
    
- Reintentos.
    
- Pruebas.
    
- Dependencias.
    

### Discrepancias heredadas

Si una nota anterior conserva expresiones como “pendiente de aprobación” sobre una decisión ya adoptada para el incremento, se registra su estado real en el índice de decisiones.

No se interpreta esa frase aislada como una obligación de reabrir todo el diseño.

Si existe una contradicción material —por ejemplo, autenticación JWT frente a sesiones— debe resolverse explícitamente antes de implementar esa parte.

## 24. Pendientes consolidados

Se conservan DEC-01 a DEC-09 de la nota 11.

|   |   |   |
|---|---|---|
|Referencia|Situación|Momento necesario|
|DEC-01|Rúbrica y restricciones académicas por confirmar|Antes de comprometer la entrega académica o una restricción tecnológica conocida|
|DEC-02|Equipo y disponibilidad por confirmar|Antes de asignar fechas y responsabilidades|
|DEC-03|Publicación vacía especificada|Implementar y probar con HU-14|
|DEC-04|Advertencias y motivos especificados|Aplicar en publicación, políticas e intercambios|
|DEC-05|Activación inmediata de políticas especificada|Construir y verificar impacto|
|DEC-06|Mecanismo de acceso especificado; procedimiento humano por concretar|Base técnica antes del acceso; canal real antes del piloto|
|DEC-07|Idempotencia y huellas especificadas|Implementar con habilitadores y operaciones|
|DEC-08|OpenAPI y límites por completar progresivamente|Antes del contrato afectado|
|DEC-09|Entorno, presupuesto y responsables operativos pendientes|Antes del piloto|

### 24.1. Antes de preparar I0

- Confirmar carpeta del repositorio.
    
- Identificar si ya existe código.
    
- Autorizar el alcance de preparación.
    
- Adoptar la arquitectura de referencia para ese incremento.
    
- Identificar el entorno de ejecución disponible.
    

### 24.2. Antes de las funciones afectadas

- Fijar versiones compatibles.
    
- Completar límites de contrato.
    
- Precisar actividad de sesión.
    
- Resolver los detalles técnicos necesarios sin cambiar reglas.
    
- Preparar datos y pruebas.
    

### 24.3. Antes del piloto

- Infraestructura y edición.
    
- Parámetros reales de planificación.
    
- Soporte y responsables.
    
- Retención.
    
- Cifrado y claves.
    
- Respaldo y recuperación ensayados.
    
- Acceso inicial y recuperación asistida.
    
- Salida y entrega de datos.
    
- Condiciones comerciales y de datos.
    
- Pruebas y validación de tareas.
    

### No bloquean I0 por sí solos

- Instalar un MCP.
    
- Conocer un precio definitivo.
    
- Tener empresas pagando.
    
- Cerrar todos los detalles del piloto.
    
- Preparar todas las evidencias académicas.
    
- Diseñar todos los casos visuales menores.
    

Una restricción académica concreta que contradiga la tecnología elegida sí requiere resolver su impacto; no se inventa esa restricción en ausencia de información.

## 25. Qué significa cerrar la planificación

### Nivel 1 — Propuesta documental consolidada

El producto, alcance, reglas, arquitectura, datos, interfaces, contratos, backlog y pruebas tienen una base descrita.

Este es el resultado documental buscado en esta secuencia.

No demuestra implementación ni aprobación comercial.

### Nivel 2 — Incremento listo para desarrollar

Para el incremento elegido:

- Existe referencia identificada.
    
- Hay tarea delimitada.
    
- Se conocen dependencias.
    
- Los bloqueos necesarios están resueltos.
    
- El entorno y carpeta están autorizados.
    
- La verificación está definida.
    

No es necesario resolver todo el piloto para alcanzar este nivel.

### Nivel 3 — Preparación para piloto

Requiere:

- Software implementado.
    
- Pruebas.
    
- Operación.
    
- Seguridad.
    
- Recuperación.
    
- Soporte.
    
- Condiciones de datos.
    
- Decisión de liberación.
    

No se alcanza redactando más notas.

### Criterio práctico

Después de esta consolidación, el trabajo pasa a incrementos de implementación. La documentación se actualiza cuando el desarrollo lo justifique, no como una secuencia indefinida de revisiones.

## 26. Siguiente paso: preparar I0

El siguiente paso es una tarea técnica acotada, cuando se confirme la ubicación y se autorice.

### Entrada

- Carpeta del repositorio.
    
- Código existente, si lo hay.
    
- Nota 06 como arquitectura de referencia.
    
- I0 y habilitadores de la nota 11.
    
- Controles iniciales de la nota 12.
    
- Contexto definido aquí.
    

No requiere volver a cargar todos los PDFs o releer las quince notas completas.

### Alcance inicial

1. Inspeccionar la carpeta.
    
2. Preservar cualquier trabajo existente.
    
3. Crear o adaptar la estructura autorizada.
    
4. Incorporar el paquete mínimo de contexto.
    
5. Comprobar compatibilidad del stack.
    
6. Fijar versiones utilizadas.
    
7. Preparar una ejecución local mínima.
    
8. Preparar persistencia y migraciones iniciales cuando corresponda.
    
9. Configurar verificaciones básicas.
    
10. Registrar el resultado real.
    

### Fuera de esta tarea

- Construir todos los módulos.
    
- Desplegar el piloto.
    
- Usar datos reales.
    
- Instalar MCP por defecto.
    
- Contratar servicios.
    
- Crear cuentas externas.
    
- Publicar el repositorio.
    
- Contactar empresas.
    

### Resultado esperado

Un entorno inicial reproducible con contexto identificado y una siguiente tarea concreta.

Si una incompatibilidad técnica aparece, se registra antes de sustituir tecnologías.

## 27. Primeros pasos funcionales posteriores

Después de I0, seguir I1 de la nota 11.

### Orden de base

1. Seguridad, persistencia y auditoría necesarias.
    
2. Mecanismo inicial de establecimiento de contraseña.
    
3. Incorporación de empresa y primer administrador.
    
4. Acceso empresarial.
    
5. Administración básica de cuentas.
    
6. Recuperación y cambio de contraseña.
    
7. Verificación de aislamiento y revocación.
    

El primer administrador no puede depender de una sesión empresarial que todavía no tiene forma de obtener.

Las entregas parciales se identifican como tales.

Después continúan equipo, políticas y planificación según sus dependencias; no se empiezan todos los módulos simultáneamente.

## 28. Condiciones de cierre de esta nota

- Método de contexto definido.
    
- Carpeta de planificación identificada.
    
- Ubicación del código diferenciada.
    
- Responsabilidad de Obsidian y repositorio clara.
    
- Transferencia documental controlada.
    
- Contexto estable acotado.
    
- Ficha de tarea definida.
    
- Registro factual de estado.
    
- Decisiones especificadas separadas de pendientes.
    
- MCP opcional y sujeto a evaluación.
    
- Permisos y exposición considerados.
    
- Pendientes separados por momento.
    
- Siguiente incremento delimitado.
    
- Sin exigir otra reescritura general.
    

La autorización de I0 y su carpeta se registra al iniciar esa tarea.

## 29. Estado final y transición

### Documentación

- Las quince notas existen en la carpeta de planificación identificada.
    
- La secuencia ha recibido revisiones de coherencia.
    
- Esta nota consolida el método para desarrollar.
    
- Las revisiones son especificaciones, no evidencia de código funcionando.
    
- No se afirma que los PDFs originales hayan sido actualizados.
    
- No se afirma que la rúbrica esté confirmada.
    

### Herramientas

- La variante y configuración de Antigravity deben comprobarse al preparar su uso.
    
- No se ha configurado un MCP mediante esta secuencia.
    
- No se han creado aquí los archivos de contexto propuestos.
    
- La ubicación del repositorio sigue por confirmar.
    

### Producto

- No se acredita implementación.
    
- No se acreditan pruebas ejecutadas.
    
- No se acredita preparación para piloto.
    
- No se acreditan clientes, pagos ni ingresos.
    

### Siguiente acción

**Confirmar la carpeta del repositorio y autorizar la preparación de I0.**

A partir de ahí, avanzar mediante tareas pequeñas con aceptación y evidencia. Volver a una nota únicamente cuando exista una discrepancia o decisión concreta que lo requiera.