# Diseño visual del tercer avance — TurnoShift

## Intención

Preparar una demostración visual coherente y profesional de los módulos ya funcionales de TurnoShift: Acceso, Inicio, Configuración, Puestos, Equipo e Indisponibilidades. El rediseño no cambia contratos, reglas de negocio, RBAC ni flujos de datos.

## Alcance

- Unificar tokens, jerarquía visual, tarjetas, formularios, tablas, filtros, modales, estados vacíos, carga y error.
- Aplicar la referencia `references/dashboard-violet-reference.png` como guía de composición, tipografía, redondez y acentos.
- Conservar el ámbar como identidad y acción primaria de TurnoShift.
- Usar el violeta como acento complementario para selección, foco, estados informativos, progreso y elementos de planificación futuros.
- Confirmar comportamiento responsive y ausencia de overflow horizontal a 80 %, 100 % y 125 % de zoom.

## Exclusiones

- No crear pantallas funcionales de planificación (F5), Mi horario (F6) ni Políticas Operativas.
- No crear datos ficticios, métricas decorativas, endpoints, cambios backend ni dependencias npm.
- No sustituir la paleta de marca ámbar por violeta.

## Sistema visual

### Color

| Rol | Token | Valor |
| --- | --- | --- |
| Acción principal / marca | `--accent` | `#B45309` |
| Acción principal hover | `--accent-hover` | `#92400E` |
| Violeta de interfaz | `--violet-600` | `#6557E8` |
| Violeta suave | `--violet-100` | `#EEEAFE` |
| Fondo de aplicación | `--canvas` | `#F7F7FC` |
| Superficie | `--surface` | `#FFFFFF` |
| Texto principal | `--ink` | `#171533` |
| Texto secundario | `--muted` | `#77748C` |

Los colores semánticos de éxito, alerta y error mantienen suficiente contraste y no se comunican exclusivamente por color.

### Tipografía y forma

- `DM Sans` para marca, títulos y cifras de alta jerarquía.
- `Inter` para texto de interfaz, formularios y tablas.
- Tarjetas: radio 16 px, borde lila/gris muy tenue y sombra corta.
- Botones y controles: radio 10–12 px; píldoras solo para filtros, roles y estados.
- Espaciado base de 4 px, con mayor respiración entre secciones.

## Aplicación por superficie

1. **Shell:** canvas lila muy sutil, cabecera blanca, barra lateral clara; navegación activa violeta suave con indicador, mientras las acciones principales permanecen ámbar.
2. **Inicio:** tarjeta de bienvenida más clara, módulos homogéneos, estados de conexión y rol con jerarquía semántica.
3. **Configuración:** formularios agrupados en tarjetas limpias y avisos legibles; no cambiar los campos disponibles.
4. **Puestos, Equipo e Indisponibilidades:** cabecera y filtros consistentes, tablas legibles y acciones claramente separadas por prioridad.
5. **Modales:** fondo con blur moderado, encabezado/final de acciones visualmente fijos, cuerpo con scroll interno solo cuando sea necesario.
6. **Acceso y setup:** conservar la experiencia sobria, incorporando mejor contraste y superficies del mismo sistema.

## Criterios de aceptación visual

- Las pantallas existentes siguen usando datos reales y conservan sus permisos.
- No aparece desbordamiento horizontal global.
- El foco por teclado y los mensajes de error permanecen visibles.
- El ámbar continúa identificando confirmaciones y acciones de negocio.
- El violeta aparece como capa complementaria consistente, no como sustitución indiscriminada de la marca.
