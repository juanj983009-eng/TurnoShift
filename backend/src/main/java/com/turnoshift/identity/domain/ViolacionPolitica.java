package com.turnoshift.identity.domain;

/**
 * Códigos estables de violación de política de contraseña.
 *
 * <p>Cada constante tiene un código de error estable utilizable en
 * respuestas HTTP (cuando corresponda) sin exponer detalles internos.
 */
public enum ViolacionPolitica {

    /** La contraseña está vacía o es nula. */
    CONTRASENA_VACIA("IDENTITY_PASSWORD_EMPTY"),

    /** Menos de {@link PasswordPolicy#MIN_CODE_POINTS} puntos de código. */
    DEMASIADO_CORTA("IDENTITY_PASSWORD_TOO_SHORT"),

    /** Más de {@link PasswordPolicy#MAX_CODE_POINTS} puntos de código. */
    DEMASIADO_LARGA("IDENTITY_PASSWORD_TOO_LONG"),

    /** Contraseña presente en la lista de contraseñas bloqueadas. */
    CONTRASENA_BLOQUEADA("IDENTITY_PASSWORD_BLOCKED");

    private final String codigo;

    ViolacionPolitica(String codigo) {
        this.codigo = codigo;
    }

    /** @return código estable utilizable como error en respuestas HTTP */
    public String getCodigo() {
        return codigo;
    }
}
