/**
 * TurnoShift — Configuration Page (HU-05)
 * Gestión de la configuración organizacional y sede activa única.
 * Conectado con los contratos reales OpenAPI 3.1 y alineado con Stitch.
 */

import { useState, useEffect, useRef, type FormEvent } from 'react';
import { useAuth } from '../../context/AuthContext';
import { organizationApi } from '../../api/organizationApi';
import { positionApi } from '../../api/positionApi';
import { employeeApi } from '../../api/employeeApi';
import organizationArt from '../../assets/illustrations/organization-context.png';
import timezoneArt from '../../assets/illustrations/timezone-context.png';
import { generateIdempotencyKey } from '../../utils/idempotency';
import { ApiProblemError } from '../../types/api';
import type { OrganizationConfigurationResponse } from '../../types/organization';
import { Link } from '../../components/routing/Router';
import {
  IconBuilding,
  IconUsers,
  IconClock,
  IconLock,
  IconCheck,
  IconAlertTriangle,
  IconRefresh,
  IconBriefcase,
  IconArrowRight
} from '../../components/common/Icons';
import './ConfigurationPage.css';

interface PendingIntent {
  key: string;
  value: string;
}

export default function ConfigurationPage() {
  const { session } = useAuth();
  const isAdmin = session?.user.role === 'ADMINISTRADOR';
  const canReadTeam = isAdmin || session?.user.role === 'SUPERVISOR';
  const [teamCounts, setTeamCounts] = useState<{ positions: number; employees: number } | null>(null);
  const [teamLoadError, setTeamLoadError] = useState(false);

  useEffect(() => {
    if (!canReadTeam) return;
    let cancelled = false;
    setTeamCounts(null);
    setTeamLoadError(false);
    Promise.all([positionApi.getPositions(true), employeeApi.getEmployees(true)])
      .then(([positions, employees]) => {
        if (!cancelled) setTeamCounts({ positions: positions.length, employees: employees.length });
      })
      .catch(() => { if (!cancelled) setTeamLoadError(true); });
    return () => { cancelled = true; };
  }, [canReadTeam, session?.organization.id]);

  // ── Estado de Carga Inicial ─────────────────────────────────────────────
  const [config, setConfig] = useState<OrganizationConfigurationResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<ApiProblemError | null>(null);

  // ── Estado de Campos Editables ──────────────────────────────────────────
  const [orgName, setOrgName] = useState<string>('');
  const [siteName, setSiteName] = useState<string>('');

  // ── Estados de Guardado y Retroalimentación ──────────────────────────────
  const [isSavingOrg, setIsSavingOrg] = useState<boolean>(false);
  const [isSavingSite, setIsSavingSite] = useState<boolean>(false);
  const [orgError, setOrgError] = useState<string | null>(null);
  const [siteError, setSiteError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);

  // ── Intenciones de Idempotencia (conservadas ante fallos de red) ────────
  const pendingOrgIntent = useRef<PendingIntent | null>(null);
  const pendingSiteIntent = useRef<PendingIntent | null>(null);

  // Carga inicial
  const loadConfiguration = async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const data = await organizationApi.getConfiguration();
      setConfig(data);
      setOrgName(data.organization.displayName);
      setSiteName(data.activeSite.name);
    } catch (err: unknown) {
      if (err instanceof ApiProblemError) {
        setLoadError(err);
      } else {
        setLoadError(
          new ApiProblemError({
            type: '/problems/unknown',
            title: 'Error inesperado',
            status: 500,
            detail: 'No fue posible cargar la configuración organizacional.'
          })
        );
      }
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadConfiguration();
  }, []);

  // ── Recarga Manual ante Conflicto 409 ───────────────────────────────────
  const handleReload = async () => {
    setConflictMessage(null);
    setOrgError(null);
    setSiteError(null);
    setSuccessMessage(null);
    pendingOrgIntent.current = null;
    pendingSiteIntent.current = null;
    await loadConfiguration();
  };

  // ── Guardar Nombre de Organización ──────────────────────────────────────
  const handleSaveOrgName = async (e: FormEvent) => {
    e.preventDefault();
    if (!config || !isAdmin || isSavingOrg) return;

    // Validación en cliente: texto no vacío y máximo 150 caracteres.
    // Sin recortar silenciosamente el valor enviado.
    if (orgName.length === 0) {
      setOrgError('El nombre de la organización no puede estar vacío.');
      return;
    }
    if (orgName.trim().length === 0) {
      setOrgError('El nombre de la organización no puede consistir únicamente en espacios en blanco.');
      return;
    }
    if (orgName.length > 150) {
      setOrgError('El nombre de la organización no puede superar los 150 caracteres.');
      return;
    }

    // Gestión de Idempotencia: reutilizar la clave si es reintento del mismo valor tras fallo de red
    let idempotencyKey: string;
    if (pendingOrgIntent.current && pendingOrgIntent.current.value === orgName) {
      idempotencyKey = pendingOrgIntent.current.key;
    } else {
      idempotencyKey = generateIdempotencyKey();
      pendingOrgIntent.current = { key: idempotencyKey, value: orgName };
    }

    setIsSavingOrg(true);
    setOrgError(null);
    setSuccessMessage(null);
    setConflictMessage(null);

    try {
      const updatedOrg = await organizationApi.updateDisplayName(
        {
          displayName: orgName, // Se envía el valor exacto sin mutación previa
          expectedLockVersion: config.organization.lockVersion
        },
        idempotencyKey
      );

      // Éxito: limpiar intención de guardado
      pendingOrgIntent.current = null;

      // Actualizar estado local con nueva versión de bloqueo
      setConfig((prev) => (prev ? { ...prev, organization: updatedOrg } : null));
      setOrgName(updatedOrg.displayName);
      setSuccessMessage('Nombre de la organización actualizado exitosamente.');
    } catch (err: unknown) {
      if (err instanceof ApiProblemError) {
        if (err.isNetworkError) {
          // Error de red: conservar pendingOrgIntent para que el reintento use la misma clave
          setOrgError('Error de conexión con el servidor. Puedes reintentar la operación.');
        } else {
          // Respuesta Problem Details definitiva del servidor: limpiar intención
          pendingOrgIntent.current = null;

          if (err.status === 409 || err.code === 'CONCURRENT_MODIFICATION') {
            setConflictMessage(
              err.detail || 'La organización fue modificada concurrentemente por otra sesión. Recarga los datos para continuar.'
            );
          } else if (err.status === 400 && err.fieldErrors.length > 0) {
            const fieldErr = err.fieldErrors.find((f) => f.field === 'displayName');
            setOrgError(fieldErr?.message || err.detail || 'Error de validación al guardar el nombre.');
          } else {
            setOrgError(err.detail || err.title || 'No fue posible actualizar el nombre de la organización.');
          }
        }
      } else {
        setOrgError('Ocurrió un error inesperado al procesar la solicitud.');
      }
    } finally {
      setIsSavingOrg(false);
    }
  };

  // ── Guardar Nombre de Sede Activa ───────────────────────────────────────
  const handleSaveSiteName = async (e: FormEvent) => {
    e.preventDefault();
    if (!config || !isAdmin || isSavingSite) return;

    // Validación en cliente: texto no vacío y máximo 150 caracteres.
    // Sin recortar silenciosamente el valor enviado.
    if (siteName.length === 0) {
      setSiteError('El nombre de la sede no puede estar vacío.');
      return;
    }
    if (siteName.trim().length === 0) {
      setSiteError('El nombre de la sede no puede consistir únicamente en espacios en blanco.');
      return;
    }
    if (siteName.length > 150) {
      setSiteError('El nombre de la sede no puede superar los 150 caracteres.');
      return;
    }

    // Gestión de Idempotencia: reutilizar la clave si es reintento del mismo valor tras fallo de red
    let idempotencyKey: string;
    if (pendingSiteIntent.current && pendingSiteIntent.current.value === siteName) {
      idempotencyKey = pendingSiteIntent.current.key;
    } else {
      idempotencyKey = generateIdempotencyKey();
      pendingSiteIntent.current = { key: idempotencyKey, value: siteName };
    }

    setIsSavingSite(true);
    setSiteError(null);
    setSuccessMessage(null);
    setConflictMessage(null);

    try {
      const updatedSite = await organizationApi.updateActiveSiteName(
        {
          name: siteName, // Se envía el valor exacto sin mutación previa
          expectedLockVersion: config.activeSite.lockVersion
        },
        idempotencyKey
      );

      // Éxito: limpiar intención de guardado
      pendingSiteIntent.current = null;

      // Actualizar estado local con nueva versión de bloqueo
      setConfig((prev) => (prev ? { ...prev, activeSite: updatedSite } : null));
      setSiteName(updatedSite.name);
      setSuccessMessage('Nombre de la sede activa actualizado exitosamente.');
    } catch (err: unknown) {
      if (err instanceof ApiProblemError) {
        if (err.isNetworkError) {
          // Error de red: conservar pendingSiteIntent para que el reintento use la misma clave
          setSiteError('Error de conexión con el servidor. Puedes reintentar la operación.');
        } else {
          // Respuesta Problem Details definitiva del servidor: limpiar intención
          pendingSiteIntent.current = null;

          if (err.status === 409 || err.code === 'CONCURRENT_MODIFICATION') {
            setConflictMessage(
              err.detail || 'La sede activa fue modificada concurrentemente por otra sesión. Recarga los datos para continuar.'
            );
          } else if (err.status === 400 && err.fieldErrors.length > 0) {
            const fieldErr = err.fieldErrors.find((f) => f.field === 'name');
            setSiteError(fieldErr?.message || err.detail || 'Error de validación al guardar el nombre.');
          } else {
            setSiteError(err.detail || err.title || 'No fue posible actualizar el nombre de la sede activa.');
          }
        }
      } else {
        setSiteError('Ocurrió un error inesperado al procesar la solicitud.');
      }
    } finally {
      setIsSavingSite(false);
    }
  };

  // ── Render: Estado de Carga Inicial ──────────────────────────────────────
  if (isLoading) {
    return (
      <div className="configuration-page">
        <div className="config-loading-state" role="status" aria-live="polite">
          <span className="config-spinner-large" aria-hidden="true" />
          <span className="config-loading-text">Cargando configuración organizacional...</span>
        </div>
      </div>
    );
  }

  // ── Render: Error de Carga Inicial ───────────────────────────────────────
  if (loadError || !config) {
    return (
      <div className="configuration-page">
        <div className="config-error-state" role="alert">
          <IconAlertTriangle size={32} className="config-error-icon" />
          <h2 className="config-error-title">No fue posible cargar la configuración</h2>
          <p className="config-error-detail">
            {loadError?.detail || 'Ocurrió un error al obtener la información de la organización desde el servidor.'}
          </p>
          <button type="button" className="config-btn-retry" onClick={loadConfiguration}>
            <IconRefresh size={16} />
            <span>Reintentar carga</span>
          </button>
        </div>
      </div>
    );
  }

  const isOrgModified = orgName !== config.organization.displayName;
  const isSiteModified = siteName !== config.activeSite.name;

  return (
    <div className="configuration-page config-workspace">
      {/* ── Cabecera de Página ───────────────────────────────────────────── */}
      <header className="config-header">
        <img className="config-header__art" src={organizationArt} alt="" />
        <div>
        <h1 className="config-header__title">Configuración</h1>
        <p className="config-header__subtitle">
          Administra la información de tu organización.
        </p>
        </div>
        <span className="config-context-pill"><IconBuilding size={14} />{config.activeSite.code} · {config.activeSite.name}</span>
      </header>

      <div className="config-workspace__columns">
      {/* ── Tarjeta Principal: Datos de la Empresa y Sede Activa ─────────── */}
      <section className="config-card" aria-labelledby="org-config-title">
        <div className="config-card__header">
          <div className="config-card__icon-box" aria-hidden="true">
            <IconBuilding size={20} />
          </div>
          <div className="config-card__titles">
            <h2 id="org-config-title" className="config-card__title">
              Empresa y sede
            </h2>
            <p className="config-card__description">
              Parámetros corporativos principales y referencia horaria local.
            </p>
          </div>
        </div>

        {/* Banner de Conflicto Concurrente (409) */}
        {conflictMessage && (
          <div className="config-banner config-banner--conflict" role="alert">
            <div className="config-banner__left">
              <IconAlertTriangle size={18} className="config-banner__icon" />
              <span>{conflictMessage}</span>
            </div>
            <button
              type="button"
              className="config-btn--reload"
              onClick={handleReload}
              aria-label="Recargar datos actualizados del servidor"
            >
              <IconRefresh size={14} />
              <span>Recargar datos</span>
            </button>
          </div>
        )}

        {/* Banner de Éxito */}
        {successMessage && (
          <div className="config-banner config-banner--success" role="status" aria-live="polite">
            <div className="config-banner__left">
              <IconCheck size={18} className="config-banner__icon" />
              <span>{successMessage}</span>
            </div>
          </div>
        )}

        {/* Rejilla de Campos Corporativos */}
        <div className="config-grid">
          {/* ── Campo 1: Nombre de la Empresa ── */}
          <form className="config-field" onSubmit={handleSaveOrgName} noValidate>
            <div className="config-field__header">
              <label htmlFor="org-display-name" className="config-field__label">
                Nombre de la empresa
              </label>
              <div className="config-field__badge-group">
                {isOrgModified && <span className="config-unsaved" role="status">Cambios sin guardar</span>}
              </div>
            </div>

            <input
              id="org-display-name"
              type="text"
              className={`config-field__input ${orgError ? 'config-field__input--error' : ''} ${!isAdmin ? 'config-field__input--disabled' : ''}`}
              value={orgName}
              onChange={(e) => {
                setOrgName(e.target.value);
                if (orgError) setOrgError(null);
                if (successMessage) setSuccessMessage(null);
              }}
              maxLength={150}
              disabled={isSavingOrg || !isAdmin}
              aria-invalid={orgError ? 'true' : 'false'}
              aria-describedby={orgError ? 'org-name-error' : 'org-name-helper'}
            />

            <p id="org-name-helper" className="config-field__helper">
              Nombre oficial reflejado en reportes y operaciones.
            </p>

            {orgError && (
              <p id="org-name-error" className="config-field__error" role="alert">
                {orgError}
              </p>
            )}

            {isAdmin && (
              <div className="config-field__footer">
                <button
                  type="submit"
                  className="config-btn-save"
                  disabled={!isOrgModified || isSavingOrg || orgName.trim().length === 0}
                >
                  {isSavingOrg ? (
                    <>
                      <span className="config-btn-spinner" aria-hidden="true" />
                      <span>Guardando...</span>
                    </>
                  ) : (
                    <span>Guardar empresa</span>
                  )}
                </button>
              </div>
            )}
          </form>

          {/* ── Campo 2: Sede Activa ── */}
          <form className="config-field" onSubmit={handleSaveSiteName} noValidate>
            <div className="config-field__header">
              <label htmlFor="active-site-name" className="config-field__label">
                Sede activa
              </label>
              <div className="config-field__badge-group">
                {isSiteModified && <span className="config-unsaved" role="status">Cambios sin guardar</span>}
                <span className="config-badge config-badge--code" title="Código inmutable de la sede">
                  {config.activeSite.code}
                </span>
              </div>
            </div>

            <input
              id="active-site-name"
              type="text"
              className={`config-field__input ${siteError ? 'config-field__input--error' : ''} ${!isAdmin ? 'config-field__input--disabled' : ''}`}
              value={siteName}
              onChange={(e) => {
                setSiteName(e.target.value);
                if (siteError) setSiteError(null);
                if (successMessage) setSuccessMessage(null);
              }}
              maxLength={150}
              disabled={isSavingSite || !isAdmin}
              aria-invalid={siteError ? 'true' : 'false'}
              aria-describedby={siteError ? 'site-name-error' : 'site-name-helper'}
            />

            <p id="site-name-helper" className="config-field__helper">
              Sede única activa en este despliegue para la planificación operativa.
            </p>

            {siteError && (
              <p id="site-name-error" className="config-field__error" role="alert">
                {siteError}
              </p>
            )}

            {isAdmin && (
              <div className="config-field__footer">
                <button
                  type="submit"
                  className="config-btn-save"
                  disabled={!isSiteModified || isSavingSite || siteName.trim().length === 0}
                >
                  {isSavingSite ? (
                    <>
                      <span className="config-btn-spinner" aria-hidden="true" />
                      <span>Guardando...</span>
                    </>
                  ) : (
                    <span>Guardar sede</span>
                  )}
                </button>
              </div>
            )}
          </form>

        </div>
        <dl className="config-identity">
          <div><dt>Organización</dt><dd>{config.organization.displayName}</dd></div>
          <div><dt>Sede</dt><dd>{config.activeSite.code}</dd></div>
        </dl>
      </section>

      <aside className="config-workspace__aside">
        <section className="config-timezone" aria-labelledby="config-timezone-title">
          <div className="config-side-heading">
            <span className="config-side-icon"><IconClock size={22} /></span>
            <div><h2 id="config-timezone-title">Zona horaria</h2><p>Referencia horaria para las operaciones.</p></div>
            <span className="config-badge config-badge--lock"><IconLock size={12} />Solo lectura</span>
          </div>
          <div className="config-timezone__body">
            <div><label htmlFor="org-timezone" className="config-field__label">Zona horaria institucional</label>
              <input id="org-timezone" className="config-field__input config-field__input--disabled" value={config.organization.timezoneId} readOnly aria-describedby="org-timezone-helper" />
              <p id="org-timezone-helper" className="config-field__helper">Referencia local para las operaciones de tu organización.</p>
            </div>
            <img src={timezoneArt} alt="" />
          </div>
        </section>
        {canReadTeam && <section className="config-team" aria-labelledby="config-team-title">
          <div className="config-side-heading"><span className="config-side-icon"><IconUsers size={22} /></span>
            <div><h2 id="config-team-title">Estructura del equipo</h2><p>Puestos de trabajo y colaboradores.</p></div>
          </div>
          {teamLoadError ? <p role="status" className="config-team__error">No se pudo cargar el resumen del equipo.</p> :
            <div className="config-team__counts" aria-busy={!teamCounts}>
              <div><IconBriefcase size={23} /><span><strong>{teamCounts?.positions ?? '…'}</strong><small>Puestos activos</small></span></div>
              <div><IconUsers size={23} /><span><strong>{teamCounts?.employees ?? '…'}</strong><small>Colaboradores activos</small></span></div>
            </div>}
          <nav className="config-team__links" aria-label="Estructura del equipo">
            <Link to="/app/puestos">Ver puestos <IconArrowRight size={16} /></Link>
            <Link to="/app/equipo">Ver equipo <IconArrowRight size={16} /></Link>
          </nav>
        </section>}
      </aside>
      </div>
    </div>
  );
}
