
---
# TurnoShift SaaS — Formulación y Visión del Producto

## 1. Resumen ejecutivo

**TurnoShift** es una propuesta de plataforma SaaS B2B para ayudar a empresas con personal que trabaja por turnos a organizar sus horarios, gestionar cambios y mantener trazabilidad de su operación.

El producto busca centralizar un proceso que puede estar distribuido entre hojas de cálculo, mensajes y registros separados: la planificación de turnos, su comunicación, los intercambios y, según el alcance que se apruebe, el seguimiento básico de asistencia.

Su finalidad es evolucionar hacia un servicio comercial que genere ingresos recurrentes mediante suscripciones empresariales.

La primera versión será un **MVP funcional**: un producto limitado pero completo en sus procesos esenciales, que permita validar utilidad, facilidad de uso y disposición de pago con organizaciones representativas.

El curso **Integrador 1: Sistemas de Software** será el primer marco de desarrollo y evaluación. La visión del producto continuará más allá de la entrega académica.

> Esta nota define la dirección del producto. No constituye evidencia de demanda, garantía de rentabilidad ni aprobación de todas las funcionalidades mencionadas.

## 2. Origen y propósito

El proyecto parte de una propuesta documentada de gestión de turnos, intercambios, asistencia y consolidación de horas.

Durante la revisión inicial se identificó la necesidad de distinguir entre:

- La visión comercial de largo plazo.
    
- Las funciones indispensables del primer MVP.
    
- Las exigencias académicas del curso.
    
- Las capacidades que deberán incorporarse después de validar el producto.
    

El propósito de TurnoShift no es reunir la mayor cantidad posible de funcionalidades, sino resolver de forma confiable un problema operativo por el que una empresa esté dispuesta a pagar.

## 3. Problema que se busca resolver

### 3.1 Hipótesis del problema

En algunas organizaciones con horarios rotativos, la planificación y modificación de turnos podría depender de herramientas desconectadas y coordinación informal.

Esto puede producir:

- Varias versiones del mismo horario.
    
- Asignaciones incompatibles o superpuestas.
    
- Dificultad para conocer la disponibilidad del personal.
    
- Cambios de turno sin autorización o sin registro.
    
- Tiempo administrativo dedicado a confirmar horarios.
    
- Falta de claridad sobre quién aprobó una modificación.
    
- Diferencias entre lo programado y lo registrado como asistencia.
    
- Trabajo adicional al preparar reportes de horas.
    

Estas situaciones son **hipótesis de partida**. Deberán contrastarse con entrevistas, ejemplos reales del proceso y pruebas de uso.

### 3.2 Problema central

> La falta de un proceso centralizado y trazable para planificar, comunicar y modificar turnos dificulta la coordinación del personal y aumenta el trabajo administrativo.

### 3.3 Evidencia disponible y faltante

Se cuenta con documentos de formulación, alcance y planificación del proyecto.

Todavía no se considera demostrado:

- Cuántas empresas presentan el problema.
    
- Qué sectores lo experimentan con mayor intensidad.
    
- Cuánto tiempo o dinero pierden.
    
- Qué soluciones utilizan actualmente.
    
- Por qué cambiarían esas soluciones.
    
- Qué funcionalidades consideran indispensables.
    
- Cuánto estarían dispuestas a pagar.
    

Estas evidencias se desarrollarán en [[02 - Modelo de Negocio SaaS y Validación Comercial]].

## 4. Visión del producto

> Convertir TurnoShift en un servicio confiable y sencillo para que las empresas organicen sus turnos y sus colaboradores conozcan y gestionen sus horarios, manteniendo control sobre los cambios y visibilidad de la operación.

La visión comercial contempla:

- Atender a varias empresas con separación de sus datos.
    
- Ofrecer acceso web con una experiencia adecuada en dispositivos móviles.
    
- Facilitar la incorporación de nuevas organizaciones.
    
- Mantener procesos operativos comprensibles.
    
- Incorporar capacidades adicionales según evidencia de uso y demanda.
    
- Generar ingresos recurrentes que permitan sostener infraestructura, mantenimiento y soporte.
    

El crecimiento deberá basarse en clientes que obtengan valor y continúen utilizando el servicio, no únicamente en registros o visitas.

## 5. Cliente objetivo inicial

### 5.1 Perfil preliminar

Empresas que:

- Organizan personal mediante turnos.
    
- Modifican sus horarios de manera recurrente.
    
- Tienen una persona responsable de planificar o aprobar cambios.
    
- Necesitan comunicar horarios a sus colaboradores.
    
- Pueden acceder a una aplicación web.
    
