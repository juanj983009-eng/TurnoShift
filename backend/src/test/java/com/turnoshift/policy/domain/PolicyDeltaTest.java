package com.turnoshift.policy.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit: PolicyDelta")
class PolicyDeltaTest {

    @Test
    @DisplayName("Retorna null cuando no existe política previa (Optional.empty)")
    void retornaNullSinPoliticaPrevia() {
        PolicyThresholds proposed = new PolicyThresholds(660, 2400);
        PolicyDelta delta = PolicyDelta.calculate(Optional.empty(), proposed);
        assertThat(delta).isNull();
    }

    @Test
    @DisplayName("Calcula deltas positivos, negativos y nulos exactamente cuando hay política previa")
    void calculaDeltasExactamente() {
        PolicyThresholds current = new PolicyThresholds(660, 2400);

        // Caso 1: Aumento de descanso mínimo (+60), aviso semanal idéntico (0)
        PolicyThresholds prop1 = new PolicyThresholds(720, 2400);
        PolicyDelta delta1 = PolicyDelta.calculate(Optional.of(current), prop1);
        assertThat(delta1).isNotNull();
        assertThat(delta1.minimumRestMinutesDelta()).isEqualTo(60);
        assertThat(delta1.weeklyWarningMinutesDelta()).isZero();

        // Caso 2: Reducción de descanso (-60), reducción de aviso semanal (-400)
        PolicyThresholds prop2 = new PolicyThresholds(600, 2000);
        PolicyDelta delta2 = PolicyDelta.calculate(Optional.of(current), prop2);
        assertThat(delta2).isNotNull();
        assertThat(delta2.minimumRestMinutesDelta()).isEqualTo(-60);
        assertThat(delta2.weeklyWarningMinutesDelta()).isEqualTo(-400);

        // Caso 3: Valores idénticos (0, 0)
        PolicyThresholds prop3 = new PolicyThresholds(660, 2400);
        PolicyDelta delta3 = PolicyDelta.calculate(Optional.of(current), prop3);
        assertThat(delta3).isNotNull();
        assertThat(delta3.minimumRestMinutesDelta()).isZero();
        assertThat(delta3.weeklyWarningMinutesDelta()).isZero();
    }
}
