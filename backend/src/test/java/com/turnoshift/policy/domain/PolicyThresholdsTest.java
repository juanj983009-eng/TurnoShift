package com.turnoshift.policy.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: PolicyThresholds")
class PolicyThresholdsTest {

    @Test
    @DisplayName("Crea correctamente con valores válidos por defecto (660, 2400) y extremos")
    void creacionValida() {
        PolicyThresholds thresholds = new PolicyThresholds(660, 2400);
        assertThat(thresholds.minimumRestMinutes()).isEqualTo(660);
        assertThat(thresholds.weeklyWarningMinutes()).isEqualTo(2400);

        PolicyThresholds extremosMin = new PolicyThresholds(1, 1);
        assertThat(extremosMin.minimumRestMinutes()).isEqualTo(1);
        assertThat(extremosMin.weeklyWarningMinutes()).isEqualTo(1);

        PolicyThresholds extremosMax = new PolicyThresholds(1440, 10080);
        assertThat(extremosMax.minimumRestMinutes()).isEqualTo(1440);
        assertThat(extremosMax.weeklyWarningMinutes()).isEqualTo(10080);
    }

    @ParameterizedTest
    @ValueSource(ints = { -100, -1, 0, 1441, 2000 })
    @DisplayName("Rechaza minimumRestMinutes fuera de rango [1, 1440]")
    void rechazaMinimumRestFueraDeRango(int minRest) {
        assertThatThrownBy(() -> new PolicyThresholds(minRest, 2400))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("VALIDATION_FAILED");
                    assertThat(pve.getMessage()).contains("descanso mínimo");
                });
    }

    @ParameterizedTest
    @ValueSource(ints = { -100, -1, 0, 10081, 20000 })
    @DisplayName("Rechaza weeklyWarningMinutes fuera de rango [1, 10080]")
    void rechazaWeeklyWarningFueraDeRango(int weeklyWarning) {
        assertThatThrownBy(() -> new PolicyThresholds(660, weeklyWarning))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("VALIDATION_FAILED");
                    assertThat(pve.getMessage()).contains("aviso semanal");
                });
    }
}
