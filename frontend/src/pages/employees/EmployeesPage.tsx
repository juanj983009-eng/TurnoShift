/**
 * TurnoShift — Employees Page (HU-07)
 * Directorio de colaboradores, filtros y mutaciones seguras con control de concurrencia e idempotencia.
 */

import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useAuth } from '../../context/AuthContext';
import { employeeApi } from '../../api/employeeApi';
import { positionApi } from '../../api/positionApi';
import { organizationApi } from '../../api/organizationApi';
import { generateIdempotencyKey } from '../../utils/idempotency';
import { ApiProblemError } from '../../types/api';
import type {
  EmployeeResponse,
  EmployeeStatusFilter,
  PendingEmployeeIntent
} from '../../types/employee';
import type { PositionResponse } from '../../types/position';
import type { ActiveSiteView } from '../../types/organization';
import { EmployeeModal, type EmployeeModalFormData } from './EmployeeModal';
import { EmployeeStatusModal } from './EmployeeStatusModal';
import teamArt from '../../assets/illustrations/team-workspace.png';
import {
  IconUsers,
  IconSearch,
  IconBuilding,
  IconAlertTriangle,
  IconRefresh,
  IconPlus,
  IconEdit,
  IconCheck,
  IconCalendar,
  IconArrowRight
} from '../../components/common/Icons';
import { Link } from '../../components/routing/Router';
import './EmployeesPage.css';

/**
 * Obtiene las iniciales del nombre visible para renderizar el avatar accesible.
 */
