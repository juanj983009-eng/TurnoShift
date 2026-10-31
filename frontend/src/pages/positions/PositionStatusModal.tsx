/**
 * TurnoShift — Position Status Modal (Conmutación de Estado Activo/Inactivo)
 * Diálogo accesible de confirmación para el cambio de estado de puestos (HU-06).
 */

import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import type { PositionResponse } from '../../types/position';
import { IconClose, IconAlertTriangle } from '../../components/common/Icons';

interface PositionStatusModalProps {
  readonly isOpen: boolean;
  readonly position: PositionResponse | null;
  readonly isSubmitting: boolean;
  readonly serverError: string | null;
  readonly onClose: () => void;
  readonly onConfirm: () => Promise<void>;
}

export function PositionStatusModal({
  isOpen,
  position,
  isSubmitting,
  serverError,
  onClose,
  onConfirm
}: PositionStatusModalProps) {
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

  if (!isOpen || !position) return null;

  const isDeactivating = position.isActive;

  return createPortal(
    <div
      className="modal-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget && !isSubmitting) {
          onClose();
        }
      }}
      role="presentation"
    >
      <div
        className="modal-card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="status-modal-title"
      >
        <div className="modal-header">
          <h2 id="status-modal-title" className="modal-title">
            {isDeactivating ? 'Desactivar puesto de trabajo' : 'Activar puesto de trabajo'}
          </h2>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            disabled={isSubmitting}
            aria-label="Cerrar modal"
          >
            <IconClose size={20} />
          </button>
        </div>

        <div className="modal-body">
          {serverError && (
            <div className="config-banner config-banner--conflict" role="alert">
              <span className="config-banner__left">{serverError}</span>
            </div>
          )}

          <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 'var(--radius-md)',
                backgroundColor: isDeactivating ? 'var(--danger-subtle)' : 'var(--accent-subtle)',
                color: isDeactivating ? 'var(--danger)' : 'var(--accent)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}
              aria-hidden="true"
            >
              <IconAlertTriangle size={18} />
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--stone-950)', fontWeight: 600 }}>
                {isDeactivating
                  ? `¿Deseas desactivar el puesto "${position.name}" (${position.code})?`
                  : `¿Deseas activar el puesto "${position.name}" (${position.code})?`}
              </p>
              <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--stone-600)', lineHeight: 1.4 }}>
                {isDeactivating
                  ? 'El puesto quedará marcado como inactivo.'
                  : 'El puesto quedará marcado como activo.'}
              </p>
            </div>
          </div>
        </div>

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
            type="button"
            className={isDeactivating ? 'modal-btn-danger' : 'positions-btn-primary'}
            onClick={onConfirm}
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <span className="config-btn-spinner" aria-hidden="true" />
                <span>Procesando...</span>
              </>
            ) : (
              <span>{isDeactivating ? 'Confirmar desactivación' : 'Confirmar activación'}</span>
            )}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
