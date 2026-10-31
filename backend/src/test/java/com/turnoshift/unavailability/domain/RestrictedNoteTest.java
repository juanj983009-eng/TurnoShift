package com.turnoshift.unavailability.domain;

import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestrictedNoteTest {

    @Test
    @DisplayName("Preserva contenido válido exactamente sin transformación ni recortar espacios")
    void preservaContenidoValidoSinTransformacion() {
        String textoOriginal = "  Cita con especialista médico  ";
        RestrictedNote note = RestrictedNote.of(textoOriginal);
        assertThat(note.value()).isEqualTo(textoOriginal);
    }

    @Test
    @DisplayName("Rechaza valor nulo")
    void rechazaNull() {
        assertThatThrownBy(() -> RestrictedNote.of(null))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("no puede ser nula");
    }

    @Test
    @DisplayName("Rechaza cadena vacía")
    void rechazaCadenaVacia() {
        assertThatThrownBy(() -> RestrictedNote.of(""))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("no puede estar vacía");
    }

    @Test
    @DisplayName("Rechaza cadena con solo espacios normales")
    void rechazaEspaciosNormales() {
        assertThatThrownBy(() -> RestrictedNote.of("   \t\n  "))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("no puede estar vacía");
    }

    @Test
    @DisplayName("Rechaza cadena que contiene únicamente NBSP (\\u00A0)")
    void rechazaSoloNbsp() {
        assertThatThrownBy(() -> RestrictedNote.of("\u00A0\u00A0"))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("no puede estar vacía");
    }

    @Test
    @DisplayName("Rechaza mezcla de espacios Unicode")
    void rechazaMezclaEspaciosUnicode() {
        // Mezcla de NBSP (\u00A0), espacio em (\u2003), espacio en (\u2002), tabulaciones y saltos de línea
        assertThatThrownBy(() -> RestrictedNote.of("\u00A0 \t\u2003\n\u2002"))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("no puede estar vacía");
    }

    @Test
    @DisplayName("Rechaza nota que supera el límite de 500 unidades UTF-16")
    void rechazaSupera500UnidadesUtf16() {
        String textoLargo = "a".repeat(501);
        assertThatThrownBy(() -> RestrictedNote.of(textoLargo))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("Acepta nota en el límite exacto de 500 unidades UTF-16")
    void aceptaLimite500UnidadesUtf16() {
        String textoLimite = "b".repeat(500);
        RestrictedNote note = RestrictedNote.of(textoLimite);
        assertThat(note.value()).hasSize(500);
    }
}
