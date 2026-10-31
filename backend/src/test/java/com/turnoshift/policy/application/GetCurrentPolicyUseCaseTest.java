package com.turnoshift.policy.application;

import com.turnoshift.policy.domain.PolicyReason;
import com.turnoshift.policy.domain.PolicyRepository;
import com.turnoshift.policy.domain.PolicyThresholds;
import com.turnoshift.policy.domain.PolicyVersion;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: GetCurrentPolicyUseCase")
class GetCurrentPolicyUseCaseTest {

    @Mock
    private PolicyRepository policyRepository;

    private GetCurrentPolicyUseCase useCase;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetCurrentPolicyUseCase(policyRepository);
    }

    @Test
    @DisplayName("Retorna Optional.empty() cuando la organización no tiene política configurada")
    void retornaEmptySinPolitica() {
        when(policyRepository.findCurrentPolicy(orgId)).thenReturn(Optional.empty());

        Optional<PolicyVersion> result = useCase.get(orgId);

        assertThat(result).isEmpty();
        verify(policyRepository).findCurrentPolicy(orgId);
    }

    @Test
    @DisplayName("Retorna la política vigente cuando está configurada")
    void retornaPoliticaVigente() {
        PolicyVersion version = new PolicyVersion(
                UUID.randomUUID(),
                orgId,
                1,
                new PolicyThresholds(660, 2400),
                Instant.parse("2026-09-24T12:00:00Z"),
                UUID.randomUUID(),
                new PolicyReason("Configuración inicial"),
                Instant.parse("2026-09-24T12:00:00Z")
        );
        when(policyRepository.findCurrentPolicy(orgId)).thenReturn(Optional.of(version));

        Optional<PolicyVersion> result = useCase.get(orgId);

        assertThat(result).contains(version);
        verify(policyRepository).findCurrentPolicy(orgId);
    }

    @Test
    @DisplayName("Rechaza organizationId nulo")
    void rechazaOrgIdNulo() {
        assertThatThrownBy(() -> useCase.get(null))
                .isInstanceOf(NullPointerException.class);
    }
}
