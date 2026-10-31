/**
 * TurnoShift — Idempotency Utilities
 * Generación de claves de idempotencia estándar UUID v4 sin dependencias externas.
 */

/**
 * Genera una clave de idempotencia UUID v4 utilizando la Web Cryptography API nativa (crypto.randomUUID).
 * Se utiliza como valor de la cabecera HTTP 'Idempotency-Key' en operaciones mutadoras.
 */
export function generateIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }

  // Fallback seguro compatible con RFC 4122 v4 en caso de entornos sin crypto.randomUUID
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
