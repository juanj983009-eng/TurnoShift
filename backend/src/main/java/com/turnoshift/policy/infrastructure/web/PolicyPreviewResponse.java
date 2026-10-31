package com.turnoshift.policy.infrastructure.web;

import com.turnoshift.policy.application.PolicyPreviewResult;

import java.util.List;

public record PolicyPreviewResponse(
        PolicyResponse currentPolicy,
        ProposedValuesDto proposedValues,
        PolicyDeltaDto delta,
        boolean canProceed,
        List<String> blockers,
        List<String> warnings,
        String validationFingerprint,
        PolicyRequirementsDto requirements
) {
    public static PolicyPreviewResponse from(PolicyPreviewResult result) {
        return new PolicyPreviewResponse(
                PolicyResponse.from(result.currentPolicy()),
                new ProposedValuesDto(
                        result.proposedValues().minimumRestMinutes(),
                        result.proposedValues().weeklyWarningMinutes()
                ),
                PolicyDeltaDto.from(result.delta()),
                result.canProceed(),
                result.blockers() != null ? List.copyOf(result.blockers()) : List.of(),
                result.warnings() != null ? List.copyOf(result.warnings()) : List.of(),
                result.validationFingerprint(),
                new PolicyRequirementsDto(result.reasonRequired())
        );
    }
}
