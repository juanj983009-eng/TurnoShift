import { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { Link } from '../../components/routing/Router';
import {
  IconCalendar,
  IconArrowRight,
  IconBriefcase,
  IconUsers,
  IconCheck
} from '../../components/common/Icons';
import { positionApi } from '../../api/positionApi';
import { employeeApi } from '../../api/employeeApi';
import { unavailabilityApi } from '../../api/unavailabilityApi';
import type { UnavailabilityReason } from '../../types/unavailability';
import organizationIllustration from '../../assets/illustrations/organization-illustration.png';
import positionsIllustration from '../../assets/illustrations/positions-illustration.png';
import teamIllustration from '../../assets/illustrations/team-illustration.png';
import unavailabilityIllustration from '../../assets/illustrations/unavailability-illustration.png';
import organizationContext from '../../assets/illustrations/organization-context.png';
import timezoneContext from '../../assets/illustrations/timezone-context.png';
import sessionContext from '../../assets/illustrations/session-context.png';
import './HomePage.css';

type OperationalSummary = {
  readonly activePositions: number;
  readonly activeEmployees: number;
  readonly upcomingUnavailabilities: number;
  readonly reasons: ReadonlyMap<UnavailabilityReason | 'SIN_MOTIVO', number>;
};

const reasonLabels: Readonly<Record<UnavailabilityReason | 'SIN_MOTIVO', string>> = {
  VACACIONES: 'Vacaciones',
  PERMISO_PERSONAL: 'Permiso personal',
  MEDICO: 'Motivo médico',
  FORMACION: 'Formación',
  OTRO: 'Otro motivo',
  SIN_MOTIVO: 'Sin motivo indicado'
};

const getOperationalWindow = (): { readonly from: string; readonly to: string } => {
  const from = new Date();
  const to = new Date(from);
  to.setDate(to.getDate() + 30);
  return { from: from.toISOString(), to: to.toISOString() };
};

export default function HomePage() {
  const { session } = useAuth();
  const [operationalSummary, setOperationalSummary] = useState<OperationalSummary | null>(null);
  const [isOperationalSummaryLoading, setIsOperationalSummaryLoading] = useState(false);
  const [operationalSummaryError, setOperationalSummaryError] = useState<string | null>(null);
  const canReadOperationalSummary = session?.user.role === 'ADMINISTRADOR' || session?.user.role === 'SUPERVISOR';

  useEffect(() => {
    if (!session || !canReadOperationalSummary) {
      setOperationalSummary(null);
      setOperationalSummaryError(null);
      setIsOperationalSummaryLoading(false);
      return undefined;
    }

    let isCancelled = false;
    setIsOperationalSummaryLoading(true);
    setOperationalSummaryError(null);

    const { from, to } = getOperationalWindow();
    void Promise.all([
      positionApi.getPositions(true),
      employeeApi.getEmployees(true),
      unavailabilityApi.getOrganizationUnavailabilities(from, to, false)
    ])
      .then(([positions, employees, unavailabilities]) => {
        if (isCancelled) return;

        const reasons = new Map<UnavailabilityReason | 'SIN_MOTIVO', number>();
        for (const unavailability of unavailabilities.items) {
          const reason = unavailability.reasonCode ?? 'SIN_MOTIVO';
          reasons.set(reason, (reasons.get(reason) ?? 0) + 1);
        }

        setOperationalSummary({
          activePositions: positions.length,
          activeEmployees: employees.length,
          upcomingUnavailabilities: unavailabilities.items.length,
          reasons
        });
      })
      .catch(() => {
        if (!isCancelled) {
          setOperationalSummaryError('No se pudo actualizar el resumen operativo. Puedes consultar cada módulo para ver la información disponible.');
        }
      })
      .finally(() => {
        if (!isCancelled) {
          setIsOperationalSummaryLoading(false);
        }
      });

    return () => {
      isCancelled = true;
    };
  }, [canReadOperationalSummary]);

  if (!session) return null;

  const { user, organization } = session;

  const getGreeting = (): string => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Buenos días';
    if (hour < 19) return 'Buenas tardes';
    return 'Buenas noches';
  };

  const getRoleLabel = (r: string): string => {
    switch (r) {
      case 'ADMINISTRADOR':
        return 'Administrador';
      case 'SUPERVISOR':
        return 'Supervisor';
      case 'COLABORADOR':
        return 'Colaborador';
      default:
        return r;
    }
  };

  return (
    <div className="home-page">
      {/* Saludo y Cabecera de Inicio */}
      <section className="home-hero">
        <div className="home-hero__header">
          <span className="home-hero__role-badge">
            {getRoleLabel(user.role)}
          </span>
          <h1 className="home-hero__title">
            {getGreeting()}, {user.displayName}
          </h1>
          <p className="home-hero__subtitle">
            Panel de control operativo de <strong>{organization.displayName}</strong>.
          </p>
        </div>

        {/* Resumen de Sesión Activa Real */}
        <div className="home-session-strip">
          <article className="home-session-card">
            <div className="home-session-card__art" aria-hidden="true">
              <img src={organizationContext} alt="" width="112" height="112" />
            </div>
            <div className="home-session-label">Organización</div>
            <div className="home-session-value">{organization.displayName}</div>
          </article>

          <article className="home-session-card">
            <div className="home-session-card__art" aria-hidden="true">
              <img src={timezoneContext} alt="" width="112" height="112" />
            </div>
            <div className="home-session-label">Zona horaria oficial</div>
            <div className="home-session-value">{organization.timeZone}</div>
          </article>

          <article className="home-session-card home-session-card--lifted">
            <div className="home-session-card__art" aria-hidden="true">
              <img src={sessionContext} alt="" width="112" height="112" />
            </div>
            <div className="home-session-label">Estado de conexión</div>
            <div className="home-session-value">Sesión activa y segura</div>
          </article>
        </div>
      </section>

      <div className="home-dashboard-grid">
        {/* Módulos Operativos Accesibles según Rol */}
        <section className="home-modules">
        <h2 className="home-section-title">Módulos del Sistema</h2>

        <div className="home-grid">
          {/* Módulo Configuración (solo Administrador) */}
          {user.role === 'ADMINISTRADOR' && (
            <div className="home-card">
              <div className="home-card__header">
                <div className="home-card__art">
                  <img src={organizationIllustration} alt="" width="96" height="96" />
                </div>
                <span className="home-card__badge">Fase F2</span>
              </div>
              <h3 className="home-card__title">Configuración Organizacional</h3>
              <p className="home-card__text">
                Administra el nombre oficial de la organización, la sede activa principal y consulta la zona horaria institucional.
              </p>
              <div className="home-card__action">
                <Link to="/app/configuracion" className="home-card__btn">
                  <span>Acceder a Configuración</span>
                  <IconArrowRight size={14} />
                </Link>
              </div>
            </div>
          )}

          {/* Módulo Puestos de Trabajo (Administrador y Supervisor) */}
          {(user.role === 'ADMINISTRADOR' || user.role === 'SUPERVISOR') && (
            <div className="home-card">
              <div className="home-card__header">
                <div className="home-card__art">
                  <img src={positionsIllustration} alt="" width="96" height="96" />
                </div>
                <span className="home-card__badge">
                  {user.role === 'SUPERVISOR' ? 'Consulta · F2' : 'Fase F2'}
                </span>
              </div>
              <h3 className="home-card__title">Puestos de Trabajo</h3>
              <p className="home-card__text">
                {user.role === 'SUPERVISOR'
                  ? 'Consulta el catálogo maestro de puestos de trabajo operativos de la empresa.'
                  : 'Gestiona el catálogo de puestos operativos: altas, edición y conmutación de estado activo/inactivo.'}
              </p>
              <div className="home-card__action">
                <Link to="/app/puestos" className="home-card__btn">
                  <span>{user.role === 'SUPERVISOR' ? 'Consultar Puestos' : 'Gestionar Puestos'}</span>
                  <IconArrowRight size={14} />
                </Link>
              </div>
            </div>
          )}

          {/* Módulo Equipo (Administrador y Supervisor) */}
          {(user.role === 'ADMINISTRADOR' || user.role === 'SUPERVISOR') && (
            <div className="home-card">
              <div className="home-card__header">
                <div className="home-card__art">
                  <img src={teamIllustration} alt="" width="96" height="96" />
                </div>
                <span className="home-card__badge">
                  {user.role === 'SUPERVISOR' ? 'Consulta · F3' : 'Fase F3'}
                </span>
              </div>
              <h3 className="home-card__title">Equipo y Colaboradores</h3>
              <p className="home-card__text">
                {user.role === 'SUPERVISOR'
                  ? 'Consulta la nómina de colaboradores y sus puestos asignados en la sede activa.'
                  : 'Consulta y gestiona la nómina de colaboradores y sus asignaciones a puestos de trabajo.'}
              </p>
              <div className="home-card__action">
                <Link to="/app/equipo" className="home-card__btn">
                  <span>{user.role === 'SUPERVISOR' ? 'Consultar Directorio' : 'Directorio de Equipo'}</span>
                  <IconArrowRight size={14} />
                </Link>
              </div>
            </div>
          )}

          {/* Módulo Indisponibilidades (Administrador y Supervisor) */}
          {(user.role === 'ADMINISTRADOR' || user.role === 'SUPERVISOR') && (
            <div className="home-card">
              <div className="home-card__header">
                <div className="home-card__art">
                  <img src={unavailabilityIllustration} alt="" width="96" height="96" />
                </div>
                <span className="home-card__badge">Fase F4</span>
              </div>
              <h3 className="home-card__title">Indisponibilidades</h3>
              <p className="home-card__text">
                Consulta y seguimiento de periodos de ausencia y restricciones de disponibilidad del personal.
              </p>
              <div className="home-card__action">
                <Link to="/app/indisponibilidades" className="home-card__btn">
                  <span>Ver Indisponibilidades</span>
                  <IconArrowRight size={14} />
                </Link>
              </div>
            </div>
          )}

          {/* Acceso a Mi Horario si el usuario tiene vinculación de empleado */}
          {user.employeeId && (
            <div className="home-card">
              <div className="home-card__header">
                <div className="home-card__icon-box home-card__icon-box--neutral">
                  <IconCalendar size={22} />
                </div>
                <span className="home-card__badge">Personal</span>
              </div>
              <h3 className="home-card__title">Mi Horario Personal</h3>
              <p className="home-card__text">
                Consulta tu programación de turnos asignados y gestiona tus registros de indisponibilidad.
              </p>
              <div className="home-card__action">
                <Link to="/app/mi-horario" className="home-card__btn">
                  <span>Ver mi horario</span>
                  <IconArrowRight size={14} />
                </Link>
              </div>
            </div>
          )}
        </div>
        </section>

        {canReadOperationalSummary && (
          <section className="home-operations" aria-labelledby="operational-summary-title">
          <div className="home-operations__heading">
            <div>
              <p className="home-operations__eyebrow">Vista operativa</p>
              <h2 id="operational-summary-title" className="home-operations__title">Resumen de hoy</h2>
            </div>
            <p className="home-operations__window">Datos verificados para los próximos 30 días</p>
          </div>

          {operationalSummaryError && (
            <p className="home-operations__error" role="status">{operationalSummaryError}</p>
          )}

          <div className="home-operations__layout" aria-busy={isOperationalSummaryLoading}>
            <div className="home-kpis">
              <article className="home-kpi home-kpi--violet">
                <div className="home-kpi__icon" aria-hidden="true"><IconBriefcase size={19} /></div>
                <p className="home-kpi__label">Puestos activos</p>
                <strong className="home-kpi__value">{isOperationalSummaryLoading ? '…' : operationalSummary?.activePositions ?? '—'}</strong>
                <Link to="/app/puestos" className="home-kpi__link">Ver catálogo <IconArrowRight size={14} /></Link>
              </article>

              <article className="home-kpi home-kpi--amber">
                <div className="home-kpi__icon" aria-hidden="true"><IconUsers size={19} /></div>
                <p className="home-kpi__label">Colaboradores activos</p>
                <strong className="home-kpi__value">{isOperationalSummaryLoading ? '…' : operationalSummary?.activeEmployees ?? '—'}</strong>
                <Link to="/app/equipo" className="home-kpi__link">Ver directorio <IconArrowRight size={14} /></Link>
              </article>

              <article className="home-kpi home-kpi--lilac">
                <div className="home-kpi__icon" aria-hidden="true"><IconCalendar size={19} /></div>
                <p className="home-kpi__label">Indisponibilidades próximas</p>
                <strong className="home-kpi__value">{isOperationalSummaryLoading ? '…' : operationalSummary?.upcomingUnavailabilities ?? '—'}</strong>
                <Link to="/app/indisponibilidades" className="home-kpi__link">Revisar periodos <IconArrowRight size={14} /></Link>
              </article>
            </div>

            <aside className="home-availability" aria-labelledby="availability-title">
              <div className="home-availability__header">
                <div>
                  <p className="home-operations__eyebrow">Disponibilidad</p>
                  <h3 id="availability-title" className="home-availability__title">Ausencias próximas</h3>
                </div>
                <span className="home-availability__status"><IconCheck size={14} /> Datos reales</span>
              </div>

              {isOperationalSummaryLoading ? (
                <p className="home-availability__empty" role="status">Actualizando información…</p>
              ) : operationalSummary && operationalSummary.upcomingUnavailabilities > 0 ? (
                <ul className="home-availability__reasons">
                  {[...operationalSummary.reasons.entries()].map(([reason, count]) => (
                    <li key={reason} className="home-availability__reason">
                      <span>{reasonLabels[reason]}</span>
                      <strong>{count}</strong>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="home-availability__empty">No hay indisponibilidades registradas para los próximos 30 días.</p>
              )}

              <Link to="/app/indisponibilidades" className="home-availability__action">
                Gestionar indisponibilidades <IconArrowRight size={14} />
              </Link>
            </aside>
          </div>
          </section>
        )}

        <aside className="home-actions" aria-labelledby="operational-actions-title">
          <div className="home-actions__heading">
            <p className="home-operations__eyebrow">Acceso directo</p>
            <h2 id="operational-actions-title" className="home-actions__title">Acciones operativas</h2>
            <p className="home-actions__subtitle">Accesos rápidos a los módulos del sistema.</p>
          </div>
          <nav className="home-actions__list" aria-label="Acciones operativas">
            {user.role === 'ADMINISTRADOR' && (
              <Link to="/app/configuracion" className="home-actions__link"><span><strong>Configuración</strong><small>Acceder a Configuración</small></span><IconArrowRight size={16} aria-hidden="true" /></Link>
            )}
            {(user.role === 'ADMINISTRADOR' || user.role === 'SUPERVISOR') && (
              <Link to="/app/puestos" className="home-actions__link"><span><strong>Puestos</strong><small>{user.role === 'SUPERVISOR' ? 'Consultar puestos' : 'Gestionar puestos'}</small></span><IconArrowRight size={16} aria-hidden="true" /></Link>
            )}
            {(user.role === 'ADMINISTRADOR' || user.role === 'SUPERVISOR') && (
              <Link to="/app/equipo" className="home-actions__link"><span><strong>Equipo</strong><small>{user.role === 'SUPERVISOR' ? 'Consultar directorio' : 'Directorio de equipo'}</small></span><IconArrowRight size={16} aria-hidden="true" /></Link>
            )}
            {(user.role === 'ADMINISTRADOR' || user.role === 'SUPERVISOR') && (
              <Link to="/app/indisponibilidades" className="home-actions__link"><span><strong>Indisponibilidades</strong><small>Ver indisponibilidades</small></span><IconArrowRight size={16} aria-hidden="true" /></Link>
            )}
            {user.employeeId && (
              <Link to="/app/mi-horario" className="home-actions__link"><span><strong>Mi horario</strong><small>Ver mi horario</small></span><IconArrowRight size={16} aria-hidden="true" /></Link>
            )}
          </nav>
        </aside>

      </div>

      {/* Nota de Integridad y Desarrollo Incremental */}
      <section className="home-integrity-note">
        <h3 className="home-integrity-title">Integridad de Datos en TurnoShift</h3>
        <p className="home-integrity-text">
          Esta plataforma opera exclusivamente con información verificada del servidor. Los módulos de gestión operativa se irán activando según el plan incremental aprobado, conectando directamente con las APIs de backend sin datos simulados.
        </p>
      </section>
    </div>
  );
}
