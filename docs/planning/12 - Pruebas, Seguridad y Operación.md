
## 1. Propósito

Definir cómo comprobar que TurnoShift:

- Cumple los requisitos y reglas de negocio.
    
- Protege la información de cada empresa.
    
- Mantiene publicaciones e intercambios consistentes.
    
- Responde correctamente ante concurrencia y errores.
    
- Permite recuperar operaciones de resultado incierto.
    
- Puede desplegarse, respaldarse y restaurarse.
    
- Tiene procedimientos de soporte, continuidad y salida asistida.
    

El objetivo es establecer controles y evidencias antes de utilizar datos reales.

**Estado actual:** plan propuesto. Ninguna prueba, configuración, copia de respaldo o simulacro se considera ejecutado por aparecer aquí.

### Cambios de esta revisión

- Mantiene PF-01 a PF-30 y PC-01 a PC-12.
    
- Añade cobertura para las decisiones ya consolidadas.
    
- Incorpora advertencias específicas, publicación vacía y previsualizaciones.
    
- Precisa el rechazo por un responsable participante.
    
- Completa recuperación de resultados y exportación consistente.
    
- Añade el ensayo de salida asistida.
    
- Mantiene los objetivos operativos existentes como propuestas por validar.
    

## 2. Documentos relacionados

- [[03 - Alcance MVP y Roadmap Comercial]]
    
- [[04 - Roles, Procesos y Reglas de Negocio]]
    
- [[05 - Requisitos y Casos de Uso]]
    
- [[06 - Arquitectura y Stack Tecnológico]]
    
- [[07 - Modelo de Datos y DER]]
    
- [[08 - Navegación, Pantallas y Flujos de Usuario]]
    
- [[09 - Design System y Wireframes]]
    
- [[10 - Contratos API e Integraciones]]
    
- [[11 - Backlog MVP y Plan de Implementación]]
    
- [[13 - Lanzamiento Piloto y Evolución Comercial]]
    
- [[14 - Gestión Académica y Entregables]]
    
- [[15 - Contexto de Desarrollo y Cierre de Planificación]]
    

Las pruebas verifican los contratos y reglas existentes. No deben introducir una interpretación distinta para conseguir que una implementación incorrecta pase.

## 3. Principios de calidad

### 3.1. Integridad antes que continuidad aparente

No se aceptan:

- Intercambios aplicados parcialmente.
    
- Publicaciones duplicadas por reintentos.
    
- Sobrescrituras silenciosas.
    
- Acceso a datos de otra empresa.
    
- Confirmación de éxito sin resultado persistido.
    
- Aceptaciones de advertencias que no correspondan a lo revisado.
    
- Recuperaciones que reactiven accesos revocados sin control.
    

Un rechazo comprensible es preferible a confirmar una operación incompatible.

### 3.2. Seguridad desde el primer incremento

Permisos, aislamiento, sesiones y auditoría se comprueban desde el acceso inicial.

No se agregan únicamente antes del piloto.

### 3.3. Evidencia proporcional a la afirmación

- “Tiene respaldo” requiere una copia protegida y comprobada.
    
- “Se puede restaurar” requiere restauración real.
    
- “Es atómico” requiere observar el resultado ante fallos.
    
- “Está aislado por empresa” requiere pruebas cruzadas.
    
- “Funciona en móvil” requiere completar tareas.
    
- “No duplica operaciones” requiere probar reintentos y respuestas perdidas.
    

### 3.4. Objetivos, no garantías comerciales

Los tiempos y capacidades de esta nota son objetivos de validación.

No constituyen SLA, soporte permanente ni garantía de ausencia de incidentes.

### 3.5. Verificar también después de integrar

Una historia probada sin solicitudes o publicaciones relacionadas debe volver a verificarse cuando se incorporen esos módulos.

Las pruebas de una función aislada no sustituyen las comprobaciones del sistema integrado.

## 4. Responsabilidades

|Función|Responsabilidad|
|---|---|
|Producto|Aceptar alcance y decidir liberación|
|Técnica|Integridad, arquitectura y cambios|
|Pruebas|Diseñar casos, ejecutar y registrar resultados|
|Operación|Despliegues, copias, recuperación y alertas|
|Piloto|Coordinar empresas, soporte y comunicación|
|Administrador empresarial|Configurar y operar su organización|

Una persona puede asumir varias funciones.

Antes del piloto deben existir responsables identificados, canales de contacto y una alternativa de atención o una limitación expresamente reconocida.

El operador de plataforma no sustituye al responsable independiente que debe resolver un intercambio.

## 5. Entornos

|   |   |   |
|---|---|---|
|Entorno|Datos|Uso|
|Desarrollo|Ficticios|Construcción|
|Pruebas|Ficticios y reproducibles|Verificación automatizada|
|Preproducción|Ficticios representativos|Ensayos integrales|
|Piloto|Reales, mínimos y autorizados|Uso controlado|

### Reglas

- Credenciales diferentes por entorno.
    
- Sin uso de la base del piloto para pruebas por comodidad.
    
- Sin carga automática de demostración en empresas reales.
    
- Restauraciones iniciales en un entorno aislado.
    
- Procesos de avisos controlados durante restauraciones.
    
- Canales externos deshabilitados en ensayos si se incorporan en el futuro.
    
- Acceso protegido también a entornos temporales.
    
- Pruebas de carga y seguridad solo sobre infraestructura propia o autorizada.
    

Los mecanismos para provocar fallos de prueba no deben quedar expuestos en el servicio del piloto.

## 6. Datos de prueba

### 6.1. Empresas

Preparar:

- Empresa A operativa.
    
- Empresa B para aislamiento.
    
- Empresa suspendida.
    
- Empresa recién incorporada sin publicaciones.
    
- Empresas adicionales para el ensayo de rendimiento.
    

Los nombres y contactos son ficticios.

### 6.2. Usuarios y colaboradores

Incluir:

- Administrador activo.
    
- Supervisor activo.
    
- Colaborador activo.
    
- Responsable que también participa en un intercambio.
    
- Usuario sin vinculación.
    
- Cuenta con colaborador vinculado inactivo.
    
- Usuario desactivado.
    
- Colaborador sin cuenta.
    
- Último administrador activo.
    
- Usuario de otra empresa.
    
- Cuenta sin contraseña configurada.
    

### 6.3. Planificación

Preparar:

- Semana sin publicación.
    
- Publicación vacía.
    
- Publicación con turnos, pero ninguno para una persona.
    
- Borrador nuevo.
    
- Borrador basado en publicación vigente.
    
- Borrador desactualizado.
    
- Líneas nuevas y líneas asociadas a asignaciones existentes.
    
- Publicaciones históricas.
    
- Turnos nocturnos.
    
- Turnos que cruzan semana o periodo de reporte.
    
