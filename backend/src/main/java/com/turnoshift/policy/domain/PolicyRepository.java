package com.turnoshift.policy.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository {

    Optional<PolicyVersion> findCurrentPolicy(UUID organizationId);

    Optional<UUID> findCurrentPolicyVersionId(UUID organizationId);

    List<PolicyVersion> findHistory(UUID organizationId);

    int findNextVersionNumber(UUID organizationId);

    PolicyVersion insertPolicyVersion(NewPolicyVersion newVersion);

    void updateCurrentPolicyVersion(UUID organizationId, UUID newPolicyVersionId, UUID expectedCurrentPolicyVersionId);

    void acquirePolicyLock(UUID organizationId);
}
