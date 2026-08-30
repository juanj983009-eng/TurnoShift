# REGLAS DE DESARROLLO — TURNOSHIFT

## 1. Modificación y Preservación de Código
- No modificar código existente sin una justificación directa basada en tracebacks, logs o reglas de la tarea activa.
- Preservar comentarios, docstrings e implementaciones previas que no correspondan directamente al cambio en ejecución.
- Mantener contratos de API existentes y actualizar todos los sitios de llamada al modificar firmas.

## 2. Lectura y Consulta Selectiva
- No cargar ni releer documentos extensos en su totalidad si la información necesaria puede localizarse por encabezado exacto o sección acotada.
- Verificar definiciones de estructuras de datos y esquemas completos antes de consumir o modificar sus instancias.

## 3. Seguridad y Control de Datos
- **Aislamiento Multi-tenant:** El identificador de organización proviene siempre de la sesión del servidor, jamás de parámetros mutables por el usuario.
- **Prohibición de Secretos:** Nunca incluir contraseñas, llaves API, tokens o credenciales reales en el repositorio ni en archivos de contexto.
- **Datos de prueba:** Utilizar exclusivamente datos ficticios reproducibles para pruebas locales y de integración.

## 4. Verificación y Calidad
- Prohibido declarar tareas finalizadas sin ejecución exitosa y evidencias de pruebas automatizadas o comandos de verificación.
- No enmascarar errores ni capturar excepciones de forma silenciosa para simular éxito artificial.
- Las validaciones de permisos, reglas de negocio e integridad se realizan siempre en el servidor. La interfaz refleja el estado pero no reemplaza las comprobaciones del backend.

## 5. Control de Alcance
- No añadir funcionalidades, dependencias ni abstracciones no solicitadas ni autorizadas explícitamente en la tarea activa.
- Respetar los límites y la secuencia definida en los incrementos de construcción (`I0` a `I7`).
