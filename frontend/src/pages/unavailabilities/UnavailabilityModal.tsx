/**
 * TurnoShift — Unavailability Modal (Alta y Edición)
 * Diálogo accesible para el registro y modificación de indisponibilidades (HU-08 / HU-10 / F4.3).
 *
 * Invariantes temporales estrictas:
 * 1. Formato Parcial: horas explícitas UTC convertidas con Date.UTC, jamás new Date(valor).
 * 2. Formato Día Completo: allDay=true, startDate y endDate exclusiva calculada sumando 1 día
 *    mediante aritmética de fecha-calendario (Date.UTC sobre componentes numéricos), jamás new Date('YYYY-MM-DD').
 *    En creación, el valor inicial se calcula en la zona horaria de la organización (Intl.DateTimeFormat formatToParts).
 * 3. En edición: no se infiere allDay desde startsAt/endsAt (se abre siempre en modo parcial UTC).
 *    Si el usuario cambia a día completo, debe ingresar fechas locales explícitas con aclaración contextual.
 * 4. Exclusión mutua estricta: parcial omite allDay/startDate/endDate; día completo omite startsAt/endsAt.
 * 5. restrictedNote: confidencial (RN-014), máx 500 UTF-16, sin trim(), sin recortar y sin registrar en consola.
 */

import { useState, useEffect, useRef, type FormEvent } from 'react';
import { createPortal } from 'react-dom';
import type {
  EmployeeUnavailabilityResponse,
  UnavailabilityReason,
  CreateUnavailabilityRequest,
  UpdateUnavailabilityRequest
} from '../../types/unavailability';
import type { EmployeeResponse } from '../../types/employee';
import { IconClose, IconAlertTriangle } from '../../components/common/Icons';
import calendarArt from '../../assets/illustrations/absence-calendar.png';

export interface UnavailabilityModalSubmitPayload {
  readonly employeeId: string;
  readonly requestData: CreateUnavailabilityRequest | UpdateUnavailabilityRequest;
}

interface UnavailabilityModalProps {
  readonly isOpen: boolean;
  readonly mode: 'create' | 'edit';
  readonly unavailability: EmployeeUnavailabilityResponse | null;
  readonly employees: readonly EmployeeResponse[];
  readonly isSubmitting: boolean;
  readonly serverError: string | null;
  readonly onClose: () => void;
  readonly onSubmit: (payload: UnavailabilityModalSubmitPayload) => Promise<void>;
}

/**
 * Convierte un valor de input datetime-local ("YYYY-MM-DDTHH:mm") a ISO-8601 UTC estricto.
 * Utiliza exclusivamente Date.UTC sobre los componentes numéricos, evitando desfases locales.
 */
export function parseDateTimeLocalToIsoUtc(value: string): string | null {
  if (!value || typeof value !== 'string') return null;
  const parts = value.split('T');
  if (parts.length !== 2) return null;
  const [datePart, timePart] = parts;
  const dateComponents = datePart.split('-').map(Number);
  const timeComponents = timePart.split(':').map(Number);
  if (dateComponents.length !== 3 || timeComponents.length < 2) return null;

  const [y, m, d] = dateComponents;
  const [h, min] = timeComponents;
  if (isNaN(y) || isNaN(m) || isNaN(d) || isNaN(h) || isNaN(min)) return null;

  const timestamp = Date.UTC(y, m - 1, d, h, min, 0, 0);
  return new Date(timestamp).toISOString();
}

/**
 * Formatea un instante ISO UTC a formato "YYYY-MM-DDTHH:mm" para input datetime-local.
 * Lee los componentes UTC puros sin conversión por zona horaria local del navegador.
 */
export function formatIsoUtcToDateTimeLocal(isoUtc: string): string {
  if (!isoUtc) return '';
  const d = new Date(isoUtc);
  if (isNaN(d.getTime())) return '';
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  const h = String(d.getUTCHours()).padStart(2, '0');
  const min = String(d.getUTCMinutes()).padStart(2, '0');
  return `${y}-${m}-${day}T${h}:${min}`;
}