function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return 'TS';
  if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export default function EmployeesPage() {
  const { session } = useAuth();
  const isAdmin = session?.user.role === 'ADMINISTRADOR';
  const isSupervisor = session?.user.role === 'SUPERVISOR';

  // ── Estado de Datos Reales ──────────────────────────────────────────────
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [positions, setPositions] = useState<PositionResponse[]>([]);
  const [activeSite, setActiveSite] = useState<ActiveSiteView | null>(null);

  // ── Filtros y Búsqueda ──────────────────────────────────────────────────
  const [statusFilter, setStatusFilter] = useState<EmployeeStatusFilter>('all');
  const [positionFilter, setPositionFilter] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // ── Estado de Carga y Error ─────────────────────────────────────────────
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<ApiProblemError | null>(null);
  const [teamCounts, setTeamCounts] = useState<{ total: number; active: number; inactive: number } | null>(null);

  useEffect(() => {
    let cancelled = false;
    setTeamCounts(null);
    if (isLoading || loadError) return;
    const summarize = (items: EmployeeResponse[]) => {
      if (cancelled) return;
      const active = items.filter(item => item.active).length;
      setTeamCounts({ total: items.length, active, inactive: items.length - active });
    };
    if (statusFilter === 'all') summarize(employees);
    else employeeApi.getEmployees().then(summarize).catch(() => { /* Summary unavailable; do not substitute zero. */ });
    return () => { cancelled = true; };
  }, [employees, statusFilter, isLoading, loadError]);

  // ── Alertas Globales de la Página ───────────────────────────────────────
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // ── Estado de Modales ───────────────────────────────────────────────────
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [selectedEmployee, setSelectedEmployee] = useState<EmployeeResponse | null>(null);
  const [isSubmittingModal, setIsSubmittingModal] = useState<boolean>(false);
  const [modalServerError, setModalServerError] = useState<string | null>(null);
  const [modalCodeError, setModalCodeError] = useState<string | null>(null);

  // Modal de activación / desactivación
  const [isStatusModalOpen, setIsStatusModalOpen] = useState<boolean>(false);
  const [statusTargetEmployee, setStatusTargetEmployee] = useState<EmployeeResponse | null>(null);
  const [isSubmittingStatus, setIsSubmittingStatus] = useState<boolean>(false);
  const [statusServerError, setStatusServerError] = useState<string | null>(null);

  // ── Referencia de Intención de Idempotencia (conservada ante error de red) ──
  const pendingIntentRef = useRef<PendingEmployeeIntent | null>(null);

  // ── Carga Coordinada con Promise.all ────────────────────────────────────
  const loadData = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const activeParam =
        statusFilter === 'active' ? true : statusFilter === 'inactive' ? false : undefined;

      const [employeesData, positionsData, configData] = await Promise.all([
        employeeApi.getEmployees(activeParam),
        positionApi.getPositions(),
        organizationApi.getConfiguration()
      ]);

      setEmployees(employeesData);
      setPositions(positionsData);
      setActiveSite(configData.activeSite);
    } catch (err: unknown) {
      if (err instanceof ApiProblemError) {
        setLoadError(err);
      } else {
        setLoadError(
          new ApiProblemError({
            type: '/problems/unknown',
            title: 'Error inesperado',
            status: 500,
            detail: 'No fue posible cargar el directorio de colaboradores.'
          })
        );
      }
    } finally {
      setIsLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Recarga manual ante conflicto o acción del usuario
  const handleReload = async () => {
    setConflictMessage(null);
    setSuccessMessage(null);
    pendingIntentRef.current = null;
    await loadData();
  };

  // ── Resolución en Memoria de Puestos ────────────────────────────────────
  const positionMap = useMemo(() => {
    const map = new Map<string, string>();
    for (const pos of positions) {
      map.set(pos.id, pos.name);
    }
    return map;
  }, [positions]);

  const resolvePositionName = (positionId: string): string => {
    return positionMap.get(positionId) || 'Puesto no identificado';
  };

  const resolveSiteName = (siteId: string): string => {
    if (activeSite && activeSite.id === siteId) {
      return activeSite.name;
    }
    return activeSite?.name || 'Sede no identificada';
  };

  // ── Filtrado Local en Memoria (Puesto y Búsqueda por Texto) ─────────────
  const filteredEmployees = useMemo(() => {
    return employees.filter((emp) => {
      // Filtro por puesto de trabajo
      if (positionFilter !== 'all' && emp.positionId !== positionFilter) {
        return false;
      }

      // Búsqueda en memoria por código o displayName
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const codeMatches = emp.code.toLowerCase().includes(q);
        const nameMatches = emp.displayName.toLowerCase().includes(q);
        if (!codeMatches && !nameMatches) {
          return false;
        }
      }

      return true;
    });
  }, [employees, positionFilter, searchQuery]);

  // ── Apertura de Modales (Solo ADMINISTRADOR) ────────────────────────────
  const handleOpenCreateModal = () => {
    if (!isAdmin) return;
    setModalMode('create');
    setSelectedEmployee(null);
    setModalServerError(null);
    setModalCodeError(null);
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (emp: EmployeeResponse) => {
    if (!isAdmin) return;
    setModalMode('edit');
    setSelectedEmployee(emp);
    setModalServerError(null);
    setModalCodeError(null);
    setIsModalOpen(true);
  };

  const handleOpenStatusModal = (emp: EmployeeResponse) => {
    if (!isAdmin) return;
    setStatusTargetEmployee(emp);
    setStatusServerError(null);
    setIsStatusModalOpen(true);
  };

  // ── Envío de Alta o Edición ─────────────────────────────────────────────
  const handleModalSubmit = async (formData: EmployeeModalFormData) => {
    if (!isAdmin) return;
    setIsSubmittingModal(true);
    setModalServerError(null);
    setModalCodeError(null);
    setConflictMessage(null);
    setSuccessMessage(null);

    if (modalMode === 'create') {
      const codeToSend = formData.code || '';
      const displayNameToSend = formData.displayName || '';
      const positionIdToSend = formData.positionId || '';

      // Reutilizar o generar clave de idempotencia verificando coincidencia exacta
      let idempotencyKey: string;
      const pending = pendingIntentRef.current;
      if (
        pending &&
        pending.operation === 'create' &&
        pending.code === codeToSend &&
        pending.displayName === displayNameToSend &&
        pending.positionId === positionIdToSend
      ) {
        idempotencyKey = pending.key;
      } else {
        idempotencyKey = generateIdempotencyKey();
        pendingIntentRef.current = {
          operation: 'create',
          key: idempotencyKey,
          code: codeToSend,
          displayName: displayNameToSend,
          positionId: positionIdToSend
        };
      }

      try {
        const createdEmployee = await employeeApi.createEmployee(
          {
            code: codeToSend,
            displayName: displayNameToSend,
            positionId: positionIdToSend
          },
          idempotencyKey
        );

        // Éxito: limpiar intención y actualizar lista con respuesta confirmada
        pendingIntentRef.current = null;
        setEmployees((prev) => [createdEmployee, ...prev]);
        setIsModalOpen(false);
        setSuccessMessage(`Colaborador ${createdEmployee.code} registrado exitosamente.`);
      } catch (err: unknown) {
        if (err instanceof ApiProblemError) {
          if (err.isNetworkError) {
            setModalServerError(
              'Error de conexión. Puedes reintentar la operación con la misma clave de idempotencia.'
            );
          } else {
            // Error de negocio o validación: invalidar intención
            pendingIntentRef.current = null;

            if (err.code === 'EMPLOYEE_CODE_ALREADY_EXISTS') {
              setModalCodeError('El código ya está en uso por otro colaborador.');
            } else if (err.code === 'POSITION_INACTIVE') {
              setModalServerError('El puesto de trabajo seleccionado se encuentra inactivo.');
            } else if (err.code === 'POSITION_NOT_FOUND') {
              setModalServerError('El puesto de trabajo especificado no existe.');
            } else if (err.code === 'NO_ACTIVE_SITE') {
              setModalServerError('La organización no cuenta con una sede activa.');
            } else if (err.code === 'EMPLOYEE_LICENSE_LIMIT_REACHED') {
              setModalServerError(
                'Se ha alcanzado el límite de colaboradores activos contratados para la organización.'
              );
            } else if (
              err.code === 'IDEMPOTENCY_KEY_REUSED' ||
              err.code === 'IDEMPOTENCY_KEY_EXPIRED'
            ) {
              setModalServerError(
                'Conflicto con la clave de idempotencia. Por favor reintenta la operación.'
              );
            } else {
              setModalServerError(err.detail || err.title || 'Error al registrar el colaborador.');
            }
          }
        } else {
          pendingIntentRef.current = null;
          setModalServerError('Ocurrió un error inesperado al procesar la solicitud.');
        }
      } finally {
        setIsSubmittingModal(false);
      }
    } else if (modalMode === 'edit' && selectedEmployee) {
      const empId = selectedEmployee.id;
      const expectedLockVersion = selectedEmployee.lockVersion;
      const displayNameToSend = formData.displayName;
      const positionIdToSend = formData.positionId;

      // Reutilizar o generar clave de idempotencia verificando coincidencia exacta
      let idempotencyKey: string;
      const pending = pendingIntentRef.current;
      if (
        pending &&
        pending.operation === 'edit' &&
        pending.employeeId === empId &&
        pending.displayName === displayNameToSend &&
        pending.positionId === positionIdToSend &&
        pending.expectedLockVersion === expectedLockVersion
      ) {
        idempotencyKey = pending.key;
      } else {
        idempotencyKey = generateIdempotencyKey();
        pendingIntentRef.current = {
          operation: 'edit',
          key: idempotencyKey,
          employeeId: empId,
          displayName: displayNameToSend,
          positionId: positionIdToSend,
          expectedLockVersion
        };
      }

      try {
        const updatedEmployee = await employeeApi.updateEmployee(
          empId,
          {
            displayName: displayNameToSend,
            positionId: positionIdToSend,
            expectedLockVersion
          },
          idempotencyKey
        );

        pendingIntentRef.current = null;
        setEmployees((prev) =>
          prev.map((e) => (e.id === updatedEmployee.id ? updatedEmployee : e))
        );
        setIsModalOpen(false);
        setSuccessMessage(`Colaborador ${updatedEmployee.code} actualizado exitosamente.`);
      } catch (err: unknown) {
        if (err instanceof ApiProblemError) {
          if (err.isNetworkError) {
            setModalServerError('Error de conexión. Puedes reintentar la operación.');
          } else {
            pendingIntentRef.current = null;

            if (err.status === 409 && (err.code === 'CONCURRENT_MODIFICATION' || !err.code)) {
              setIsModalOpen(false);
              setConflictMessage(
                err.detail ||
                  'El colaborador fue modificado concurrentemente por otra sesión. Recarga el directorio para continuar.'
              );
            } else if (err.code === 'POSITION_INACTIVE') {
              setModalServerError('El nuevo puesto asignado se encuentra inactivo.');
            } else if (err.code === 'POSITION_NOT_FOUND') {
              setModalServerError('El puesto de trabajo especificado no existe.');
            } else if (err.code === 'EMPLOYEE_NOT_FOUND') {
              setModalServerError('El colaborador no fue encontrado.');
            } else if (
              err.code === 'IDEMPOTENCY_KEY_REUSED' ||
              err.code === 'IDEMPOTENCY_KEY_EXPIRED'
            ) {
              setModalServerError(
                'Conflicto con la clave de idempotencia. Por favor reintenta la operación.'
              );
            } else {
              setModalServerError(
                err.detail || err.title || 'Error al actualizar el colaborador.'
              );
            }
          }
        } else {
          pendingIntentRef.current = null;
          setModalServerError('Ocurrió un error inesperado al procesar la solicitud.');
        }
      } finally {
        setIsSubmittingModal(false);
      }
    }
  };

  // ── Conmutación de Estado (Activar / Desactivar) ─────────────────────────
  const handleConfirmStatusChange = async () => {
    if (!isAdmin || !statusTargetEmployee) return;
    setIsSubmittingStatus(true);
    setStatusServerError(null);
    setConflictMessage(null);
    setSuccessMessage(null);

    const empId = statusTargetEmployee.id;
    const expectedLockVersion = statusTargetEmployee.lockVersion;
    const isActivating = !statusTargetEmployee.active;
    const operationName = isActivating ? 'activate' : 'deactivate';

    let idempotencyKey: string;
    const pending = pendingIntentRef.current;
    if (
      pending &&
      pending.operation === operationName &&
      pending.employeeId === empId &&
      pending.expectedLockVersion === expectedLockVersion
    ) {
      idempotencyKey = pending.key;
    } else {
      idempotencyKey = generateIdempotencyKey();
      pendingIntentRef.current = {
        operation: operationName,
        key: idempotencyKey,
        employeeId: empId,
        expectedLockVersion
      };
    }

    try {
      const updatedEmployee = isActivating
        ? await employeeApi.activateEmployee(empId, { expectedLockVersion }, idempotencyKey)
        : await employeeApi.deactivateEmployee(empId, { expectedLockVersion }, idempotencyKey);

      pendingIntentRef.current = null;
      setEmployees((prev) =>
        prev.map((e) => (e.id === updatedEmployee.id ? updatedEmployee : e))
      );
      setIsStatusModalOpen(false);
      setSuccessMessage(
        `Colaborador ${updatedEmployee.code} ${
          updatedEmployee.active ? 'activado' : 'desactivado'
        } exitosamente.`
      );
    } catch (err: unknown) {
      if (err instanceof ApiProblemError) {
        if (err.isNetworkError) {
          setStatusServerError('Error de conexión. Puedes reintentar la operación.');
        } else {
          pendingIntentRef.current = null;

          if (err.status === 409 && err.code === 'CONCURRENT_MODIFICATION') {
            setIsStatusModalOpen(false);
            setConflictMessage(
              err.detail ||
                'El estado del colaborador fue modificado concurrentemente por otra sesión. Recarga el directorio para continuar.'
            );
          } else if (err.code === 'EMPLOYEE_LICENSE_LIMIT_REACHED') {
            setStatusServerError(
              err.detail ||
                'Se ha alcanzado el límite de colaboradores activos contratados para la organización.'
            );
          } else if (err.code === 'EMPLOYEE_NOT_FOUND') {
            setStatusServerError('El colaborador no fue encontrado.');
          } else if (
            err.code === 'IDEMPOTENCY_KEY_REUSED' ||
            err.code === 'IDEMPOTENCY_KEY_EXPIRED'
          ) {
            setStatusServerError(
              'Conflicto con la clave de idempotencia. Por favor reintenta la operación.'
            );
          } else {
            setStatusServerError(
              err.detail || err.title || 'Error al cambiar el estado del colaborador.'
            );
          }
        }
      } else {
        pendingIntentRef.current = null;
        setStatusServerError('Ocurrió un error inesperado al procesar la solicitud.');
      }
    } finally {
      setIsSubmittingStatus(false);
    }
  };

  return (
    <div className="employees-page employees-workspace">
      {/* ── Cabecera de Página ───────────────────────────────────────────── */}
      <header className="employees-header">
        <div className="employees-header__titles">
          <span className="employees-header__eyebrow">OPERACIONES • DIRECTORIO DE PERSONAL</span>
          <h1 className="employees-header__title">Equipo y colaboradores</h1>
          <p className="employees-header__subtitle">
            Organiza a las personas y sus puestos de trabajo.
          </p>
          <div className="employees-overview" aria-label="Resumen del equipo">
            <span><IconUsers size={14} /><strong>{teamCounts?.total ?? '—'}</strong> registrados</span>
            <span className="employees-overview__active"><strong>{teamCounts?.active ?? '—'}</strong> activos</span>
            <span className="employees-overview__inactive"><strong>{teamCounts?.inactive ?? '—'}</strong> inactivos</span>
          </div>
        </div>

        <img src={teamArt} alt="" className="employees-header__art" />
        <div className="employees-header__actions">
          <Link to="/app/indisponibilidades" className="employees-btn-secondary">
            <IconCalendar size={16} />
            <span>Indisponibilidades</span>
          </Link>
          {isAdmin && (
            <button
              type="button"
              className="employees-btn-primary"
              onClick={handleOpenCreateModal}
            >
              <IconPlus size={16} />
              <span>Nuevo colaborador</span>
            </button>
          )}
        </div>
      </header>

      {/* ── Aviso para Supervisor (Modo Consulta) ────────────────────────── */}
      {isSupervisor && (
        <div className="employees-notice-banner" role="status">
          <span>
            <strong>Modo consulta:</strong> Tienes permisos para consultar el directorio de colaboradores y sus asignaciones. Las acciones de registro, edición y cambio de estado están reservadas para usuarios Administradores.
          </span>
        </div>
      )}

      {/* ── Banners Contextuales (Conflicto / Éxito) ──────────────────────── */}
      {conflictMessage && (
        <div className="employees-banner employees-banner--conflict" role="alert">
          <div className="employees-banner__left">
            <IconAlertTriangle size={18} className="employees-banner__icon" />
            <span>{conflictMessage}</span>
          </div>
          <button
            type="button"
            className="employees-banner-btn-reload"
            onClick={handleReload}
            aria-label="Recargar directorio actualizado del servidor"
          >
            <IconRefresh size={14} />
            <span>Recargar directorio</span>
          </button>
        </div>
      )}

      {successMessage && (
        <div className="employees-banner employees-banner--success" role="status" aria-live="polite">
          <div className="employees-banner__left">
            <IconCheck size={18} className="employees-banner__icon" />
            <span>{successMessage}</span>
          </div>
        </div>
      )}

      {/* ── Barra de Herramientas: Filtros y Búsqueda ────────────────────── */}
      <div className="employees-workspace__columns">
      <section className="employees-directory" aria-label="Directorio del equipo">
      <div className="employees-toolbar">
        <div className="employees-toolbar__controls">
          {/* Filtro por Estado */}
          <div className="employees-toolbar__filters" role="group" aria-label="Filtro de estado de colaboradores">
            <button
              type="button"
              className={`employees-filter-btn ${statusFilter === 'all' ? 'employees-filter-btn--active' : ''}`}
              onClick={() => setStatusFilter('all')}
            >
              Todos
            </button>
            <button
              type="button"
              className={`employees-filter-btn ${statusFilter === 'active' ? 'employees-filter-btn--active' : ''}`}
              onClick={() => setStatusFilter('active')}
            >
              Activos
            </button>
            <button
              type="button"
              className={`employees-filter-btn ${statusFilter === 'inactive' ? 'employees-filter-btn--active' : ''}`}
              onClick={() => setStatusFilter('inactive')}
            >
              Inactivos
            </button>
          </div>

          {/* Filtro por Puesto */}
          <div className="employees-position-select-wrapper">
            <select
              className="employees-position-select"
              value={positionFilter}
              onChange={(e) => setPositionFilter(e.target.value)}
              aria-label="Filtrar por puesto de trabajo"
            >
              <option value="all">Todos los puestos</option>
              {positions.map((pos) => (
                <option key={pos.id} value={pos.id}>
                  {pos.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Búsqueda en Memoria */}
        <div className="employees-toolbar__search">
          <IconSearch size={16} className="employees-search-icon" aria-hidden="true" />
          <input
            type="text"
            className="employees-search-input"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Buscar por código o nombre..."
            aria-label="Buscar colaboradores por código o nombre"
          />
          {searchQuery && (
            <button
              type="button"
              className="employees-search-clear"
              onClick={() => setSearchQuery('')}
              aria-label="Limpiar búsqueda"
            >
              ×
            </button>
          )}
        </div>
      </div>

      {/* ── Estado de Carga ──────────────────────────────────────────────── */}
      {isLoading && (
        <div className="employees-loading-state" role="status" aria-live="polite">
          <span className="employees-spinner-large" aria-hidden="true" />
          <span className="employees-loading-text">Cargando directorio de colaboradores...</span>
        </div>
      )}

      {/* ── Estado de Error con Reintento ─────────────────────────────────── */}
      {!isLoading && loadError && (
        <div className="employees-error-state" role="alert">
          <IconAlertTriangle size={32} className="employees-error-icon" />
          <h2 className="employees-error-title">No fue posible cargar el directorio</h2>
          <p className="employees-error-detail">
            {loadError.detail || 'Ocurrió un error al obtener la información de colaboradores.'}
          </p>
          <button type="button" className="employees-btn-retry" onClick={handleReload}>
            <IconRefresh size={16} />
            <span>Reintentar carga</span>
          </button>
        </div>
      )}

      {/* ── Contenido de la Tabla / Estados Vacíos ───────────────────────── */}
      {!isLoading && !loadError && (
        <>
          {employees.length === 0 ? (
            /* Directorio Vacío */
            <div className="employees-empty-card">
              <div className="employees-empty-icon" aria-hidden="true">
                <IconUsers size={24} />
              </div>
              <h2 className="employees-empty-title">{statusFilter === 'all' ? 'No hay colaboradores registrados' : statusFilter === 'active' ? 'No hay colaboradores activos' : 'No hay colaboradores inactivos'}</h2>
              <p className="employees-empty-text">
                {statusFilter === 'all'
                  ? 'La organización aún no cuenta con colaboradores registrados en el sistema.'
                  : statusFilter === 'active'
                  ? 'No se encontraron colaboradores en estado activo.'
                  : 'No se encontraron colaboradores en estado inactivo.'}
              </p>
              {isAdmin && statusFilter === 'all' && (
                <div className="employees-empty-action">
                  <button
                    type="button"
                    className="employees-btn-primary"
                    onClick={handleOpenCreateModal}
                  >
                    <IconPlus size={16} />
                    <span>Registrar primer colaborador</span>
                  </button>
                </div>
              )}
              {statusFilter !== 'all' && (
                <button type="button" className="employees-empty-reset" onClick={() => setStatusFilter('all')}>
                  Ver todos los colaboradores
                </button>
              )}
            </div>
          ) : filteredEmployees.length === 0 ? (
            /* Búsqueda o Filtro sin Resultados */
            <div className="employees-empty-card">
              <h2 className="employees-empty-title">Sin resultados de búsqueda</h2>
              <p className="employees-empty-text">
                No se encontraron colaboradores que coincidan con los criterios de búsqueda o filtros seleccionados.
              </p>
              <div className="employees-empty-action">
                <button
                  type="button"
                  className="employees-btn-secondary"
                  onClick={() => {
                    setSearchQuery('');
                    setPositionFilter('all');
                    setStatusFilter('all');
                  }}
                >
                  Limpiar filtros y búsqueda
                </button>
              </div>
            </div>
          ) : (
            /* Tabla Accesible de Colaboradores */
            <div className="employees-table-card">
              <div className="employees-table-container">
                <table className="employees-table" aria-label="Directorio de colaboradores">
                  <thead>
                    <tr>
                      <th scope="col" style={{ width: isAdmin ? '32%' : '35%' }}>Colaborador</th>
                      <th scope="col" style={{ width: '25%' }}>Puesto</th>
                      <th scope="col" style={{ width: '23%' }}>Sede</th>
                      <th scope="col" style={{ width: isAdmin ? '10%' : '17%' }}>Estado</th>
                      {isAdmin && (
                        <th scope="col" style={{ width: '10%', textAlign: 'right' }}>
                          Acciones
                        </th>
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {filteredEmployees.map((emp) => (
                      <tr key={emp.id}>
                        <td className="employees-collab-cell">
                          <div className="employees-collab-info">
                            <div className="employees-avatar" aria-hidden="true">
                              {getInitials(emp.displayName)}
                            </div>
                            <div className="employees-collab-text">
                              <span className="employees-display-name">{emp.displayName}</span>
                              <span className="employees-code-badge">{emp.code}</span>
                            </div>
                          </div>
                        </td>
                        <td className="employees-position-cell">
                          {resolvePositionName(emp.positionId)}
                        </td>
                        <td className="employees-site-cell">
                          <span className="employees-site-name">
                            <IconBuilding size={14} className="employees-site-icon" aria-hidden="true" />
                            <span>{resolveSiteName(emp.siteId)}</span>
                          </span>
                        </td>
                        <td>
                          <span
                            className={`employees-status-badge ${
                              emp.active
                                ? 'employees-status-badge--active'
                                : 'employees-status-badge--inactive'
                            }`}
                          >
                            {emp.active ? 'Activo' : 'Inactivo'}
                          </span>
                        </td>
                        {isAdmin && (
                          <td className="employees-actions-cell">
                            <button
                              type="button"
                              className="employees-btn-edit"
                              onClick={() => handleOpenEditModal(emp)}
                              aria-label={`Editar colaborador ${emp.code}`}
                            >
                              <IconEdit size={14} />
                              <span>Editar</span>
                            </button>
                            <button
                              type="button"
                              className={`employees-btn-toggle ${
                                emp.active
                                  ? 'employees-btn-toggle--deactivate'
                                  : 'employees-btn-toggle--activate'
                              }`}
                              onClick={() => handleOpenStatusModal(emp)}
                              aria-label={`${emp.active ? 'Desactivar' : 'Activar'} colaborador ${emp.code}`}
                            >
                              <span>{emp.active ? 'Desactivar' : 'Activar'}</span>
                            </button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}

      {!isLoading && !loadError && <p className="employees-directory__footer">Mostrando {filteredEmployees.length} colaboradores</p>}
      </section>
      <aside className="employees-context" aria-labelledby="team-context-title">
        <img src={teamArt} alt="" className="employees-context__art" />
        <h2 id="team-context-title">Tu equipo</h2>
        <p>Colaboradores de tu organización.</p>
        <div className="employees-context__site"><IconBuilding size={17} /><span>{activeSite?.name ?? 'Sede no disponible'}</span></div>
        <dl>
          <div><dt><span className="employees-context__dot employees-context__dot--active" />Activos</dt><dd>{teamCounts?.active ?? '—'}</dd></div>
          <div><dt><span className="employees-context__dot" />Inactivos</dt><dd>{teamCounts?.inactive ?? '—'}</dd></div>
        </dl>
        <Link to="/app/puestos" className="employees-context__link">Ver puestos <IconArrowRight size={16} /></Link>
        <Link to="/app/indisponibilidades" className="employees-context__note"><IconCalendar size={20} /><span>Consulta las indisponibilidades antes de planificar.</span></Link>
      </aside>
      </div>

      {/* ── Modales ──────────────────────────────────────────────────────── */}
      <EmployeeModal
        isOpen={isModalOpen}
        mode={modalMode}
        employee={selectedEmployee}
        positions={positions}
        isSubmitting={isSubmittingModal}
        serverError={modalServerError}
        codeServerError={modalCodeError}
        onClose={() => setIsModalOpen(false)}
        onSubmit={handleModalSubmit}
      />

      <EmployeeStatusModal
        isOpen={isStatusModalOpen}
        employee={statusTargetEmployee}
        isSubmitting={isSubmittingStatus}
        serverError={statusServerError}
        onClose={() => setIsStatusModalOpen(false)}
        onConfirm={handleConfirmStatusChange}
      />
    </div>
  );
}
