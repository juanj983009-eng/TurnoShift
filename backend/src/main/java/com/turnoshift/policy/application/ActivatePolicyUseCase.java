package com.turnoshift.policy.application;

import com.turnoshift.policy.domain.NewPolicyVersion;
import com.turnoshift.policy.domain.PolicyConcurrentModificationException;
import com.turnoshift.policy.domain.PolicyRepository;
import com.turnoshift.policy.domain.PolicyVersion;
import com.turnoshift.policy.domain.ValidationFingerprint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ActivatePolicyUseCase {

    private final PolicyRepository policyRepository;

    public ActivatePolicyUseCase(PolicyRepository policyRepository) {
        this.policyRepository = Objects.requireNonNull(policyRepository, "policyRepository no puede ser nulo");
    }

    public PolicyVersion activate(ActivatePolicyCommand command) {
        Objects.requireNonNull(command, "command no puede ser nulo");

        // 1. Candado exclusivo a nivel de aplicación por organización
        policyRepository.acquirePolicyLock(command.organizationId());

        // 2. Verificación estricta de la huella de validación canónica
        ValidationFingerprint fingerprint = new ValidationFingerprint(command.validationFingerprint());
        fingerprint.verify(command.organizationId(), command.expectedCurrentPolicyVersionId(), command.proposedThresholds());

        // 3. Verificación de coincidencia con la versión vigente en base de datos
        Optional<UUID> currentIdOpt = policyRepository.findCurrentPolicyVersionId(command.organizationId());
        UUID actualCurrentId = currentIdOpt.orElse(null);
        if (!Objects.equals(actualCurrentId, command.expectedCurrentPolicyVersionId())) {
            throw new PolicyConcurrentModificationException(
                    "Modificación concurrente detectada: la política vigente ha cambiado respecto a la versión esperada"
            );
        }

        // 4. Determinar siguiente número de versión
        int nextVersion = policyRepository.findNextVersionNumber(command.organizationId());

        // 5. Crear objeto de inserción NewPolicyVersion sin timestamps Java
        NewPolicyVersion draft = new NewPolicyVersion(
                command.organizationId(),
                nextVersion,
                command.proposedThresholds(),
                command.actorUserId(),
                command.changeReason()
        );

        // 6. Inserción en base de datos recuperando la versión persistida con timestamps autoritativos de SQL Server
        PolicyVersion persistedVersion = policyRepository.insertPolicyVersion(draft);

        // 7. Actualización atómica del puntero current_policy_version_id en organizations
        policyRepository.updateCurrentPolicyVersion(
                command.organizationId(),
                persistedVersion.id(),
                command.expectedCurrentPolicyVersionId()
        );

        // 8. Retornar versión definitiva
        return persistedVersion;
    }
}