/**
 * Suma exactamente 1 día calendario a una fecha "YYYY-MM-DD" para obtener la endDate exclusiva del contrato.
 * Utiliza aritmética de componentes numéricos con Date.UTC, jamás new Date('YYYY-MM-DD').
 */
export function addOneDayCalendar(dateStr: string): string {
  if (!dateStr) return '';
  const [y, m, d] = dateStr.split('-').map(Number);
  if (isNaN(y) || isNaN(m) || isNaN(d)) return '';
  const nextDate = new Date(Date.UTC(y, m - 1, d + 1));
  const nextY = nextDate.getUTCFullYear();
  const nextM = String(nextDate.getUTCMonth() + 1).padStart(2, '0');
  const nextD = String(nextDate.getUTCDate()).padStart(2, '0');
  return `${nextY}-${nextM}-${nextD}`;
}

/**
 * Resta exactamente 1 día calendario a una endDate exclusiva para presentar la fecha inclusiva en la UI.
 */
export function subtractOneDayCalendar(dateStr: string): string {
  if (!dateStr) return '';
  const [y, m, d] = dateStr.split('-').map(Number);
  if (isNaN(y) || isNaN(m) || isNaN(d)) return '';
  const prevDate = new Date(Date.UTC(y, m - 1, d - 1));
  const prevY = prevDate.getUTCFullYear();
  const prevM = String(prevDate.getUTCMonth() + 1).padStart(2, '0');
  const prevD = String(prevDate.getUTCDate()).padStart(2, '0');
  return `${prevY}-${prevM}-${prevD}`;
}

/**
 * Obtiene la fecha actual en formato "YYYY-MM-DD" según la zona horaria especificada.
 * Utiliza Intl.DateTimeFormat con formatToParts para evitar desfases por zona horaria local o UTC.
 * No utiliza new Date('YYYY-MM-DD').
 */
