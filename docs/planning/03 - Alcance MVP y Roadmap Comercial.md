

# TurnoShift SaaS — Alcance MVP y Roadmap Comercial

## 1. Propósito

Definir qué tendrá la primera versión funcional de TurnoShift, qué quedará fuera y cómo evolucionará hacia un servicio comercial.

Este documento será la referencia para evitar incorporar funcionalidades sin evaluar su impacto.

Desarrolla:

- [[01 - Formulación y Visión del Producto]]
    
- [[02 - Modelo de Negocio SaaS y Validación Comercial]]
    

**El alcance descrito es una propuesta para aprobación.** No representa funcionalidades implementadas ni compromisos comerciales ya asumidos.

## 2. Decisión central de alcance

### Propuesta

El MVP se concentrará en:

> Incorporar empresas, gestionar sus colaboradores, preparar y publicar horarios, detectar conflictos y tramitar intercambios autorizados, manteniendo separación de datos y trazabilidad.

La asistencia y la consolidación de horas se reservarán para una entrega posterior.

### Justificación

La planificación y los intercambios forman un proceso completo que puede validarse por sí mismo.

La asistencia añade problemas distintos:

- Identidad de quien marca.
    
- Entradas y salidas incompletas.
    
- Correcciones.
    
- Tardanzas y justificaciones.
    
- Diferencias entre tiempo programado y trabajado.
    
- Reglas de cálculo.
    
- Posibles disputas sobre registros.
    

Separar estas capacidades permite verificar primero si el cliente obtiene suficiente valor del proceso central.

### Condición de revisión

Si las entrevistas muestran que las empresas no contratarían una solución sin asistencia, deberá revisarse el alcance antes de congelar el backlog.

No se añadirá asistencia automáticamente manteniendo el mismo calendario: será necesario retirar otra capacidad, ampliar plazo o revisar el segmento.

## 3. Qué significa MVP funcional

El MVP no será una colección de pantallas demostrativas.

Debe permitir completar sus procesos esenciales con:

- Persistencia real.
    
- Autenticación.
    
- Permisos aplicados en el servidor.
    
- Separación entre empresas.
    
- Validaciones.
    
- Manejo de errores.
    
- Historial de acciones relevantes.
    
- Pruebas.
    
- Despliegue reproducible.
    
- Respaldo y recuperación.
    

Una función pendiente no se mostrará como operativa.

El uso de datos ficticios en una demostración se identificará claramente.

## 4. Segmento y límites operativos iniciales

Se mantiene como hipótesis comercial investigar restaurantes y cafeterías.

### Recorte propuesto para el primer piloto

- Empresas con una sede operativa.
    
- Aproximadamente 10 a 50 colaboradores por empresa.
    
- Planificación semanal.
    
- Turnos definidos mediante fecha y hora de inicio y fin.
    
- Un contexto horario configurado por organización.
    
- Acceso web.
    
- Incorporación acompañada.
    

Estos límites son un escenario de diseño y validación, no una capacidad técnica ya medida.

### Relación con la visión multisede

El documento comercial propone investigar empresas de una a tres sedes.

El MVP comenzaría por el subconjunto de una sede. La operación multisede quedaría para una fase posterior.

La arquitectura deberá distinguir organización y sede para permitir evolución, pero no se implementarán anticipadamente todos los procesos multisede.

### Supuestos que deberán confirmarse

- El cliente acepta planificar manualmente con validaciones.
    
- No necesita un optimizador automático como condición de compra.
    
- Sus reglas principales pueden expresarse con parámetros acotados.
    
- La coordinación de turnos aporta valor sin integrar nómina.
    
- Puede utilizar una aplicación conectada a Internet.
    

## 5. Usuarios incluidos

### Operador de TurnoShift

Responsable de incorporar y administrar el servicio de las empresas piloto.

No equivale al administrador de una empresa cliente.

### Administrador de empresa

Configura los datos operativos, administra usuarios y supervisa el funcionamiento de su organización.

### Supervisor

Prepara horarios, publica planificaciones y resuelve solicitudes dentro de su ámbito autorizado.

### Colaborador

Consulta sus turnos y participa en intercambios permitidos.

El MVP utilizará roles predefinidos. La matriz exacta de permisos se establecerá en [[04 - Roles, Procesos y Reglas de Negocio]].

No se incluye un constructor libre de roles y permisos personalizados.

## 6. Capacidades incluidas

### MVP-01 — Incorporación y administración básica de empresas

El operador podrá:

- Registrar una organización.
    
- Configurar su sede inicial.
    
- Establecer los límites acordados del piloto.
    
