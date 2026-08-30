# RESUMEN ESTABLE DEL PRODUCTO — TURNOSHIFT

## 1. Producto y Propósito
TurnoShift es una plataforma web SaaS diseñada para la gestión integral de turnos de trabajo, horarios e intercambios entre colaboradores. Su segmento inicial propuesto son restaurantes y cafeterías con una operación acotada.

## 2. Alcance del MVP
- **Multitenancy y Aislamiento:** Organizaciones estrictamente aisladas. Una sede activa por empresa en la etapa inicial.
- **Acceso e Identidad:** Incorporación asistida. Distinción clara entre usuarios de cuenta y colaboradores sin cuenta. Roles predefinidos.
- **Planificación:** Gestión de indisponibilidades, políticas versionadas de activación inmediata, creación de borradores manuales y publicaciones inmutables.
- **Intercambios:** Proceso de solicitud e intercambio de turnos entre colaboradores con supervisión y aprobación por un responsable independiente.
- **Visibilidad y Operación:** Consultas de horario personal, reportes de horas programadas e intercambios, exportación CSV consistente, registro de auditoría, avisos y procedimientos de salida asistida.

## 3. Exclusiones Principales
El MVP **no** incluye: control de asistencia en tiempo real, procesamiento de nómina, generación automática de horarios (algoritmos estocásticos/IA), aplicación móvil nativa, modo offline, multisede avanzada, cobros automáticos, chat interno ni generador personalizado de reportes.

## 4. Stack Propuesto (Propuesta de partida)
- **Backend:** Java 21, Spring Boot 4.0.x (sujeto a prueba de compatibilidad), Spring MVC, Spring Security, Spring Session JDBC, Spring Data JPA, Flyway, Maven Wrapper.
- **Base de Datos:** SQL Server 2022 (Express/Developer).
- **Frontend:** React, TypeScript, Vite, CSS Vanilla con variables de diseño, OpenAPI.
- **Pruebas y Entorno:** JUnit, Testcontainers, Vitest, Testing Library, Playwright, Docker Compose, Nginx.

## 5. Invariantes de Negocio y Arquitectura
1. **Autenticación y Permisos:** Sesiones administradas en servidor. La pertenencia a la empresa se resuelve desde la sesión y los permisos se validan en el backend en cada petición.
2. **Inmutabilidad:** Las publicaciones y revisiones históricas son inmutables. Los turnos ya iniciados se conservan intactos frente a modificaciones posteriores.
3. **Atomicidad en Intercambios:** El intercambio involucra dos asignaciones reservadas y aplica ambos cambios o ninguno. Los participantes no pueden actuar como aprobadores independientes.
