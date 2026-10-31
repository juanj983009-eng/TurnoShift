/**
 * TurnoShift — Position Modal (Alta y Edición)
 * Modal accesible para el registro y actualización de puestos de trabajo (HU-06).
 */

import { useState, useEffect, type FormEvent } from 'react';
import { createPortal } from 'react-dom';
import type { PositionResponse } from '../../types/position';
import { IconClose, IconLock } from '../../components/common/Icons';
import positionArt from '../../assets/illustrations/positions-illustration.png';

interface PositionModalProps {
  readonly isOpen: boolean;
  readonly mode: 'create' | 'edit';
  readonly position: PositionResponse | null;
  readonly isSubmitting: boolean;
  readonly serverError: string | null;
  readonly codeServerError: string | null;
  readonly onClose: () => void;
  readonly onSubmit: (data: { code?: string; name: string }) => Promise<void>;
}

export function PositionModal({
  isOpen,
  mode,
  position,
  isSubmitting,
  serverError,
  codeServerError,
  onClose,
  onSubmit
}: PositionModalProps) {
  const [code, setCode] = useState<string>('');
  const [name, setName] = useState<string>('');
  const [codeClientError, setCodeClientError] = useState<string | null>(null);
  const [nameClientError, setNameClientError] = useState<string | null>(null);

  // Sincronizar datos al abrir o cambiar de modo
  useEffect(() => {
    if (!isOpen) return;

    if (mode === 'edit' && position) {
      setCode(position.code);
      setName(position.name);
    } else {
      setCode('');
      setName('');
    }
    setCodeClientError(null);
    setNameClientError(null);
  }, [isOpen, mode, position]);

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

  if (!isOpen) return null;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;

    let hasError = false;
    setCodeClientError(null);
    setNameClientError(null);

    // Validación de código solo en modo creación
    if (mode === 'create') {
      if (code.length < 2 || code.length > 32) {
        setCodeClientError('El código debe tener entre 2 y 32 caracteres.');
        hasError = true;
      } else if (!/^[A-Z0-9-]+$/.test(code)) {
        setCodeClientError(
          'El código solo puede contener letras mayúsculas, dígitos y guiones (ej. CAJ-01). No se permiten minúsculas.'
        );
        hasError = true;
      }
    }

    // Validación de nombre en ambos modos
    if (name.length < 1 || name.length > 100) {
      setNameClientError('El nombre debe tener entre 1 y 100 caracteres.');
      hasError = true;
    } else if (name.trim().length === 0) {
      setNameClientError('El nombre no puede consistir únicamente en espacios en blanco.');
      hasError = true;
    }

    if (hasError) return;

    // Enviar sin modificar ni recortar silenciosamente los valores
    await onSubmit({
      code: mode === 'create' ? code : undefined,
      name
    });
  };

  const isEdit = mode === 'edit';
  const effectiveCodeError = codeServerError || codeClientError;

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
        className="modal-card catalog-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="position-modal-title"
      >
        <div className="modal-header">
          <img className="catalog-dialog__art" src={positionArt} alt="" />
          <h2 id="position-modal-title" className="modal-title">
            {isEdit ? `Editar puesto: ${position?.name}` : 'Nuevo puesto de trabajo'}
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

        <form className="modal-form" onSubmit={handleSubmit} noValidate>
          <div className="modal-body">
            {/* Mensaje de error general de servidor */}
            {serverError && (
              <div className="config-banner config-banner--conflict" role="alert">
                <span className="config-banner__left">{serverError}</span>
              </div>
            )}

            {/* Campo Código */}
            <div className="config-field">
              <div className="config-field__header">
                <label htmlFor="position-code-input" className="config-field__label">
                  Código identificador
                </label>
                {isEdit && (
                  <span className="config-badge config-badge--lock">
                    <IconLock size={12} />
                    <span>Código inmutable</span>
                  </span>
                )}
              </div>

              {isEdit ? (
                <input
                  id="position-code-input"
                  type="text"
                  className="config-field__input config-field__input--disabled"
                  value={code}
                  readOnly
                  disabled
                />
              ) : (
                <input
                  id="position-code-input"
                  type="text"
                  className={`config-field__input ${effectiveCodeError ? 'config-field__input--error' : ''}`}
                  value={code}
                  onChange={(e) => {
                    setCode(e.target.value);
                    if (codeClientError) setCodeClientError(null);
                  }}
                  placeholder="Ej. MED-URG, CAJ-01"
                  maxLength={32}
                  disabled={isSubmitting}
                  aria-invalid={effectiveCodeError ? 'true' : 'false'}
                  aria-describedby={effectiveCodeError ? 'code-error' : 'code-helper'}
                  autoFocus
                />
              )}

              <p id="code-helper" className="config-field__helper">
                {isEdit
                  ? 'El código del puesto es inmutable tras la creación.'
                  : 'Identificador único (letras mayúsculas, dígitos y guiones). No se convertirá automáticamente.'}
              </p>

              {effectiveCodeError && (
                <p id="code-error" className="config-field__error" role="alert">
                  {effectiveCodeError}
                </p>
              )}
            </div>

            {/* Campo Nombre */}
            <div className="config-field">
              <div className="config-field__header">
                <label htmlFor="position-name-input" className="config-field__label">
                  Nombre descriptivo del puesto
                </label>
              </div>

              <input
                id="position-name-input"
                type="text"
                className={`config-field__input ${nameClientError ? 'config-field__input--error' : ''}`}
                value={name}
                onChange={(e) => {
                  setName(e.target.value);
                  if (nameClientError) setNameClientError(null);
                }}
                placeholder="Ej. Médico de Urgencias, Cajero Principal"
                maxLength={100}
                disabled={isSubmitting}
                aria-invalid={nameClientError ? 'true' : 'false'}
                aria-describedby={nameClientError ? 'name-error' : 'name-helper'}
                autoFocus={isEdit}
              />

              <p id="name-helper" className="config-field__helper">
                Título del puesto visible en cuadrantes de turno y asignaciones.
              </p>

              {nameClientError && (
                <p id="name-error" className="config-field__error" role="alert">
                  {nameClientError}
                </p>
              )}
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
              type="submit"
              className="positions-btn-primary"
              disabled={isSubmitting}
            >
              {isSubmitting ? (
                <>
                  <span className="config-btn-spinner" aria-hidden="true" />
                  <span>Guardando...</span>
                </>
              ) : (
                <span>{isEdit ? 'Guardar cambios' : 'Crear puesto'}</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
}
