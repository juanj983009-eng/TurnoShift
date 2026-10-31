/**
 * TurnoShift — Positions Page (HU-06)
 * Catálogo maestro de puestos de trabajo y mutaciones seguras con control de concurrencia.
 */

import { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { positionApi } from '../../api/positionApi';
import { generateIdempotencyKey } from '../../utils/idempotency';
import { ApiProblemError } from '../../types/api';
import type { PositionResponse, PositionStatusFilter } from '../../types/position';
import { PositionModal } from './PositionModal';
import { PositionStatusModal } from './PositionStatusModal';
import { Link } from '../../components/routing/Router';
import positionsArt from '../../assets/illustrations/positions-illustration.png';
import {
  IconPlus,
  IconEdit,
  IconSearch,
  IconCheck,
  IconAlertTriangle,
  IconRefresh,
  IconBriefcase,
  IconArrowRight
} from '../../components/common/Icons';
import './PositionsPage.css';

type PendingOperationIntent =
  | {
      readonly operation: 'create';
      readonly key: string;
      readonly code: string;
      readonly name: string;
    }
  | {
      readonly operation: 'edit';
      readonly key: string;
      readonly positionId: string;
      readonly name: string;
      readonly expectedLockVersion: number;
    }
  | {
      readonly operation: 'status';
      readonly key: string;
      readonly positionId: string;
      readonly targetStatus: boolean;
      readonly expectedLockVersion: number;
    };

export default function PositionsPage() {
  const { session } = useAuth();
  const isAdmin = session?.user.role === 'ADMINISTRADOR';
  const isSupervisor = session?.user.role === 'SUPERVISOR';

  // ── Estado del Catálogo y Filtros ───────────────────────────────────────
  const [positions, setPositions] = useState<PositionResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState<PositionStatusFilter>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<ApiProblemError | null>(null);
  const [catalogCounts, setCatalogCounts] = useState<{ total: number; active: number; inactive: number } | null>(null);

  useEffect(() => {
    let cancelled = false;
    setCatalogCounts(null);
    if (isLoading || loadError) return;
    const summarize = (items: PositionResponse[]) => {
      if (cancelled) return;
      const active = items.filter(item => item.isActive).length;
      setCatalogCounts({ total: items.length, active, inactive: items.length - active });
    };
    if (statusFilter === 'all') summarize(positions);
    else positionApi.getPositions().then(summarize).catch(() => { /* Counts remain unavailable. */ });
    return () => { cancelled = true; };
  }, [positions, statusFilter, isLoading, loadError]);

  // ── Alertas Globales de la Página ───────────────────────────────────────
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // ── Estado de Modales ───────────────────────────────────────────────────
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [selectedPosition, setSelectedPosition] = useState<PositionResponse | null>(null);
  const [isSubmittingModal, setIsSubmittingModal] = useState<boolean>(false);
  const [modalServerError, setModalServerError] = useState<string | null>(null);
  const [modalCodeError, setModalCodeError] = useState<string | null>(null);

  // Modal de confirmación de activación / desactivación
  const [isStatusModalOpen, setIsStatusModalOpen] = useState<boolean>(false);
  const [statusTargetPosition, setStatusTargetPosition] = useState<PositionResponse | null>(null);
  const [isSubmittingStatus, setIsSubmittingStatus] = useState<boolean>(false);
  const [statusServerError, setStatusServerError] = useState<string | null>(null);

  // ── Referencia de Intención de Idempotencia (conservada ante error de red) ──
  const pendingIntentRef = useRef<PendingOperationIntent | null>(null);

  // ── Carga de Puestos según Filtro de Estado ─────────────────────────────
  const loadPositions = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const activeParam =
        statusFilter === 'active' ? true : statusFilter === 'inactive' ? false : undefined;
      const data = await positionApi.getPositions(activeParam);
      setPositions(data);
    } catch (err: unknown) {
      if (err instanceof ApiProblemError) {
        setLoadError(err);
      } else {
        setLoadError(
          new ApiProblemError({
            type: '/problems/unknown',
            title: 'Error inesperado',
            status: 500,
            detail: 'No fue posible cargar el catálogo de puestos.'
          })
        );
      }
    } finally {
      setIsLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadPositions();
  }, [loadPositions]);

  // Recarga manual ante conflicto
  const handleReload = async () => {
    setConflictMessage(null);
    setSuccessMessage(null);
    pendingIntentRef.current = null;
    await loadPositions();
  };

  // ── Filtrado Local por Texto (Búsqueda Reactiva) ────────────────────────
  const filteredPositions = useMemo(() => {
    if (!searchQuery.trim()) return positions;
    const q = searchQuery.toLowerCase();
    return positions.filter(
      (pos) => pos.code.toLowerCase().includes(q) || pos.name.toLowerCase().includes(q)
    );
  }, [positions, searchQuery]);

  // ── Apertura de Modales ─────────────────────────────────────────────────
  const handleOpenCreateModal = () => {
    if (!isAdmin) return;
    setModalMode('create');
    setSelectedPosition(null);
    setModalServerError(null);
    setModalCodeError(null);
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (pos: PositionResponse) => {
    if (!isAdmin) return;
    setModalMode('edit');
    setSelectedPosition(pos);
    setModalServerError(null);
    setModalCodeError(null);
    setIsModalOpen(true);
  };

  const handleOpenStatusModal = (pos: PositionResponse) => {
    if (!isAdmin) return;
    setStatusTargetPosition(pos);
    setStatusServerError(null);
    setIsStatusModalOpen(true);
  };

  // ── Envío de Alta o Edición ─────────────────────────────────────────────
  const handleModalSubmit = async (formData: { code?: string; name: string }) => {
    if (!isAdmin) return;
    setIsSubmittingModal(true);
    setModalServerError(null);
    setModalCodeError(null);
    setConflictMessage(null);
    setSuccessMessage(null);

    if (modalMode === 'create') {
      const codeToSend = formData.code || '';
      const nameToSend = formData.name;

      // Reutilizar o generar clave de idempotencia verificando operación y payload exacto
      let idempotencyKey: string;
      const pending = pendingIntentRef.current;
      if (
        pending &&
        pending.operation === 'create' &&
        pending.code === codeToSend &&
        pending.name === nameToSend
      ) {
        idempotencyKey = pending.key;
      } else {
        idempotencyKey = generateIdempotencyKey();
        pendingIntentRef.current = {
          operation: 'create',
          key: idempotencyKey,
          code: codeToSend,
          name: nameToSend
        };
      }

      try {
        const createdPos = await positionApi.createPosition(
          { code: codeToSend, name: nameToSend },
          idempotencyKey
        );

        // Éxito: limpiar intención
        pendingIntentRef.current = null;
        setIsModalOpen(false);

        // Si el filtro actual es coherente (todos o activos), añadir al catálogo local
        if (statusFilter !== 'inactive') {
          setPositions((prev) => [createdPos, ...prev]);
        }
        setSuccessMessage(`Puesto ${createdPos.code} creado exitosamente.`);
      } catch (err: unknown) {
        if (err instanceof ApiProblemError) {
          if (err.isNetworkError) {
            // Conservar clave para reintento idéntico
            setModalServerError('Error de conexión. Puedes reintentar la operación.');
          } else {
            pendingIntentRef.current = null;

            if (err.status === 409 || err.code === 'POSITION_CODE_ALREADY_EXISTS') {
              setModalCodeError(
                err.detail || 'El código de puesto ya existe en la organización.'
              );
            } else if (err.status === 400 && err.fieldErrors.length > 0) {
              const codeErr = err.fieldErrors.find((f) => f.field === 'code');
              const nameErr = err.fieldErrors.find((f) => f.field === 'name');
              if (codeErr) setModalCodeError(codeErr.message);
              if (nameErr) setModalServerError(nameErr.message);
              if (!codeErr && !nameErr) setModalServerError(err.detail || 'Error de validación.');
            } else {
              setModalServerError(err.detail || err.title || 'Error al crear el puesto.');
            }
          }
        } else {
          pendingIntentRef.current = null;
          setModalServerError('Ocurrió un error inesperado al guardar.');
        }
      } finally {
        setIsSubmittingModal(false);
      }
    } else {
      // Modo edición
      if (!selectedPosition) return;
      const nameToSend = formData.name;
      const expectedLockVersion = selectedPosition.lockVersion;

      let idempotencyKey: string;
      const pending = pendingIntentRef.current;
      if (
        pending &&
        pending.operation === 'edit' &&
        pending.positionId === selectedPosition.id &&
        pending.name === nameToSend &&
        pending.expectedLockVersion === expectedLockVersion
      ) {
        idempotencyKey = pending.key;
      } else {
        idempotencyKey = generateIdempotencyKey();
        pendingIntentRef.current = {
          operation: 'edit',
          key: idempotencyKey,
          positionId: selectedPosition.id,
          name: nameToSend,
          expectedLockVersion
        };
      }

      try {
        const updatedPos = await positionApi.updatePosition(
          selectedPosition.id,
          {
            name: nameToSend,
            expectedLockVersion
          },
          idempotencyKey
        );

        // Éxito: limpiar intención
        pendingIntentRef.current = null;
        setIsModalOpen(false);

        // Actualizar en el estado local con la respuesta real del servidor
        setPositions((prev) =>
          prev.map((p) => (p.id === updatedPos.id ? updatedPos : p))
        );
        setSuccessMessage(`Puesto ${updatedPos.code} actualizado exitosamente.`);
      } catch (err: unknown) {
        if (err instanceof ApiProblemError) {
          if (err.isNetworkError) {
            setModalServerError('Error de conexión. Puedes reintentar la operación.');
          } else {
            pendingIntentRef.current = null;

            if (err.status === 409 || err.code === 'CONCURRENT_MODIFICATION') {
              setIsModalOpen(false);
              setConflictMessage(
                err.detail ||
                  'El puesto fue modificado concurrentemente por otra sesión. Recarga el catálogo para continuar.'
              );
            } else if (err.status === 400 && err.fieldErrors.length > 0) {
              const nameErr = err.fieldErrors.find((f) => f.field === 'name');
              setModalServerError(nameErr?.message || err.detail || 'Error de validación.');
            } else {
              setModalServerError(err.detail || err.title || 'Error al actualizar el puesto.');
            }
          }
        } else {
          pendingIntentRef.current = null;
          setModalServerError('Ocurrió un error inesperado al guardar.');
        }
      } finally {
        setIsSubmittingModal(false);
      }
    }
  };

  // ── Conmutación de Estado (Activar / Desactivar) ─────────────────────────
  const handleConfirmStatusChange = async () => {
    if (!isAdmin || !statusTargetPosition) return;
    setIsSubmittingStatus(true);
    setStatusServerError(null);
    setConflictMessage(null);
    setSuccessMessage(null);

    const targetStatus = !statusTargetPosition.isActive;
    const expectedLockVersion = statusTargetPosition.lockVersion;

    let idempotencyKey: string;
    const pending = pendingIntentRef.current;
    if (
      pending &&
      pending.operation === 'status' &&
      pending.positionId === statusTargetPosition.id &&
      pending.targetStatus === targetStatus &&
      pending.expectedLockVersion === expectedLockVersion
    ) {
      idempotencyKey = pending.key;
    } else {
      idempotencyKey = generateIdempotencyKey();
      pendingIntentRef.current = {
        operation: 'status',
        key: idempotencyKey,
        positionId: statusTargetPosition.id,
        targetStatus,
        expectedLockVersion
      };
    }

    try {
      const updatedPos = await positionApi.changePositionStatus(
        statusTargetPosition.id,
        {
          isActive: targetStatus,
          expectedLockVersion
        },
        idempotencyKey
      );

      // Éxito: limpiar intención
      pendingIntentRef.current = null;
      setIsStatusModalOpen(false);

      // Si el filtro de la vista no coincide con el nuevo estado, removerlo o actualizarlo
      if (
        (statusFilter === 'active' && !updatedPos.isActive) ||
        (statusFilter === 'inactive' && updatedPos.isActive)
      ) {
        setPositions((prev) => prev.filter((p) => p.id !== updatedPos.id));
      } else {
        setPositions((prev) =>
          prev.map((p) => (p.id === updatedPos.id ? updatedPos : p))
        );
      }

      setSuccessMessage(
        `Puesto ${updatedPos.code} ${updatedPos.isActive ? 'activado' : 'desactivado'} exitosamente.`
      );
    } catch (err: unknown) {
      if (err instanceof ApiProblemError) {
        if (err.isNetworkError) {
          setStatusServerError('Error de conexión. Puedes reintentar la operación.');
        } else {
          pendingIntentRef.current = null;

          if (err.status === 409 || err.code === 'CONCURRENT_MODIFICATION') {
            setIsStatusModalOpen(false);
            setConflictMessage(
              err.detail ||
                'El estado del puesto fue modificado concurrentemente por otra sesión. Recarga el catálogo para continuar.'
            );
          } else {
            setStatusServerError(err.detail || err.title || 'Error al cambiar el estado del puesto.');
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
    <div className="positions-page positions-workspace">
      {/* ── Cabecera de Página ───────────────────────────────────────────── */}
      <header className="positions-header">
        <img className="positions-header__art" src={positionsArt} alt="" />
        <div className="positions-header__titles">
          <span className="positions-header__eyebrow">OPERACIONES • CATÁLOGO MAESTRO</span>
          <h1 className="positions-header__title">Puestos de trabajo</h1>
          <p className="positions-header__subtitle">
            Define los puestos que dan forma a tu equipo.
          </p>
          <div className="positions-overview" aria-label="Resumen del catálogo">
            <span><IconBriefcase size={14} /><strong>{catalogCounts?.total ?? '—'}</strong> registrados</span>
            <span className="positions-overview__active"><strong>{catalogCounts?.active ?? '—'}</strong> activos</span>
            <span className="positions-overview__inactive"><strong>{catalogCounts?.inactive ?? '—'}</strong> inactivos</span>
          </div>
        </div>

        {isAdmin && (
          <div className="positions-header__action">
            <button
              type="button"
              className="positions-btn-primary"
              onClick={handleOpenCreateModal}
            >
              <IconPlus size={16} />
              <span>Nuevo puesto</span>
            </button>
          </div>
        )}
      </header>

      {/* ── Aviso para Supervisor (Modo Consulta) ────────────────────────── */}
      {isSupervisor && (
        <div className="positions-notice-banner" role="status">
          <span>
            <strong>Modo consulta:</strong> Tienes permisos para consultar el catálogo de puestos operativos. Las acciones de creación, edición y cambio de estado están reservadas para usuarios Administradores.
          </span>
        </div>
      )}

      {/* ── Banners Contextuales (Conflicto / Éxito) ──────────────────────── */}
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
            aria-label="Recargar catálogo actualizado del servidor"
          >
            <IconRefresh size={14} />
            <span>Recargar catálogo</span>
          </button>
        </div>
      )}

      {successMessage && (
        <div className="config-banner config-banner--success" role="status" aria-live="polite">
          <div className="config-banner__left">
            <IconCheck size={18} className="config-banner__icon" />
            <span>{successMessage}</span>
          </div>
        </div>
      )}

      {/* ── Barra de Herramientas: Filtros y Búsqueda ────────────────────── */}
      <div className="positions-workspace__columns">
      <section className="positions-catalog" aria-label="Catálogo de puestos">
      <div className="positions-toolbar">
        <div className="positions-toolbar__filters" role="group" aria-label="Filtro de estado de puestos">
          <button
            type="button"
            className={`positions-filter-btn ${statusFilter === 'all' ? 'positions-filter-btn--active' : ''}`}
            onClick={() => setStatusFilter('all')}
          >
            Todos
          </button>
          <button
            type="button"
            className={`positions-filter-btn ${statusFilter === 'active' ? 'positions-filter-btn--active' : ''}`}
            onClick={() => setStatusFilter('active')}
          >
            Activos
          </button>
          <button
            type="button"
            className={`positions-filter-btn ${statusFilter === 'inactive' ? 'positions-filter-btn--active' : ''}`}
            onClick={() => setStatusFilter('inactive')}
          >
            Inactivos
          </button>
        </div>

        <div className="positions-toolbar__search">
          <IconSearch size={16} className="positions-search-icon" aria-hidden="true" />
          <input
            type="text"
            className="positions-search-input"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Buscar por código o nombre..."
            aria-label="Buscar puestos por código o nombre"
          />
          {searchQuery && (
            <button
              type="button"
              className="positions-search-clear"
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
        <div className="config-loading-state" role="status" aria-live="polite">
          <span className="config-spinner-large" aria-hidden="true" />
          <span className="config-loading-text">Cargando catálogo de puestos...</span>
        </div>
      )}

      {/* ── Estado de Error con Reintento ─────────────────────────────────── */}
      {!isLoading && loadError && (
        <div className="config-error-state" role="alert">
          <IconAlertTriangle size={32} className="config-error-icon" />
          <h2 className="config-error-title">No fue posible cargar el catálogo</h2>
          <p className="config-error-detail">
            {loadError.detail || 'Ocurrió un error al obtener la lista de puestos de trabajo.'}
          </p>
          <button type="button" className="config-btn-retry" onClick={handleReload}>
            <IconRefresh size={16} />
            <span>Reintentar carga</span>
          </button>
        </div>
      )}

      {/* ── Contenido de la Tabla / Estados Vacíos ───────────────────────── */}
      {!isLoading && !loadError && (
        <>
          {positions.length === 0 ? (
            /* Catálogo Vacío Total */
            <div className="positions-empty-card">
              <div className="positions-empty-icon" aria-hidden="true">
                <IconBriefcase size={24} />
              </div>
              <h2 className="positions-empty-title">No hay puestos de trabajo registrados</h2>
              <p className="positions-empty-text">
                Define los puestos operativos (ej. CAJERO, COCINERO, ENFERMERO) para organizar y asignar a tus colaboradores.
              </p>
              {isAdmin && (
                <div className="positions-empty-action">
                  <button
                    type="button"
                    className="positions-btn-primary"
                    onClick={handleOpenCreateModal}
                  >
                    <IconPlus size={16} />
                    <span>Crear primer puesto</span>
                  </button>
                </div>
              )}
            </div>
          ) : filteredPositions.length === 0 ? (
            /* Búsqueda o Filtro sin Resultados */
            <div className="positions-empty-card">
              <h2 className="positions-empty-title">Sin resultados de búsqueda</h2>
              <p className="positions-empty-text">
                No se encontraron puestos que coincidan con &ldquo;{searchQuery}&rdquo; en el filtro seleccionado.
              </p>
              <div className="positions-empty-action">
                <button
                  type="button"
                  className="positions-btn-secondary"
                  onClick={() => {
                    setSearchQuery('');
                    setStatusFilter('all');
                  }}
                >
                  Limpiar filtros y búsqueda
                </button>
              </div>
            </div>
          ) : (
            /* Tabla Accesible de Puestos */
            <div className="positions-table-card">
              <div className="positions-table-container">
                <table className="positions-table" aria-label="Catálogo de puestos de trabajo">
                  <thead>
                    <tr>
                      <th scope="col" style={{ width: '160px' }}>Código</th>
                      <th scope="col">Nombre del puesto</th>
                      <th scope="col" style={{ width: '130px' }}>Estado</th>
                      {isAdmin && (
                        <th scope="col" style={{ width: '180px', textAlign: 'right' }}>
                          Acciones
                        </th>
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {filteredPositions.map((pos) => (
                      <tr key={pos.id}>
                        <td>
                          <span className="positions-code-badge">{pos.code}</span>
                        </td>
                        <td className="positions-name-cell"><span className="positions-name-content"><span className="positions-row-icon" aria-hidden="true"><IconBriefcase size={19} /></span>{pos.name}</span></td>
                        <td>
                          <span
                            className={`positions-status-badge ${pos.isActive ? 'positions-status-badge--active' : 'positions-status-badge--inactive'}`}
                          >
                            {pos.isActive ? 'Activo' : 'Inactivo'}
                          </span>
                        </td>
                        {isAdmin && (
                          <td className="positions-actions-cell">
                            <button
                              type="button"
                              className="positions-btn-edit"
                              onClick={() => handleOpenEditModal(pos)}
                              aria-label={`Editar puesto ${pos.code}`}
                            >
                              <IconEdit size={14} />
                              <span>Editar</span>
                            </button>
                            <button
                              type="button"
                              className={`positions-btn-toggle ${pos.isActive ? 'positions-btn-toggle--deactivate' : 'positions-btn-toggle--activate'}`}
                              onClick={() => handleOpenStatusModal(pos)}
                              aria-label={`${pos.isActive ? 'Desactivar' : 'Activar'} puesto ${pos.code}`}
                            >
                              <span>{pos.isActive ? 'Desactivar' : 'Activar'}</span>
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

      {!isLoading && !loadError && <p className="positions-catalog__footer">Mostrando {filteredPositions.length} puestos</p>}
      </section>
      <aside className="positions-context" aria-labelledby="positions-context-title">
        <img src={positionsArt} className="positions-context__art" alt="" />
        <h2 id="positions-context-title">Tu catálogo</h2>
        <p>Los puestos activos se pueden asignar a colaboradores.</p>
        <dl>
          <div><dt><span className="positions-context__dot positions-context__dot--active" />Activos</dt><dd>{catalogCounts?.active ?? '—'}</dd></div>
          <div><dt><span className="positions-context__dot positions-context__dot--inactive" />Inactivos</dt><dd>{catalogCounts?.inactive ?? '—'}</dd></div>
        </dl>
        <Link to="/app/equipo" className="positions-context__link">Ver equipo <IconArrowRight size={16} /></Link>
        <p className="positions-context__note">Desactivar un puesto conserva su registro.</p>
      </aside>
      </div>

      {/* ── Modales ──────────────────────────────────────────────────────── */}
      <PositionModal
        isOpen={isModalOpen}
        mode={modalMode}
        position={selectedPosition}
        isSubmitting={isSubmittingModal}
        serverError={modalServerError}
        codeServerError={modalCodeError}
        onClose={() => setIsModalOpen(false)}
        onSubmit={handleModalSubmit}
      />

      <PositionStatusModal
        isOpen={isStatusModalOpen}
        position={statusTargetPosition}
        isSubmitting={isSubmittingStatus}
        serverError={statusServerError}
        onClose={() => setIsStatusModalOpen(false)}
        onConfirm={handleConfirmStatusChange}
      />
    </div>
  );
}
