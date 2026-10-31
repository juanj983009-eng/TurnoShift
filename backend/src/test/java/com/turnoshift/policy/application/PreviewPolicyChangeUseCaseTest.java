package com.turnoshift.policy.application;

import com.turnoshift.policy.domain.PolicyConcurrentModificationException;
import com.turnoshift.policy.domain.PolicyReason;
import com.turnoshift.policy.domain.PolicyRepository;
import com.turnoshift.policy.domain.PolicyThresholds;
import com.turnoshift.policy.domain.PolicyVersion;
import com.turnoshift.policy.domain.ValidationFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: PreviewPolicyChangeUseCase")
class PreviewPolicyChangeUseCaseTest {

    @Mock
    private PolicyRepository policyRepository;

    private PreviewPolicyChangeUseCase useCase;

    private final UUID orgId = UUID.randomUUID();
    private final PolicyThresholds proposed = new PolicyThresholds(720, 2400);

    @BeforeEach
    void setUp() {
        useCase = new PreviewPolicyChangeUseCase(policyRepository);
    }

    @Test
    @DisplayName("Previsualiza exitosamente cuando no hay política previa y expectedCurrentPolicyVersionId es null")
    void previewSinPoliticaPrevia() {
        when(policyRepository.findCurrentPolicy(orgId)).thenReturn(Optional.empty());

        PolicyPreviewResult result = useCase.preview(orgId, null, proposed);

        assertThat(result.currentPolicy()).isNull();
        assertThat(result.proposedValues()).isEqualTo(proposed);
        assertThat(result.delta()).isNull();
        assertThat(result.canProceed()).isTrue();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.reasonRequired()).isTrue();

        String expectedFingerprint = ValidationFingerprint.calculate(orgId, null, proposed).value();
        assertThat(result.validationFingerprint()).isEqualTo(expectedFingerprint);

        // Sin mutación en repositorio
        verify(policyRepository, never()).insertPolicyVersion(any());
        verify(policyRepository, never()).updateCurrentPolicyVersion(any(), any(), any());
    }

    @Test
    @DisplayName("Previsualiza exitosamente con política previa y calcula delta y huella correspondiente")
    void previewConPoliticaPrevia() {
        UUID policyId = UUID.randomUUID();
        PolicyVersion current = new PolicyVersion(
                policyId, orgId, 1,
                new PolicyThresholds(660, 2400), Instant.parse("2026-09-24T10:00:00Z"),
                UUID.randomUUID(), new PolicyReason("Inicial"), Instant.parse("2026-09-24T10:00:00Z")
        );
        when(policyRepository.findCurrentPolicy(orgId)).thenReturn(Optional.of(current));

        PolicyPreviewResult result = useCase.preview(orgId, policyId, proposed);

        assertThat(result.currentPolicy()).isEqualTo(current);
        assertThat(result.proposedValues()).isEqualTo(proposed);
        assertThat(result.delta()).isNotNull();
        assertThat(result.delta().minimumRestMinutesDelta()).isEqualTo(60); // 720 - 660
        assertThat(result.delta().weeklyWarningMinutesDelta()).isZero();     // 2400 - 2400

        String expectedFingerprint = ValidationFingerprint.calculate(orgId, policyId, proposed).value();
        assertThat(result.validationFingerprint()).isEqualTo(expectedFingerprint);

        // Sin mutación en repositorio
        verify(policyRepository, never()).insertPolicyVersion(any());
    }

    @Test
    @DisplayName("Lanza PolicyConcurrentModificationException si expectedCurrent es null pero existe política previa")
    void conflictoConcurrenteExpectedNullConPoliticaExistente() {
        UUID policyId = UUID.randomUUID();
        PolicyVersion current = new PolicyVersion(
                policyId, orgId, 1,
                new PolicyThresholds(660, 2400), Instant.now(),
                UUID.randomUUID(), new PolicyReason("Inicial"), Instant.now()
        );
        when(policyRepository.findCurrentPolicy(orgId)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> useCase.preview(orgId, null, proposed))
                .isInstanceOf(PolicyConcurrentModificationException.class)
                .hasMessageContaining("concurrente");
    }

    @Test
    @DisplayName("Lanza PolicyConcurrentModificationException si expectedCurrent difiere del ID de la política actual")
    void conflictoConcurrenteExpectedDiferente() {
        UUID policyId = UUID.randomUUID();
        UUID otroId = UUID.randomUUID();
        PolicyVersion current = new PolicyVersion(
                policyId, orgId, 1,
                new PolicyThresholds(660, 2400), Instant.now(),
                UUID.randomUUID(), new PolicyReason("Inicial"), Instant.now()
        );
        when(policyRepository.findCurrentPolicy(orgId)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> useCase.preview(orgId, otroId, proposed))
                .isInstanceOf(PolicyConcurrentModificationException.class)
                .hasMessageContaining("concurrente");
    }

    @Test
    @DisplayName("Lanza PolicyConcurrentModificationException si expectedCurrent no es null pero no existe política previa")
    void conflictoConcurrenteExpectedNoNullSinPolitica() {
        UUID esperado = UUID.randomUUID();
        when(policyRepository.findCurrentPolicy(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.preview(orgId, esperado, proposed))
                .isInstanceOf(PolicyConcurrentModificationException.class)
                .hasMessageContaining("concurrente");
    }
}
