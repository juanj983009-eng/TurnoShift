package com.turnoshift.identity.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de Argon2PasswordService — sin Docker, sin Spring, sin red.
 *
 * <p>Verifica propiedades del hash Argon2id sin imprimir contraseña ni hash.
 * No establece límites de tiempo de CI.
 */
@DisplayName("Argon2PasswordService: propiedades del hash Argon2id")
class Argon2PasswordServiceTest {

    private Argon2PasswordService servicio;

    @BeforeEach
    void configurar() {
        servicio = new Argon2PasswordService();
    }

    @Test
    @DisplayName("Codificar produce un hash no vacío")
    void codificarProduceHashNoVacio() {
        String hash = servicio.codificar("ContrasenaDePrueba-Sintetica-15plus");
        assertThat(hash).isNotBlank();
    }

    @Test
    @DisplayName("El hash no contiene el texto plano")
    void hashNoContieneTextoplano() {
        String contrasena = "TextoPlanoDeContrasenaDePrueba!!";
        String hash = servicio.codificar(contrasena);
        assertThat(hash).doesNotContain(contrasena);
    }

    @Test
    @DisplayName("Contraseña correcta verifica")
    void contrasenaCorrectaVerifica() {
        String c = "ContrasenaDePrueba-Correcta-2026";
        String hash = servicio.codificar(c);
        assertThat(servicio.verificar(c, hash)).isTrue();
    }

    @Test
    @DisplayName("Contraseña incorrecta no verifica")
    void contrasenaIncorrectaNoVerifica() {
        String correcta  = "ContrasenaDePrueba-Correcta-2026";
        String incorrecta = "ContrasenaDePrueba-Incorrecta-2026";
        String hash = servicio.codificar(correcta);
        assertThat(servicio.verificar(incorrecta, hash)).isFalse();
    }

    @Test
    @DisplayName("Dos codificaciones producen hashes distintos (salt aleatorio)")
    void dosHashesDiferentes() {
        String c = "ContrasenaDePrueba-Salt-2026";
        assertThat(servicio.codificar(c)).isNotEqualTo(servicio.codificar(c));
    }

    @Test
    @DisplayName("Ambos hashes verifican con la misma contraseña")
    void ambosHashesVerifican() {
        String c = "ContrasenaDePrueba-BothVerify-2026";
        String h1 = servicio.codificar(c);
        String h2 = servicio.codificar(c);
        assertThat(servicio.verificar(c, h1)).isTrue();
        assertThat(servicio.verificar(c, h2)).isTrue();
    }

    @Test
    @DisplayName("El hash cabe en VARCHAR(512)")
    void hashCabeEnVarchar512() {
        String hash = servicio.codificar("ContrasenaDePrueba-VarcharLen-2026");
        assertThat(hash.length()).isLessThanOrEqualTo(512);
    }

    @Test
    @DisplayName("El hash contiene el identificador de variante Argon2id")
    void hashContieneVarianteId() {
        String hash = servicio.codificar("ContrasenaDePrueba-Variant-2026");
        assertThat(hash).contains("argon2id");
    }
}
