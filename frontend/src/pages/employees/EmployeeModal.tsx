/**
 * TurnoShift — Employee Modal (Alta y Edición)
 * Diálogo accesible para el registro y actualización de colaboradores (HU-07).
 */

import { useState, useEffect, useRef, useMemo, type FormEvent } from 'react';
import { createPortal } from 'react-dom';
import type { EmployeeResponse } from '../../types/employee';
import type { PositionResponse } from '../../types/position';
import { IconClose, IconLock } from '../../components/common/Icons';
import teamArt from '../../assets/illustrations/team-workspace.png';

export interface EmployeeModalFormData {
  readonly code?: string;
  readonly displayName?: string;
  readonly positionId?: string;
}

interface EmployeeModalProps {
  readonly isOpen: boolean;
  readonly mode: 'create' | 'edit';
  readonly employee: EmployeeResponse | null;
  readonly positions: PositionResponse[];
  readonly isSubmitting: boolean;
  readonly serverError: string | null;
  readonly codeServerError: string | null;
  readonly onClose: () => void;
  readonly onSubmit: (data: EmployeeModalFormData) => Promise<void>;
}

export function EmployeeModal({
  isOpen,
  mode,
  employee,
  positions,
  isSubmitting,
  serverError,
  codeServerError,
  onClose,
  onSubmit
}: EmployeeModalProps) {
  const [code, setCode] = useState<string>('');
  const [displayName, setDisplayName] = useState<string>('');
  const [positionId, setPositionId] = useState<string>('');

  const [codeClientError, setCodeClientError] = useState<string | null>(null);
  const [nameClientError, setNameClientError] = useState<string | null>(null);
  const [positionClientError, setPositionClientError] = useState<string | null>(null);
  const [generalClientError, setGeneralClientError] = useState<string | null>(null);

  const codeInputRef = useRef<HTMLInputElement>(null);
  const nameInputRef = useRef<HTMLInputElement>(null);

  const isEdit = mode === 'edit';

  // Opciones de puestos para el selector
  const availablePositions = useMemo(() => {
    if (!isEdit || !employee) {
      // En alta: exclusivamente puestos activos
      return positions.filter((p) => p.isActive);
    }

    // En edición:
    // Si el puesto actual del colaborador está inactivo, se incluye como asignación actual.
    // Todas las demás opciones permitidas para cambio deben ser estrictamente activas.
    const currentPos = positions.find((p) => p.id === employee.positionId);
    const activePositions = positions.filter((p) => p.isActive);

    if (currentPos && !currentPos.isActive) {
      return [
        {
          ...currentPos,
          name: `${currentPos.name} (Inactivo - Asignación actual)`
        },
        ...activePositions.filter((p) => p.id !== currentPos.id)
      ];
    }

    return activePositions;
  }, [isEdit, employee, positions]);

  // Sincronizar datos al abrir o cambiar de modo
  useEffect(() => {
    if (!isOpen) return;

    if (isEdit && employee) {
      setCode(employee.code);
      setDisplayName(employee.displayName);
      setPositionId(employee.positionId);
    } else {
      setCode('');
      setDisplayName('');
      setPositionId('');
    }

    setCodeClientError(null);
    setNameClientError(null);
    setPositionClientError(null);
    setGeneralClientError(null);
  }, [isOpen, isEdit, employee]);

  // Foco inicial accesible
  useEffect(() => {
    if (!isOpen) return;

    const timer = setTimeout(() => {
      if (isEdit) {
        nameInputRef.current?.focus();
      } else {
        codeInputRef.current?.focus();
      }
    }, 50);

    return () => clearTimeout(timer);
  }, [isOpen, isEdit]);

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
    setPositionClientError(null);
    setGeneralClientError(null);

    // Validación de código (solo en modo creación)
    if (!isEdit) {
      if (!code || code.trim().length === 0) {
        setCodeClientError('El código del colaborador es obligatorio.');
        hasError = true;
      } else if (code.length < 2 || code.length > 32) {
        setCodeClientError('El código debe tener entre 2 y 32 caracteres.');
        hasError = true;
      } else if (!/^[A-Z0-9-]+$/.test(code)) {
        // No convertir silenciosamente a mayúsculas: validar según contrato
        setCodeClientError(
          'El código solo puede contener letras mayúsculas, dígitos y guiones (ej. EMP-01). No se permiten minúsculas ni espacios.'
        );
        hasError = true;
      }
    }

    // Validación de nombre visible
    if (!displayName || displayName.trim().length === 0) {
      setNameClientError('El nombre visible es obligatorio.');
      hasError = true;
    } else if (displayName.length < 1 || displayName.length > 100) {
      setNameClientError('El nombre debe tener entre 1 y 100 caracteres.');
      hasError = true;
    }

    // Validación de puesto
    if (!positionId) {
      setPositionClientError('Debes seleccionar un puesto de trabajo.');
      hasError = true;
    }

    if (hasError) return;

    if (!isEdit) {
      // Modo Creación: enviar code, displayName y positionId
      await onSubmit({
        code,
        displayName: displayName.trim(),
        positionId
      });
    } else if (employee) {
      // Modo Edición: verificar que haya cambios
      const displayNameChanged = displayName.trim() !== employee.displayName;
      const positionIdChanged = positionId !== employee.positionId;

      if (!displayNameChanged && !positionIdChanged) {
        setGeneralClientError('Debes modificar al menos un campo para guardar cambios.');
        return;
      }

      // No reenviar positionId si no fue cambiado
      const updateData: { displayName?: string; positionId?: string } = {};
      if (displayNameChanged) {
        updateData.displayName = displayName.trim();
      }
      if (positionIdChanged) {
        updateData.positionId = positionId;
      }

      await onSubmit(updateData);
    }
  };

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
        aria-labelledby="employee-modal-title"
      >
        <div className="modal-header">
          <img className="catalog-dialog__art" src={teamArt} alt="" />
          <h2 id="employee-modal-title" className="modal-title">
            {isEdit ? 'Editar colaborador' : 'Nuevo colaborador'}
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
            {/* Error general del servidor o cliente */}
            {(serverError || generalClientError) && (
              <div className="employees-banner employees-banner--conflict" role="alert">
                <span>{serverError || generalClientError}</span>
              </div>
            )}

            {/* Campo Código */}
            <div className="employee-field">
              <div className="employee-field__header">
                <label htmlFor="employee-code-input" className="employee-field__label">
                  Código de colaborador
                </label>
                {isEdit && (
                  <span className="employee-badge-lock">
                    <IconLock size={12} />
                    <span>Inmutable</span>
                  </span>
                )}
              </div>

              {isEdit ? (
                <input
                  id="employee-code-input"
                  type="text"
                  className="employee-field__input employee-field__input--disabled"
                  value={code}
                  readOnly
                  disabled
                />
              ) : (
                <input
                  ref={codeInputRef}
                  id="employee-code-input"
                  type="text"
                  className={`employee-field__input ${
                    effectiveCodeError ? 'employee-field__input--error' : ''
                  }`}
                  value={code}
                  onChange={(e) => {
                    setCode(e.target.value);
                    if (codeClientError) setCodeClientError(null);
                  }}
                  placeholder="Ej. EMP-001"
                  maxLength={32}
                  disabled={isSubmitting}
                  aria-invalid={effectiveCodeError ? 'true' : 'false'}
                  aria-describedby={effectiveCodeError ? 'code-error' : 'code-helper'}
                />
              )}

              <p id="code-helper" className="employee-field__helper">
                {isEdit
                  ? 'El código del colaborador no se puede modificar tras la creación.'
                  : 'Entre 2 y 32 caracteres (mayúsculas, dígitos y guiones). No se convertirá automáticamente.'}
              </p>

              {effectiveCodeError && (
                <p id="code-error" className="employee-field__error" role="alert">
                  {effectiveCodeError}
                </p>
              )}
            </div>

            {/* Campo Nombre Visible */}
            <div className="employee-field">
              <div className="employee-field__header">
                <label htmlFor="employee-name-input" className="employee-field__label">
                  Nombre visible / completo
                </label>
              </div>

              <input
                ref={nameInputRef}
                id="employee-name-input"
                type="text"
                className={`employee-field__input ${
                  nameClientError ? 'employee-field__input--error' : ''
                }`}
                value={displayName}
                onChange={(e) => {
                  setDisplayName(e.target.value);
                  if (nameClientError) setNameClientError(null);
                  if (generalClientError) setGeneralClientError(null);
                }}
                placeholder="Ej. Carlos Mendoza"
                maxLength={100}
                disabled={isSubmitting}
                aria-invalid={nameClientError ? 'true' : 'false'}
                aria-describedby={nameClientError ? 'name-error' : 'name-helper'}
              />

              <p id="name-helper" className="employee-field__helper">
                Nombre que se mostrará en cuadrantes de turnos y directorios operativos.
              </p>

              {nameClientError && (
                <p id="name-error" className="employee-field__error" role="alert">
                  {nameClientError}
                </p>
              )}
            </div>

            {/* Campo Puesto de Trabajo */}
            <div className="employee-field">
              <div className="employee-field__header">
                <label htmlFor="employee-position-select" className="employee-field__label">
                  Puesto de trabajo asignado
                </label>
              </div>

              <select
                id="employee-position-select"
                className={`employee-field__select ${
                  positionClientError ? 'employee-field__select--error' : ''
                }`}
                value={positionId}
                onChange={(e) => {
                  setPositionId(e.target.value);
                  if (positionClientError) setPositionClientError(null);
                  if (generalClientError) setGeneralClientError(null);
                }}
                disabled={isSubmitting}
                aria-invalid={positionClientError ? 'true' : 'false'}
                aria-describedby={positionClientError ? 'pos-error' : 'pos-helper'}
              >
                <option value="">Selecciona un puesto...</option>
                {availablePositions.map((pos) => (
                  <option key={pos.id} value={pos.id}>
                    {pos.name}
                  </option>
                ))}
              </select>

              <p id="pos-helper" className="employee-field__helper">
                {isEdit
                  ? 'Si deseas reasignar el puesto, solo puedes seleccionar puestos actualmente activos.'
                  : 'Solo se muestran puestos activos disponibles en el catálogo.'}
              </p>

              {positionClientError && (
                <p id="pos-error" className="employee-field__error" role="alert">
                  {positionClientError}
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
              className="modal-btn-primary"
              disabled={isSubmitting}
            >
              {isSubmitting ? (
                <span>Guardando...</span>
              ) : (
                <span>{isEdit ? 'Guardar cambios' : 'Registrar colaborador'}</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
}
