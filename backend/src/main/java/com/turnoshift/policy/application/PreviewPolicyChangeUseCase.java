package com.turnoshift.policy.application;

import com.turnoshift.policy.domain.PolicyConcurrentModificationException;
import com.turnoshift.policy.domain.PolicyDelta;
import com.turnoshift.policy.domain.PolicyRepository;
import com.turnoshift.policy.domain.PolicyThresholds;
import com.turnoshift.policy.domain.PolicyVersion;
import com.turnoshift.policy.domain.ValidationFingerprint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PreviewPolicyChangeUseCase {

    private final PolicyRepository policyRepository;

    public PreviewPolicyChangeUseCase(PolicyRepository policyRepository) {
        this.policyRepository = Objects.requireNonNull(policyRepository, "policyRepository no puede ser nulo");
    }

    public PolicyPreviewResult preview(
            UUID organizationId,
            UUID expectedCurrentPolicyVersionId,
            PolicyThresholds proposedThresholds
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(proposedThresholds, "proposedThresholds no puede ser nulo");

        Optional<PolicyVersion> currentPolicyOpt = policyRepository.findCurrentPolicy(organizationId);

        // Verificación de consistencia del estado esperado
        UUID actualCurrentId = currentPolicyOpt.map(PolicyVersion::id).orElse(null);
        if (!Objects.equals(actualCurrentId, expectedCurrentPolicyVersionId)) {
            throw new PolicyConcurrentModificationException(
                    "Modificación concurrente detectada: el estado actual de la política no coincide con la versión esperada"
            );
        }

        // Cálculo de delta respecto a la versión actual
        PolicyDelta delta = PolicyDelta.calculate(
                currentPolicyOpt.map(PolicyVersion::thresholds),
                proposedThresholds
        );

        // Generación de huella canónica determinista
        String fingerprint = ValidationFingerprint.calculate(
                organizationId,
                expectedCurrentPolicyVersionId,
                proposedThresholds
        ).value();

        return new PolicyPreviewResult(
                currentPolicyOpt.orElse(null),
                proposedThresholds,
                delta,
                true,
                Collections.emptyList(),
                Collections.emptyList(),
                fingerprint,
                true
        );
    }
}
