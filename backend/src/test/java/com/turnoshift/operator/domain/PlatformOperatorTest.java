package com.turnoshift.operator.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Dominio: PlatformOperator")
class PlatformOperatorTest {

    private static final UUID VALID_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String VALID_EMAIL = "operador@turnoshift.com";
    private static final String VALID_NORM_EMAIL = "operador@turnoshift.com";
    private static final String VALID_NAME = "Operador de Plataforma";
    private static final String VALID_HASH = "$argon2id$v=19$m=65536,t=3,p=1$dummyhashval";
    private static final OffsetDateTime VALID_TIME = OffsetDateTime.of(2026, 9, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    @DisplayName("Construcción exitosa con todos los campos válidos")
    void instanciar_conCamposValidos_construyeCorrectamente() {
        PlatformOperator operator = new PlatformOperator(
                VALID_ID,
                VALID_EMAIL,
                VALID_NORM_EMAIL,
                VALID_NAME,
                VALID_HASH,
                true,
                1L,
                0L,
                VALID_TIME,
                VALID_TIME
        );

        assertThat(operator.id()).isEqualTo(VALID_ID);
        assertThat(operator.email()).isEqualTo(VALID_EMAIL);
        assertThat(operator.normalizedEmail()).isEqualTo(VALID_NORM_EMAIL);
        assertThat(operator.displayName()).isEqualTo(VALID_NAME);
        assertThat(operator.passwordHash()).isEqualTo(VALID_HASH);
        assertThat(operator.isActive()).isTrue();
        assertThat(operator.authVersion()).isEqualTo(1L);
        assertThat(operator.lockVersion()).isZero();
        assertThat(operator.createdAt()).isEqualTo(VALID_TIME);
        assertThat(operator.updatedAt()).isEqualTo(VALID_TIME);
    }

    @Test
    @DisplayName("toString() redacta el passwordHash y el correo para prevenir fugas en logs")
    void toString_noFiltraPasswordHashNiEmail_redactaProtegido() {
        PlatformOperator operator = new PlatformOperator(
                VALID_ID,
                VALID_EMAIL,
                VALID_NORM_EMAIL,
                VALID_NAME,
                VALID_HASH,
                true,
                1L,
                0L,
                VALID_TIME,
                VALID_TIME
        );

        String str = operator.toString();

        assertThat(str)
                .contains("passwordHash=[PROTECTED]")
                .contains("email=[PROTECTED]")
                .contains("normalizedEmail=[PROTECTED]")
                .doesNotContain(VALID_HASH)
                .doesNotContain(VALID_EMAIL)
                .contains(VALID_NAME);
    }

    @Test
    @DisplayName("Campos obligatorios nulos lanzan NullPointerException")
    void validaciones_camposNulos_lanzanNullPointerException() {
        assertThatThrownBy(() -> new PlatformOperator(null, VALID_EMAIL, VALID_NORM_EMAIL, VALID_NAME, VALID_HASH, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");

        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, null, VALID_NORM_EMAIL, VALID_NAME, VALID_HASH, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("email");

        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, null, VALID_NAME, VALID_HASH, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("normalizedEmail");

        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, VALID_NORM_EMAIL, null, VALID_HASH, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("displayName");

        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, VALID_NORM_EMAIL, VALID_NAME, null, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("passwordHash");

        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, VALID_NORM_EMAIL, VALID_NAME, VALID_HASH, true, 1L, 0L, null, VALID_TIME))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdAt");

        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, VALID_NORM_EMAIL, VALID_NAME, VALID_HASH, true, 1L, 0L, VALID_TIME, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("updatedAt");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("Cadenas vacías o en blanco lanzan IllegalArgumentException")
    void validaciones_camposVaciosOBlancos_lanzanIllegalArgumentException(String emptyOrBlank) {
        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, emptyOrBlank, VALID_NORM_EMAIL, VALID_NAME, VALID_HASH, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, emptyOrBlank, VALID_NAME, VALID_HASH, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, VALID_NORM_EMAIL, emptyOrBlank, VALID_HASH, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, VALID_NORM_EMAIL, VALID_NAME, emptyOrBlank, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Longitudes máximas de campos lanzan IllegalArgumentException al exceder")
    void validaciones_longitudesExcedidas_lanzanIllegalArgumentException() {
        String longEmail = "a".repeat(255) + "@turnoshift.com";
        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, longEmail, VALID_NORM_EMAIL, VALID_NAME, VALID_HASH, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, longEmail, VALID_NAME, VALID_HASH, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(IllegalArgumentException.class);

        String longName = "n".repeat(151);
        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, VALID_NORM_EMAIL, longName, VALID_HASH, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(IllegalArgumentException.class);

        String longHash = "h".repeat(513);
        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, VALID_NORM_EMAIL, VALID_NAME, longHash, true, 1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Versiones negativas lanzan IllegalArgumentException")
    void validaciones_versionesNegativas_lanzanIllegalArgumentException() {
        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, VALID_NORM_EMAIL, VALID_NAME, VALID_HASH, true, -1L, 0L, VALID_TIME, VALID_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authVersion");

        assertThatThrownBy(() -> new PlatformOperator(VALID_ID, VALID_EMAIL, VALID_NORM_EMAIL, VALID_NAME, VALID_HASH, true, 1L, -1L, VALID_TIME, VALID_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lockVersion");
    }
}