- Identifican dificultades concretas con su proceso actual.
    

El tamaño de empresa, país de lanzamiento y sector inicial están pendientes de validación.

### 5.2 Sectores candidatos

La propuesta original menciona:

- Restaurantes.
    
- Comercio minorista.
    
- Clínicas.
    
- Empresas de seguridad.
    

Son candidatos de investigación, no mercados confirmados para el lanzamiento.

El MVP se diseñará alrededor de un segmento inicial definido. No se asumirá que todos los sectores comparten las mismas reglas, necesidades o condiciones de trabajo.

### 5.3 Comprador y usuarios

|Participante|Interés principal|
|---|---|
|Comprador empresarial|Evaluar si el servicio justifica su costo y contratarlo.|
|Administrador de la empresa|Configurar la organización, usuarios y accesos.|
|Supervisor o planificador|Organizar horarios, resolver conflictos y aprobar cambios.|
|Colaborador|Consultar sus turnos y realizar las acciones permitidas.|
|Operador de TurnoShift|Incorporar clientes y mantener el servicio.|

Una persona puede desempeñar varias funciones.

Estos perfiles describen responsabilidades del negocio. Los roles técnicos y permisos definitivos se definirán posteriormente.

El operador de TurnoShift no debe confundirse con el administrador de una empresa cliente.

## 6. Propuesta de valor

### Para la empresa

Centralizar la planificación y los cambios de turno en un lugar donde sea posible consultar el horario vigente, detectar incompatibilidades y revisar las decisiones realizadas.

### Para el supervisor

Reducir la coordinación manual y disponer de un proceso claro para crear, publicar y modificar horarios.

### Para el colaborador

Consultar información actualizada sobre sus turnos y solicitar cambios mediante un procedimiento visible y controlado.

### Hipótesis de diferenciación

TurnoShift buscará diferenciarse mediante:

- Facilidad de uso.
    
- Claridad del proceso de cambios.
    
- Trazabilidad de decisiones.
    
- Incorporación sencilla de empresas.
    
- Enfoque en un segmento y sus problemas concretos.
    

Estas características constituyen una intención de diseño. Su ventaja frente a otras soluciones deberá comprobarse mediante investigación y pruebas.

## 7. Experiencia principal que debe resolver

El recorrido central propuesto es:

1. Una empresa se incorpora al servicio.
    
2. Configura sus colaboradores y datos operativos indispensables.
    
3. El responsable prepara una planificación de turnos.
    
4. El sistema identifica conflictos según las reglas definidas.
    
5. El responsable corrige y publica el horario.
    
6. Los colaboradores consultan sus turnos.
    
7. Los cambios siguen un proceso de solicitud y autorización.
    
8. La empresa consulta el horario actualizado y el historial de modificaciones.
    

La asistencia y los reportes de horas son extensiones candidatas de este recorrido. Su presencia en el MVP se decidirá según prioridad, esfuerzo y necesidades del segmento seleccionado.

“Preparar una planificación” no implica generación automática ni optimización mediante inteligencia artificial.

## 8. Objetivos del producto

### OBJ-01 — Resolver un problema validado

Identificar un segmento y confirmar qué dificultades de planificación justifican construir y utilizar TurnoShift.

### OBJ-02 — Entregar un MVP de extremo a extremo

Permitir completar el proceso principal sin depender de funcionalidades simuladas presentadas como terminadas.

### OBJ-03 — Facilitar el uso operativo

Lograr que los usuarios representativos puedan realizar las tareas principales con instrucciones razonables y sin asistencia constante del desarrollador.

### OBJ-04 — Establecer una base SaaS confiable

Contemplar desde el diseño la separación entre empresas, el control de acceso, la protección de datos y la continuidad del servicio.

### OBJ-05 — Validar la posibilidad de ingresos recurrentes

Investigar disposición de pago y evaluar, durante los pilotos, si el valor entregado justifica una contratación recurrente.

### OBJ-06 — Cumplir los compromisos académicos

Generar las evidencias de análisis, diseño, implementación y validación exigidas por el curso, sin confundir la calificación académica con la validación comercial.

Las metas numéricas y sus métodos de medición se fijarán cuando exista un escenario de evaluación definido. No se mantiene como resultado garantizado la reducción del 80% planteada en los documentos iniciales.

## 9. Enfoque comercial

El modelo de partida es **SaaS B2B por suscripción**.

Quedan por definir:

- Quién toma la decisión de compra.
    
- Qué unidad determinará el cobro.
    
- Qué incluye la suscripción.
    
- Qué límites tendrá el servicio.
    
- Si existirá prueba gratuita o piloto acompañado.
    