export function getTodayDateInTimeZone(timeZone: string, date: Date = new Date()): string {
  try {
    const formatter = new Intl.DateTimeFormat('en-CA', {
      timeZone: timeZone || 'UTC',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
    const parts = formatter.formatToParts(date);
    const year = parts.find((p) => p.type === 'year')?.value;
    const month = parts.find((p) => p.type === 'month')?.value;
    const day = parts.find((p) => p.type === 'day')?.value;
    if (year && month && day) {
      return `${year}-${month}-${day}`;
    }
  } catch {
    // Si la zona horaria no es válida en el entorno, fallback seguro a componentes UTC
  }
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  const d = String(date.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

interface UnavailabilityModalProps {
  readonly isOpen: boolean;
  readonly mode: 'create' | 'edit';
  readonly unavailability: EmployeeUnavailabilityResponse | null;
  readonly employees: readonly EmployeeResponse[];
  readonly timeZone?: string;
  readonly isSubmitting: boolean;
  readonly serverError: string | null;
  readonly onClose: () => void;
  readonly onSubmit: (payload: UnavailabilityModalSubmitPayload) => Promise<void>;
}

export function UnavailabilityModal({
  isOpen,
  mode,
  unavailability,
  employees,
  timeZone = 'UTC',
  isSubmitting,
  serverError,
  onClose,
  onSubmit
}: UnavailabilityModalProps) {
  const isEdit = mode === 'edit';

  // Modalidad temporal: 'partial' (horas UTC) vs. 'allDay' (fechas locales con endDate exclusiva)
  const [temporalMode, setTemporalMode] = useState<'partial' | 'allDay'>('partial');

  // Formulario: Colaborador
  const [employeeId, setEmployeeId] = useState<string>('');

  // Formulario: Modo Parcial (datetime-local representativo de UTC)
  const [startDateTimeUtc, setStartDateTimeUtc] = useState<string>('');
  const [endDateTimeUtc, setEndDateTimeUtc] = useState<string>('');

  // Formulario: Modo Día Completo (fechas locales inclusivas en UI)
  const [startDate, setStartDate] = useState<string>('');
  const [endInclusiveDate, setEndInclusiveDate] = useState<string>('');

  // Formulario: Motivo y Nota Restringida
  const [reasonCode, setReasonCode] = useState<string>('');
  const [restrictedNote, setRestrictedNote] = useState<string>('');

  // Errores de validación cliente
  const [employeeError, setEmployeeError] = useState<string | null>(null);
  const [dateError, setDateError] = useState<string | null>(null);
  const [noteError, setNoteError] = useState<string | null>(null);

  const initialFocusRef = useRef<HTMLSelectElement | HTMLInputElement>(null);

  // Sincronizar estado al abrir o cambiar modo/indisponibilidad
  useEffect(() => {
    if (!isOpen) return;

    if (isEdit && unavailability) {
      setEmployeeId(unavailability.employeeId);
      setReasonCode(unavailability.reasonCode || '');
      setRestrictedNote(unavailability.restrictedNote || '');

      // Reglas 1 y 2: Siempre modo "Horario parcial (UTC)" por defecto en edición.
      // NO inferir "allDay" desde startsAt/endsAt (ni usando T00:00:00Z ni ninguna otra heurística).
      // Inicializar campos datetime-local desde los instantes UTC existentes exactamente.
      // NO derivar startDate/endDate con slice(0, 10).
      setTemporalMode('partial');
      setStartDateTimeUtc(formatIsoUtcToDateTimeLocal(unavailability.startsAt));
      setEndDateTimeUtc(formatIsoUtcToDateTimeLocal(unavailability.endsAt));

      // No se precalculan fechas locales; si el usuario convierte la edición a día completo,
      // deberá seleccionarlas explícitamente.
      setStartDate('');
      setEndInclusiveDate('');
    } else {
      setEmployeeId(employees.length > 0 ? employees[0].id : '');
      setTemporalMode('partial');

      // Fechas por defecto para creación: ahora + 2 horas
      const now = new Date();
      const inTwoHours = new Date(now.getTime() + 2 * 60 * 60 * 1000);
      setStartDateTimeUtc(formatIsoUtcToDateTimeLocal(now.toISOString()));
      setEndDateTimeUtc(formatIsoUtcToDateTimeLocal(inTwoHours.toISOString()));

      // Regla 4: Para crear día completo, el valor inicial se calcula en la zona horaria
      // de la organización mediante Intl.DateTimeFormat (...formatToParts), jamás con slice(0, 10).
      const todayStr = getTodayDateInTimeZone(timeZone, now);
      setStartDate(todayStr);
      setEndInclusiveDate(todayStr);

      setReasonCode('');
      setRestrictedNote('');
    }

    setEmployeeError(null);
    setDateError(null);
    setNoteError(null);
  }, [isOpen, isEdit, unavailability, employees, timeZone]);

  // Foco accesible al abrir
  useEffect(() => {
    if (!isOpen) return;
    const timer = setTimeout(() => {
      initialFocusRef.current?.focus();
    }, 60);
    return () => clearTimeout(timer);
  }, [isOpen]);

  // Cerrar al presionar Escape
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isSubmitting) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, isSubmitting, onClose]);

  if (!isOpen) return null;

  // Resolver nombre del colaborador para modo edición
  const currentEmployee = employees.find((emp) => emp.id === employeeId);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;

    setEmployeeError(null);
    setDateError(null);
    setNoteError(null);

    let hasClientError = false;

    if (!employeeId) {
      setEmployeeError('Debes seleccionar un colaborador.');
      hasClientError = true;
    }

    // Validación de longitud de nota (máx 500 caracteres UTF-16, sin alterar su contenido)
    if (restrictedNote.length > 500) {
      setNoteError('La nota restringida no puede superar los 500 caracteres.');
      hasClientError = true;
    }

    let requestData: CreateUnavailabilityRequest | UpdateUnavailabilityRequest;

    if (temporalMode === 'partial') {
      const startsAtUtc = parseDateTimeLocalToIsoUtc(startDateTimeUtc);
      const endsAtUtc = parseDateTimeLocalToIsoUtc(endDateTimeUtc);

      if (!startsAtUtc || !endsAtUtc) {
        setDateError('Debes ingresar fechas y horas de inicio y fin válidas en UTC.');
        hasClientError = true;
        return;
      }

      if (startsAtUtc >= endsAtUtc) {
        setDateError('La fecha/hora de inicio debe ser anterior a la de fin.');
        hasClientError = true;
      }

      // En creación: el periodo no puede haber finalizado en el pasado (RN-013)
      if (!isEdit && new Date(endsAtUtc).getTime() <= Date.now()) {
        setDateError('No se puede registrar una indisponibilidad en un periodo que ya ha finalizado.');
        hasClientError = true;
      }

      if (hasClientError) return;

      // Formato Parcial: omite estrictamente allDay, startDate y endDate
      const commonPartial = {
        startsAt: startsAtUtc,
        endsAt: endsAtUtc,
        reasonCode: (reasonCode ? (reasonCode as UnavailabilityReason) : null),
        restrictedNote: (restrictedNote === '' ? null : restrictedNote)
      };

      if (isEdit && unavailability) {
        requestData = {
          ...commonPartial,
          expectedLockVersion: unavailability.lockVersion
        };
      } else {
        requestData = commonPartial;
      }
    } else {
      // Modo Día Completo
      if (!startDate || !endInclusiveDate) {
        setDateError('Debes seleccionar fecha de inicio y fecha de fin.');
        hasClientError = true;
        return;
      }

      if (startDate > endInclusiveDate) {
        setDateError('La fecha de inicio debe ser anterior o igual a la fecha final.');
        hasClientError = true;
      }

      const calculatedEndDateExclusive = addOneDayCalendar(endInclusiveDate);
      if (!calculatedEndDateExclusive) {
        setDateError('Error al calcular el rango del día completo.');
        hasClientError = true;
      }

      if (hasClientError) return;

      // Formato Día Completo: omite estrictamente startsAt y endsAt
      const commonAllDay = {
        allDay: true,
        startDate,
        endDate: calculatedEndDateExclusive,
        reasonCode: (reasonCode ? (reasonCode as UnavailabilityReason) : null),
        restrictedNote: (restrictedNote === '' ? null : restrictedNote)
      };

      if (isEdit && unavailability) {
        requestData = {
          ...commonAllDay,
          expectedLockVersion: unavailability.lockVersion
        };
      } else {
        requestData = commonAllDay;
      }
    }

    await onSubmit({
      employeeId,
      requestData
    });
  };

  return createPortal(
    <div
      className="modal-backdrop absence-dialog-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget && !isSubmitting) {
          onClose();
        }
      }}
    >
      <div
        className="modal-card modal-card--unavail absence-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="unavail-modal-title"
      >
        {/* Cabecera del Modal */}
        <div className="modal-header">
          <img className="absence-dialog__art" src={calendarArt} alt="" />
          <div className="modal-header__info">
            <h2 id="unavail-modal-title" className="modal-title">
              {isEdit ? 'Modificar Indisponibilidad' : 'Registrar Indisponibilidad'}
            </h2>
            <p className="absence-dialog__subtitle">Define el periodo de ausencia del colaborador.</p>
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            disabled={isSubmitting}
            aria-label="Cerrar diálogo"
          >
            <IconClose size={20} />
          </button>
        </div>

        {/* Formulario */}
        <form className="modal-form" onSubmit={handleSubmit} noValidate>
          <div className="modal-body">
            {/* Error General del Servidor */}
            {serverError && (
              <div className="unavail-server-error" role="alert">
                <IconAlertTriangle size={18} className="unavail-server-error-icon" />
                <span>{serverError}</span>
              </div>
            )}

            {/* 1. Selección de Colaborador */}
            <div className="unavail-form-group">
              <label htmlFor="unavail-employee-select" className="unavail-form-label">
                Colaborador <span className="unavail-required-star">*</span>
              </label>

              {isEdit ? (
                /* En edición el colaborador es de solo lectura */
                <div className="unavail-readonly-employee">
                  <span className="unavail-readonly-name">
                    {currentEmployee?.displayName || 'Colaborador seleccionado'}
                  </span>
                  <span className="unavail-readonly-code">
                    {currentEmployee?.code || unavailability?.employeeId.slice(0, 8)}
                  </span>
                  <span className="unavail-readonly-hint">(No mutable tras la creación)</span>
                </div>
              ) : (
                /* En creación se elige de los empleados activos */
                <select
                  id="unavail-employee-select"
                  ref={initialFocusRef as React.RefObject<HTMLSelectElement>}
                  className={`unavail-form-select ${employeeError ? 'unavail-input--error' : ''}`}
                  value={employeeId}
                  onChange={(e) => setEmployeeId(e.target.value)}
                  disabled={isSubmitting}
                >
                  {employees.map((emp) => (
                    <option key={emp.id} value={emp.id}>
                      {emp.code} — {emp.displayName}
                    </option>
                  ))}
                </select>
              )}
              {employeeError && <span className="unavail-field-error">{employeeError}</span>}
            </div>

            {/* 2. Selector Mutuamente Excluyente de Modalidad Temporal */}
            <div className="unavail-form-group">
              <span className="unavail-form-label">Modalidad del Periodo</span>
              <div className="unavail-modal-tabs" role="tablist">
                <button
                  type="button"
                  role="tab"
                  aria-selected={temporalMode === 'partial'}
                  className={`unavail-modal-tab-btn ${
                    temporalMode === 'partial' ? 'unavail-modal-tab-btn--active' : ''
                  }`}
                  onClick={() => setTemporalMode('partial')}
                  disabled={isSubmitting}
                >
                  Por horas (UTC)
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={temporalMode === 'allDay'}
                  className={`unavail-modal-tab-btn ${
                    temporalMode === 'allDay' ? 'unavail-modal-tab-btn--active' : ''
                  }`}
                  onClick={() => setTemporalMode('allDay')}
                  disabled={isSubmitting}
                >
                  Día completo (fechas locales)
                </button>
              </div>
            </div>

            {/* 3A. Modo Parcial: Inicio y Fin en UTC */}
            {temporalMode === 'partial' && (
              <div className="unavail-form-row">
                <div className="unavail-form-group">
                  <label htmlFor="unavail-start-utc" className="unavail-form-label">
                    Inicio (UTC) <span className="unavail-required-star">*</span>
                  </label>
                  <input
                    id="unavail-start-utc"
                    type="datetime-local"
                    className={`unavail-form-input ${dateError ? 'unavail-input--error' : ''}`}
                    value={startDateTimeUtc}
                    onChange={(e) => setStartDateTimeUtc(e.target.value)}
                    disabled={isSubmitting}
                  />
                  <span className="unavail-field-hint">Ingresar hora en referencia UTC absoluta</span>
                </div>

                <div className="unavail-form-group">
                  <label htmlFor="unavail-end-utc" className="unavail-form-label">
                    Fin (UTC) <span className="unavail-required-star">*</span>
                  </label>
                  <input
                    id="unavail-end-utc"
                    type="datetime-local"
                    className={`unavail-form-input ${dateError ? 'unavail-input--error' : ''}`}
                    value={endDateTimeUtc}
                    onChange={(e) => setEndDateTimeUtc(e.target.value)}
                    disabled={isSubmitting}
                  />
                  <span className="unavail-field-hint">Ingresar hora en referencia UTC absoluta</span>
                </div>
              </div>
            )}

            {/* 3B. Modo Día Completo: Fechas Locales con EndDate Exclusiva Transparente */}
            {temporalMode === 'allDay' && (
              <>
                {isEdit && (
                  <div
                    className="unavail-neutral-note"
                    role="note"
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      marginBottom: 'var(--space-2)'
                    }}
                  >
                    <span
                      style={{ display: 'inline-flex', flexShrink: 0, color: 'var(--stone-500)' }}
                      aria-hidden="true"
                    >
                      <IconAlertTriangle size={15} />
                    </span>
                    <span>
                      El formato original no se conserva. Para guardar como día completo, selecciona las fechas locales.
                    </span>
                  </div>
                )}
                <div className="unavail-form-row">
                  <div className="unavail-form-group">
                    <label htmlFor="unavail-start-date" className="unavail-form-label">
                      Fecha de inicio (inclusiva) <span className="unavail-required-star">*</span>
                    </label>
                    <input
                      id="unavail-start-date"
                      type="date"
                      className={`unavail-form-input ${dateError ? 'unavail-input--error' : ''}`}
                      value={startDate}
                      onChange={(e) => setStartDate(e.target.value)}
                      disabled={isSubmitting}
                    />
                  </div>

                  <div className="unavail-form-group">
                    <label htmlFor="unavail-end-date" className="unavail-form-label">
                      Fecha de fin (inclusive) <span className="unavail-required-star">*</span>
                    </label>
                    <input
                      id="unavail-end-date"
                      type="date"
                      className={`unavail-form-input ${dateError ? 'unavail-input--error' : ''}`}
                      value={endInclusiveDate}
                      onChange={(e) => setEndInclusiveDate(e.target.value)}
                      disabled={isSubmitting}
                    />
                    <span className="unavail-field-hint">
                      Último día de ausencia. El sistema enviará la fecha exclusiva requerida (+1 día).
                    </span>
                  </div>
                </div>
              </>
            )}

            {dateError && <span className="unavail-field-error">{dateError}</span>}

            {/* 4. Motivo Operativo Opcional */}
            <div className="unavail-form-group">
              <label htmlFor="unavail-reason-select" className="unavail-form-label">
                Motivo operativo (opcional)
              </label>
              <select
                id="unavail-reason-select"
                className="unavail-form-select"
                value={reasonCode}
                onChange={(e) => setReasonCode(e.target.value)}
                disabled={isSubmitting}
              >
                <option value="">Sin motivo especificado</option>
                <option value="VACACIONES">Vacaciones</option>
                <option value="PERMISO_PERSONAL">Permiso personal</option>
                <option value="MEDICO">Médico</option>
                <option value="FORMACION">Formación</option>
                <option value="OTRO">Otro motivo</option>
              </select>
            </div>

            {/* 5. Nota Restringida Opcional (RN-014) */}
            <div className="unavail-form-group">
              <div className="unavail-label-with-counter">
                <label htmlFor="unavail-note-textarea" className="unavail-form-label">
                  Nota restringida confidencial (opcional)
                </label>
                <span className="unavail-char-counter">{restrictedNote.length} / 500</span>
              </div>
              <textarea
                id="unavail-note-textarea"
                className={`unavail-form-textarea ${noteError ? 'unavail-input--error' : ''}`}
                value={restrictedNote}
                onChange={(e) => setRestrictedNote(e.target.value)}
                maxLength={500}
                rows={3}
                placeholder="Detalle o justificación contextual para supervisión..."
                disabled={isSubmitting}
              />
              {noteError && <span className="unavail-field-error">{noteError}</span>}

              {/* Advertencia visual de privacidad */}
              <div className="unavail-confidential-note-notice">
                <span className="unavail-confidential-lock" aria-hidden="true">🔒</span>
                <span>
                  Visible solo para administradores y supervisores. No se comparte con el colaborador ni se publica en turnos.
                </span>
              </div>
            </div>
          </div>

          {/* Pie y Botones de Acción */}
          <div className="modal-footer">
            <button
              type="button"
              className="modal-btn-cancel"
              onClick={onClose}
              disabled={isSubmitting}
            >
              Cancelar
            </button>
            <button
              type="submit"
              className="modal-btn-submit"
              disabled={isSubmitting}
            >
              {isSubmitting
                ? 'Guardando...'
                : isEdit
                ? 'Guardar cambios'
                : 'Registrar indisponibilidad'}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
}
