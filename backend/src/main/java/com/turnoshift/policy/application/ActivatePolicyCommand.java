package com.turnoshift.policy.application;

import com.turnoshift.policy.domain.PolicyReason;
import com.turnoshift.policy.domain.PolicyThresholds;

import java.util.Objects;
import java.util.UUID;

public record ActivatePolicyCommand(
        UUID organizationId,
        UUID actorUserId,
        UUID expectedCurrentPolicyVersionId,
        PolicyThresholds proposedThresholds,
        String validationFingerprint,
        PolicyReason changeReason
) {
    public ActivatePolicyCommand {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");
        Objects.requireNonNull(proposedThresholds, "proposedThresholds no puede ser nulo");
        Objects.requireNonNull(validationFingerprint, "validationFingerprint no puede ser nulo");
        Objects.requireNonNull(changeReason, "changeReason no puede ser nulo");
    }
}
