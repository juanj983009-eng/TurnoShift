package com.turnoshift.policy.application;

import com.turnoshift.policy.domain.PolicyRepository;
import com.turnoshift.policy.domain.PolicyVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetPolicyHistoryUseCase {

    private final PolicyRepository policyRepository;

    public GetPolicyHistoryUseCase(PolicyRepository policyRepository) {
        this.policyRepository = Objects.requireNonNull(policyRepository, "policyRepository no puede ser nulo");
    }

    public List<PolicyVersion> get(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        return policyRepository.findHistory(organizationId);
    }
}
