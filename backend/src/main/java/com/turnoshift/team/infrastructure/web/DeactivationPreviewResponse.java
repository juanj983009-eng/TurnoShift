package com.turnoshift.team.infrastructure.web;

import com.turnoshift.team.domain.DeactivationPreview;

import java.util.List;
import java.util.UUID;

public record DeactivationPreviewResponse(
        UUID employeeId,
        String displayName,
        boolean canDeactivate,
        List<String> blockers
) {
    public static DeactivationPreviewResponse from(DeactivationPreview preview) {
        return new DeactivationPreviewResponse(
                preview.employeeId(),
                preview.displayName(),
                preview.canDeactivate(),
                preview.blockers()
        );
    }
}