- Descanso exacto y descanso insuficiente.
    
- Solapamiento.
    
- Indisponibilidad parcial.
    
- Turnos próximos a empezar e iniciados.
    

### 6.4. Decisiones y políticas

Preparar:

- Primera política.
    
- Cambio de política sin conflictos.
    
- Cambio que introduce un bloqueo.
    
- Cambio que genera advertencias.
    
- Publicación con varias advertencias.
    
- Evaluación cuyo contexto cambia antes de confirmar.
    
- Confirmaciones faltantes, repetidas o ajenas a la evaluación.
    
- Cambio de zona permitido y bloqueado.
    

Los umbrales utilizados son ficticios, no parámetros legales ni valores aprobados para un cliente.

### 6.5. Intercambios

Cubrir:

- `PENDIENTE_COMPANERO`
    
- `PENDIENTE_SUPERVISOR`
    
- `APROBADA`
    
- `RECHAZADA`
    
- `CANCELADA`
    
- `VENCIDA`
    
- `INVALIDADA`
    

También:

- Dos solicitudes que compiten por una asignación desde lados distintos.
    
- Intercambio dentro de una semana.
    
- Intercambio entre semanas.
    
- Responsable participante.
    
- Solicitud cuya revisión referenciada cambió.
    
- Solicitud con estado persistido pendiente, pero temporalmente vencida.
    

### 6.6. Operaciones y recuperación

Preparar:

- Resultado exitoso recuperable.
    
- Rechazo de negocio recuperable.
    
- Transacción todavía sin confirmar.
    
- Registro fuera de retención.
    
- Respuesta perdida después de confirmar.
    
- Acceso revocado después de una operación.
    
- Copia anterior a cambios de seguridad.
    
- Avisos pendientes en un punto restaurado.
    

## 7. Niveles de prueba

|   |   |
|---|---|
|Nivel|Comprueba|
|Unitarias|Cálculos, intervalos, condiciones y transiciones|
|Integración|Restricciones, persistencia, permisos y transacciones|
|Contrato|Entradas, respuestas y errores|
|Componentes|Formularios y estados|
|Extremo a extremo|Recorridos completos|
|Concurrencia|Operaciones incompatibles simultáneas|
|Seguridad|Acceso, aislamiento y exposición|
|Rendimiento|Comportamiento bajo carga definida|
|Accesibilidad|Teclado, ampliación y tecnologías de asistencia|
|Operación|Despliegue, respaldo, restauración y procedimientos|

Las pruebas críticas de persistencia se ejecutan contra SQL Server conforme a la arquitectura, no solo contra una base en memoria.

No se añade una plataforma nueva de pruebas sin necesidad justificada.

## 8. Matriz de pruebas funcionales críticas

Cada fila representa un caso o familia acotada. Si requiere variantes, se identifican como subcasos, por ejemplo `PF-36.1`.

### 8.1. Casos existentes

|   |   |   |
|---|---|---|
|ID|Caso|Resultado esperado|
|PF-01|Acceso válido|Sesión y pantalla autorizadas|
|PF-02|Credenciales incorrectas|Error genérico|
|PF-03|Recurso de otra empresa|Sin acceso ni exposición de información|
|PF-04|Desactivar o degradar al último administrador|Rechazo|
|PF-05|Desactivar colaborador con compromisos|Bloqueo y referencias autorizadas|
|PF-06|Indisponibilidad contra turno publicado|Rechazo sin cambio parcial|
|PF-07|Segundo borrador abierto|Conflicto controlado|
|PF-08|Turno con intervalo inválido|Error sin guardado|
|PF-09|Turnos superpuestos|Bloqueo de publicación|
|PF-10|Descanso entre semanas|Evaluación de ambos periodos|
|PF-11|Superar umbral semanal|Advertencia con cálculo y periodo|
|PF-12|Publicar borrador válido|Publicación, decisión y auditoría consistentes|
|PF-13|Publicar sobre base antigua|Rechazo sin sobrescritura|
|PF-14|Consultar publicación histórica|Solo lectura y etiqueta histórica|
|PF-15|Modificar o retirar turno iniciado|Rechazo|
|PF-16|Consultar horario propio|Solo asignaciones propias publicadas|
|PF-17|Proponer intercambio|Dos reservas y horario intacto|
|PF-18|Aceptar propuesta|Pendiente de aprobación; horario intacto|
|PF-19|Aprobar siendo participante|Rechazo|
|PF-20|Aprobar como responsable independiente|Ambas reasignaciones o ninguna|
|PF-21|Aprobar entre semanas|Dos publicaciones consistentes|
|PF-22|Cancelar o rechazar|Transición válida y liberación de reservas|
|PF-23|Actuar sobre solicitud vencida|Rechazo aunque el mantenimiento se retrase|
|PF-24|Cambiar revisión referenciada|Invalidación correspondiente|
|PF-25|Cambiar otra asignación|No invalidar indiscriminadamente|
|PF-26|Consultar horas programadas|Publicaciones vigentes, sin asistencia ficticia|
|PF-27|Exportar reporte consultado|Mismos filtros, permisos y fuente compatible|
|PF-28|Abrir aviso sin permiso actual|Acceso denegado al recurso|
|PF-29|Reutilizar enlace de recuperación|Sin segundo uso|
|PF-30|Cambiar contraseña|Sesiones anteriores invalidadas según contrato|

### 8.2. Casos incorporados

|   |   |   |
|---|---|---|
|ID|Caso|Resultado esperado|
|PF-31|Incorporación y primer acceso|Alta consistente sin contraseña compartida ni dependencia de una sesión empresarial previa|
|PF-32|Ajustar capacidad|Límites aplicados sin eliminar recursos existentes|
|PF-33|Suspender y reactivar|Bloqueo de sesiones existentes; sin reactivar cuentas ni solicitudes terminales|
|PF-34|Publicar semana vacía válida|Confirmación, motivo y publicación con cero asignaciones|
|PF-35|Vacío sin confirmación, sin motivo o retirando turnos iniciados|Rechazo de cada variante|
|PF-36|Reconocer advertencias|Solo acepta el conjunto concreto y completo de la evaluación|
|PF-37|Confirmar evaluación desactualizada|Rechazo y nueva revisión|
|PF-38|Activar y cambiar política|Versión inmediata, impacto validado e historia preservada|
|PF-39|Cambiar zona horaria|Previsualización; bloqueo tras publicar o por pertenencia semanal|
|PF-40|Cambiar vínculo o desactivar cuenta|Restricciones e invalidaciones correctas, sin transferir solicitudes|
|PF-41|Cambiar puesto con compromisos|Rechazo cuando introduce incompatibilidad|
|PF-42|Estados del horario personal|Distingue no publicado, vacío, sin turnos propios y sin acceso personal|
|PF-43|Marcar aviso leído repetidamente|Conserva primera fecha y no ejecuta acciones de negocio|
|PF-44|Rechazar como responsable participante|Rechazo; no confundir con respuesta del compañero|
|PF-45|Previsualizar y aprobar intercambio con advertencias|Confirmación específica, revalidación y evidencia|
|PF-46|Periodo del reporte de intercambios|Selecciona por creación y evalúa estado al generar|
|PF-47|Fuente modificada antes del CSV|Conflicto; exige actualizar y revisar|
|PF-48|Guardado y descarte de borrador|Estado explícito, versión correcta y publicación intacta|
|PF-49|Alta de cuenta y emisión de enlace|Pasos separados; un fallo del enlace no duplica el alta|
|PF-50|Consultar decisiones y auditoría|Actor, motivo y advertencias autorizados, sin edición ni secretos|

