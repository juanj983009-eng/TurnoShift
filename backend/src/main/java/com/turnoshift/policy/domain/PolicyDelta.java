package com.turnoshift.policy.domain;

import java.util.Optional;

public record PolicyDelta(Integer minimumRestMinutesDelta, Integer weeklyWarningMinutesDelta) {

    public static PolicyDelta calculate(Optional<PolicyThresholds> currentThresholds, PolicyThresholds proposed) {
        if (currentThresholds == null || currentThresholds.isEmpty()) {
            return null;
        }
        PolicyThresholds current = currentThresholds.get();
        return new PolicyDelta(
                proposed.minimumRestMinutes() - current.minimumRestMinutes(),
                proposed.weeklyWarningMinutes() - current.weeklyWarningMinutes()
        );
    }
}
