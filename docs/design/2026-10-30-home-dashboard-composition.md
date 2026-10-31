# Composición de Inicio — TurnoShift

## Intención

Reorganizar la pantalla de Inicio como un panel operativo compacto y asimétrico, inspirado en la jerarquía visual aprobada por el usuario: módulos a la izquierda, datos reales en el centro y acciones existentes a la derecha.

## Alcance

- Conservar rutas, permisos RBAC, textos funcionales y llamadas API actuales.
- Mostrar solamente los contadores y disponibilidad ya obtenidos por `HomePage`.
- Mantener Índigo `#5D5FEF` como acción y panel destacado, con superficies `#FFFFFF` sobre canvas `#EFF0F6`.
- No crear planificación, tareas, gráficos, calendarios ni métricas ficticias.

## Composición aprobada

1. Cabecera: saludo a la izquierda y tres tarjetas contextuales compactas a la derecha.
2. Columna izquierda: panel blanco de módulos operativos, con cuatro accesos ilustrados en cuadrícula 2×2.
3. Columna central: panel blanco de resumen operativo con los contadores reales de puestos, colaboradores e indisponibilidades; debajo, tarjeta índigo de disponibilidad.
4. Columna derecha: panel blanco de acciones operativas que reutiliza las rutas reales de los módulos.
5. La composición debe caber en el viewport de demostración sin scroll exterior y responder apilándose por debajo de 980 px.