- Crear o habilitar su primer administrador.
    
- Consultar su estado de servicio.
    
- Activar o suspender acceso mediante un procedimiento controlado.
    

La operación podrá utilizar una herramienta administrativa mínima o un procedimiento seguro documentado. No exige un portal comercial completo.

La suspensión no implicará eliminación automática de datos.

No se incluye acceso irrestricto del operador a la información de los colaboradores. Cualquier mecanismo de soporte privilegiado deberá estar delimitado y auditado.

### MVP-02 — Acceso y gestión de usuarios

Incluye:

- Inicio y cierre de sesión.
    
- Control de sesión.
    
- Usuarios asociados a una organización.
    
- Roles predefinidos.
    
- Activación y desactivación.
    
- Cambio de contraseña.
    
- Recuperación de acceso mediante un mecanismo seguro.
    

La recuperación podrá ser asistida durante el piloto, con verificación de identidad y trazabilidad. No se enviarán contraseñas permanentes en texto plano.

La identificación de la organización durante el acceso se resolverá en arquitectura y diseño.

No se incluye cambiar libremente entre múltiples empresas desde una sola sesión.

### MVP-03 — Colaboradores y datos operativos

Incluye:

- Registro y actualización de colaboradores.
    
- Estado activo o inactivo.
    
- Puesto o función operativa.
    
- Asociación con la sede.
    
- Tipos o plantillas básicas de turno.
    
- Restricciones de disponibilidad registradas por un responsable autorizado.
    

El registro de colaborador y la cuenta de acceso serán conceptos diferenciados.

No se recopilarán datos de nómina o información sensible que no sea necesaria para el alcance aprobado.

### MVP-04 — Planificación semanal

Un usuario autorizado podrá:

- Crear una planificación en borrador.
    
- Consultar colaboradores y turnos.
    
- Asignar turnos manualmente.
    
- Modificar o retirar asignaciones del borrador.
    
- Consultar horas programadas.
    
- Ejecutar validaciones.
    
- Publicar la planificación.
    

Las asignaciones conservarán fechas y horas completas. Se contemplarán turnos que crucen medianoche.

La planificación semanal será una forma de organizar la interfaz, no una limitación que impida detectar conflictos con la semana anterior o siguiente.

No se exige arrastrar y soltar para completar el proceso. Un formulario claro será suficiente para el MVP.

### MVP-05 — Validación de asignaciones

El alcance mínimo contempla:

- Inicio anterior al fin.
    
- Colaborador activo.
    
- Pertenencia a la organización y sede.
    
- Ausencia de solapamientos.
    
- Compatibilidad con indisponibilidades registradas.
    
- Descanso mínimo configurado.
    
- Revisión del límite de horas programadas del periodo definido.
    

Las reglas se clasificarán como:

- **Bloqueantes:** impiden completar la operación.
    
- **Advertencias:** informan una situación que puede aceptarse según una regla explícita.
    

La clasificación y las excepciones se definirán en el documento de reglas de negocio.

No se añadirá un botón general para ignorar cualquier validación.

Los parámetros no se presentarán como garantía de cumplimiento legal. Su origen, significado y vigencia deberán estar documentados.

### MVP-06 — Publicación y consulta de horarios

Incluye:

- Diferenciación entre borrador y horario publicado.
    
- Consulta de la planificación vigente.
    
- Vista personal del colaborador.
    
- Identificación de cambios posteriores.
    
- Historial mínimo de publicaciones y modificaciones.
    
- Avisos internos sobre eventos relevantes.
    

Las modificaciones del horario publicado deberán pasar por un flujo explícito de revisión y registro.

No se promete actualización instantánea por canales externos. La estrategia de refresco de la aplicación se definirá en diseño técnico.

### MVP-07 — Intercambio controlado de turnos

El flujo inicial será:

1. Un colaborador solicita intercambiar un turno futuro por otro.
    
2. El compañero acepta o rechaza.
    
3. Un supervisor autorizado revisa.
    
4. El sistema vuelve a validar las condiciones.
    
5. Si corresponde, se actualizan ambas asignaciones.
    
6. Se conserva el historial y se informa el resultado.
    

Límites propuestos:

- Dos colaboradores.
    
- Misma organización y sede.
    
- Turnos publicados y todavía no iniciados.
    
- Funciones compatibles según reglas explícitas.
    
- Sin conflictos de horario o disponibilidad.
    
- Sin modificación automática antes de la aprobación final.
    

La aprobación debe ser atómica: no puede intercambiarse un turno y quedar pendiente el otro.

