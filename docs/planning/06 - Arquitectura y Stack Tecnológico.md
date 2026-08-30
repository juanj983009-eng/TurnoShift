

# TurnoShift SaaS — Arquitectura y Stack Tecnológico

## 1. Propósito

Definir la estructura técnica del MVP de TurnoShift y justificar las decisiones necesarias para construirlo como un SaaS comercial.

La arquitectura debe permitir:

- Separar datos entre empresas.
    
- Aplicar permisos efectivos.
    
- Mantener consistencia de horarios e intercambios.
    
- Conservar trazabilidad.
    
- Recuperar información.
    
- Desarrollar y probar por módulos.
    
- Evolucionar sin implementar anticipadamente todas las funciones futuras.
    

Este documento propone decisiones. No describe infraestructura instalada ni funcionalidades implementadas.

## 2. Requisitos que condicionan la arquitectura

Las decisiones se fundamentan en [[05 - Requisitos y Casos de Uso]], especialmente:

- Aislamiento empresarial.
    
- Desactivación efectiva de usuarios.
    
- Cambios de permisos aplicados en la siguiente operación protegida.
    
- Publicaciones versionadas.
    
- Intercambios atómicos.
    
- Conflictos entre semanas.
    
- Detección de ediciones desactualizadas.
    
- Auditoría obligatoria.
    
- Avisos recuperables.
    
- Respaldo y restauración.
    
- Desarrollo individual con alcance controlado.
    

La arquitectura no se seleccionará únicamente por popularidad o por parecer más avanzada.

## 3. Estilo arquitectónico elegido

### Propuesta: monolito modular

TurnoShift tendrá:

- Una aplicación frontend.
    
- Una aplicación backend.
    
- Una base de datos relacional principal.
    
- Módulos internos con responsabilidades delimitadas.
    
- Un despliegue inicial sencillo.
    

Un monolito modular significa que los módulos comparten proceso y despliegue, pero no deben acceder libremente a la implementación interna de los demás.

### Comparación de alternativas

|Alternativa|Evaluación para este MVP|
|---|---|
|Monolito sin límites internos|Sencillo al comenzar, pero favorece acoplamiento y cambios difíciles de controlar.|
|Monolito modular|Permite separar responsabilidades y conservar transacciones locales. Es la opción propuesta.|
|Microservicios|Añadirían despliegues, comunicación distribuida y consistencia entre servicios sin una necesidad demostrada.|

### Consecuencia aceptada

Una actualización del backend despliega el conjunto de módulos.

No se promete escalado independiente ni disponibilidad independiente por módulo.

La modularidad se verificará mediante estructura, interfaces y pruebas, no únicamente mediante nombres de carpetas.

## 4. Stack propuesto

|   |   |   |
|---|---|---|
|Área|Tecnología propuesta|Finalidad|
|Lenguaje backend|Java 21|Lógica de negocio y servicios.|
|Framework backend|Spring Boot 4.0.x|Aplicación web y configuración.|
|HTTP|Spring MVC|API REST síncrona.|
|Seguridad|Spring Security|Autenticación, autorización y protección de solicitudes.|
|Sesiones|Spring Session JDBC|Persistencia de sesiones en base de datos.|
|Persistencia|Spring Data JPA y consultas SQL delimitadas|Acceso a datos.|
|Base de datos|SQL Server 2022|Integridad, transacciones y almacenamiento.|
|Migraciones|Flyway|Evolución versionada del esquema.|
|Construcción backend|Maven Wrapper|Compilación y pruebas reproducibles.|
|Frontend|React y TypeScript|Interfaz por componentes y contratos tipados.|
|Construcción frontend|Vite|Desarrollo y generación de archivos estáticos.|
|Estilos|CSS organizado y variables de diseño|Implementación del Design System.|
|Contratos|OpenAPI y springdoc-openapi compatible|Documentación y verificación de la API.|
|Pruebas backend|JUnit y pruebas de integración|Verificación de reglas y persistencia.|
|Base real en pruebas|Testcontainers con SQL Server|Verificar comportamiento del motor elegido.|
|Pruebas frontend|Vitest y Testing Library|Componentes y comportamiento.|
|Pruebas de navegador|Playwright|Flujos completos.|
|Despliegue inicial|Docker Compose|Entorno reproducible.|
|Entrada web|Nginx|Archivos estáticos, proxy y terminación HTTPS cuando corresponda.|
|Versionado|Git|Código, configuración y documentación.|

