package com.turnoshift.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Generador criptográfico de tokens y cálculo determinista de resumen SHA-256.
 *
 * <p>El token raw contiene al menos 256 bits de entropía generados con {@link SecureRandom},
 * codificados en Base64 URL-safe sin relleno (padding), resultando en una cadena de 43 caracteres.
 *
 * <p>El hash SHA-256 (32 bytes) se utiliza para almacenamiento y búsquedas en la tabla
 * {@code password_reset_tokens.token_hash BINARY(32)}. El token original nunca se persiste.
 */
public class SecureTokenGenerator {

    private static final int TOKEN_ENTROPY_BYTES = 32;
    private final SecureRandom secureRandom;

    public SecureTokenGenerator() {
        this.secureRandom = new SecureRandom();
    }

    public SecureTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom no puede ser nulo");
    }

    /**
     * Genera un token aleatorio con 256 bits de entropía codificado en Base64 URL-safe sin relleno.
     *
     * @return cadena de 43 caracteres Base64 URL-safe.
     */
    public String generateRawToken() {
        byte[] entropy = new byte[TOKEN_ENTROPY_BYTES];
        secureRandom.nextBytes(entropy);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }

    /**
     * Calcula el resumen SHA-256 del token en texto plano.
     *
     * @param rawToken token en texto plano no nulo y no vacío.
     * @return arreglo de 32 bytes con el hash SHA-256.
     */
    public byte[] computeSha256(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken no puede ser nulo");
        if (rawToken.isEmpty()) {
            throw new IllegalArgumentException("rawToken no puede ser vacío");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en el entorno", e);
        }
    }
}
