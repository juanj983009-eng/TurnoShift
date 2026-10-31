package com.turnoshift.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de EmailNormalizer — sin Docker, sin Spring, sin red.
 */
@DisplayName("EmailNormalizer: normalización y validación de capacidad UTF-16")
class EmailNormalizerTest {

    @Test
    @DisplayName("Recorta espacios ASCII exteriores")
    void recortaEspaciosAsciiExteriores() {
        assertThat(EmailNormalizer.normalizar("  user@example.com  "))
                .isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("Recorta espacio no separable (U+00A0 NBSP) exterior")
    void recortaEspacioNoSeparableExterior() {
        assertThat(EmailNormalizer.normalizar("\u00A0user@example.com\u00A0"))
                .isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("Convierte a minúsculas con Locale.ROOT")
    void convierteMinusculasLocaleRoot() {
        assertThat(EmailNormalizer.normalizar("USER@EXAMPLE.COM"))
                .isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("Locale turco: 'I' normaliza a 'i' (no a 'ı' turco) con Locale.ROOT")
    void localeTurcoNormalizaCorrectamente() {
        String resultado = EmailNormalizer.normalizar("ISTANBUL@EXAMPLE.COM");
        assertThat(resultado)
                .isEqualTo("istanbul@example.com")
                .doesNotContain("\u0131");
    }

    @Test
    @DisplayName("NFD + recomposición NFC produce el mismo resultado que NFC directo")
    void aplicaNFC() {
        String nfd = "\u0065\u0301@example.com";
        String nfc = "\u00E9@example.com";
        assertThat(EmailNormalizer.normalizar(nfd))
                .isEqualTo(EmailNormalizer.normalizar(nfc))
                .isEqualTo("\u00E9@example.com");
    }

    @Test
    @DisplayName("No elimina puntos internos")
    void noEliminaPuntosInternos() {
        assertThat(EmailNormalizer.normalizar("first.last@example.com"))
                .isEqualTo("first.last@example.com");
    }

    @Test
    @DisplayName("No elimina ni interpreta sufijos +")
    void noEliminaSufijosPlus() {
        assertThat(EmailNormalizer.normalizar("user+tag@example.com"))
                .isEqualTo("user+tag@example.com");
    }

    @Test
    @DisplayName("No modifica espacios internos")
    void conservaEspaciosInternos() {
        assertThat(EmailNormalizer.normalizar("  a b@example.com  "))
                .isEqualTo("a b@example.com");
    }

    @Test
    @DisplayName("Carácter fuera del BMP (emoji U+1F600) se conserva en el resultado")
    void caracterFueraBMP() {
        String emoji = "\uD83D\uDE00";
        String correo = emoji + "@example.com";
        String normalizado = EmailNormalizer.normalizar(correo);
        assertThat(normalizado).contains(emoji);
    }

    @Test
    @DisplayName("Cadena de exactamente 254 unidades UTF-16: válida")
    void cadena254UnidadesEsValida() {
        String valor = "x".repeat(254);
        assertThat(valor).hasSize(254);
        EmailNormalizer.validarCapacidadUtf16(valor, "email");
    }

    @Test
    @DisplayName("Cadena de 255 unidades UTF-16: lanza CapacidadEmailExcedidaException")
    void cadena255UnidadesLanzaExcepcion() {
        String excede = "x".repeat(255);
        assertThatThrownBy(() -> EmailNormalizer.validarCapacidadUtf16(excede, "email"))
                .isInstanceOf(CapacidadEmailExcedidaException.class)
                .hasMessageContaining(CapacidadEmailExcedidaException.CODIGO)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("Emoji (2 unidades UTF-16) × 127 = 254 unidades: válido")
    void caracterFueraBMPCuentaDobleHaciaLimite() {
        String emoji = "\uD83D\uDE00";
        String limite = emoji.repeat(127);
        assertThat(limite.length()).isEqualTo(254);
        EmailNormalizer.validarCapacidadUtf16(limite, "email");

        String superaLimite = emoji.repeat(128);
        assertThatThrownBy(() -> EmailNormalizer.validarCapacidadUtf16(superaLimite, "email"))
                .isInstanceOf(CapacidadEmailExcedidaException.class);
    }

    @Test
    @DisplayName("null en validarCapacidadUtf16 no lanza excepción")
    void nullEsPermitido() {
        EmailNormalizer.validarCapacidadUtf16(null, "email");
    }

    @Test
    @DisplayName("normalizar(null) lanza IllegalArgumentException")
    void normalizarNullLanzaExcepcion() {
        assertThatThrownBy(() -> EmailNormalizer.normalizar(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
