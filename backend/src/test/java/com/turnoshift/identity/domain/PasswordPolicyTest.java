package com.turnoshift.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de PasswordPolicy — sin Docker, sin Spring, sin red.
 */
@DisplayName("PasswordPolicy: validación de contraseña (NIST SP 800-63B)")
class PasswordPolicyTest {

    private static final PasswordBlocklist FAKE_BLOCKLIST =
            new FakeBlocklist(Set.of("password12345678", "contraseña123456", "turnoshift2026!!"));

    @Test
    @DisplayName("Contraseña de exactamente 15 puntos de código: válida")
    void contrasena15PuntosDeCodigo() {
        String c = "a".repeat(15);
        assertThat(c.codePointCount(0, c.length())).isEqualTo(15);
        assertThat(PasswordPolicy.validar(c, FAKE_BLOCKLIST)).isEmpty();
    }

    @Test
    @DisplayName("Contraseña de 128 puntos de código: válida")
    void contrasena128PuntosDeCodigo() {
        assertThat(PasswordPolicy.validar("x".repeat(128), FAKE_BLOCKLIST)).isEmpty();
    }

    @Test
    @DisplayName("Contraseña con espacios internos: válida (no se recortan)")
    void contrasenaConEspaciosInternos() {
        String c = "abc de fgh ij klm";
        assertThat(PasswordPolicy.validar(c, FAKE_BLOCKLIST)).isEmpty();
    }

    @Test
    @DisplayName("8 emojis = 8 puntos de código = 16 unidades UTF-16: inválida (< 15 code points)")
    void medidaEnPuntosDeCodigo() {
        String emoji = "\uD83D\uDE00";
        String c = emoji.repeat(8);
        assertThat(c.length()).isEqualTo(16);
        assertThat(c.codePointCount(0, c.length())).isEqualTo(8);
        assertThat(PasswordPolicy.validar(c, FAKE_BLOCKLIST))
                .contains(ViolacionPolitica.DEMASIADO_CORTA);
    }

    @Test
    @DisplayName("15 emojis = 15 puntos de código = 30 unidades UTF-16: válida")
    void contrasena15Emojis() {
        String emoji = "\uD83D\uDE00";
        String c = emoji.repeat(15);
        assertThat(c.codePointCount(0, c.length())).isEqualTo(15);
        assertThat(PasswordPolicy.validar(c, FAKE_BLOCKLIST)).isEmpty();
    }

    @Test
    @DisplayName("NFD normalizado a NFC antes de medir: 14 é-NFD → 14 é-NFC → inválida")
    void nfcAntesdeMedir() {
        String eNFD = "\u0065\u0301"; // 2 code points en NFD
        String c = eNFD.repeat(14);  // 28 code points NFD, 14 NFC
        String nfc = Normalizer.normalize(c, Normalizer.Form.NFC);
        assertThat(nfc.codePointCount(0, nfc.length())).isEqualTo(14);
        assertThat(PasswordPolicy.validar(c, FAKE_BLOCKLIST))
                .contains(ViolacionPolitica.DEMASIADO_CORTA);
    }

    @Test
    @DisplayName("Contraseña nula: CONTRASENA_VACIA")
    void contrasenaNula() {
        assertThat(PasswordPolicy.validar(null, FAKE_BLOCKLIST))
                .containsExactly(ViolacionPolitica.CONTRASENA_VACIA);
    }

    @Test
    @DisplayName("Contraseña vacía: CONTRASENA_VACIA")
    void contrasenaVacia() {
        assertThat(PasswordPolicy.validar("", FAKE_BLOCKLIST))
                .containsExactly(ViolacionPolitica.CONTRASENA_VACIA);
    }

    @Test
    @DisplayName("14 puntos de código: DEMASIADO_CORTA")
    void contrasena14Puntos() {
        assertThat(PasswordPolicy.validar("a".repeat(14), FAKE_BLOCKLIST))
                .containsExactly(ViolacionPolitica.DEMASIADO_CORTA);
    }

    @Test
    @DisplayName("129 puntos de código: DEMASIADO_LARGA")
    void contrasena129Puntos() {
        assertThat(PasswordPolicy.validar("b".repeat(129), FAKE_BLOCKLIST))
                .containsExactly(ViolacionPolitica.DEMASIADO_LARGA);
    }

    @Test
    @DisplayName("Contraseña en blocklist: CONTRASENA_BLOQUEADA")
    void contrasenaEnBlocklist() {
        assertThat(PasswordPolicy.validar("password12345678", FAKE_BLOCKLIST))
                .containsExactly(ViolacionPolitica.CONTRASENA_BLOQUEADA);
    }

    @Test
    @DisplayName("Otra entrada del blocklist: CONTRASENA_BLOQUEADA")
    void contrasenaEnBlocklistOtraEntrada() {
        assertThat(PasswordPolicy.validar("turnoshift2026!!", FAKE_BLOCKLIST))
                .containsExactly(ViolacionPolitica.CONTRASENA_BLOQUEADA);
    }

    @Test
    @DisplayName("Blocklist nulo: IllegalStateException con código IDENTITY_BLOCKLIST_NOT_PROVIDED")
    void blocklistNuloLanzaIllegalState() {
        assertThatThrownBy(() -> PasswordPolicy.validar("validaContrasena123456", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_BLOCKLIST_NOT_PROVIDED");
    }

    @Test
    @DisplayName("Blocklist no disponible: propaga PasswordBlocklistUnavailableException fail-closed")
    void blocklistNoDisponible_propagaExcepcion() {
        PasswordBlocklist failingBlocklist = contrasena -> {
            throw new PasswordBlocklistUnavailableException();
        };

        assertThatThrownBy(() -> PasswordPolicy.validar("validaContrasena123456", failingBlocklist))
                .isInstanceOf(PasswordBlocklistUnavailableException.class)
                .hasMessageContaining("[IDENTITY_BLOCKLIST_UNAVAILABLE]");
    }

    @Test
    @DisplayName("Contraseña con violación estructural no invoca blocklist (evita llamadas innecesarias)")
    void contrasenaInvalida_noInvocaBlocklist() {
        PasswordBlocklist shouldNotBeCalled = contrasena -> {
            throw new AssertionError("La lista de bloqueo no debió ser consultada para contraseña inválida");
        };

        List<ViolacionPolitica> violaciones = PasswordPolicy.validar("corta", shouldNotBeCalled);
        assertThat(violaciones).containsExactly(ViolacionPolitica.DEMASIADO_CORTA);
    }
}