### 8.3. Advertencias y evidencia

PF-36 y PF-45 incluyen:

- Ninguna advertencia.
    
- Una advertencia.
    
- Varias advertencias de distintas personas o periodos.
    
- Reconocimiento faltante.
    
- Reconocimiento duplicado.
    
- Referencia desconocida.
    
- Referencia de otra evaluación.
    
- Motivo requerido ausente.
    
- Cambio de política o asignación después de previsualizar.
    
- Valores de horas manipulados por el cliente.
    

**Verificar en persistencia:**

- Actor correcto.
    
- Política aplicada.
    
- Periodos y cantidades calculados por el servidor.
    
- Motivo.
    
- Relación con la decisión.
    
- Ausencia de evidencia duplicada por reintento.
    

Aceptar una advertencia no permite omitir un bloqueo.

### 8.4. Publicación e historial

PF-12 a PF-15 y PF-34 a PF-37 verifican:

- Cierre del borrador.
    
- Actualización de publicación vigente.
    
- Revisiones inmutables.
    
- Líneas nuevas convertidas en asignaciones oficiales.
    
- Asignaciones iniciadas preservadas.
    
- Invalidaciones y reservas.
    
- Auditoría y eventos pendientes.
    
- Resultado recuperable.
    
- Ausencia de efectos parciales.
    

Una publicación vacía sigue siendo una publicación: bloquea el cambio posterior de zona conforme a la regla.

### 8.5. Políticas y zona

PF-38 y PF-39 incluyen:

- Primera política sin publicaciones.
    
- Cambio con publicaciones futuras.
    
- Descanso insuficiente introducido por el cambio.
    
- Turno iniciado relevante para el descanso del siguiente.
    
- Nuevo umbral que produce advertencias.
    
- Ausencia de programación futura de activación.
    
- Conservación de versiones históricas.
    
- Cambio de zona que conserva instantes.
    
- Línea de borrador que quedaría fuera de su semana.
    
- Publicación concurrente durante el cambio de zona.
    

### 8.6. Consultas y exportación

PF-26, PF-27, PF-46 y PF-47 verifican:

- Máximo de 92 días.
    
- Segmentos que empiezan antes del periodo.
    
- Límite final exclusivo.
    
- Filtros autorizados.
    
- Fuente consistente durante cálculo.
    
- Cambio relevante antes o durante la exportación.
    
- Estado efectivo de solicitudes vencidas.
    
- Metadatos.
    
- Texto peligroso para hojas de cálculo.
    
- Ausencia de información privada innecesaria.
    

La hora de generación por sí sola no debe provocar un conflicto de fuente en cada intento.

## 9. Pruebas de fechas y horas

Las pruebas utilizan un reloj controlable.

No dependen de esperar hasta una hora real ni del reloj del navegador.

### Casos obligatorios

- Conversión entre instantes y zona empresarial.
    
- Inicio de semana local en lunes.
    
- Intervalo con inicio incluido y fin excluido.
    
- Turno nocturno.
    
- Cruce de semana.
    
- División de minutos entre periodos.
    
- Turnos que comparten un extremo.
    
- Descanso exactamente igual al mínimo.
    
- Descanso inmediatamente inferior.
    
- Turno que empieza durante una previsualización.
    
- Vencimiento exacto al iniciar el primero de los turnos.
    
- Precisión de minutos en edición.
    
- Rechazo de segundos o fracciones no admitidos.
    
- Cambio de zona y pertenencia semanal.
    
- Restricción posterior a la primera publicación.
    

Dos turnos adyacentes no se solapan por compartir un extremo, pero pueden incumplir el descanso mínimo.

Cuando se habiliten zonas con cambios estacionales, deben comprobarse horas ambiguas e inexistentes. No se promete soporte operativo para una zona cuyo comportamiento requerido no se haya probado.

## 10. Pruebas de concurrencia e idempotencia

### 10.1. Casos existentes

|   |   |   |
|---|---|---|
|ID|Operaciones simultáneas|Resultado esperado|
|PC-01|Dos ediciones del mismo borrador|Sin sobrescritura de versión antigua|
|PC-02|Dos publicaciones del mismo borrador|Una única publicación efectiva|
|PC-03|Dos propuestas sobre un turno|Una única reserva compatible|
|PC-04|Aceptación y cancelación|Transiciones coherentes con versión y estado|
|PC-05|Aprobación y cancelación|Sin intercambio parcial|
|PC-06|Aprobación y modificación del turno|Resultado consistente tras revalidar|
|PC-07|Publicación e indisponibilidad|No se confirman condiciones incompatibles|
|PC-08|Desactivación y asignación|No aparece un compromiso inválido|
|PC-09|Política y publicación|Aplicación de contexto y política coherentes|
|PC-10|Dos consumos de un enlace|Un único consumo válido|
|PC-11|Misma clave y contenido|Un único efecto|
|PC-12|Misma clave y distinto contenido|Conflicto de idempotencia|

### 10.2. Casos adicionales

|   |   |   |
|---|---|---|
|ID|Escenario|Resultado esperado|
|PC-13|Publicaciones de semanas adyacentes|Respeta solapamiento y descanso global|
|PC-14|Activación de política y aprobación|Sin decisión basada en contexto incompatible|
|PC-15|Cambio de zona y primera publicación|Solo se confirman resultados compatibles|
|PC-16|Exportación y cambio de fuentes|CSV coherente o conflicto, nunca mezcla|
|PC-17|Edición tras previsualizar|Confirmación antigua rechazada|
|PC-18|Desactivación de cuenta y aprobación|Permisos y elegibilidad actuales|
|PC-19|Propuestas que usan lados opuestos de una asignación|Exclusividad independiente del lado|
|PC-20|Recuperar resultado tras revocar permisos|Autorización actual antes de devolver datos|
|PC-21|Consultar resultado mientras la primera transacción sigue abierta|Ausencia temporal no desencadena otro efecto|
|PC-22|Dos procesadores del mismo evento|Sin duplicación indebida del aviso|