### Política de versiones

- No utilizar versiones `SNAPSHOT`, experimentales o etiquetas flotantes en entregas.
    
- Fijar versiones exactas al preparar el entorno.
    
- Conservar el archivo de bloqueo de dependencias frontend.
    
- Utilizar la gestión de dependencias de Spring Boot cuando corresponda.
    
- Probar las actualizaciones antes de incorporarlas.
    
- Registrar versiones e imágenes de cada entrega.
    

La documentación consultada muestra Java 21 dentro del rango compatible de Spring Boot actual. Se propone la línea 4.0.x como base inicial, sujeta a una prueba de compatibilidad de todas las dependencias. [Requisitos oficiales de Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html).

No se afirma que la combinación completa ya haya sido ejecutada.

## 5. Continuidad con los documentos originales

### Se conserva

- Backend Java.
    
- Spring Boot.
    
- API REST.
    
- SQL Server.
    
- OpenAPI.
    
- Docker.
    
- Separación entre frontend y backend.
    

### Se concreta

- React con TypeScript para el frontend.
    
- Monolito modular.
    
- Sesiones de servidor.
    
- Aislamiento por organización.
    
- Migraciones versionadas.
    
- Estrategia de concurrencia.
    
- Publicaciones históricas.
    
- Auditoría y eventos pendientes.
    

### Se sustituye

La propuesta inicial de autenticación JWT se reemplaza por sesiones de servidor para el MVP web.

Si el curso exige JWT expresamente, deberá revisarse esta decisión antes de implementar. No se mantendrán mecanismos contradictorios en documentos diferentes.

## 6. Decisión sobre SQL Server

Se propone conservar SQL Server para mantener coherencia con la documentación inicial.

No se afirma que el curso lo exija: esa condición sigue pendiente de confirmación.

### Ediciones

- **Desarrollo y pruebas:** Express para aproximar el entorno objetivo; Developer puede utilizarse en pruebas autorizadas sin depender de funciones exclusivas.
    
- **Piloto operativo:** Express, si sus límites y condiciones resultan adecuados.
    
- **Crecimiento:** evaluar otra edición o servicio administrado según carga, operación y costos.
    

