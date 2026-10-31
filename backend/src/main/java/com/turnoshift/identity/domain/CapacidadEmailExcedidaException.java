package com.turnoshift.identity.domain;

/**
 * Excepción de dominio lanzada cuando un campo de correo supera la capacidad
 * física de la columna NVARCHAR(254) en unidades UTF-16.
 *
 * <p>Código de error estable: {@code IDENTITY_EMAIL_CAPACITY_EXCEEDED}.
 * No incluye el valor del correo en el mensaje.
 */
public final class CapacidadEmailExcedidaException extends RuntimeException {

    public static final String CODIGO = "IDENTITY_EMAIL_CAPACITY_EXCEEDED";

    private final String campo;
    private final int longitudActual;
    private final int limiteUtf16;

    public CapacidadEmailExcedidaException(String campo, int longitudActual, int limiteUtf16) {
        super(String.format(
                "[%s] El campo '%s' supera la capacidad física de %d unidades UTF-16 (%d unidades). "
                + "NVARCHAR(%3$d) no equivale al límite RFC 5321 en octetos.",
                CODIGO, campo, limiteUtf16, longitudActual));
        this.campo = campo;
        this.longitudActual = longitudActual;
        this.limiteUtf16 = limiteUtf16;
    }

    public String getCampo() { return campo; }
    public int getLongitudActual() { return longitudActual; }
    public int getLimiteUtf16() { return limiteUtf16; }
}
