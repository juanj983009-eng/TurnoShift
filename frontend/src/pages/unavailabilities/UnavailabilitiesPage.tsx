/**
 * TurnoShift — UnavailabilitiesPage (HU-08 / HU-10 / F4.2)
 * Pantalla de listado y consulta de indisponibilidades de personal.
 * Accesible para ADMINISTRADOR y SUPERVISOR (RN-012, RN-014).
 */

import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useRouter, Link } from '../../components/routing/Router';
import { unavailabilityApi } from '../../api/unavailabilityApi';
import { employeeApi } from '../../api/employeeApi';
import { ApiProblemError } from '../../types/api';
import { generateIdempotencyKey } from '../../utils/idempotency';
import type {
  EmployeeUnavailabilityResponse,
  UnavailabilityReason,
  CreateUnavailabilityRequest,
  UpdateUnavailabilityRequest,
  PendingUnavailabilityIntent
} from '../../types/unavailability';
import type { EmployeeResponse } from '../../types/employee';
import {
  IconCalendar,
  IconUsers,
  IconSearch,
  IconAlertTriangle,
  IconRefresh,
  IconPlus,
  IconEdit,
  IconClose,
  IconCheck,
  IconClock,
  IconArrowRight
} from '../../components/common/Icons';
import {
  UnavailabilityModal,
  type UnavailabilityModalSubmitPayload
} from './UnavailabilityModal';
import { UnavailabilityCancelModal } from './UnavailabilityCancelModal';
import calendarArt from '../../assets/illustrations/absence-calendar.png';
import './UnavailabilitiesPage.css';

type WindowPresetDays = 30 | 60 | 90;

const REASON_CONFIG: Record<UnavailabilityReason, { label: string; className: string }> = {
  VACACIONES: { label: 'Vacaciones', className: 'unavail-reason--vacaciones' },
  PERMISO_PERSONAL: { label: 'Permiso personal', className: 'unavail-reason--personal' },
  MEDICO: { label: 'Médico', className: 'unavail-reason--medico' },
  FORMACION: { label: 'Formación', className: 'unavail-reason--formacion' },
  OTRO: { label: 'Otro motivo', className: 'unavail-reason--otro' }
};