Si una asignación cambia mientras la solicitud está pendiente, no se ejecutará usando información desactualizada.

Se contemplarán rechazo, cancelación e invalidación. Los estados y responsables se detallarán en el documento 04.

### MVP-08 — Reportes básicos

Incluye:

- Planificación por semana.
    
- Turnos por colaborador.
    
- Horas programadas por colaborador y periodo.
    
- Solicitudes de intercambio y su resultado.
    
- Exportación CSV de información autorizada.
    

Los reportes se identificarán como **programación**, no como asistencia real.

No se denominarán “horas trabajadas”, “horas extras pagables” o “nómina” a valores obtenidos únicamente de los turnos planificados.

### MVP-09 — Auditoría y avisos internos

Se registrarán acciones críticas como:

- Administración de usuarios.
    
- Cambios de configuración.
    
- Publicaciones.
    
- Modificaciones de asignaciones.
    
- Resoluciones de intercambios.
    
- Operaciones de administración del servicio.
    

La auditoría deberá permitir identificar actor, organización, acción y momento, sin almacenar contraseñas o secretos.

Los avisos internos se limitarán a eventos relevantes del alcance. No se incluye una plataforma general de mensajería entre empleados.

## 7. Base SaaS indispensable

Aunque los primeros clientes se incorporen manualmente, el producto debe atender a varias organizaciones sin mezclar información.

### Requisitos mínimos

- Separación de datos por organización.
    
- Autorización tanto por rol como por pertenencia.
    
- Límites de uso verificables.
    
- Configuración empresarial acotada.
    
- Historial de operaciones.
    
- Gestión segura de credenciales.
    
- Tratamiento controlado de errores.
    
- Monitoreo básico.
    
- Respaldo.
    
- Procedimiento de recuperación.
    
- Procedimiento de exportación y cierre.
    

### Validación de aislamiento

Las pruebas deben utilizar al menos dos organizaciones distintas y comprobar que una no pueda consultar ni modificar recursos de la otra, incluso alterando identificadores en las solicitudes.

Ocultar información en la interfaz no constituye aislamiento suficiente.

### Administración comercial inicial

El estado de contratación, periodo y condiciones podrá mantenerse en un registro operativo controlado durante el piloto.

No será necesario implementar desde el primer día:

- Checkout.
    
- Cobro recurrente automático.
    
- Cupones.
    
- Prorrateos.
    
- Facturación automática.
    
- Cambios autoservicio de plan.
    

Las condiciones comerciales deben existir aunque su gestión todavía sea manual.

## 8. Funcionalidades fuera del MVP

### Asistencia y remuneraciones

- Marcación de entrada y salida.
    
- PIN de asistencia.
    
- Biometría.
    
- Geolocalización.
    
- Justificaciones.
    
- Cálculo de tiempo realmente trabajado.
    
- Horas extras monetizadas.
    
- Recargos nocturnos.
    
- Nómina.
    
- Pagos a trabajadores.
    

### Automatización avanzada

- Generación automática de mallas.
    
- Optimización de cobertura.
    
- Predicción de demanda.
    
- Inteligencia artificial.
    
- Recomendaciones automáticas de reemplazo.
    

### Expansión operativa

- Operación multisede completa.
    
- Intercambios entre sedes.
    
- Varios países y marcos normativos.
    
- Reglas arbitrarias configuradas por el cliente.
    
- Convenios laborales complejos.
    

### Experiencia y canales

- Aplicaciones móviles nativas.
    
- Funcionamiento offline.
    
- Chat interno.
    
- WhatsApp o SMS.
    
- Integración con calendarios externos.
    
- Integraciones con POS, ERP o nómina.
    

### Comercialización avanzada

- Marketplace.
    
- Programa de afiliados.
    
- Planes ilimitados.
    
- Personalización de marca por empresa.
    
- Portal completo de autoservicio comercial.
    

Estas exclusiones no impiden su evaluación posterior. Evitan tratarlas como compromisos de la primera entrega.

## 9. Cambios respecto a los documentos originales

|Tema original|Propuesta revisada|
|---|---|
|Marcaciones web/PIN|Entrega posterior al MVP inicial.|
|Justificaciones y tardanzas|Entrega posterior, asociada a asistencia.|
|Cálculo de horas ordinarias, extras y recargos|MVP limitado a horas programadas; cálculo de asistencia separado.|
|Cierre de nómina|Retirado del alcance inicial.|
|Reportes Excel/CSV|CSV en el MVP; otros formatos según demanda.|
|Varios sectores|Un segmento inicial por validar.|
|Gestión de sedes|Una sede operativa por organización durante el primer piloto.|
|SaaS sin detalle de aislamiento|Separación entre organizaciones como requisito obligatorio.|
|Despliegue en producción|Entornos y condiciones diferenciados para demostración, piloto y servicio comercial.|