SQL Server Developer está destinado a desarrollo y pruebas, no a producción. SQL Server 2022 Express es una edición gratuita con límites, entre ellos 10 GB por base relacional, capacidad de cómputo limitada y aproximadamente 1,4 GB de memoria para el buffer pool. Estos límites corresponden específicamente a la versión 2022. [Ediciones y capacidades oficiales](https://learn.microsoft.com/en-us/sql/sql-server/editions-and-components-of-sql-server-2022?view=sql-server-ver17).

### Consecuencias

- Los límites de la base compartida afectan al conjunto de empresas, no individualmente a cada una.
    
- Debe medirse el crecimiento de historial, auditoría, sesiones y avisos.
    
- La infraestructura sigue teniendo costo aunque la edición sea gratuita.
    
- No se utilizará Developer en un servicio operativo por el hecho de que el piloto no cobre.
    
- Antes del lanzamiento se verificarán las condiciones vigentes del despliegue elegido.
    

Cambiar a PostgreSQL podría evaluarse antes de implementar, pero no se diseñará compatibilidad simultánea con dos motores.

## 7. Componentes de ejecución

|   |   |   |
|---|---|---|
|Componente|Responsabilidad|Exposición|
|Navegador|Presentación e interacción.|Dispositivo del usuario.|
|Nginx|Servir frontend y dirigir solicitudes a la API.|Punto público HTTPS.|
|Backend Spring Boot|Seguridad, reglas, transacciones y tareas internas.|Red interna del despliegue.|
|SQL Server|Datos empresariales, sesiones e información operativa.|Sin acceso público directo.|
|Almacenamiento de respaldos|Copias protegidas fuera del host principal.|Acceso restringido.|

### Mismo origen para frontend y API

El navegador accederá al frontend y a `/api` mediante el mismo origen.

Esto simplifica el manejo de cookies y evita habilitar CORS innecesariamente.

En desarrollo podrá utilizarse el proxy de Vite hacia el backend.

Node.js se utilizará para construir y probar el frontend; no será un segundo backend de negocio. Vite proporciona plantillas React con TypeScript y genera recursos para despliegue. [Documentación de Vite](https://vite.dev/guide/).

## 8. Módulos del backend

### `tenancy`

Propietario de:

- Organizaciones.
    
- Sede inicial.
    
- Estado de servicio.
    
- Límites operativos.
    

Expone consultas autorizadas sobre pertenencia, estado y capacidad.

### `identity`

Propietario de:

- Usuarios.
    
- Credenciales.
    
- Roles predefinidos.
    
- Sesiones.
    
- Recuperación de acceso.
    
- Vinculación de identidad con colaborador mediante contratos definidos.
    

### `workforce`

Propietario de:

- Colaboradores.
    
- Puestos.
    
- Indisponibilidades.
    
- Elegibilidad operativa.
    

### `scheduling`

Propietario de:

- Tipos de turno.
    
- Políticas de planificación.
    
- Borradores.
    
- Asignaciones.
    
- Publicaciones y revisiones.
    
- Validación de horarios.
    
- Cálculo de tiempo programado.
    

### `swaps`

Propietario de:

- Solicitudes de intercambio.
    
- Participantes.
    
- Estados.
    
- Respuestas y motivos.
    

Solicita a `scheduling` validar y aplicar el cambio de asignaciones.

### `reporting`

Responsable de:

- Consultas de programación.
    
- Totales por periodo.
    
- Exportaciones.
    

Puede usar consultas de lectura especializadas, documentadas y limitadas a la organización.

No modifica asignaciones ni estados operativos.

### `audit`

Propietario del registro de cambios críticos.

Participa en la misma transacción de las operaciones que requieren auditoría obligatoria.

### `notifications`

Responsable de:

- Eventos pendientes de aviso.
    
- Avisos internos.
    
- Destinatarios.
    
- Reintentos y deduplicación.
    
- Estado de lectura cuando se implemente.
    

### Infraestructura compartida

Se limita a capacidades técnicas como:

- Tiempo.
    
- Transacciones.
    
- Errores.
    
- Correlación.
    
- Coordinación de escrituras.
    
- Contexto autenticado.
    

No se creará un módulo `common` que concentre reglas de todos los dominios.

## 9. Límites entre módulos

### Estructura interna propuesta

Cada módulo separará, cuando corresponda:

- `api`: entrada HTTP.
    
- `application`: casos de uso y coordinación.
    
- `domain`: reglas y estados.
    
- `infrastructure`: persistencia e integraciones.
    

No es obligatorio crear interfaces o carpetas vacías.

### Reglas

- Los controladores no contienen reglas de negocio.
    
- Los módulos no acceden a repositorios internos ajenos.
    
- La comunicación interna utiliza interfaces explícitas.
    
- No se devuelve una entidad JPA directamente como respuesta HTTP.
    
- Las consultas especializadas de reportes tienen dependencias documentadas.
    
- No se crean dependencias circulares.
    

### Intercambios

`swaps` coordina la solicitud y llama a una interfaz de `scheduling`.

Ambos trabajan dentro de una misma transacción local.

`scheduling` no necesita conocer los detalles internos de `swaps`. La coordinación de solicitudes afectadas se realizará mediante un servicio de aplicación definido para esa operación.

Los límites se comprobarán mediante pruebas de arquitectura.

## 10. Estrategia multiempresa

### Base compartida y esquema compartido

Se propone:

- Una base de datos principal.
    
- Tablas compartidas.
    
- Identificación explícita de organización en los datos empresariales.
    

Se utilizará consistentemente `organization_id`.

No se crearán una base y un despliegue por cliente en el MVP.

### Controles

1. Resolver la organización a partir de una identidad autenticada.
    
2. Filtrar todas las consultas empresariales.
    
3. Validar pertenencia de asociaciones.
    
4. Aplicar restricciones de base de datos.
    
5. Probar intentos de acceso cruzado.
    

### Integridad de relaciones

Las relaciones entre recursos empresariales deberán incluir la organización cuando sea necesario para impedir asociaciones cruzadas.

Por ejemplo, una asignación no podrá referenciar un colaborador de otra empresa aunque el identificador exista.

Los UUID no constituyen una medida suficiente de autorización.

### Repositorios

No se expondrán operaciones empresariales genéricas sin contexto, como una búsqueda por identificador que omita la organización.

Las búsquedas globales del operador deben estar separadas y limitadas a las funciones administrativas autorizadas.

### Seguridad por filas en la base

No se dependerá inicialmente de políticas RLS.

Podrán evaluarse como defensa adicional, pero no sustituirán la autorización de la aplicación ni se activarán sin probar su interacción con conexiones reutilizadas y tareas internas.

## 11. Autenticación y sesiones

### Decisión: sesión opaca de servidor

El navegador conservará únicamente un identificador de sesión mediante cookie.

La sesión se almacenará en SQL Server usando Spring Session JDBC, que proporciona persistencia JDBC para sesiones HTTP. [Documentación oficial](https://docs.spring.io/spring-session/reference/configuration/jdbc.html).

### Motivo

El MVP es una aplicación web propia, sin clientes externos ni aplicaciones móviles nativas.

Necesita:

- Cerrar sesiones.
    
- Bloquear usuarios.
    
- Suspender organizaciones.
    
- Aplicar cambios de permisos sin esperar a que venza un token autónomo.
    

La sesión de servidor encaja directamente con estos requisitos.

### Identificación empresarial en el acceso

Se propone un código de empresa más correo y contraseña.

El código identifica el contexto de inicio, pero no concede autorización.

Después del acceso, la organización autorizada se obtiene de la sesión validada; no de un campo libre enviado en cada solicitud.

### Cookie de sesión

En el entorno público:

- `HttpOnly`.
    
- `Secure`.
    
- `SameSite=Lax`, sujeto a verificación del flujo final.
    
- Sin compartirla entre dominios innecesariamente.
    
- Identificador renovado después de autenticar.
    
- Eliminación de la cookie al cerrar sesión.
    
- Invalidación de la sesión en el servidor.
    

No se almacenarán credenciales de sesión en `localStorage` o `sessionStorage`.

### Comprobaciones por solicitud

La aplicación comprobará el estado vigente de:

- Usuario.
    
- Organización.
    
- Rol y permisos.
    

No se utilizará indefinidamente una copia antigua de autoridades guardada en la sesión.

Una desactivación o cambio de permisos afecta a las solicitudes iniciadas después de confirmarse el cambio. Las operaciones críticas también revalidarán autorización dentro de su transacción antes de modificar datos.

### Duración propuesta

- Inactividad máxima: 30 minutos.
    
- Duración absoluta: 8 horas.
    
- Sin “recordarme” en el MVP.
    

Son parámetros de producto propuestos y ajustables antes del piloto.

## 12. CSRF y protección del navegador

El uso de cookies requiere protección CSRF para operaciones que modifican estado.

Se propone:

- Mantener CSRF habilitado.
    
- Facilitar el token CSRF al frontend mediante un mecanismo definido.
    
- Enviarlo en las solicitudes de modificación.
    
- Renovarlo cuando corresponda después del acceso o cierre.
    
- No utilizar solicitudes GET para modificar datos.
    
- Restringir orígenes y encabezados permitidos.
    

La cookie de sesión y el token CSRF tienen finalidades diferentes. La sesión permanece inaccesible a JavaScript.

Spring Security documenta consideraciones específicas para integrar CSRF con aplicaciones de una sola página. [Guía oficial de CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

También se aplicarán:

- Escape y renderizado seguro de contenido.
    
- Encabezados de seguridad.
    
- Política de contenido ajustada al frontend.
    
- Límites de tamaño de solicitud.
    
- Restricciones de intentos de acceso.
    

No se desactivarán estas protecciones para resolver errores de integración.

## 13. Contraseñas y recuperación

### Almacenamiento

Se propone Argon2id mediante una biblioteca mantenida e integrada con Spring Security.

Los parámetros se fijarán después de medir el costo en el entorno objetivo.

No se diseñará un algoritmo propio.

### Recuperación asistida durante el piloto

1. Se verifica la identidad mediante un procedimiento aprobado.
    
2. Se genera un mecanismo temporal de un solo uso.
    
3. Se entrega por un canal verificado.
    
4. El usuario establece su contraseña.
    
5. Se invalida el mecanismo.
    
6. Se revocan las sesiones anteriores.
    
7. Se registra el evento sin secretos.
    

Propuesta de vencimiento del mecanismo: 30 minutos.

El servidor conservará una representación no recuperable del secreto temporal cuando sea posible, no su valor reutilizable.

La recuperación asistida no permite omitir la verificación de identidad.

### Operador del SaaS

La administración inicial del servicio se realizará mediante una herramienta restringida que invoque servicios autorizados, no mediante ediciones improvisadas de tablas.

No se expondrá un panel público de superadministrador en el MVP.

Antes del piloto se definirá cómo proteger ese acceso administrativo y recuperar su control.

## 14. Persistencia y migraciones

### Acceso a datos

Se propone JPA para operaciones habituales y SQL explícito donde aporte claridad o control:

- Reportes.
    
- Bloqueos.
    
- Restricciones particulares.
    
- Consultas agregadas.
    

Se evitará acceder a relaciones perezosas desde la serialización HTTP.

Las transacciones se delimitarán en servicios de aplicación.

### Migraciones

Flyway administrará los cambios de esquema. Su integración con SQL Server requiere las dependencias y controlador correspondientes. [Referencia oficial de Flyway para SQL Server](https://documentation.red-gate.com/flyway/reference/database-driver-reference/sql-server-database).

Reglas:

- Una migración aplicada no se modifica.
    
- Los cambios posteriores se expresan mediante nuevas migraciones.
    
- El esquema se valida al iniciar.
    
- No se utilizará actualización automática de Hibernate en producción.
    
- Las tablas de sesión también se gestionarán de forma controlada.
    
- Los datos de demostración estarán separados de los cambios estructurales.
    
- La cuenta de ejecución no tendrá permisos administrativos de esquema.
    
- Las migraciones se ejecutarán con una identidad de despliegue apropiada.
    

No se mantendrán scripts DDL manuales divergentes del historial de migraciones.

## 15. Concurrencia e integridad

### Problema

La validación previa de una pantalla no impide que otra operación cambie los datos antes de guardar.

El problema incluye:

- Publicaciones de semanas adyacentes.
    
- Intercambios simultáneos.
    
- Cambios de disponibilidad.
    
- Cambios de política.
    
- Desactivaciones.
    

### Estrategia inicial

Se combinarán:

1. Versiones optimistas para detectar ediciones desactualizadas.
    
2. Restricciones de base de datos.
    
3. Coordinación transaccional de escrituras por organización.
    
4. Revalidación dentro de la transacción.
    

### Coordinación por organización

Para el MVP se propone un bloqueo transaccional de aplicación por organización mediante `sp_getapplock`.

SQL Server permite asociar estos bloqueos a una transacción y liberarlos al finalizarla. La aplicación deberá comprobar el resultado y tratar tiempos de espera o fallos explícitamente. [Documentación de Microsoft](https://learn.microsoft.com/en-us/sql/relational-databases/system-stored-procedures/sp-getapplock-transact-sql?view=sql-server-ver17).

Procedimiento:

1. Iniciar la transacción.
    
2. Adquirir el bloqueo de la organización.
    
3. Releer identidad, estado y datos relevantes.
    
4. Comprobar versiones.
    
5. Validar el resultado completo.
    
6. Persistir cambios, auditoría y eventos pendientes.
    
7. Confirmar.
    

Todas las escrituras que puedan romper estas invariantes deben seguir el mismo protocolo, incluidas las tareas administrativas o internas.

El bloqueo se adquiere antes de las lecturas de negocio que fundamentan la decisión.

### Costo aceptado

Las escrituras críticas de una empresa se procesan de forma serializada.

Esto simplifica la consistencia inicial, pero puede convertirse en un límite de rendimiento. Debe medirse con la carga propuesta.

No se mantendrá el bloqueo mientras el usuario confirma una pantalla ni durante llamadas externas.

Si el rendimiento resulta insuficiente, se evaluará una coordinación más granular sin eliminar las garantías.

## 16. Publicaciones y modelo temporal

### Publicaciones inmutables

Cada publicación generará una versión que no se edita posteriormente.

La planificación mantendrá una referencia a su versión vigente.

El borrador indicará:

- Semana y sede.
    
- Versión de origen.
    
- Versión de edición.
    
- Estado.
    

### Revisiones

Una revisión crea una nueva versión publicada.

Los turnos iniciados o finalizados se conservan conforme a las reglas del documento 04.

### Intercambios

El intercambio actualiza las asignaciones vigentes y produce las nuevas versiones de las planificaciones afectadas dentro de la misma transacción.

No se implementará event sourcing: no será necesario reconstruir todo el estado reproduciendo eventos históricos.

El diseño exacto de asignaciones estables, revisiones y publicaciones se desarrollará en [[07 - Modelo de Datos y DER]].

### Tiempo

Se propone:

- Representar instantes con tipos temporales adecuados.
    
- Conservar la zona horaria de la organización.
    
- Usar `java.time`.
    
- Persistir instantes de forma coherente con UTC.
    
- Calcular semanas en la zona de la organización.
    
- No utilizar números decimales de horas como fuente original del tiempo.
    

Los minutos u horas de reportes se derivan de los intervalos, con reglas de precisión explícitas.

## 17. Auditoría y avisos recuperables

### Auditoría

Los cambios críticos y su auditoría se guardan dentro de la misma transacción.

No se utilizará una transacción independiente que permita confirmar un cambio sin su auditoría obligatoria.

La cuenta ordinaria no debe disponer de funciones para editar el historial.

La auditoría de aplicación no se presentará como inalterable frente a un administrador de base de datos; ese riesgo requiere controles operativos adicionales.

### Bandeja transaccional de eventos

Se propone una tabla de eventos pendientes, también denominada outbox.

La operación principal guarda:

- Cambio de negocio.
    
- Auditoría.
    
- Evento pendiente.
    

Todo se confirma conjuntamente.

Un proceso interno transforma esos eventos en avisos.

### Garantías

- Reintentos controlados.
    
- Identificador estable por evento.
    
- Deduplicación por evento y destinatario.
    
- Registro de errores y número de intentos.
    
- Procedimiento de recuperación de eventos fallidos.
    

No se promete entrega “exactamente una vez”. Se diseña procesamiento repetible sin avisos duplicados para el usuario.

No se incorporarán Kafka o RabbitMQ en el MVP.

## 18. API y contratos

### Estilo

API REST con JSON bajo `/api/v1`.

Las rutas concretas se definirán en [[10 - Contratos API e Integraciones]].

### Reglas

- DTO de entrada y salida.
    
- Validación de campos.
    
- Paginación.
    
- Ordenamiento mediante campos permitidos.
    
- Errores estructurados.
    
- Identificador de correlación.
    
- Versiones esperadas en operaciones concurrentes.
    
- Operaciones explícitas para publicar, aprobar, cancelar o desactivar.
    
- Sin campos libres que permitan cambiar estados arbitrariamente.
    

### Errores propuestos

- `400`: entrada inválida.
    
- `401`: sin sesión válida.
    
- `403`: operación no autorizada.
    
- `404`: recurso no disponible dentro del ámbito autorizado.
    
- `409`: conflicto de versión, estado o regla de negocio.
    
- `429`: exceso de solicitudes.
    
- `503`: indisponibilidad temporal.
    

La respuesta exacta de una organización suspendida deberá ser consistente en backend y frontend.

### OpenAPI

Se mantendrá una especificación revisable y verificaciones de correspondencia con la implementación.

springdoc-openapi documenta compatibilidad de su línea 3.x con Spring Boot 4; la combinación exacta se fijará después de la prueba técnica. [Documentación de springdoc](https://springdoc.org/).

Swagger UI estará restringido o deshabilitado en el entorno público.

No se expondrá la API como servidor MCP ni como integración pública para terceros durante el MVP.

## 19. Arquitectura del frontend

### Organización por funcionalidades

Se proponen áreas para:

- Acceso.
    
- Usuarios.
    
- Colaboradores y catálogos.
    
- Planificación.
    
- Intercambios.
    
- Reportes.
    
- Avisos.
    
- Configuración.
    

Los componentes comunes se limitarán a presentación y comportamiento reutilizable.

### Estado

- Formularios: estado local.
    
- Navegación: rutas explícitas.
    
- Sesión: contexto autenticado consultado al backend.
    
- Datos empresariales: recuperación e invalidación controlada tras cambios.
    

No se utilizará el estado frontend como autoridad sobre permisos, organización o publicación vigente.

### Reglas de interacción

- Reconsultar después de cambios críticos.
    
- Mostrar conflictos sin perder silenciosamente el trabajo.
    
- Limpiar datos empresariales al cerrar sesión.
    
- No conservar cachés de una sesión para otra.
    
- Tratar como texto el contenido externo.
    
- Ofrecer una vista móvil alternativa a la cuadrícula.
    
- No aplicar visualmente un intercambio como definitivo antes de confirmarlo en servidor.
    

No se añade Redux ni otra biblioteca global de estado sin una necesidad concreta.

El diseño visual se desarrollará en los documentos 08 y 09.

## 20. Reportes y exportaciones

Los reportes utilizarán consultas optimizadas y limitadas por organización.

Para evitar mezclar estados:

- Se identificarán las versiones publicadas utilizadas.
    
- Los datos históricos de esas versiones serán inmutables.
    
- La selección de versiones será consistente.
    
- Las exportaciones conservarán su referencia temporal.
    

Si una consulta necesita varias lecturas, deberá garantizar una vista consistente mediante una transacción de lectura apropiada o referencias inmutables fijadas al comenzar.

Se limitarán:

- Rango temporal.
    
- Filas exportadas.
    
- Tiempo de ejecución.
    
- Tamaño del resultado.
    

El CSV deberá escapar correctamente contenido y neutralizar valores que puedan interpretarse como fórmulas al abrirse en una hoja de cálculo.

No se generarán exportaciones mediante consultas sin ámbito empresarial.

## 21. Despliegue y operación

### Entornos

- **Local:** desarrollo.
    
- **Pruebas:** automatización y datos sintéticos.
    
- **Demostración:** presentación controlada.
    
- **Piloto:** uso acordado con organizaciones.
    
- **Comercial:** operación bajo condiciones de servicio definidas.
    

No se utilizarán datos reales como datos de prueba por comodidad.

### Despliegue inicial

Se propone un host Linux x86-64 con:

- Nginx.
    
- Una instancia del backend.
    
- SQL Server.
    
- Volúmenes persistentes.
    
- Acceso administrativo restringido.
    

El proveedor y tamaño se elegirán después de medir consumo y revisar presupuesto.

### Límites reconocidos

Un solo host es un punto único de fallo.

Docker Compose no aporta por sí mismo alta disponibilidad, respaldos ni recuperación automática del negocio.

No se prometerá disponibilidad continua mientras la operación no pueda sostenerla.

### Respaldos

- Copias periódicas.
    
- Almacenamiento fuera del host principal.
    
- Protección de credenciales.
    
- Retención definida.
    
- Alertas ante fallos.
    
- Restauraciones ensayadas.
    

Conservar un volumen no equivale a tener un respaldo.

La programación no dependerá de funciones exclusivas de una edición de SQL Server no incluida en el despliegue.

## 22. Estrategia de pruebas

### Dominio

- Restricciones temporales.
    
- Estados.
    
- Elegibilidad.
    
- Cálculo de programación.
    
- Independencia del aprobador.
    

### Persistencia

- Migraciones.
    
- Relaciones entre organizaciones.
    
- Unicidad.
    
- Bloqueos.
    
- Versiones.
    
- Atomicidad.
    
- Auditoría.
    
- Sesiones.
    

Se utilizará SQL Server real para las pruebas que dependan de sus transacciones y restricciones. Testcontainers dispone de un módulo para SQL Server, cuyo uso requiere aceptar las condiciones de la imagen correspondiente. [Documentación oficial](https://java.testcontainers.org/modules/databases/mssqlserver/).

No se utilizará H2 como sustituto para demostrar estas garantías.

### API y seguridad

- Sesión y CSRF.
    
- Cambio inmediato de permisos.
    
- Suspensión.
    
- Acceso cruzado.
    
- Validación de campos.
    
- Errores.
    
- Repetición y concurrencia.
    

### Navegador

- Recorrido completo del MVP.
    
- Sesión vencida.
    
- Conflictos de versión.
    
- Intercambio.
    
- Responsive.
    
- Navegación por teclado.
    
- Exportación.
    

## 23. Pruebas de concepto antes de construir módulos completos

Estas pruebas se programarán al comenzar la implementación, después del cierre de planificación.

### PoC-01 — Compatibilidad del stack

Verificar:

- Java y Spring Boot.
    
- SQL Server y controlador.
    
- Migraciones.
    
- Spring Session JDBC.
    
- OpenAPI.
    
- Frontend y proxy.
    

### PoC-02 — Seguridad de sesión

Demostrar:

- Login y logout reales.
    
- Cookie protegida.
    
- CSRF.
    
- Desactivación con sesión abierta.
    
- Cambio de rol efectivo.
    
- Suspensión empresarial.
    

### PoC-03 — Concurrencia

Demostrar:

- Dos publicaciones incompatibles no se confirman.
    
- Una asignación no recibe dos solicitudes pendientes.
    
- Un intercambio no se aplica parcialmente.
    
- Un borrador antiguo no sobrescribe una publicación nueva.
    

### PoC-04 — Respaldo y restauración

Restaurar una base de prueba en un entorno separado y verificar datos, versiones y acceso.

Una PoC fallida exige revisar la decisión correspondiente antes de extender la implementación.

## 24. Decisiones arquitectónicas resumidas

|   |   |   |
|---|---|---|
|ID|Decisión propuesta|Consecuencia|
|ADR-001|Monolito modular|Un despliegue backend con límites internos verificables.|
|ADR-002|React y TypeScript|Frontend tipado; requiere proceso de construcción.|
|ADR-003|Java, Spring Boot y SQL Server|Continuidad con la base técnica original.|
|ADR-004|Base y esquema compartidos|Aislamiento obligatorio por organización.|
|ADR-005|Sesiones JDBC, no JWT autónomo|Revocación y permisos actuales con dependencia de almacenamiento de sesión.|
|ADR-006|Mismo origen y CSRF|Simplifica cookies y exige integración de protección en frontend.|
|ADR-007|Coordinación de escrituras por organización|Consistencia inicial sencilla, con límite de concurrencia que debe medirse.|
|ADR-008|Publicaciones inmutables|Historial verificable y consumo adicional de almacenamiento.|
|ADR-009|Auditoría y outbox transaccionales|Cambios trazables y avisos recuperables.|
|ADR-010|Despliegue inicial en un host|Operación sencilla, sin alta disponibilidad.|
|ADR-011|Migraciones versionadas|Cambios reproducibles y controlados.|

Los ADR se ampliarán solo cuando una decisión lo necesite. No se duplicará todo este documento en notas adicionales.

## 25. Complejidad excluida

No se incluyen inicialmente:

- Microservicios.
    
- Kubernetes.
    
- Redis.
    
- Kafka o RabbitMQ.
    
- Event sourcing.
    
- Bases separadas por cliente.
    
- Motor genérico de reglas.
    
- Arquitectura reactiva.
    
- GraphQL.
    
- Servidor OAuth propio.
    
- Aplicaciones móviles nativas.
    
- Motor de inteligencia artificial.
    
- Exposición de herramientas MCP del producto.
    

Estas exclusiones no impiden evolucionar. Evitan incorporar costos que todavía no responden a requisitos del MVP.

## 26. Pendientes de aprobación

- Confirmar restricciones tecnológicas del curso.
    
- Aprobar React con TypeScript.
    
- Aprobar sesiones de servidor en sustitución de JWT.
    
- Aprobar SQL Server como motor único.
    
- Validar edición y condiciones del entorno operativo.
    
- Aceptar el límite inicial de una instancia backend.
    
- Aprobar el protocolo de coordinación por organización.
    
- Definir procedimiento administrativo y recuperación.
    
- Estimar infraestructura después de las pruebas técnicas.
    
- Fijar versiones exactas antes de comenzar a construir.
    

## 27. Documentos relacionados

- [[04 - Roles, Procesos y Reglas de Negocio]]
    
- [[05 - Requisitos y Casos de Uso]]
    
- [[07 - Modelo de Datos y DER]]
    
- [[08 - Navegación, Pantallas y Flujos de Usuario]]
    
- [[09 - Design System y Wireframes]]
    
- [[10 - Contratos API e Integraciones]]
    
- [[11 - Backlog MVP y Plan de Implementación]]
    
- [[12 - Pruebas, Seguridad y Operación]]
    

## 28. Criterio de cierre

La arquitectura estará aprobada como base cuando:

- Las decisiones respondan a los requisitos.
    
- No existan contradicciones con el alcance.
    
- La separación empresarial tenga controles concretos.
    
- Las sesiones cumplan desactivación y permisos actuales.
    
- Las operaciones críticas tengan una estrategia de consistencia.
    
- Las responsabilidades de los módulos estén delimitadas.
    
- La operación comercial no dependa de una edición no autorizada para ese uso.
    
- Las limitaciones de disponibilidad y capacidad estén reconocidas.
    
- Las pruebas de concepto estén incluidas en el plan de implementación.
    

La aprobación del diseño no equivale a demostrar su funcionamiento: esa evidencia se obtendrá durante las pruebas técnicas y de aceptación.