- Cuánto costará atender a cada cliente.
    
- Qué condiciones permitirán comenzar a cobrar.
    

No se fijan todavía precios ni nombres de planes.

La capacidad de registrar clientes, sus condiciones comerciales y su estado de servicio debe considerarse en el diseño. La automatización de cobros podrá incorporarse en una etapa posterior si no es indispensable para validar el MVP.

## 10. Principios del producto

1. **Valor antes que cantidad de funciones.** Cada capacidad debe responder a una necesidad identificada.
    
2. **Procesos completos antes que pantallas aisladas.** Un flujo debe incluir validaciones, errores, permisos y resultado.
    
3. **Separación entre empresas desde la base.** Ninguna organización debe acceder a información de otra.
    
4. **Simplicidad operativa.** El uso cotidiano no debería requerir conocimientos técnicos.
    
5. **Control y trazabilidad.** Las modificaciones sensibles deben identificar actor, acción y momento.
    
6. **Alcance explícito.** Una funcionalidad futura no se presentará como parte del MVP.
    
7. **Evidencia antes que promesas.** Ahorros, adopción y disposición de pago deberán medirse.
    
8. **Evolución gradual.** Se prepararán límites claros sin construir anticipadamente todas las funciones futuras.
    
9. **Protección de datos.** Solo se solicitarán datos necesarios para los procesos aprobados.
    
10. **Sostenibilidad comercial.** El soporte y la operación forman parte del producto, no son tareas ajenas a él.
    

## 11. Límites de la visión inicial

TurnoShift no se plantea inicialmente como:

- Un ERP general.
    
- Un sistema integral de recursos humanos.
    
- Una plataforma de procesamiento bancario.
    
- Un sistema completo de cálculo de remuneraciones.
    
- Una herramienta de vigilancia invasiva del personal.
    
- Una garantía automática de cumplimiento de toda normativa laboral.
    
- Una solución universal para cualquier sector desde su primera versión.
    

Cualquier cálculo de horas o validación laboral deberá tener límites documentados y reglas verificadas para el contexto aplicable.

## 12. Evolución prevista

### Etapa 1 — Descubrimiento y planificación

Definir cliente inicial, problema, modelo de negocio, alcance, experiencia y arquitectura.

### Etapa 2 — MVP funcional

Implementar y probar el proceso central y las capacidades SaaS indispensables.

### Etapa 3 — Piloto controlado

Evaluar el producto con usuarios representativos, registrar problemas y comprobar su utilidad operativa.

### Etapa 4 — Primera oferta comercial

Establecer condiciones de contratación, soporte y cobro cuando exista preparación suficiente para atender clientes.

### Etapa 5 — Evolución

Priorizar mejoras a partir del uso, la retención, los costos operativos y las necesidades comerciales.

Estas etapas no implican fechas ni ingresos garantizados. Sus entregables y condiciones de avance se definirán en el roadmap.

## 13. Decisiones y pendientes

### Dirección confirmada

- TurnoShift se concibe como un SaaS comercial.
    
- Se construirá primero un MVP funcional.
    
- La planificación se realizará en Obsidian antes de implementar.
    
- La documentación se organizará por temas y se elaborará en orden.
    
- El curso será el primer hito del proyecto.
    

### Decisiones pendientes

- Segmento y cliente inicial.
    
- País y contexto operativo de lanzamiento.
    
- Empresa o participantes para el piloto.
    
- Alcance definitivo del MVP.
    
- Funciones de asistencia y consolidación de horas.
    
- Incorporación de empresas: asistida o autoservicio.
    
- Modelo de cobro y precios.
    
- Stack y decisiones de arquitectura.
    
- Disponibilidad de tiempo y presupuesto.
    
- Requisitos oficiales del curso.
    

Las decisiones pendientes se resolverán en sus documentos correspondientes. No se interpretan como compromisos de implementación.

## 14. Documentos relacionados

- [[02 - Modelo de Negocio SaaS y Validación Comercial]]
    
- [[03 - Alcance MVP y Roadmap Comercial]]
    
- [[04 - Roles, Procesos y Reglas de Negocio]]
    
- [[06 - Arquitectura y Stack Tecnológico]]
    
- [[14 - Gestión Académica y Entregables]]
    

## 15. Estado de aprobación

**Estado: borrador para revisión.**

Este documento estará aprobado cuando el responsable confirme que:

- La visión refleja el producto que desea construir.
    
- El objetivo comercial está correctamente representado.
    
- El problema descrito es una hipótesis que debe validarse.
    
- Los límites iniciales son adecuados.
    
- Los pendientes están identificados sin presentarse como decisiones cerradas.