Estos cambios deberán reflejarse posteriormente en acta, EDT, cronograma y entregables.

Si el curso exige asistencia u otra capacidad excluida, se revisará la planificación antes de aprobarla. No se mantendrán documentos contradictorios.

## 10. Recorrido mínimo de demostración

La versión inicial debe permitir demostrar que:

1. Se incorpora una empresa.
    
2. Su administrador accede.
    
3. Registra colaboradores y disponibilidad.
    
4. El supervisor crea una semana de turnos.
    
5. El sistema rechaza una asignación superpuesta.
    
6. El supervisor corrige y publica.
    
7. Un colaborador consulta su horario.
    
8. Solicita un intercambio.
    
9. El compañero acepta.
    
10. El supervisor aprueba después de una nueva validación.
    
11. Ambos horarios quedan actualizados.
    
12. Se consulta la trazabilidad.
    
13. Se exportan horas programadas.
    
14. Una segunda empresa no puede acceder a estos datos.
    

Este recorrido se convertirá en casos de prueba y evidencias de aceptación.

## 11. Criterios generales de aceptación del MVP

### Funcionales

- El recorrido principal puede completarse sin editar directamente la base de datos.
    
- No se pierden cambios silenciosamente ante operaciones concurrentes.
    
- Los estados de planificación e intercambio son consistentes.
    
- Los reportes reflejan los datos vigentes y su significado correcto.
    

### Seguridad

- Los permisos se verifican en servidor.
    
- Se demuestra aislamiento entre organizaciones.
    
- No se exponen credenciales ni secretos.
    
- La desactivación de acceso tiene un comportamiento definido y comprobado.
    
- No existen defectos críticos conocidos que impidan usar el alcance aprobado.
    

### Calidad

- Las reglas críticas tienen pruebas positivas, negativas y de límites.
    
- Se prueban cruces de medianoche y de semana.
    
- La interfaz funciona en los tamaños acordados.
    
- Se contemplan carga, vacío, error, conflicto y sesión vencida.
    
- Las validaciones incluyen procesos completos, no solo cobertura de código.
    

La meta de cobertura se fijará en el plan de pruebas. Alcanzar un porcentaje no sustituye la comprobación de reglas y seguridad.

### Operación

- El despliegue es reproducible.
    
- La configuración no depende de secretos incluidos en el repositorio.
    
- Se verifica la restauración de un respaldo.
    
- Existen instrucciones de uso y soporte.
    
- Las limitaciones están documentadas.
    

## 12. Prioridades y recortes permitidos

Si las estimaciones superan la capacidad disponible, primero se simplificarán:

- Animaciones y detalles visuales secundarios.
    
- Arrastrar y soltar.
    
- Filtros avanzados.
    
- Duplicación de semanas.
    
- Exportaciones adicionales.
    
- Estadísticas decorativas.
    
- Automatización de tareas administrativas del operador.
    

No se recortarán para cumplir una fecha:

- Aislamiento de empresas.
    
- Autorización.
    
- Integridad de asignaciones.
    
- Validación final de intercambios.
    
- Recuperación de información.
    
- Pruebas de los procesos críticos.
    

Si el núcleo sigue sin caber, se revisará el alcance o el calendario explícitamente.

## 13. Roadmap por etapas

### R0 — Descubrimiento y planificación

**Resultado:** un alcance implementable, con hipótesis comerciales y decisiones identificadas.

Incluye:

- Documentos de planificación.
    
- Investigación del cliente.
    
- Diseño funcional.
    
- Arquitectura.
    
- Modelo de datos.
    
- Backlog.
    
- Estrategia de pruebas.
    
- Riesgos y estimaciones.
    

**Salida:** no existen decisiones bloqueantes para comenzar la primera entrega.

La investigación comercial puede continuar durante el desarrollo, pero sus pendientes deben permanecer visibles.

### R1 — MVP de planificación e intercambios

**Resultado:** proceso central completo con base SaaS.

Incluye las capacidades MVP-01 a MVP-09.

**Salida:** criterios funcionales, de seguridad y operación verificados en un entorno controlado.

No equivale todavía a validación comercial.

### R2 — Piloto acompañado

**Resultado:** evidencia de uso con organizaciones representativas.

Incluye:

- Incorporación.
    
- Capacitación breve.
    
- Uso durante ciclos reales.
    
