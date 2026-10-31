package com.turnoshift.policy.application;

import com.turnoshift.policy.domain.NewPolicyVersion;
import com.turnoshift.policy.domain.PolicyConcurrentModificationException;
import com.turnoshift.policy.domain.PolicyOperationBusyException;
import com.turnoshift.policy.domain.PolicyReason;
import com.turnoshift.policy.domain.PolicyRepository;
import com.turnoshift.policy.domain.PolicyThresholds;
import com.turnoshift.policy.domain.PolicyValidationException;
import com.turnoshift.policy.domain.PolicyVersion;
import com.turnoshift.policy.domain.ValidationFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: ActivatePolicyUseCase")
class ActivatePolicyUseCaseTest {

    @Mock
    private PolicyRepository policyRepository;

    private ActivatePolicyUseCase useCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final PolicyThresholds thresholds = new PolicyThresholds(660, 2400);
    private final PolicyReason reason = new PolicyReason("Configuración inicial autorizada");

    @BeforeEach
    void setUp() {
        useCase = new ActivatePolicyUseCase(policyRepository);
    }

    @Test
    @DisplayName("Activa exitosamente versión 1 inicial y retorna timestamps autoritativos del repositorio")
    void activaVersion1Inicial() {
        String validFp = ValidationFingerprint.calculate(orgId, null, thresholds).value();
        ActivatePolicyCommand command = new ActivatePolicyCommand(
                orgId, userId, null, thresholds, validFp, reason
        );

        when(policyRepository.findCurrentPolicyVersionId(orgId)).thenReturn(Optional.empty());
        when(policyRepository.findNextVersionNumber(orgId)).thenReturn(1);

        UUID generatedId = UUID.randomUUID();
        Instant sqlServerEffectiveAt = Instant.parse("2026-09-24T23:59:59.123Z");
        Instant sqlServerCreatedAt = Instant.parse("2026-09-24T23:59:59.123Z");
        PolicyVersion persistedVersion = new PolicyVersion(
                generatedId, orgId, 1, thresholds,
                sqlServerEffectiveAt, userId, reason, sqlServerCreatedAt
        );
        when(policyRepository.insertPolicyVersion(any(NewPolicyVersion.class))).thenReturn(persistedVersion);

        PolicyVersion result = useCase.activate(command);

        assertThat(result).isSameAs(persistedVersion);
        assertThat(result.id()).isEqualTo(generatedId);
        assertThat(result.effectiveAt()).isEqualTo(sqlServerEffectiveAt);
        assertThat(result.createdAt()).isEqualTo(sqlServerCreatedAt);

        // Verificación estricta de orden transaccional: lock -> insert -> update pointer
        InOrder inOrder = inOrder(policyRepository);
        inOrder.verify(policyRepository).acquirePolicyLock(orgId);
        inOrder.verify(policyRepository).findCurrentPolicyVersionId(orgId);
        inOrder.verify(policyRepository).findNextVersionNumber(orgId);
        inOrder.verify(policyRepository).insertPolicyVersion(new NewPolicyVersion(orgId, 1, thresholds, userId, reason));
        inOrder.verify(policyRepository).updateCurrentPolicyVersion(orgId, generatedId, null);
    }

    @Test
    @DisplayName("Activa exitosamente versión subsiguiente N+1 actualizando puntero con versión previa")
    void activaVersionSubsiguiente() {
        UUID previousPolicyId = UUID.randomUUID();
        String validFp = ValidationFingerprint.calculate(orgId, previousPolicyId, thresholds).value();
        ActivatePolicyCommand command = new ActivatePolicyCommand(
                orgId, userId, previousPolicyId, thresholds, validFp, reason
        );

        when(policyRepository.findCurrentPolicyVersionId(orgId)).thenReturn(Optional.of(previousPolicyId));
        when(policyRepository.findNextVersionNumber(orgId)).thenReturn(2);

        UUID newId = UUID.randomUUID();
        Instant sqlServerNow = Instant.parse("2026-09-25T01:00:00.000Z");
        PolicyVersion persistedV2 = new PolicyVersion(
                newId, orgId, 2, thresholds, sqlServerNow, userId, reason, sqlServerNow
        );
        when(policyRepository.insertPolicyVersion(any(NewPolicyVersion.class))).thenReturn(persistedV2);

        PolicyVersion result = useCase.activate(command);

        assertThat(result).isSameAs(persistedV2);
        assertThat(result.versionNumber()).isEqualTo(2);

        InOrder inOrder = inOrder(policyRepository);
        inOrder.verify(policyRepository).acquirePolicyLock(orgId);
        inOrder.verify(policyRepository).insertPolicyVersion(new NewPolicyVersion(orgId, 2, thresholds, userId, reason));
        inOrder.verify(policyRepository).updateCurrentPolicyVersion(orgId, newId, previousPolicyId);
    }

    @Test
    @DisplayName("Interrumpe ejecución de inmediato si acquirePolicyLock arroja PolicyOperationBusyException")
    void abortaSiBloqueoOcupado() {
        doThrow(new PolicyOperationBusyException("Recurso ocupado"))
                .when(policyRepository).acquirePolicyLock(orgId);

        String fp = ValidationFingerprint.calculate(orgId, null, thresholds).value();
        ActivatePolicyCommand command = new ActivatePolicyCommand(
                orgId, userId, null, thresholds, fp, reason
        );

        assertThatThrownBy(() -> useCase.activate(command))
                .isInstanceOf(PolicyOperationBusyException.class);

        verify(policyRepository, never()).findCurrentPolicyVersionId(any());
        verify(policyRepository, never()).insertPolicyVersion(any());
        verify(policyRepository, never()).updateCurrentPolicyVersion(any(), any(), any());
    }

    @Test
    @DisplayName("Lanza PolicyValidationException con INVALID_VALIDATION_FINGERPRINT si la huella no coincide")
    void rechazaHuellaInvalida() {
        // Huella válida para otros umbrales
        PolicyThresholds otros = new PolicyThresholds(720, 2400);
        String fpInvalido = ValidationFingerprint.calculate(orgId, null, otros).value();

        ActivatePolicyCommand command = new ActivatePolicyCommand(
                orgId, userId, null, thresholds, fpInvalido, reason
        );

        assertThatThrownBy(() -> useCase.activate(command))
                .isInstanceOf(PolicyValidationException.class)
                .satisfies(ex -> {
                    PolicyValidationException pve = (PolicyValidationException) ex;
                    assertThat(pve.getErrorCode()).isEqualTo("INVALID_VALIDATION_FINGERPRINT");
                });

        verify(policyRepository, never()).insertPolicyVersion(any());
    }

    @Test
    @DisplayName("Lanza PolicyConcurrentModificationException si la versión actual en BD difiere de expectedCurrent")
    void rechazaModificacionConcurrente() {
        UUID expectedId = UUID.randomUUID();
        UUID actualIdEnBd = UUID.randomUUID();

        String fp = ValidationFingerprint.calculate(orgId, expectedId, thresholds).value();
        ActivatePolicyCommand command = new ActivatePolicyCommand(
                orgId, userId, expectedId, thresholds, fp, reason
        );

        when(policyRepository.findCurrentPolicyVersionId(orgId)).thenReturn(Optional.of(actualIdEnBd));

        assertThatThrownBy(() -> useCase.activate(command))
                .isInstanceOf(PolicyConcurrentModificationException.class);

        verify(policyRepository, never()).insertPolicyVersion(any());
    }
}
