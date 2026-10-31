package com.turnoshift.policy.application;

import com.turnoshift.policy.domain.PolicyDelta;
import com.turnoshift.policy.domain.PolicyThresholds;
import com.turnoshift.policy.domain.PolicyVersion;

import java.util.List;

public record PolicyPreviewResult(
        PolicyVersion currentPolicy,
        PolicyThresholds proposedValues,
        PolicyDelta delta,
        boolean canProceed,
        List<String> blockers,
        List<String> warnings,
        String validationFingerprint,
        boolean reasonRequired
) {}
