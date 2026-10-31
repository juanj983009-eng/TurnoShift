/**
 * TurnoShift — Types: Configuración Organizacional y Sede Activa (HU-05)
 * Contratos alineados estrictamente con OpenAPI 3.1 (v0.2.4-i2.4).
 */

/**
 * Vista de lectura de la configuración general de la organización.
 * Nota: timezoneId es de solo lectura en I2.1 para contextualizar operaciones;
 * su edición se habilitará en el subincremento I3.
 */
export interface OrganizationView {
  readonly id: string;
  readonly displayName: string;
  readonly timezoneId: string;
  readonly lockVersion: number;
}

/**
 * Vista de lectura de la sede activa única de la organización en el MVP.
 * Nota: code es inmutable tras la creación de la sede.
 */
export interface ActiveSiteView {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly lockVersion: number;
}

/**
 * Respuesta combinada de configuración organizacional y sede activa.
 * GET /api/v1/organization/configuration
 */
export interface OrganizationConfigurationResponse {
  readonly organization: OrganizationView;
  readonly activeSite: ActiveSiteView;
}

/**
 * Petición para actualizar el nombre visible de la organización.
 * PATCH /api/v1/organization/configuration/display-name
 */
export interface UpdateOrganizationDisplayNameRequest {
  readonly displayName: string;
  readonly expectedLockVersion: number;
}

/**
 * Petición para actualizar el nombre descriptivo de la sede activa única.
 * PATCH /api/v1/organization/configuration/active-site/name
 */
export interface UpdateActiveSiteNameRequest {
  readonly name: string;
  readonly expectedLockVersion: number;
}
