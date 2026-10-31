package com.turnoshift.policy.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: PolicyReason")
class PolicyReasonTest {

    @Test
    @DisplayName("Crea correctamente con motivos válidos y conserva el texto con fidelidad")
    void creacionValida() {
        String texto = "Configuración inicial de prueba";
        PolicyReason reason = new PolicyReason(texto);
        assertThat(reason.value()).isEqualTo(texto);

        // Motivo con espacios internos y acentos/Unicode
        String unicodeTexto = "Ajuste de jornada según acuerdo colectivo — 2026";
        PolicyReason unicodeReason = new PolicyReason(unicodeTexto);
        assertThat(unicodeReason.value()).isEqualTo(unicodeTexto);

        // Motivo que contiene caracteres no espaciales con espacios Unicode
        String mixto = "\u00A0Ajuste con NBSP\u3000";
        PolicyReason mixtoReason = new PolicyReason(mixto);
        assertThat(mixtoReason.value()).isEqualTo(mixto);
    }

    @Test
    @DisplayName("Rechaza motivo nulo con REASON_REQUIRED")
    void rechazaNulo() {
        assertThatThrownBy(() -> new PolicyReason(null))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("REASON_REQUIRED");
                    assertThat(pve.getMessage()).contains("obligatorio");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "\t\n\r",
            "\u00A0",              // NBSP (Non-breaking space)
            "\u00A0\u00A0\u00A0",
            "\u2009",              // Thin space
            "\u2009\u2009",
            "\u3000",              // Ideographic space
            " \u00A0 \t \u2009 \u3000 "
    })
    @DisplayName("Rechaza motivo vacío o compuesto exclusivamente por espacios ASCII y Unicode con REASON_REQUIRED")
    void rechazaSoloEspacios(String espacios) {
        assertThatThrownBy(() -> new PolicyReason(espacios))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("REASON_REQUIRED");
                    assertThat(pve.getMessage()).contains("espacios");
                });
    }

    @Test
    @DisplayName("Acepta longitud exactamente de 500 unidades UTF-16")
    void acepta500UnidadesUtf16() {
        String exacto500 = "A".repeat(500);
        PolicyReason reason = new PolicyReason(exacto500);
        assertThat(reason.value()).hasSize(500);
    }

    @Test
    @DisplayName("Rechaza longitud superior a 500 unidades UTF-16")
    void rechazaMayorA500UnidadesUtf16() {
        String excede500 = "A".repeat(501);
        assertThatThrownBy(() -> new PolicyReason(excede500))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("REASON_REQUIRED");
                    assertThat(pve.getMessage()).contains("500");
                });
    }

    @Test
    @DisplayName("Calcula correctamente límite de 500 unidades UTF-16 con emojis (2 unidades UTF-16 por emoji)")
    void calculaLimiteUtf16ConEmojis() {
        // Cada emoji "😀" (\uD83D\uDE00) ocupa 2 caracteres/unidades UTF-16 en Java
        String emoji = "😀";
        assertThat(emoji.length()).isEqualTo(2);

        // 250 emojis = 500 unidades UTF-16 -> Aceptado
        String exacto500Emojis = emoji.repeat(250);
        assertThat(exacto500Emojis.length()).isEqualTo(500);
        PolicyReason reason = new PolicyReason(exacto500Emojis);
        assertThat(reason.value()).hasSize(500);

        // 251 emojis = 502 unidades UTF-16 -> Rechazado
        String excede500Emojis = emoji.repeat(251);
        assertThat(excede500Emojis.length()).isEqualTo(502);
        assertThatThrownBy(() -> new PolicyReason(excede500Emojis))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("REASON_REQUIRED");
                    assertThat(pve.getMessage()).contains("500");
                });
    }
}