- Registro de problemas.
    
- Medición de esfuerzo y soporte.
    
- Revisión con el comprador.
    

**Salida:** decisión documentada de continuar, ajustar o replantear.

Si solo se usan datos simulados, se identificará como evaluación funcional, no como validación comercial real.

### R3 — Primera oferta de pago

**Resultado:** servicio preparado para atender clientes bajo condiciones explícitas.

Incluye:

- Oferta y límites.
    
- Precio validado o experimental claramente informado.
    
- Condiciones de contratación y cancelación.
    
- Soporte.
    
- Gestión del periodo de servicio.
    
- Procedimientos ante incidentes.
    
- Revisión de obligaciones legales y administrativas aplicables.
    

Los cobros podrán gestionarse de forma asistida si existe un procedimiento adecuado.

**Salida:** clientes que contratan y un servicio que puede sostener su operación.

### R4 — Expansión guiada por evidencia

Candidatos:

- Asistencia y correcciones.
    
- Consolidación de horas.
    
- Operación multisede.
    
- Registro de disponibilidad por el colaborador.
    
- Plantillas avanzadas.
    
- Notificaciones externas.
    
- Autoservicio de incorporación y suscripción.
    

El orden no está aprobado. Se elegirá según necesidad repetida, disposición de pago, riesgo y esfuerzo.

### R5 — Automatización y escala

Candidatos:

- Optimización de turnos.
    
- Integraciones.
    
- Analítica avanzada.
    
- Predicción de demanda.
    
- Funciones empresariales.
    
- Expansión territorial.
    

Esta etapa no justifica introducir ahora infraestructura o módulos que el MVP no necesita.

## 14. Fechas y capacidad

Las etapas anteriores no tienen todavía fechas comprometidas.

Los documentos iniciales mencionan una ventana académica de 16 semanas y fechas de entrega que deben conciliarse.

El cronograma revisado se elaborará después de:

- Confirmar obligaciones del curso.
    
- Estimar el backlog.
    
- Conocer horas semanales disponibles.
    
- Identificar dependencias.
    
- Reservar tiempo de pruebas y correcciones.
    

No se reutilizará el calendario original como si ya contemplara este alcance.

## 15. Control de cambios

Toda nueva solicitud deberá registrar:

1. Problema que resuelve.
    
2. Solicitante y evidencia.
    
3. Usuarios afectados.
    
4. Prioridad.
    
5. Esfuerzo y riesgos.
    
6. Impacto en datos, seguridad e interfaz.
    
7. Funciones o fechas afectadas.
    
8. Decisión: incluir, posponer o rechazar.
    

Una petición comercial no se convierte automáticamente en parte del MVP.

Los cambios aprobados deberán actualizar las notas relacionadas, el backlog y las pruebas correspondientes.

## 16. Pendientes antes de congelar el alcance

- Confirmar que planificación e intercambios aportan valor sin asistencia.
    
- Aprobar el recorte inicial de una sede.
    
- Confirmar requisitos académicos obligatorios.
    
- Definir reglas bloqueantes y advertencias.
    
- Establecer el procedimiento de recuperación de acceso.
    
- Definir el comportamiento de suspensión y cierre.
    
- Confirmar capacidad de desarrollo y soporte.
    
- Estimar las capacidades incluidas.
    
- Resolver discrepancias con los documentos originales.
    

No es necesario cerrar precios o todas las funciones futuras para comenzar el desarrollo. Sí deben estar resueltas las decisiones que afecten la primera entrega.

## 17. Documentos relacionados

- [[04 - Roles, Procesos y Reglas de Negocio]]
    
- [[05 - Requisitos y Casos de Uso]]
    
- [[06 - Arquitectura y Stack Tecnológico]]
    
- [[07 - Modelo de Datos y DER]]
    
- [[11 - Backlog MVP y Plan de Implementación]]
    
- [[12 - Pruebas, Seguridad y Operación]]
    
- [[13 - Lanzamiento Piloto y Evolución Comercial]]
    
- [[14 - Gestión Académica y Entregables]]
    

## 18. Estado de aprobación

**Estado: borrador.**

Aprobar esta nota implica aceptar que:

- El MVP inicial se concentra en planificación e intercambios.
    
- La base multiempresa es obligatoria.
    
- La primera operación se limita a una sede por empresa.
    
- La asistencia y la nómina no están incluidas.
    
- Las exclusiones no se implementarán sin control de cambios.
    
- La preparación técnica y la validación comercial son hitos distintos.
    
- El roadmap expresa dirección, no promesas de entrega automática.