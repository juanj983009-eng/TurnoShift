package com.turnoshift.policy.infrastructure.web;

import com.turnoshift.policy.domain.PolicyVersion;

import java.time.Instant;
import java.util.UUID;

public record PolicyResponse(
        UUID id,
        int versionNumber,
        int minimumRestMinutes,
        int weeklyWarningMinutes,
        Instant effectiveAt,
        String changeReason,
        UUID createdByUserId
) {
    public static PolicyResponse from(PolicyVersion version) {
        if (version == null) {
            return null;
        }
        return new PolicyResponse(
                version.id(),
                version.versionNumber(),
                version.thresholds().minimumRestMinutes(),
                version.thresholds().weeklyWarningMinutes(),
                version.effectiveAt(),
                version.changeReason().value(),
                version.createdByUserId()
        );
    }
}
