package com.turnoshift.identity.domain;

/**
 * Excepción técnica lanzada cuando el repositorio de candidatos no puede
 * ejecutar la consulta por un error de acceso a datos (caída de base de datos,
 * timeout, error de red, etc.).
 *
 * <p>Esta excepción es conceptualmente distinta de "usuario no encontrado":
 * un resultado vacío significa que el candidato no existe; esta excepción
 * significa que la búsqueda no pudo realizarse.
 *
 * <p><b>Seguridad:</b> el mensaje nunca incluye SQL, credenciales, hashes
 * ni detalles internos de la causa. La causa original puede envolvirse para
 * logging, pero debe ser filtrada antes de cualquier respuesta HTTP.
 *
 * <p><b>Capa HTTP (pendiente):</b> cuando exista el controlador de login,
 * esta excepción debe convertirse en 503 Service Unavailable, nunca en 401.
 *
 * <p>Sin dependencias de Spring, JDBC ni HTTP. Dominio puro.
 */
public final class AuthenticationDataAccessException extends RuntimeException {

    /**
     * @param message descripción técnica segura (sin datos de usuario, SQL ni hash)
     * @param cause   causa original para logging interno
     */
    public AuthenticationDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
