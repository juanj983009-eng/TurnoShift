package com.turnoshift.identity.infrastructure.security;

import com.password4j.Argon2Function;
import com.password4j.types.Argon2;
import com.turnoshift.identity.application.PasswordVerifier;
import org.springframework.security.crypto.password4j.Argon2Password4jPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Servicio de codificación de contraseñas basado en Argon2id.
 *
 * <p><b>Implementación:</b> {@link Argon2Password4jPasswordEncoder} de
 * Spring Security 7 / Password4j 1.8.4. Envoltura thread-safe sobre
 * la función Argon2 de Password4j.
 *
 * <p>Implementa el puerto {@link PasswordVerifier} para verificación de contraseñas.
 *
 * <p><b>Parámetros candidatos (a verificar con benchmark en hardware objetivo):</b>
 * <ul>
 *   <li>Memoria: {@value #MEMORIA_KIB} KiB (128 MiB)</li>
 *   <li>Iteraciones: {@value #ITERACIONES}</li>
 *   <li>Paralelismo: {@value #PARALELISMO}</li>
 *   <li>Longitud del hash: {@value #LONGITUD_HASH_BYTES} bytes</li>
 *   <li>Variante: Argon2<b>id</b></li>
 * </ul>
 *
 * <p>Candidato 1 (64 MiB, 3 iteraciones) desechado: mediana ~257 ms, por debajo de 500 ms.
 *
 * <p>Los parámetros y el salt aleatorio se incrustan en el hash resultante
 * (formato PHC). No se almacenan ni hash ni salt de prueba en migraciones
 * ni documentación.
 *
 * <p><b>Sin cadena de filtros HTTP:</b> este servicio no requiere
 * spring-boot-starter-security. Solo usa spring-security-crypto.
 *
 * <p><b>Pendiente:</b> repetir benchmark en hardware de producción (DEC-08-ARGON2ID).
 */
public final class Argon2PasswordService implements PasswordVerifier {

    /** Memoria en KiB. Candidato 2 (128 MiB) elegido: mediana ~500 ms. */
    public static final int MEMORIA_KIB = 131072;

    /** Número de iteraciones. Candidato: 3. */
    public static final int ITERACIONES = 3;

    /** Grado de paralelismo (hilos/carriles). Candidato: 1. */
    public static final int PARALELISMO = 1;

    /** Longitud del hash de salida en bytes. */
    public static final int LONGITUD_HASH_BYTES = 32;

    private final PasswordEncoder encoder;

    /**
     * Construye el servicio con los parámetros candidatos definidos en esta clase.
     * Los parámetros se incrustan en cada hash junto con el salt aleatorio.
     */
    public Argon2PasswordService() {
        Argon2Function funcion = Argon2Function.getInstance(
                MEMORIA_KIB, ITERACIONES, PARALELISMO,
                LONGITUD_HASH_BYTES, Argon2.ID);
        this.encoder = new Argon2Password4jPasswordEncoder(funcion);
    }

    /**
     * Codifica una contraseña produciendo un hash Argon2id con salt aleatorio.
     * Dos llamadas con la misma contraseña producen hashes distintos.
     *
     * @param contrasenaPreparada contraseña normalizada (NFC), sin recortar espacios
     * @return hash en formato PHC que incluye variante, parámetros y salt
     */
    public String codificar(String contrasenaPreparada) {
        return encoder.encode(contrasenaPreparada);
    }

    /**
     * Verifica si una contraseña en texto plano corresponde a un hash almacenado.
     *
     * @param contrasenaPreparada contraseña normalizada (NFC)
     * @param hashAlmacenado      hash tal como fue almacenado en base de datos
     * @return {@code true} si la contraseña es correcta
     */
    public boolean verificar(String contrasenaPreparada, String hashAlmacenado) {
        return encoder.matches(contrasenaPreparada, hashAlmacenado);
    }

    @Override
    public boolean matches(String preparedPassword, String storedHash) {
        return verificar(preparedPassword, storedHash);
    }
}
