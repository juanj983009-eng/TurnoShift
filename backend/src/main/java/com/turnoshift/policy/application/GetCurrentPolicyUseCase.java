package com.turnoshift.policy.application;

import com.turnoshift.policy.domain.PolicyRepository;
import com.turnoshift.policy.domain.PolicyVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetCurrentPolicyUseCase {

    private final PolicyRepository policyRepository;

    public GetCurrentPolicyUseCase(PolicyRepository policyRepository) {
        this.policyRepository = Objects.requireNonNull(policyRepository, "policyRepository no puede ser nulo");
    }

    public Optional<PolicyVersion> get(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        return policyRepository.findCurrentPolicy(organizationId);
    }
}