### 10.3. Método

Utilizar:

- Conexiones o sesiones independientes.
    
- Puntos de sincronización.
    
- Reloj controlado cuando corresponda.
    
- Verificación del estado final.
    
- Comprobación de publicaciones, revisiones, decisiones, reservas, auditoría y eventos.
    
- Repeticiones para detectar intermitencia.
    

Dos clics manuales no sustituyen una prueba controlada.

### 10.4. Fallos provocados

Introducir fallos:

- Antes de confirmar.
    
- Entre las dos reasignaciones.
    
- Al registrar auditoría.
    
- Al registrar eventos de aviso.
    
- Después de confirmar y antes de entregar la respuesta.
    
- Durante procesamiento posterior de avisos.
    
- Al esperar un bloqueo.
    
- Durante un reintento.
    

**Resultado esperado:**

- Fallo antes de confirmar: sin cambios parciales.
    
- Fallo después de confirmar: resultado recuperable dentro de las condiciones previstas.
    
- Fallo de entrega: operación principal preservada.
    
- Rechazo por vencimiento: puede persistir la finalización, pero no aplicar el intercambio.
    
- Incertidumbre: la interfaz no inventa éxito ni fracaso.
    

### 10.5. Recuperación del resultado

Comprobar que:

- La clave se conserva para la misma intención.
    
- Cambiar contenido no reutiliza esa clave.
    
- Recuperar un registro distingue `SUCCEEDED` y `REJECTED`.
    
- Un resultado no encontrado no se interpreta como “nunca ejecutado”.
    
- El reintento exacto no falla por una versión que cambió como consecuencia de su propio éxito previo.
    
- Los permisos actuales siguen siendo obligatorios.
    
- La retención de 24 horas se aplica según el contrato.
    
- Fuera de retención no se promete deduplicación.
    
- Una respuesta recuperada no se presenta como estado actual sin recargar el recurso.
    
- No se reproducen cookies, contraseñas ni enlaces.
    
- No existe una cola de comandos persistentes inventada a partir del estado interno `IN_PROGRESS`.
    

Después de restaurar una base no se reintentan operaciones ambiguas automáticamente.

## 11. Pruebas de seguridad

### 11.1. Aislamiento

Probar lecturas y escrituras de:

- Organización y sede.
    
- Usuarios y colaboradores.
    
- Puestos y plantillas.
    
- Indisponibilidades.
    
- Políticas.
    
- Borradores y publicaciones.
    
- Intercambios.
    
- Reportes y CSV.
    
- Avisos.
    
- Auditoría.
    
- Resultados de operaciones.
    

Variar rutas, filtros, cuerpos, referencias y relaciones.

Una referencia entre recursos de empresas distintas no debe persistirse.

### 11.2. Permisos por recurso

Comprobar, entre otros:

- Colaborador consulta su horario, no cualquiera.
    
- Destinatario responde únicamente su solicitud.
    
- Solicitante cancela solo la propia y en estado permitido.
    
- Responsable participante no resuelve como independiente.
    
- Supervisor no administra usuarios, colaboradores ni políticas.
    
- Usuario sin vínculo activo no utiliza funciones personales.
    
- Operador no suplanta usuarios ni resuelve intercambios empresariales.
    

No basta con comprobar cada rol una vez.

### 11.3. Entradas y salidas

Probar:

- Campos inesperados.
    
- Actor u organización enviados por el cliente.
    
- Estados que no pueden asignarse libremente.
    
- Identificadores ajenos.
    
- Valores nulos y omitidos.
    
- Cadenas excesivas.
    
- Fechas inválidas.
    
- Ordenamientos y filtros no permitidos.
    
- HTML y scripts.
    
- Contenido peligroso en CSV.
    
- Motivos privados incluidos indebidamente.
    

Las consultas deben usar parámetros y los textos no autorizados como HTML deben presentarse como texto.

### 11.4. Protección de solicitudes

Verificar:

- Solicitudes sin sesión.
    
- Protección contra falsificación según el contrato.
    
- Previsualizaciones mediante POST con sus controles aplicables.
    
- Orígenes no permitidos.
    
- Acciones administrativas.
    
- Pérdida de permisos entre consulta y confirmación.
    
- Ausencia de escrituras de negocio inesperadas mediante GET.
    

### 11.5. Automatización y revisión

Incorporar:

- Detección de secretos.
    
- Revisión de dependencias vulnerables.
    
- Revisión de configuración.
    
- Pruebas de autorización.
    
- Revisión de rutas administrativas.
    
- Exploración controlada sobre entornos autorizados.
    

Un análisis automático sin hallazgos no demuestra ausencia de vulnerabilidades.

## 12. Contraseñas, sesiones y recuperación

### 12.1. Contraseñas

Se mantiene Argon2id como propuesta de arquitectura.

