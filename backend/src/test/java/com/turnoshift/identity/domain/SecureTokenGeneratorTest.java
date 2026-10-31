package com.turnoshift.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: SecureTokenGenerator — generación criptográfica y hash SHA-256")
class SecureTokenGeneratorTest {

    private final SecureTokenGenerator generator = new SecureTokenGenerator();

    @Test
    @DisplayName("Genera secreto Base64 URL-safe sin padding de 43 caracteres (256 bits / 32 bytes)")
    void generaTokenBase64UrlSafeSinPadding() {
        String token = generator.generateRawToken();

        assertThat(token).isNotBlank();
        assertThat(token.length()).isEqualTo(43);
        assertThat(token).doesNotContain("=");
        assertThat(token).doesNotContain("+");
        assertThat(token).doesNotContain("/");

        // 43 caracteres Base64 decodifican a 32 bytes de entropía
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded).hasSize(32);
    }

    @Test
    @DisplayName("Genera tokens unívocos con alta entropía (sin colisiones en muestra)")
    void generaTokensUnivocos() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            boolean added = tokens.add(generator.generateRawToken());
            assertThat(added).as("Cada token generado debe ser único").isTrue();
        }
    }

    @Test
    @DisplayName("Calcula hash SHA-256 de exactamente 32 bytes coincidente con MessageDigest")
    void calculaHashSha256Coincidente() throws Exception {
        String rawToken = generator.generateRawToken();
        byte[] expectedHash = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));

        byte[] calculatedHash = generator.computeSha256(rawToken);

        assertThat(calculatedHash).hasSize(32);
        assertThat(calculatedHash).isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("computeSha256 rechaza entradas nulas o vacías")
    void computeSha256RechazaEntradasInvalidas() {
        assertThatThrownBy(() -> generator.computeSha256(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> generator.computeSha256(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
