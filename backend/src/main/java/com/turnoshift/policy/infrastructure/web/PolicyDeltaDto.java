package com.turnoshift.policy.infrastructure.web;

import com.turnoshift.policy.domain.PolicyDelta;

public record PolicyDeltaDto(Integer minimumRestMinutesDelta, Integer weeklyWarningMinutesDelta) {
    public static PolicyDeltaDto from(PolicyDelta delta) {
        if (delta == null) {
            return null;
        }
        return new PolicyDeltaDto(delta.minimumRestMinutesDelta(), delta.weeklyWarningMinutesDelta());
    }
}