Como referencia, OWASP recomienda una configuración mínima de 19 MiB de memoria, dos iteraciones y paralelismo uno. La implementación debe comprobar sus parámetros efectivos y medir el coste en el servidor elegido. [OWASP: almacenamiento de contraseñas](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

Para TurnoShift se verificará:

- Ausencia de contraseñas en texto claro.
    
- Sin contraseñas compartidas para altas.
    
- Límites de entrada sin truncamiento silencioso.
    
- Sin secretos en registros.
    
- Limitación de intentos.
    
- Protección frente a saturación por verificaciones costosas.
    
- Procedimiento de cambio probado.
    

Los límites de intentos se fijan antes del piloto y deben considerar también el riesgo de bloquear usuarios legítimos.

### 12.2. Sesiones

Propuesta del proyecto:

- Sesión opaca en servidor.
    
- Cookie `HttpOnly`, `Secure` y `SameSite=Lax`.
    
- Inactividad máxima: 30 minutos.
    
- Duración absoluta: 8 horas.
    
- Renovación del identificador al autenticar.
    
- Invalidación al cerrar sesión.
    
- Comprobación de acceso vigente.
    

La expiración debe imponerse en servidor; un temporizador visual no la sustituye. La gestión incluye creación, renovación e invalidación. [OWASP: gestión de sesiones](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

### Pruebas particulares

- Sesión anterior después de cambiar contraseña.
    
- Sesión abierta al suspender empresa.
    
- Sesión abierta al desactivar cuenta.
    
- Cambio de rol.
    
- Consulta periódica de avisos sin actividad del usuario.
    
- Respuesta perdida al iniciar o cerrar sesión.
    
- Ausencia de contenido protegido después de perder acceso.
    

La definición exacta de actividad debe coincidir con su implementación.

### 12.3. Configuración inicial y recuperación asistida

Procedimiento:

1. Recibir la solicitud por el canal acordado.
    
2. Verificar identidad y autoridad.
    
3. Comprobar la cuenta y el contexto autorizado.
    
4. Emitir enlace con propósito correcto.
    
5. Entregarlo mediante el canal autorizado.
    
6. Registrar la intervención sin el secreto.
    
7. Revocar enlaces anteriores conforme al contrato.
    

Vigencia inicial prevista del enlace: 30 minutos.

Comprobar:

- Inspección sin consumo.
    
- Un solo uso.
    
- Vencimiento.
    
- Revocación.
    
- Consumo concurrente.
    
- Cambio correcto y nueva autenticación.
    
- Respuesta incierta sin recuperación del secreto desde el registro general.
    

No se cambia el correo solo porque alguien lo solicita por un canal no verificado.

Si el afectado es el único administrador, se aplica el procedimiento restringido del operador y la verificación del contacto empresarial.

## 13. Configuración segura del servicio

### 13.1. Red y acceso

- HTTPS externo.
    
- Base sin exposición pública directa.
    
- Backend a través del punto de entrada previsto.
    
- Acceso administrativo restringido.
    
- Reglas de red mínimas.
    
- Supervisión de caducidad del certificado.
    
- Detalles de salud no expuestos públicamente.
    

### 13.2. Cuentas técnicas

Separar según las necesidades:

- Ejecución de la aplicación.
    
- Migraciones.
    
- Respaldo.
    
- Administración.
    

La cuenta de ejecución no debe disponer de privilegios generales innecesarios.

### 13.3. Secretos

No incluirlos en:

- Repositorio.
    
- Obsidian.
    
- Capturas.
    
- Tickets.
    
- Prompts.
    
- Archivos de ejemplo.
    
- Paquetes del frontend.
    
- Resultados recuperables de operaciones.
    

Definir almacenamiento, acceso, rotación y recuperación.

Las claves para recuperar respaldos no pueden existir únicamente en el servidor que podría perderse.

### 13.4. Navegador

Verificar:

- Protección de solicitudes.
    
- Política de origen.
    
- Cabeceras aplicables.
    
- Política de contenido compatible.
    
- Caché restringida en respuestas sensibles.
    
- Redirecciones internas seguras.
    
- Manejo del enlace temporal sin exposición innecesaria.
    
- Limpieza de vistas y datos al cambiar sesión.
    

## 14. Privacidad y ciclo de vida de datos

### 14.1. Minimización

Recoger únicamente lo necesario.

No añadir por comodidad:

- Documentos de identidad.
    
- Datos bancarios.
    
- Información médica detallada.
    
- Ubicación.
    
- Fotografías.
    
- Datos familiares.
    

Los motivos deben advertir que no se introduzcan detalles sensibles innecesarios.

### 14.2. Separación de registros

|   |   |
|---|---|
|Registro|Contenido|
|Negocio|Equipo, configuración, horarios y solicitudes|
|Decisiones y auditoría|Actor, acción, motivo y evidencia|
|Diagnóstico|Fallos, duración y correlación|
|Operaciones recuperables|Resultado acotado sin secretos|
|Respaldos|Copias protegidas para recuperación|

No se registran cuerpos completos de solicitudes de manera general.

### 14.3. Retención

Antes del piloto se define conservación para:

- Usuarios y colaboradores.
    
- Publicaciones e intercambios.
    
- Decisiones y auditoría.
    
- Registros técnicos.
    
- Avisos.
    
- Operaciones recuperables.
    
- Enlaces temporales.
    
- Respaldos.
    
- Exportaciones de salida.
    

La retención de operaciones durante 24 horas no determina la conservación del historial empresarial.

Desactivar no equivale a eliminar.

La restauración no debe devolver vigencia a enlaces o resultados cuya expiración ya pasó.

### 14.4. Salida asistida

Corresponde a RF-003 y OP-04.

Procedimiento:

1. Verificar al representante.
    
2. Registrar la solicitud.
    
3. Acordar alcance, fecha y canal.
    
4. Preparar la extracción autorizada.
    
5. Verificar pertenencia, contenido y legibilidad.
    
6. Entregar de forma protegida.
    
7. Confirmar recepción.
    
8. Cerrar acceso según lo acordado.
    
9. Aplicar conservación o eliminación autorizada.
    
10. Conservar evidencia mínima.
    

El archivo no contiene contraseñas, tokens, secretos técnicos ni datos de otras empresas.

Los CSV operativos no se presentan como exportación completa.

Se ensaya con datos ficticios, incluyendo solicitante no autorizado, extracción fallida y entrega no confirmada.

### 14.5. Límites

No se ejecutan borrados improvisados ni se promete eliminación instantánea de todas las copias.

Las condiciones jurídicas y responsabilidades sobre los datos deben revisarse antes del piloto. Esta nota no certifica cumplimiento legal.

## 15. Accesibilidad y uso

Se mantiene WCAG 2.2 AA como referencia, sin declarar conformidad antes de evaluar los criterios aplicables. [W3C: WCAG 2.2](https://www.w3.org/TR/WCAG22/).

### Verificaciones

- Contraste en la interfaz implementada.
    
- Teclado.
    
- Foco visible y no oculto.
    
- Etiquetas.
    
- Errores asociados a campos.
    
- Diálogos.
    
- Ampliación.
    
- Lectura de tablas y mensajes.
    
- Agenda móvil.
    
- Acciones sin arrastrar.
    
- Información no dependiente del color.
    
- Nombres y motivos extensos.
    
- Confirmaciones accesibles.
    
- Anuncios de estado sin interrupciones excesivas.
    

### Tareas observables

Pedir a usuarios representativos que:

- Encuentren su próximo turno.
    
- Distingan guardado, validación y publicación.
    
- Comprendan una advertencia.
    
- Expliquen una publicación vacía.
    
- Respondan un intercambio.
    
- Identifiquen cuándo cambió el horario.
    
- Entiendan un bloqueo de revisión independiente.
    
- Recuperen un conflicto de versión.
    
- Interpreten un resultado desconocido.
    
- Consulten y exporten un reporte.
    

Registrar errores, ayuda requerida e interpretaciones, no solo satisfacción declarada.

## 16. Rendimiento y capacidad

### 16.1. Escenario propuesto

- Cinco empresas ficticias.
    
- Hasta 50 colaboradores por empresa.
    
- Seis meses de historial representativo.
    
- Veinte sesiones concurrentes.
    
- Consultas mezcladas con escrituras.
    
- Ráfagas de responsables dentro de la misma empresa.
    
- Reportes de hasta 92 días.
    

Es un escenario de ensayo, no capacidad comercial garantizada.

### 16.2. Objetivos

|   |   |
|---|---|
|Operación|Objetivo propuesto|
|Consultas habituales|Percentil 95 menor de 2 segundos|
|Guardado y comandos habituales|Percentil 95 menor de 5 segundos|
|Reporte acotado|Menor de 10 segundos en el escenario|
|Publicaciones e intercambios|Sin duplicados ni resultados parciales|
|Saturación|Errores controlados y sin pérdida de integridad|

### 16.3. Registro

Cada ensayo documenta:

- Infraestructura.
    
- Configuración.
    
- Volumen exacto.
    
- Duración y calentamiento.
    
- Mezcla de operaciones.
    
- Punto de medición.
    
- Concurrencia.
    
- Latencias.
    
- Errores.
    
- CPU, memoria, disco, conexiones y bloqueos.
    

No se excluyen peticiones fallidas para mejorar artificialmente el resultado.

### 16.4. Incumplimiento

Investigar consultas, índices, paginación, contención y recursos.

No se eliminan validaciones, auditoría o aislamiento para acelerar.

Cualquier cambio de arquitectura debe responder a un problema observado y documentado.

## 17. Despliegue y liberaciones

### 17.1. Antes

- Identificar versión.
    
- Revisar cambios y migraciones.
    
- Ejecutar verificaciones requeridas.
    
- Confirmar compatibilidad.
    
- Revisar secretos.
    
- Comprobar respaldo.
    
- Definir retorno.
    
- Confirmar responsable y ventana.
    

### 17.2. Durante

- Limitar escrituras si el cambio lo exige.
    
- Controlar procesos internos.
    
- Aplicar migraciones mediante el mecanismo previsto.
    
- Desplegar componentes compatibles.
    
- Registrar resultado.
    
- Comprobar salud.
    

No se promete actualización sin interrupción.

### 17.3. Después

Comprobar:

- Acceso.
    
- Horarios.
    
- Permisos.
    
- Reportes.
    
- Procesamiento de avisos.
    
- Errores.
    
- Respaldo programado.
    

Las pruebas de escritura usan una empresa de verificación aislada, no turnos reales.

### 17.4. Retorno

Distinguir:

- Revertir código.
    
- Revertir configuración.
    
- Corregir hacia adelante.
    
- Restaurar datos.
    

Restaurar la base no es la respuesta rutinaria a un despliegue fallido: puede perder cambios legítimos.

Si una migración impide volver a la aplicación anterior, se sigue el procedimiento aprobado sin improvisar una reversión destructiva.

## 18. Respaldos

### 18.1. Objetivos

|   |   |
|---|---|
|Objetivo|Propuesta|
|RPO: pérdida máxima de datos buscada|Hasta 4 horas|
|RTO: recuperación del servicio buscada|Hasta 8 horas desde la interrupción|

Requieren validación con presupuesto, infraestructura y disponibilidad humana.

El RTO incluye detección, atención, recuperación y comprobaciones necesarias, no solo el tiempo de copiar archivos.

### 18.2. Estrategia inicial

Para la base pequeña del piloto:

- Respaldo completo cada dos horas.
    
- Copia protegida fuera del servidor principal.
    
- Comprobación de transferencia y legibilidad.
    
- Alerta al superar tres horas de antigüedad del punto recuperable.
    
- Incidente al superar cuatro horas.
    
- Copia adicional antes de cambios de alto impacto.
    

No se confunde una transferencia reciente de una copia antigua con un punto de recuperación reciente.

Se mide la duración y el impacto del respaldo antes de aceptar esta frecuencia.

### 18.3. Modelo de recuperación

Se mantiene como propuesta evaluar el modelo simple con copias completas frecuentes.

El modelo simple de SQL Server no admite respaldos del registro de transacciones ni restauración a un instante arbitrario entre copias. Si el piloto necesita recuperación puntual, debe diseñarse y ensayarse otra estrategia. [Microsoft: modelos de recuperación](https://learn.microsoft.com/en-us/sql/relational-databases/backup-restore/recovery-models-sql-server?view=sql-server-ver17).

Cambiar el modelo no constituye por sí solo una estrategia de protección completa.

### 18.4. Programación

Si se utiliza SQL Server Express, se necesita un programador externo: esa edición no incluye SQL Server Agent. Deben probarse ejecución, permisos, fallos y alertas del mecanismo elegido. [Microsoft: automatizar respaldos en Express](https://learn.microsoft.com/en-us/troubleshoot/sql/database-engine/backup-restore/schedule-automate-backup-database).

### 18.5. Protección y retención

Propuesta inicial:

- Copias de dos horas conservadas durante 48 horas.
    
- Una copia diaria durante 14 días.
    
- Destino separado.
    
- Cifrado y acceso restringido.
    
- Claves recuperables mediante procedimiento separado.
    
- Protección frente a borrado por la cuenta ordinaria de aplicación.
    

La retención requiere aprobación operativa y compatibilidad con el ciclo de vida de datos.

El mecanismo de cifrado debe verificarse para la edición e infraestructura elegidas.

### 18.6. Elementos necesarios para recuperar

Además de la base:

- Aplicación correspondiente.
    
- Migraciones.
    
- Configuración.
    
- Acceso a secretos.
    
- Claves.
    
- Configuración del servidor.
    
- Inventario.
    
- Procedimiento de despliegue.
    

No se reúnen todos los secretos y copias en un único archivo expuesto.

## 19. Restauración y simulacros

### 19.1. Verificación frente a restauración

`RESTORE VERIFYONLY` comprueba aspectos del respaldo, pero no restaura la base ni verifica completamente la estructura de sus datos. Debe complementarse con una restauración real. [Microsoft: operaciones de restauración](https://learn.microsoft.com/en-us/sql/t-sql/statements/restore-statements-for-restoring-recovering-and-managing-backups-transact-sql?view=sql-server-ver17).

### 19.2. Procedimiento

1. Declarar el incidente y registrar tiempos.
    
2. Contener escrituras y procesos afectados.
    
3. Preservar evidencia y estado disponible.
    
4. Seleccionar copia y explicar pérdida posible.
    
5. Restaurar en entorno aislado.
    
6. Comprobar integridad y compatibilidad.
    
7. Revisar empresas, publicaciones, revisiones, decisiones y reservas.
    
8. Invalidar sesiones y enlaces restaurados.
    
9. Revisar accesos revocados después del punto recuperado.
    
10. Revisar operaciones recuperables y avisos.
    
11. Conciliar cambios posteriores conocidos.
    
12. Identificar el horario que se considerará vigente.
    
13. Autorizar reapertura.
    
14. Comunicar alcance y limitaciones.
    

No se reabre únicamente porque la base inicia correctamente.

### 19.3. Comprobaciones de integridad

- Publicación vigente pertenece a su planificación.
    
- Asignaciones y revisiones corresponden a su empresa.
    
- Decisiones conservan sus publicaciones y evidencia.
    
- Intercambios aprobados mantienen ambas reasignaciones.
    
- Reservas corresponden a solicitudes pertinentes.
    
- Solicitudes vencidas no pueden ejecutarse.
    
- Eventos y avisos no se reproducen indiscriminadamente.
    
- Resultados recuperados no se confunden con operaciones posteriores perdidas.
    

### 19.4. Cambios posteriores al respaldo

Puede haber usuarios que vieron decisiones que ya no aparecen en la copia recuperada.

Por tanto:

- No se reenvían avisos antiguos automáticamente.
    
- No se repiten comandos ambiguos.
    
- Se revisan discrepancias con responsables autorizados.
    
- Se comunica la referencia vigente.
    
- Las correcciones se realizan de forma controlada y auditada.
    

### 19.5. Base compartida

Restaurar la base principal afecta a todas las empresas contenidas.

No se reemplaza toda la base para corregir un error de una sola organización.

La recuperación selectiva requiere restaurar aparte, identificar relaciones y preparar una corrección específica.

### 19.6. Frecuencia y evidencia

Propuesta:

- Antes del piloto.
    
- Mensualmente durante el piloto.
    
- Después de cambios relevantes en respaldo, esquema o infraestructura.
    

Registrar copia, tiempos, punto recuperado, pérdida estimada, errores, verificaciones y cumplimiento observado de RPO/RTO.

## 20. Supervisión operativa

### 20.1. Señales mínimas

|   |   |
|---|---|
|Señal|Detecta|
|Disponibilidad externa|Servicio inaccesible|
|Errores|Fallos de operaciones|
|Latencia|Degradación|
|Disco|Riesgo de interrupción|
|Conexiones y bloqueos|Saturación o contención|
|Punto recuperable externo|Riesgo de incumplir RPO|
|Evento pendiente más antiguo|Retraso de avisos|
|Fallos de mantenimiento|Vencimientos o tareas atascadas|
|Certificado|Riesgo de acceso inseguro o interrumpido|
|Intentos anómalos|Posible abuso|

### 20.2. Alertas

Cada alerta define:

- Condición.
    
- Destinatario.
    
- Canal.
    
- Horario.
    
- Acción inicial.
    
- Escalamiento.
    
- Criterio de cierre.
    

Se ensaya la recepción de una alerta antes del piloto.

Una alerta configurada sin destinatario comprobado no constituye un control completo.

### 20.3. Soporte

Definir:

- Responsable principal.
    
- Alternativa.
    
- Canal.
    
- Días y horarios.
    
- Tratamiento fuera de horario.
    

No se ofrece atención permanente si no existe.

## 21. Procedimientos ante fallos frecuentes

### 21.1. Publicación o aprobación de resultado incierto

1. No generar otra clave automáticamente.
    
2. Consultar el resultado de la intención.
    
3. Distinguir éxito, rechazo y ausencia de registro visible.
    
4. Consultar el recurso vigente.
    
5. Reintentar solo bajo el contrato y ventana correspondientes.
    
6. Escalar si no se puede determinar lo ocurrido.
    

### 21.2. Avisos retrasados

1. Comprobar el estado oficial.
    
2. Revisar eventos y procesamiento.
    
3. Corregir la causa.
    
4. Reanudar con deduplicación.
    
5. No revertir publicaciones por el retraso.
    

### 21.3. Base inaccesible

1. Contener reintentos dañinos.
    
2. Revisar red, proceso, disco y credenciales.
    
3. Distinguir indisponibilidad de daño.
    
4. No restaurar sin diagnóstico.
    
5. Aplicar recuperación cuando corresponda.
    

### 21.4. Posible exposición entre empresas

1. Restringir la función afectada.
    
2. Preservar evidencia.
    
3. Determinar alcance conocido y posible.
    
4. Corregir.
    
5. Ejecutar pruebas de aislamiento.
    
6. Evaluar comunicaciones aplicables.
    
7. Reabrir mediante decisión registrada.
    

### 21.5. Pérdida de acceso administrativo

1. Verificar identidad y autoridad.
    
2. Revisar si existe otro administrador.
    
3. Aplicar recuperación asistida.
    
4. Registrar intervención.
    
5. No crear cuentas ocultas ni desactivar controles globales.
    

### 21.6. Exportación de reporte desactualizada

1. Informar el cambio de fuentes.
    
2. Conservar filtros permitidos.
    
3. Consultar nuevamente.
    
4. Solicitar revisión antes de exportar.
    
5. No forzar un CSV diferente al revisado.
    

### 21.7. Salida asistida fallida

1. Detener la entrega o eliminación afectada.
    
2. Identificar el fallo.
    
3. Mantener separadas extracción, entrega, cierre y conservación.
    
4. Verificar de nuevo destinatario y contenido.
    
5. Continuar únicamente con autorización y evidencia.
    

Un error de entrega no autoriza a borrar los datos.

## 22. Gestión de incidentes

|   |   |   |
|---|---|---|
|Severidad|Ejemplo|Respuesta|
|Crítica|Exposición o corrupción|Contener y priorizar recuperación segura|
|Alta|Servicio caído o flujo principal inutilizable|Atención prioritaria y comunicación|
|Media|Función limitada con alternativa segura|Corrección y seguimiento|
|Baja|Problema menor|Backlog|

### Secuencia

1. Detectar.
    
2. Clasificar.
    
3. Contener.
    
4. Investigar.
    
5. Recuperar.
    
6. Comunicar.
    
7. Revisar causa y prevención.
    

### Registro

- Inicio conocido.
    
- Detección.
    
- Alcance.
    
- Responsable.
    
- Decisiones.
    
- Cambios realizados.
    
- Recuperación.
    
- Pérdida o exposición confirmada.
    
- Aspectos inciertos.
    
- Acciones preventivas.
    

No se eliminan registros para mejorar artificialmente las métricas.

## 23. Continuidad durante una interrupción

El MVP no tiene modo offline.

Antes del piloto se acuerda una alternativa:

- Consultar la última referencia autorizada disponible.
    
- Confirmar urgencias con el responsable empresarial.
    
- Registrar decisiones externas con fecha y autor.
    
- Evitar versiones paralelas incompatibles.
    
- Conciliar al recuperar el servicio.
    

Una copia externa puede estar desactualizada y contener datos personales.

Las decisiones externas no se convierten automáticamente en publicaciones del sistema.

La conciliación tampoco permite modificar turnos iniciados vulnerando las reglas: si un cambio externo ya es histórico, se documenta y trata mediante el procedimiento permitido, sin inventar una edición retroactiva.

## 24. Mantenimiento y ensayos operativos

### 24.1. Rutina propuesta

|   |   |
|---|---|
|Frecuencia|Actividad|
|Automática y frecuente|Salud, respaldos y tareas internas|
|Cada día de operación|Alertas y copias|
|Semanal|Errores recurrentes, capacidad y dependencias|
|Mensual|Restauración, accesos privilegiados y procedimientos|
|Antes de liberar|Pruebas, migraciones, respaldo y retorno|
|Después de incidente|Causa, corrección y prueba preventiva|

Los riesgos urgentes no esperan a una revisión rutinaria.

### 24.2. Ensayos identificados

|   |   |   |
|---|---|---|
|ID|Ensayo|Evidencia|
|PO-01|Despliegue y actualización|Versión, migraciones y comprobaciones|
|PO-02|Respaldo y alerta|Copia externa comprobada y alerta recibida|
|PO-03|Restauración completa|Integridad, seguridad, punto recuperado y tiempos|
|PO-04|Incidente y continuidad|Contención, comunicación y conciliación|
|PO-05|Salida asistida|Autoridad, extracción, entrega y cierre sin mezcla de empresas|
|PO-06|Recuperación administrativa|Identidad verificada, enlace seguro y acceso restablecido|

Estos ensayos verifican OP-01 a OP-04 y los procedimientos de acceso. No son funcionalidades adicionales.

## 25. Evidencias y trazabilidad

### 25.1. Registro de cada prueba

- Identificador.
    
- Requisito, regla e historia.
    
- Versión de aplicación.
    
- Entorno.
    
- Datos y precondiciones.
    
- Pasos.
    
- Resultado esperado.
    
- Resultado obtenido.
    
- Estado.
    
- Evidencia.
    
- Responsable.
    
- Fecha.
    
- Defectos relacionados.
    

### Estados

- No ejecutada.
    
- Aprobada.
    
- Fallida.
    
- Bloqueada.
    
- No aplicable, con justificación.
    

Un caso no se marca “no aplicable” para ocultar una función del MVP sin implementar.

### 25.2. Evidencia adecuada

- Capturas: presentación e interacción.
    
- Respuestas API: contrato.
    
- Consultas de verificación: estado persistido.
    
- Ejecución concurrente: integridad frente a carreras.
    
- Registros de restauración: recuperación.
    
- Observación de tareas: usabilidad.
    

Una captura no demuestra por sí sola atomicidad, aislamiento o restauración.

### 25.3. Cobertura por capacidad

|   |   |
|---|---|
|Capacidad|Referencias principales|
|Empresas y servicio|PF-31 a PF-33, PO-05|
|Acceso y usuarios|PF-01 a PF-04, PF-29, PF-30, PF-40, PF-49, PO-06|
|Equipo y configuración|PF-05, PF-06, PF-38, PF-39, PF-41|
|Borradores y publicación|PF-07 a PF-15, PF-34 a PF-37, PF-48|
|Horario personal|PF-16, PF-42|
|Intercambios|PF-17 a PF-25, PF-44, PF-45|
|Reportes|PF-26, PF-27, PF-46, PF-47|
|Avisos y auditoría|PF-28, PF-43, PF-50|
|Concurrencia y reintentos|PC-01 a PC-22|
|Operación|PO-01 a PO-06|

La tabla es un índice, no una declaración de cobertura completa. Cada criterio de aceptación debe enlazarse con al menos un caso o una comprobación justificada.

Las evidencias compartidas utilizan datos ficticios o información debidamente protegida.

## 26. Criterios de liberación al piloto

- Funciones comprometidas completas.
    
- Pruebas críticas aprobadas.
    
- Sin defectos críticos o altos pendientes.
    
- Aislamiento en lecturas, escrituras y exportaciones.
    
- Roles y acceso vigente verificados.
    
- Publicación vacía comprobada.
    
- Advertencias y motivos específicos comprobados.
    
- Previsualizaciones desactualizadas rechazadas.
    
- Concurrencia e idempotencia probadas.
    
- Resultados inciertos recuperables dentro de su contrato.
    
- Límites temporales verificados.
    
- Intercambios atómicos e independencia para resolver.
    
- Reportes y CSV consistentes.
    
- Recuperación de acceso ensayada.
    
- Experiencia móvil y accesibilidad revisadas.
    
- Rendimiento medido.
    
- Configuración segura.
    
- Copia externa comprobada.
    
- Restauración completa ensayada.
    
- Accesos y procesos conciliados después de restaurar.
    
- Alertas recibidas.
    
- Soporte definido.
    
- Salida asistida ensayada.
    
- Condiciones de datos revisadas.
    
- Limitaciones comunicadas.
    
- Decisión de liberación registrada.
    

Los defectos menores pendientes requieren evaluación explícita.

Si un objetivo operativo no puede cumplirse, debe revisarse y aceptarse antes de ofrecerlo a la empresa; no se presenta como satisfecho.

## 27. Decisiones pendientes

Antes del uso real:

1. Confirmar infraestructura y edición utilizada.
    
2. Fijar parámetros efectivos de contraseñas.
    
3. Fijar límites de intentos y entradas.
    
4. Precisar qué actividad renueva sesión.
    
5. Aprobar conservación por tipo de dato.
    
6. Confirmar cifrado y custodia de claves.
    
7. Validar frecuencia, duración y coste de respaldos.
    
8. Aceptar RPO y RTO realizables.
    
9. Asignar responsables y soporte.
    
10. Seleccionar y ensayar alertas.
    
11. Concretar canal y verificación de identidad.
    
12. Definir contenido y entrega de la salida asistida.
    
13. Completar los detalles pendientes de OpenAPI.
    
14. Ejecutar y registrar las pruebas.
    

Publicación vacía, reconocimiento específico de advertencias, independencia de revisión e idempotencia ya tienen diseño definido. No se mantienen como decisiones funcionales abiertas sin una objeción concreta.

Cada pendiente se vincula al trabajo correspondiente de la nota 11.

## 28. Estado de cierre

### Plan listo para orientar el desarrollo

Requiere:

- Casos críticos identificados.
    
- Trazabilidad con requisitos.
    
- Datos ficticios definidos.
    
- Métodos de prueba suficientes.
    
- Procedimientos operativos descritos.
    
- Pendientes y responsables por asignar claramente identificados.
    

### Controles implementados y verificados

Requiere ejecución y evidencia real.

**Situación actual:** especificación consolidada. Pruebas, configuraciones, simulacros y validación pendientes.

Terminar esta nota no autoriza el piloto ni demuestra que el software esté terminado.

## 29. Siguiente documento

Continuar con:

[[15 - Contexto de Desarrollo y Cierre de Planificación]]

La siguiente revisión consolidará:

- Qué decisiones se consideran base de trabajo.
    
- Qué queda pendiente antes del primer incremento.
    
- Qué condiciones pueden esperar hasta el piloto.
    
- Qué documentos consultar para cada tarea.
    
- Cómo comenzar el desarrollo sin cargar toda la planificación en cada interacción.
    

Las notas 13 y 14 siguen siendo referencias de piloto y entrega académica. Solo necesitarán cambios si la revisión final identifica una contradicción concreta.