export default function UnavailabilitiesPage() {
  const { session } = useAuth();
  const { currentPath } = useRouter();

  const timeZone = session?.organization.timeZone || 'UTC';
  const canManage =
    session?.user.role === 'ADMINISTRADOR' || session?.user.role === 'SUPERVISOR';

  // ── Parámetros de Ventana Temporal en Backend ────────────────────────────
  const [presetDays, setPresetDays] = useState<WindowPresetDays>(60);
  const [includeCancelled, setIncludeCancelled] = useState<boolean>(false);

  // ── Estados de Datos y Carga ────────────────────────────────────────────
  const [unavailabilities, setUnavailabilities] = useState<readonly EmployeeUnavailabilityResponse[]>([]);
  const [employees, setEmployees] = useState<readonly EmployeeResponse[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<ApiProblemError | null>(null);

  // ── Estados de Modales y Mutaciones (F4.3) ─────────────────────────────────
  const [isCreateModalOpen, setIsCreateModalOpen] = useState<boolean>(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState<boolean>(false);
  const [isCancelModalOpen, setIsCancelModalOpen] = useState<boolean>(false);
  const [selectedUnavailForAction, setSelectedUnavailForAction] =
    useState<EmployeeUnavailabilityResponse | null>(null);
  const [isSubmittingAction, setIsSubmittingAction] = useState<boolean>(false);
  const [modalServerError, setModalServerError] = useState<string | null>(null);

  // ── Mensajes Contextuales ──────────────────────────────────────────────────
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // ── Referencia de Intención Idempotente ───────────────────────────────────
  const pendingIntentRef = useRef<PendingUnavailabilityIntent | null>(null);

  // ── Ventana Temporal Computada (UTC ISO) ─────────────────────────────────
  const [activeWindow, setActiveWindow] = useState<{ from: string; to: string }>(() => {
    const now = new Date();
    const to = new Date(now.getTime() + 60 * 24 * 60 * 60 * 1000);
    return { from: now.toISOString(), to: to.toISOString() };
  });

  // ── Filtros Locales en Memoria ──────────────────────────────────────────
  // Pre-selección desde query param si viene de EmployeesPage (?employeeId=...)
  const initialEmployeeFilter = useMemo(() => {
    try {
      const search = window.location.search || (currentPath.includes('?') ? currentPath.split('?')[1] : '');
      const params = new URLSearchParams(search);
      return params.get('employeeId') || 'all';
    } catch {
      return 'all';
    }
  }, [currentPath]);

  const [selectedEmployeeId, setSelectedEmployeeId] = useState<string>(initialEmployeeFilter);
  const [selectedReason, setSelectedReason] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // ── Carga Coordinada de Indisponibilidades y Colaboradores ───────────────
  const loadData = useCallback(async (days: WindowPresetDays, withCancelled: boolean) => {
    setIsLoading(true);
    setLoadError(null);

    const now = new Date();
    const toDate = new Date(now.getTime() + days * 24 * 60 * 60 * 1000);
    const fromUtc = now.toISOString();
    const toUtc = toDate.toISOString();

    setActiveWindow({ from: fromUtc, to: toUtc });

    try {
      const [unavailResponse, employeesData] = await Promise.all([
        unavailabilityApi.getOrganizationUnavailabilities(fromUtc, toUtc, withCancelled),
        employeeApi.getEmployees()
      ]);

      setUnavailabilities(unavailResponse.items);
      setEmployees(employeesData);
    } catch (err: unknown) {
      if (err instanceof ApiProblemError) {
        setLoadError(err);
      } else {
        setLoadError(
          new ApiProblemError({
            type: '/problems/unknown',
            title: 'Error de conexión',
            status: 500,
            detail: 'No fue posible cargar el listado de indisponibilidades ni el catálogo de personal.'
          })
        );
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Carga inicial y ante cambios en los parámetros del backend
  useEffect(() => {
    loadData(presetDays, includeCancelled);
  }, [presetDays, includeCancelled, loadData]);

  // ── Mapa de Colaboradores para Resolución Inmediata ───────────────────────
  const employeeMap = useMemo(() => {
    const map = new Map<string, EmployeeResponse>();
    for (const emp of employees) {
      map.set(emp.id, emp);
    }
    return map;
  }, [employees]);

  // ── Filtrado Local en Memoria ────────────────────────────────────────────
  const filteredItems = useMemo(() => {
    return unavailabilities.filter((u) => {
      const emp = employeeMap.get(u.employeeId);

      // 1. Filtro por Colaborador
      if (selectedEmployeeId !== 'all' && u.employeeId !== selectedEmployeeId) {
        return false;
      }

      // 2. Filtro por Motivo
      if (selectedReason !== 'all') {
        if (selectedReason === 'none') {
          if (u.reasonCode !== null) return false;
        } else if (u.reasonCode !== selectedReason) {
          return false;
        }
      }

      // 3. Filtro por Texto (Código o Nombre)
      if (searchQuery.trim() !== '') {
        const query = searchQuery.trim().toLowerCase();
        const code = (emp?.code || '').toLowerCase();
        const name = (emp?.displayName || '').toLowerCase();
        if (!code.includes(query) && !name.includes(query)) {
          return false;
        }
      }

      return true;
    });
  }, [unavailabilities, employeeMap, selectedEmployeeId, selectedReason, searchQuery]);

  // ── Formateadores de Fecha/Hora con Zona Horaria Oficial ─────────────────
  const formatWindowDates = (fromUtc: string, toUtc: string): string => {
    try {
      const formatter = new Intl.DateTimeFormat('es-ES', {
        timeZone,
        day: 'numeric',
        month: 'short',
        year: 'numeric'
      });
      return `${formatter.format(new Date(fromUtc))} — ${formatter.format(new Date(toUtc))} (${timeZone})`;
    } catch {
      return `${fromUtc.slice(0, 10)} — ${toUtc.slice(0, 10)}`;
    }
  };

  const formatDateTime = (isoUtc: string): string => {
    try {
      return new Intl.DateTimeFormat('es-ES', {
        timeZone,
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      }).format(new Date(isoUtc));
    } catch {
      return isoUtc;
    }
  };

  const calculateDuration = (startsAt: string, endsAt: string): string => {
    try {
      const diffMs = new Date(endsAt).getTime() - new Date(startsAt).getTime();
      if (diffMs <= 0) return '0 h';
      const hours = Math.round(diffMs / (1000 * 60 * 60));
      if (hours >= 24) {
        const days = Math.floor(hours / 24);
        const remHours = hours % 24;
        return remHours > 0 ? `${days} d ${remHours} h` : `${days} d`;
      }
      return `${hours} h`;
    } catch {
      return '';
    }
  };

  const getInitials = (name: string): string => {
    const parts = name.trim().split(/\s+/);
    if (parts.length === 0 || !parts[0]) return 'CO';
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  };

  const handleResetFilters = () => {
    setSelectedEmployeeId('all');
    setSelectedReason('all');
    setSearchQuery('');
  };

  // ── Auto-descarte de Mensaje de Éxito ─────────────────────────────────────
  useEffect(() => {
    if (!successMessage) return;
    const timer = setTimeout(() => {
      setSuccessMessage(null);
    }, 5000);
    return () => clearTimeout(timer);
  }, [successMessage]);

  // ── Verificación Temporal RN-013 en Cliente ───────────────────────────────
  const isPastOrStarted = (startsAt: string): boolean => {
    return new Date(startsAt).getTime() <= Date.now();
  };

  // ── Handlers de Apertura de Modales ───────────────────────────────────────
  const handleOpenCreate = () => {
    setModalServerError(null);
    setSelectedUnavailForAction(null);
    setIsCreateModalOpen(true);
  };

  const handleOpenEdit = (u: EmployeeUnavailabilityResponse) => {
    setModalServerError(null);
    setSelectedUnavailForAction(u);
    setIsEditModalOpen(true);
  };

  const handleOpenCancel = (u: EmployeeUnavailabilityResponse) => {
    setModalServerError(null);
    setSelectedUnavailForAction(u);
    setIsCancelModalOpen(true);
  };

  // ── Handler de Creación y Edición con Idempotencia Estricta ──────────────
  const handleModalSubmit = async (payload: UnavailabilityModalSubmitPayload) => {
    setIsSubmittingAction(true);
    setModalServerError(null);

    const isEditMode = isEditModalOpen && selectedUnavailForAction !== null;

    if (!isEditMode) {
      // ── ALTA DE INDISPONIBILIDAD ──────────────────────────────────────────
      const employeeId = payload.employeeId;
      const data = payload.requestData as CreateUnavailabilityRequest;

      const reqStartsAt = 'startsAt' in data ? data.startsAt : null;
      const reqEndsAt = 'endsAt' in data ? data.endsAt : null;
      const reqAllDay = 'allDay' in data ? data.allDay : null;
      const reqStartDate = 'startDate' in data ? data.startDate : null;
      const reqEndDate = 'endDate' in data ? data.endDate : null;
      const reqReason = data.reasonCode || null;
      const reqNote = data.restrictedNote || null;

      // Reutilización segura tras error de red solo si payload es idéntico
      const pending = pendingIntentRef.current;
      let idempotencyKey: string;

      if (
        pending &&
        pending.operation === 'create' &&
        pending.employeeId === employeeId &&
        (pending.startsAt || null) === reqStartsAt &&
        (pending.endsAt || null) === reqEndsAt &&
        (pending.allDay || null) === reqAllDay &&
        (pending.startDate || null) === reqStartDate &&
        (pending.endDate || null) === reqEndDate &&
        (pending.reasonCode || null) === reqReason &&
        (pending.restrictedNote || null) === reqNote
      ) {
        idempotencyKey = pending.key;
      } else {
        idempotencyKey = generateIdempotencyKey();
        pendingIntentRef.current = {
          operation: 'create',
          key: idempotencyKey,
          employeeId,
          startsAt: reqStartsAt,
          endsAt: reqEndsAt,
          allDay: reqAllDay,
          startDate: reqStartDate,
          endDate: reqEndDate,
          reasonCode: reqReason,
          restrictedNote: reqNote
        };
      }

      try {
        const created = await unavailabilityApi.createUnavailability(
          employeeId,
          data,
          idempotencyKey
        );

        pendingIntentRef.current = null;
        setUnavailabilities((prev) => [created, ...prev]);
        setIsCreateModalOpen(false);
        setSuccessMessage('Indisponibilidad registrada exitosamente.');
      } catch (err: unknown) {
        if (err instanceof ApiProblemError) {
          if (err.isNetworkError) {
            setModalServerError(
              'Error de conexión con el servidor. Puedes reintentar la operación de forma segura.'
            );
          } else {
            pendingIntentRef.current = null;

            if (err.code === 'EMPLOYEE_NOT_FOUND') {
              setModalServerError('El colaborador seleccionado no fue encontrado.');
            } else if (err.code === 'PAST_INTERVAL_MODIFICATION_PROHIBITED') {
              setModalServerError(
                'No está permitido registrar indisponibilidades en periodos que ya han finalizado (RN-013).'
              );
            } else if (
              err.code === 'IDEMPOTENCY_KEY_REUSED' ||
              err.code === 'IDEMPOTENCY_KEY_EXPIRED'
            ) {
              setModalServerError(
                'Conflicto de clave de idempotencia. Por favor reintenta la operación.'
              );
            } else if (err.code === 'VALIDATION_FAILED' || err.status === 400) {
              setModalServerError(
                err.detail || 'Los datos de la indisponibilidad no son válidos.'
              );
            } else {
              setModalServerError(
                err.detail || err.title || 'Error al registrar la indisponibilidad.'
              );
            }
          }
        } else {
          pendingIntentRef.current = null;
          setModalServerError('Ocurrió un error inesperado al registrar la indisponibilidad.');
        }
      } finally {
        setIsSubmittingAction(false);
      }
    } else {
      // ── EDICIÓN DE INDISPONIBILIDAD ───────────────────────────────────────
      const target = selectedUnavailForAction;
      const unavailabilityId = target.id;
      const data = payload.requestData as UpdateUnavailabilityRequest;
      const expectedLockVersion = data.expectedLockVersion;

      const reqStartsAt = 'startsAt' in data ? data.startsAt : null;
      const reqEndsAt = 'endsAt' in data ? data.endsAt : null;
      const reqAllDay = 'allDay' in data ? data.allDay : null;
      const reqStartDate = 'startDate' in data ? data.startDate : null;
      const reqEndDate = 'endDate' in data ? data.endDate : null;
      const reqReason = data.reasonCode || null;
      const reqNote = data.restrictedNote || null;

      const pending = pendingIntentRef.current;
      let idempotencyKey: string;

      if (
        pending &&
        pending.operation === 'update' &&
        pending.unavailabilityId === unavailabilityId &&
        pending.expectedLockVersion === expectedLockVersion &&
        (pending.startsAt || null) === reqStartsAt &&
        (pending.endsAt || null) === reqEndsAt &&
        (pending.allDay || null) === reqAllDay &&
        (pending.startDate || null) === reqStartDate &&
        (pending.endDate || null) === reqEndDate &&
        (pending.reasonCode || null) === reqReason &&
        (pending.restrictedNote || null) === reqNote
      ) {
        idempotencyKey = pending.key;
      } else {
        idempotencyKey = generateIdempotencyKey();
        pendingIntentRef.current = {
          operation: 'update',
          key: idempotencyKey,
          unavailabilityId,
          expectedLockVersion,
          startsAt: reqStartsAt,
          endsAt: reqEndsAt,
          allDay: reqAllDay,
          startDate: reqStartDate,
          endDate: reqEndDate,
          reasonCode: reqReason,
          restrictedNote: reqNote
        };
      }

      try {
        const updated = await unavailabilityApi.updateUnavailability(
          unavailabilityId,
          data,
          idempotencyKey
        );

        pendingIntentRef.current = null;
        setUnavailabilities((prev) =>
          prev.map((u) => (u.id === updated.id ? updated : u))
        );
        setIsEditModalOpen(false);
        setSuccessMessage('Indisponibilidad actualizada exitosamente.');
      } catch (err: unknown) {
        if (err instanceof ApiProblemError) {
          if (err.isNetworkError) {
            setModalServerError(
              'Error de conexión. Puedes reintentar la modificación de forma segura.'
            );
          } else {
            pendingIntentRef.current = null;

            if (err.status === 409 && (err.code === 'CONCURRENT_MODIFICATION' || !err.code)) {
              setIsEditModalOpen(false);
              setConflictMessage(
                err.detail ||
                  'La indisponibilidad fue modificada por otro usuario concurrentemente. Se ha actualizado el listado.'
              );
              loadData(presetDays, includeCancelled);
            } else if (err.code === 'PAST_INTERVAL_MODIFICATION_PROHIBITED') {
              setModalServerError(
                'No está permitido modificar una indisponibilidad cuyo periodo ya ha iniciado o transcurrido (RN-013).'
              );
            } else if (err.code === 'UNAVAILABILITY_ALREADY_CANCELLED') {
              setIsEditModalOpen(false);
              setConflictMessage('La indisponibilidad ya fue cancelada por otro usuario.');
              loadData(presetDays, includeCancelled);
            } else if (err.code === 'UNAVAILABILITY_NOT_FOUND') {
              setIsEditModalOpen(false);
              setConflictMessage('La indisponibilidad especificada no fue encontrada.');
              loadData(presetDays, includeCancelled);
            } else if (
              err.code === 'IDEMPOTENCY_KEY_REUSED' ||
              err.code === 'IDEMPOTENCY_KEY_EXPIRED'
            ) {
              setModalServerError(
                'Conflicto de clave de idempotencia. Por favor reintenta la operación.'
              );
            } else {
              setModalServerError(
                err.detail || err.title || 'Error al actualizar la indisponibilidad.'
              );
            }
          }
        } else {
          pendingIntentRef.current = null;
          setModalServerError('Ocurrió un error inesperado al actualizar la indisponibilidad.');
        }
      } finally {
        setIsSubmittingAction(false);
      }
    }
  };

  // ── Handler de Cancelación con Idempotencia Estricta ───────────────────────
  const handleCancelConfirm = async () => {
    if (!selectedUnavailForAction) return;

    const target = selectedUnavailForAction;
    const unavailabilityId = target.id;
    const expectedLockVersion = target.lockVersion;

    setIsSubmittingAction(true);
    setModalServerError(null);

    const pending = pendingIntentRef.current;
    let idempotencyKey: string;

    if (
      pending &&
      pending.operation === 'cancel' &&
      pending.unavailabilityId === unavailabilityId &&
      pending.expectedLockVersion === expectedLockVersion
    ) {
      idempotencyKey = pending.key;
    } else {
      idempotencyKey = generateIdempotencyKey();
      pendingIntentRef.current = {
        operation: 'cancel',
        key: idempotencyKey,
        unavailabilityId,
        expectedLockVersion
      };
    }

    try {
      const cancelled = await unavailabilityApi.cancelUnavailability(
        unavailabilityId,
        { expectedLockVersion },
        idempotencyKey
      );

      pendingIntentRef.current = null;
      setUnavailabilities((prev) =>
        prev.map((u) => (u.id === cancelled.id ? cancelled : u))
      );
      setIsCancelModalOpen(false);
      setSuccessMessage('Indisponibilidad cancelada exitosamente.');
    } catch (err: unknown) {
      if (err instanceof ApiProblemError) {
        if (err.isNetworkError) {
          setModalServerError(
            'Error de conexión. Puedes reintentar la cancelación de forma segura.'
          );
        } else {
          pendingIntentRef.current = null;

          if (err.status === 409 && (err.code === 'CONCURRENT_MODIFICATION' || !err.code)) {
            setIsCancelModalOpen(false);
            setConflictMessage(
              err.detail ||
                'La indisponibilidad fue modificada por otro usuario concurrentemente. Se ha actualizado el listado.'
            );
            loadData(presetDays, includeCancelled);
          } else if (err.code === 'UNAVAILABILITY_ALREADY_CANCELLED') {
            setIsCancelModalOpen(false);
            setConflictMessage('Esta indisponibilidad ya fue cancelada por otro usuario.');
            loadData(presetDays, includeCancelled);
          } else if (err.code === 'PAST_INTERVAL_MODIFICATION_PROHIBITED') {
            setModalServerError(
              'No está permitido cancelar una indisponibilidad cuyo periodo ya ha iniciado o transcurrido (RN-013).'
            );
          } else if (
            err.code === 'IDEMPOTENCY_KEY_REUSED' ||
            err.code === 'IDEMPOTENCY_KEY_EXPIRED'
          ) {
            setModalServerError(
              'Conflicto de clave de idempotencia. Por favor reintenta la operación.'
            );
          } else {
            setModalServerError(
              err.detail || err.title || 'Error al cancelar la indisponibilidad.'
            );
          }
        }
      } else {
        pendingIntentRef.current = null;
        setModalServerError('Ocurrió un error inesperado al cancelar la indisponibilidad.');
      }
    } finally {
      setIsSubmittingAction(false);
    }
  };

  return (
    <div className="unavailabilities-page absence-workspace">
      {/* ── Cabecera de Página ───────────────────────────────────────────── */}
      <header className="unavailabilities-header">
        <img src={calendarArt} alt="" className="absence-header-art" />
        <div className="unavailabilities-header__titles">
          <span className="unavailabilities-header__eyebrow">OPERACIONES • GESTIÓN DE PERSONAL</span>
          <h1 className="unavailabilities-header__title">Indisponibilidades</h1>
          <p className="unavailabilities-header__subtitle">
            Gestiona los periodos de ausencia de tu equipo.
          </p>
          <p className="absence-permissions">Administradores y supervisores pueden gestionar estos periodos.</p>
        </div>

        <div className="unavailabilities-header__actions">
          <Link to="/app/equipo" className="unavailabilities-btn-secondary">
            <IconUsers size={16} />
            <span>Directorio de equipo</span>
          </Link>
          {canManage && (
            <button
              type="button"
              className="unavailabilities-btn-primary"
              onClick={handleOpenCreate}
            >
              <IconPlus size={16} />
              <span>Registrar indisponibilidad</span>
            </button>
          )}
        </div>
      </header>

      {/* ── Banners Contextuales (Conflicto / Éxito) ──────────────────────── */}
      {conflictMessage && (
        <div className="unavail-banner unavail-banner--conflict" role="alert">
          <div className="unavail-banner__left">
            <IconAlertTriangle size={18} className="unavail-banner__icon" />
            <span>{conflictMessage}</span>
          </div>
          <button
            type="button"
            className="unavail-banner-btn-reload"
            onClick={() => {
              setConflictMessage(null);
              loadData(presetDays, includeCancelled);
            }}
          >
            <IconRefresh size={14} />
            <span>Actualizar datos</span>
          </button>
        </div>
      )}

      {successMessage && (
        <div className="unavail-banner unavail-banner--success" role="status">
          <div className="unavail-banner__left">
            <IconCheck size={18} className="unavail-banner__icon" />
            <span>{successMessage}</span>
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={() => setSuccessMessage(null)}
            aria-label="Cerrar notificación"
          >
            <IconClose size={16} />
          </button>
        </div>
      )}

      {/* ── Barra de Control de Ventana Temporal (Backend from/to) ───────── */}
      <div className="absence-workspace__columns">
      <section className="absence-register" aria-label="Consulta de indisponibilidades">
      <section className="unavailabilities-window-bar" aria-label="Control de ventana temporal de consulta">
        <div className="unavailabilities-window-info">
          <IconCalendar size={20} className="unavailabilities-window-icon" aria-hidden="true" />
          <div className="unavailabilities-window-text">
            <span className="unavailabilities-window-label">Periodo consultado · {timeZone}</span>
            <span className="unavailabilities-window-range">
              {formatWindowDates(activeWindow.from, activeWindow.to)}
            </span>
          </div>
        </div>

        <div className="unavailabilities-presets" role="group" aria-label="Presets de ventana temporal">
          <button
            type="button"
            className={`unavailabilities-preset-btn ${presetDays === 30 ? 'unavailabilities-preset-btn--active' : ''}`}
            onClick={() => setPresetDays(30)}
          >
            30 días
          </button>
          <button
            type="button"
            className={`unavailabilities-preset-btn ${presetDays === 60 ? 'unavailabilities-preset-btn--active' : ''}`}
            onClick={() => setPresetDays(60)}
          >
            60 días
          </button>
          <button
            type="button"
            className={`unavailabilities-preset-btn ${presetDays === 90 ? 'unavailabilities-preset-btn--active' : ''}`}
            onClick={() => setPresetDays(90)}
          >
            90 días
          </button>
        </div>
      </section>

      {/* ── Barra de Herramientas: Filtros Locales y Búsqueda ────────────── */}
      <section className="unavailabilities-toolbar" aria-label="Filtros y búsqueda de indisponibilidades">
        <div className="unavailabilities-toolbar__controls">
          {/* Toggle de Canceladas */}
          <label className="unavailabilities-toggle-cancelled">
            <input
              type="checkbox"
              className="unavailabilities-toggle-checkbox"
              checked={includeCancelled}
              onChange={(e) => setIncludeCancelled(e.target.checked)}
            />
            <span>Incluir canceladas</span>
          </label>

          {/* Filtro por Colaborador */}
          <div className="unavailabilities-select-wrapper">
            <select
              className="unavailabilities-select"
              value={selectedEmployeeId}
              onChange={(e) => setSelectedEmployeeId(e.target.value)}
              aria-label="Filtrar por colaborador"
            >
              <option value="all">Todos los colaboradores</option>
              {employees.map((emp) => (
                <option key={emp.id} value={emp.id}>
                  {emp.code} — {emp.displayName}
                </option>
              ))}
            </select>
          </div>

          {/* Filtro por Motivo */}
          <div className="unavailabilities-select-wrapper">
            <select
              className="unavailabilities-select"
              value={selectedReason}
              onChange={(e) => setSelectedReason(e.target.value)}
              aria-label="Filtrar por motivo de indisponibilidad"
            >
              <option value="all">Todos los motivos</option>
              <option value="VACACIONES">Vacaciones</option>
              <option value="PERMISO_PERSONAL">Permiso personal</option>
              <option value="MEDICO">Médico</option>
              <option value="FORMACION">Formación</option>
              <option value="OTRO">Otro motivo</option>
              <option value="none">Sin motivo especificado</option>
            </select>
          </div>
        </div>

        {/* Búsqueda en Memoria */}
        <div className="unavailabilities-toolbar__search">
          <IconSearch size={16} className="unavailabilities-search-icon" aria-hidden="true" />
          <input
            type="text"
            className="unavailabilities-search-input"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Buscar por código o nombre..."
            aria-label="Buscar colaborador por código o nombre"
          />
          {searchQuery && (
            <button
              type="button"
              className="unavailabilities-search-clear"
              onClick={() => setSearchQuery('')}
              aria-label="Limpiar búsqueda"
            >
              ×
            </button>
          )}
        </div>
      </section>

      {/* ── Estado de Carga ──────────────────────────────────────────────── */}
      {isLoading && (
        <div className="unavailabilities-loading-state" role="status" aria-live="polite">
          <span className="unavailabilities-spinner-large" aria-hidden="true" />
          <span className="unavailabilities-loading-text">
            Cargando indisponibilidades y catálogo de personal...
          </span>
        </div>
      )}

      {/* ── Estado de Error con Reintento ─────────────────────────────────── */}
      {!isLoading && loadError && (
        <div className="unavailabilities-error-state" role="alert">
          <IconAlertTriangle size={32} className="unavailabilities-error-icon" />
          <h2 className="unavailabilities-error-title">No fue posible cargar las indisponibilidades</h2>
          <p className="unavailabilities-error-detail">
            {loadError.detail || 'Ocurrió un error al obtener la información del servidor.'}
          </p>
          <button
            type="button"
            className="unavailabilities-btn-retry"
            onClick={() => loadData(presetDays, includeCancelled)}
          >
            <IconRefresh size={16} />
            <span>Reintentar carga</span>
          </button>
        </div>
      )}

      {/* ── Tabla de Indisponibilidades / Estados Vacíos ─────────────────── */}
      {!isLoading && !loadError && (
        <>
          {unavailabilities.length === 0 ? (
            /* Sin indisponibilidades en la ventana */
            <div className="unavailabilities-empty-card">
              <div className="unavailabilities-empty-icon" aria-hidden="true">
                <img src={calendarArt} alt="" />
              </div>
              <h2 className="unavailabilities-empty-title">Sin indisponibilidades en este periodo</h2>
              <p className="unavailabilities-empty-text">
                No se registraron periodos de indisponibilidad en la ventana seleccionada de {presetDays} días.
              </p>
              {canManage && (
                <button
                  type="button"
                  className="unavailabilities-btn-primary"
                  onClick={handleOpenCreate}
                  style={{ marginTop: 'var(--space-2)' }}
                >
                  <IconPlus size={16} />
                  <span>Registrar un periodo</span>
                </button>
              )}
            </div>
          ) : filteredItems.length === 0 ? (
            /* Sin coincidencias tras filtros */
            <div className="unavailabilities-empty-card">
              <div className="unavailabilities-empty-icon" aria-hidden="true">
                <IconSearch size={24} />
              </div>
              <h2 className="unavailabilities-empty-title">Sin resultados para los filtros aplicados</h2>
              <p className="unavailabilities-empty-text">
                Ninguna indisponibilidad coincide con los criterios de búsqueda o filtros seleccionados.
              </p>
              <button
                type="button"
                className="unavailabilities-btn-reset"
                onClick={handleResetFilters}
              >
                Restablecer filtros
              </button>
            </div>
          ) : (
            /* Tabla Principal de Indisponibilidades */
            <div className="unavailabilities-table-card">
              <div className="unavailabilities-table-wrapper">
                <table className="unavailabilities-table">
                  <thead>
                    <tr>
                      <th scope="col">Colaborador</th>
                      <th scope="col">Intervalo ({timeZone})</th>
                      <th scope="col">Motivo</th>
                      <th scope="col">Estado</th>
                      {canManage && <th scope="col" style={{ textAlign: 'right' }}>Acciones</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {filteredItems.map((u) => {
                      const emp = employeeMap.get(u.employeeId);
                      const displayName = emp?.displayName || 'Colaborador no encontrado';
                      const code = emp?.code || u.employeeId.slice(0, 8);
                      const reasonInfo = u.reasonCode ? REASON_CONFIG[u.reasonCode] : null;

                      return (
                        <tr key={u.id}>
                          {/* Colaborador */}
                          <td>
                            <div className="unavail-cell-employee">
                              <div className="unavail-avatar" aria-hidden="true">
                                {getInitials(displayName)}
                              </div>
                              <div className="unavail-employee-info">
                                <span className="unavail-employee-name">{displayName}</span>
                                <span className="unavail-employee-code">{code}</span>
                              </div>
                            </div>
                          </td>

                          {/* Intervalo */}
                          <td>
                            <div className="unavail-cell-interval">
                              <span className="unavail-interval-dates">
                                {formatDateTime(u.startsAt)} — {formatDateTime(u.endsAt)}
                              </span>
                              <span className="unavail-interval-duration">
                                Duración: {calculateDuration(u.startsAt, u.endsAt)}
                              </span>
                            </div>
                          </td>

                          {/* Motivo */}
                          <td>
                            {reasonInfo ? (
                              <span className={`unavail-reason-badge ${reasonInfo.className}`}>
                                {reasonInfo.label}
                              </span>
                            ) : (
                              <span className="unavail-reason-badge unavail-reason--none">
                                Sin motivo
                              </span>
                            )}
                          </td>

                          {/* Estado */}
                          <td>
                            <div className="unavail-status-wrapper">
                              {u.cancelled ? (
                                <>
                                  <span className="unavail-status-badge unavail-status--cancelled">
                                    <span className="unavail-status-dot" aria-hidden="true" />
                                    <span>Cancelada</span>
                                  </span>
                                  {u.cancelledAt && (
                                    <span className="unavail-cancelled-subtext">
                                      {formatDateTime(u.cancelledAt)}
                                    </span>
                                  )}
                                </>
                              ) : (
                                <span className="unavail-status-badge unavail-status--active">
                                  <span className="unavail-status-dot" aria-hidden="true" />
                                  <span>Activa</span>
                                </span>
                              )}
                            </div>
                          </td>

                          {/* Acciones para Administradores y Supervisores */}
                          {canManage && (
                            <td style={{ textAlign: 'right' }}>
                              <div className="unavail-actions-cell">
                                <button
                                  type="button"
                                  className="unavail-btn-action unavail-btn-edit"
                                  onClick={() => handleOpenEdit(u)}
                                  disabled={u.cancelled || isPastOrStarted(u.startsAt)}
                                  title={
                                    u.cancelled
                                      ? 'No se puede editar una indisponibilidad cancelada'
                                      : isPastOrStarted(u.startsAt)
                                      ? 'No se puede editar una indisponibilidad cuyo periodo ya inició o transcurrido (RN-013)'
                                      : 'Modificar fechas, motivo o notas'
                                  }
                                >
                                  <IconEdit size={13} />
                                  <span>Editar</span>
                                </button>
                                <button
                                  type="button"
                                  className="unavail-btn-action unavail-btn-cancel-action"
                                  onClick={() => handleOpenCancel(u)}
                                  disabled={u.cancelled || isPastOrStarted(u.startsAt)}
                                  title={
                                    u.cancelled
                                      ? 'Esta indisponibilidad ya está cancelada'
                                      : isPastOrStarted(u.startsAt)
                                      ? 'No se puede cancelar una indisponibilidad cuyo periodo ya inició o transcurrido (RN-013)'
                                      : 'Cancelar esta indisponibilidad'
                                  }
                                >
                                  <IconClose size={13} />
                                  <span>Cancelar</span>
                                </button>
                              </div>
                            </td>
                          )}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* Pie de Tabla con Resumen */}
              <div className="unavailabilities-table-footer">
                <span>
                  Mostrando <strong>{filteredItems.length}</strong> de <strong>{unavailabilities.length}</strong> registros en la ventana
                </span>
                <span>Zona horaria: {timeZone}</span>
              </div>
            </div>
          )}
        </>
      )}

      {!isLoading && !loadError && filteredItems.length === 0 && <p className="absence-register__footer">0 registros para los filtros seleccionados</p>}
      </section>
      <aside className="absence-context" aria-labelledby="absence-context-title">
        <h2 id="absence-context-title">Resumen del periodo</h2>
        <img src={calendarArt} alt="" className="absence-context__art" />
        <dl>
          <div><dt><span className="absence-dot absence-dot--active" />Activas</dt><dd>{isLoading || loadError ? '—' : unavailabilities.filter(item => !item.cancelled).length}</dd></div>
          <div><dt><span className="absence-dot" />Canceladas</dt><dd>{!includeCancelled ? 'No incluidas' : isLoading || loadError ? '—' : unavailabilities.filter(item => item.cancelled).length}</dd></div>
        </dl>
        <div className="absence-context__timezone"><IconClock size={18} /><span>Zona horaria<strong>{timeZone}</strong></span></div>
        <Link to="/app/equipo" className="absence-context__link">Ver equipo <IconArrowRight size={16} /></Link>
        <p className="absence-context__privacy">Las notas confidenciales solo se muestran en el formulario autorizado.</p>
      </aside>
      </div>

      {/* ── Modales de Indisponibilidades (F4.3) ─────────────────────────── */}
      <UnavailabilityModal
        isOpen={isCreateModalOpen || isEditModalOpen}
        mode={isEditModalOpen ? 'edit' : 'create'}
        unavailability={selectedUnavailForAction}
        employees={employees}
        timeZone={timeZone}
        isSubmitting={isSubmittingAction}
        serverError={modalServerError}
        onClose={() => {
          setIsCreateModalOpen(false);
          setIsEditModalOpen(false);
          setModalServerError(null);
        }}
        onSubmit={handleModalSubmit}
      />

      <UnavailabilityCancelModal
        isOpen={isCancelModalOpen}
        unavailability={selectedUnavailForAction}
        employee={
          selectedUnavailForAction
            ? employeeMap.get(selectedUnavailForAction.employeeId) || null
            : null
        }
        timeZone={timeZone}
        isSubmitting={isSubmittingAction}
        serverError={modalServerError}
        onClose={() => {
          setIsCancelModalOpen(false);
          setModalServerError(null);
        }}
        onConfirm={handleCancelConfirm}
      />
    </div>
  );
}
