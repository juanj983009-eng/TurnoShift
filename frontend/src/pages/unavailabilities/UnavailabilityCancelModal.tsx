/**
 * TurnoShift — Unavailability Cancel Modal
 * Diálogo accesible de confirmación para cancelación lógica de indisponibilidades (HU-08 / HU-10 / F4.3).
 *
 * Invariantes:
 * - expectedLockVersion obligatorio.
 * - Deshabilitación visual si ya está cancelada o si startsAt <= now (RN-013).
 * - Sin afirmaciones ficticias de nómina, turnos ni aprobaciones.
 */

import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import type { EmployeeUnavailabilityResponse } from '../../types/unavailability';
import type { EmployeeResponse } from '../../types/employee';
import { IconClose, IconAlertTriangle } from '../../components/common/Icons';

interface UnavailabilityCancelModalProps {
  readonly isOpen: boolean;
  readonly unavailability: EmployeeUnavailabilityResponse | null;
  readonly employee: EmployeeResponse | null;
  readonly timeZone: string;
  readonly isSubmitting: boolean;
  readonly serverError: string | null;
  readonly onClose: () => void;
  readonly onConfirm: () => Promise<void>;
}

export function UnavailabilityCancelModal({
  isOpen,
  unavailability,
  employee,
  timeZone,
  isSubmitting,
  serverError,
  onClose,
  onConfirm
}: UnavailabilityCancelModalProps) {
  const confirmBtnRef = useRef<HTMLButtonElement>(null);

  // Foco accesible al abrir
  useEffect(() => {
    if (!isOpen) return;
    const timer = setTimeout(() => {
      confirmBtnRef.current?.focus();
    }, 60);
    return () => clearTimeout(timer);
  }, [isOpen]);

  // Cerrar al pulsar Escape
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

  if (!isOpen || !unavailability) return null;

  const isAlreadyCancelled = unavailability.cancelled;
  const isPastOrStarted = new Date(unavailability.startsAt).getTime() <= Date.now();
  const isBlocked = isAlreadyCancelled || isPastOrStarted;

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

  const displayName = employee?.displayName || 'Colaborador';
  const code = employee?.code || unavailability.employeeId.slice(0, 8);

  return createPortal(
    <div
      className="modal-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget && !isSubmitting) {
          onClose();
        }
      }}
    >
      <div
        className="modal-card modal-card--cancel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="unavail-cancel-title"
      >
        {/* Cabecera */}
        <div className="modal-header">
          <div className="modal-header__info">
            <span className="modal-header__eyebrow">CONFIRMACIÓN REQUERIDA</span>
            <h2 id="unavail-cancel-title" className="modal-title">
              Cancelar Indisponibilidad
            </h2>
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

        {/* Cuerpo */}
        <div className="modal-body">
          {/* Error del Servidor */}
          {serverError && (
            <div className="unavail-server-error" role="alert">
              <IconAlertTriangle size={18} className="unavail-server-error-icon" />
              <span>{serverError}</span>
            </div>
          )}

          {/* Advertencia si está bloqueada en cliente */}
          {isAlreadyCancelled ? (
            <div className="unavail-client-block-notice" role="alert">
              <IconAlertTriangle size={18} />
              <span>Esta indisponibilidad ya se encuentra cancelada en el sistema.</span>
            </div>
          ) : isPastOrStarted ? (
            <div className="unavail-client-block-notice" role="alert">
              <IconAlertTriangle size={18} />
              <span>
                No es posible cancelar una indisponibilidad cuyo periodo ya ha iniciado o transcurrido (RN-013).
              </span>
            </div>
          ) : (
            <p className="unavail-cancel-prompt">
              ¿Deseas cancelar la indisponibilidad de <strong>{displayName}</strong> ({code}) programada del{' '}
              <strong>{formatDateTime(unavailability.startsAt)}</strong> al{' '}
              <strong>{formatDateTime(unavailability.endsAt)}</strong>?
            </p>
          )}

          <div className="unavail-neutral-note">
            <span>
              La indisponibilidad quedará marcada como cancelada y retirada de la planificación operativa.
            </span>
          </div>
        </div>

        {/* Pie */}
        <div className="modal-footer">
          <button
            type="button"
            className="modal-btn-cancel"
            onClick={onClose}
            disabled={isSubmitting}
          >
            Volver
          </button>
          <button
            ref={confirmBtnRef}
            type="button"
            className="modal-btn-danger"
            onClick={onConfirm}
            disabled={isSubmitting || isBlocked}
          >
            {isSubmitting ? 'Cancelando...' : 'Confirmar cancelación'}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
