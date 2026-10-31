package com.turnoshift.operator.domain;

import com.turnoshift.operator.infrastructure.web.IncorporateOrganizationResponse;

/**
 * Resultado tipado y exhaustivo del caso de uso de incorporación de organización.
 */
public sealed interface OrganizationOnboardingResult {

    record Success(IncorporateOrganizationResponse response, boolean replayed) implements OrganizationOnboardingResult {}

    record CodeConflict(String code, String message, boolean replayed) implements OrganizationOnboardingResult {}

    record IdempotencyKeyReused() implements OrganizationOnboardingResult {}

    record IdempotencyKeyExpired() implements OrganizationOnboardingResult {}

    record DataAccessError() implements OrganizationOnboardingResult {}
}
