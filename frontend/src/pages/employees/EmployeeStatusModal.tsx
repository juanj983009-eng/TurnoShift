/**
 * TurnoShift — Employee Status Modal (Activación y Desactivación con Pre-evaluación)
 * Diálogo accesible para conmutar estado de colaboradores (HU-07).
 */

import { useState, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import type { EmployeeResponse, DeactivationPreviewResponse } from '../../types/employee';
import { employeeApi } from '../../api/employeeApi';
import { ApiProblemError } from '../../types/api';
import { IconClose, IconAlertTriangle, IconRefresh, IconCheck } from '../../components/common/Icons';

interface EmployeeStatusModalProps {
  readonly isOpen: boolean;
  readonly employee: EmployeeResponse | null;
  readonly isSubmitting: boolean;
  readonly serverError: string | null;
  readonly onClose: () => void;
  readonly onConfirm: () => Promise<void>;
}

export function EmployeeStatusModal({
  isOpen,
  employee,
  isSubmitting,
  serverError,
  onClose,
  onConfirm
}: EmployeeStatusModalProps) {
  const isDeactivating = employee?.active ?? false;

  // Estado de evaluación previa de precondiciones para desactivación
  const [isPreviewLoading, setIsPreviewLoading] = useState<boolean>(false);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [previewData, setPreviewData] = useState<DeactivationPreviewResponse | null>(null);

  // Ejecutar evaluación previa al abrir para desactivación
  const runPreview = useCallback(async (empId: string) => {
    setIsPreviewLoading(true);
    setPreviewError(null);
    setPreviewData(null);

    try {
      const result = await employeeApi.previewDeactivation(empId);
      setPreviewData(result);
    } catch (err: unknown) {
      if (err instanceof ApiProblemError) {
        if (err.isNetworkError) {
          setPreviewError('Error de conexión al evaluar precondiciones. Verifica tu red.');
        } else if (err.status === 404) {
          setPreviewError('El colaborador no fue encontrado en el servidor.');
        } else if (err.status === 503) {
          setPreviewError('El servicio de verificación no está disponible temporalmente.');
        } else {
          setPreviewError(err.detail || err.title || 'Error al evaluar precondiciones.');
        }
      } else {
        setPreviewError('Ocurrió un error inesperado al evaluar la desactivación.');
      }
    } finally {
      setIsPreviewLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!isOpen || !employee) {
      setPreviewData(null);
      setPreviewError(null);
      setIsPreviewLoading(false);
      return;
    }

    if (employee.active) {
      runPreview(employee.id);
    }
  }, [isOpen, employee, runPreview]);

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

  if (!isOpen || !employee) return null;

  // Determinar si la acción de confirmar está bloqueada
  const isBlocked = isDeactivating && (isPreviewLoading || !!previewError || previewData?.canDeactivate === false);

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
        aria-labelledby="employee-status-modal-title"
      >
        <div className="modal-header">
          <h2 id="employee-status-modal-title" className="modal-title">
            {isDeactivating ? 'Desactivar colaborador' : 'Activar colaborador'}
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
          {/* Error del servidor durante la mutación final */}
          {serverError && (
            <div className="employees-banner employees-banner--conflict" role="alert">
              <span>{serverError}</span>
            </div>
          )}

          {/* Información del colaborador */}
          <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 'var(--radius-md)',
                backgroundColor: isDeactivating ? '#fee2e2' : 'var(--accent-subtle)',
                color: isDeactivating ? 'var(--danger)' : 'var(--accent)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}
              aria-hidden="true"
            >
              {isDeactivating ? (
                <IconAlertTriangle size={18} />
              ) : (
                <IconCheck size={18} />
              )}
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
              <strong style={{ fontSize: 'var(--text-sm)', color: 'var(--stone-950)' }}>
                {employee.displayName} ({employee.code})
              </strong>
              <span style={{ fontSize: 'var(--text-xs)', color: 'var(--stone-600)' }}>
                {isDeactivating
                  ? 'El colaborador quedará marcado como inactivo.'
                  : 'El colaborador quedará marcado como activo y disponible para asignaciones de turnos.'}
              </span>
            </div>
          </div>

          {/* Evaluación de precondiciones para desactivación */}
          {isDeactivating && (
            <>
              {isPreviewLoading && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', padding: 'var(--space-3) 0' }}>
                  <span className="employees-spinner-large" style={{ width: 18, height: 18, borderWidth: 2 }} aria-hidden="true" />
                  <span style={{ fontSize: 'var(--text-xs)', color: 'var(--stone-600)' }}>
                    Evaluando precondiciones de desactivación...
                  </span>
                </div>
              )}

              {!isPreviewLoading && previewError && (
                <div className="employees-banner employees-banner--conflict" role="alert">
                  <div className="employees-banner__left">
                    <IconAlertTriangle size={16} className="employees-banner__icon" />
                    <span>{previewError}</span>
                  </div>
                  <button
                    type="button"
                    className="employees-banner-btn-reload"
                    onClick={() => runPreview(employee.id)}
                  >
                    <IconRefresh size={12} />
                    <span>Reintentar</span>
                  </button>
                </div>
              )}

              {!isPreviewLoading && previewData && !previewData.canDeactivate && (
                <div className="employee-blockers-card" role="alert">
                  <h3 className="employee-blockers-title">Impedimentos para la desactivación</h3>
                  <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: '#78350f' }}>
                    No es posible desactivar este colaborador debido a las siguientes razones operativas:
                  </p>
                  <ul className="employee-blockers-list">
                    {previewData.blockers.map((blocker, idx) => (
                      <li key={idx}>{blocker}</li>
                    ))}
                  </ul>
                </div>
              )}
            </>
          )}
        </div>

        <div className="modal-footer">
          <button
            type="button"
            className="modal-btn-cancel"
            onClick={onClose}
            disabled={isSubmitting}
          >
            {previewData?.canDeactivate === false ? 'Entendido' : 'Cancelar'}
          </button>

          {/* Botón de confirmación (oculto si hay impedimentos bloqueantes) */}
          {(!isDeactivating || previewData?.canDeactivate !== false) && (
            <button
              type="button"
              className={isDeactivating ? 'modal-btn-danger' : 'modal-btn-primary'}
              onClick={onConfirm}
              disabled={isSubmitting || isBlocked}
            >
              {isSubmitting ? (
                <span>Procesando...</span>
              ) : (
                <span>{isDeactivating ? 'Confirmar desactivación' : 'Confirmar activación'}</span>
              )}
            </button>
          )}
        </div>
      </div>
    </div>,
    document.body
  );
}
