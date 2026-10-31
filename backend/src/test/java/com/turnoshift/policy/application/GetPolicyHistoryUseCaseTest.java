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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: GetPolicyHistoryUseCase")
class GetPolicyHistoryUseCaseTest {

    @Mock
    private PolicyRepository policyRepository;

    private GetPolicyHistoryUseCase useCase;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetPolicyHistoryUseCase(policyRepository);
    }

    @Test
    @DisplayName("Retorna lista vacía si la organización no tiene versiones activadas")
    void retornaVacioSinHistorial() {
        when(policyRepository.findHistory(orgId)).thenReturn(Collections.emptyList());

        List<PolicyVersion> result = useCase.get(orgId);

        assertThat(result).isEmpty();
        verify(policyRepository).findHistory(orgId);
    }

    @Test
    @DisplayName("Retorna lista ordenada descendentemente por versionNumber delegada del repositorio")
    void retornaHistorialOrdenado() {
        PolicyVersion v1 = new PolicyVersion(
                UUID.randomUUID(), orgId, 1,
                new PolicyThresholds(660, 2400), Instant.parse("2026-09-20T10:00:00Z"),
                UUID.randomUUID(), new PolicyReason("Versión 1"), Instant.parse("2026-09-20T10:00:00Z")
        );
        PolicyVersion v2 = new PolicyVersion(
                UUID.randomUUID(), orgId, 2,
                new PolicyThresholds(720, 2400), Instant.parse("2026-09-24T10:00:00Z"),
                UUID.randomUUID(), new PolicyReason("Versión 2"), Instant.parse("2026-09-24T10:00:00Z")
        );

        when(policyRepository.findHistory(orgId)).thenReturn(List.of(v2, v1));

        List<PolicyVersion> result = useCase.get(orgId);

        assertThat(result).containsExactly(v2, v1);
        verify(policyRepository).findHistory(orgId);
    }

    @Test
    @DisplayName("Rechaza organizationId nulo")
    void rechazaOrgIdNulo() {
        assertThatThrownBy(() -> useCase.get(null))
                .isInstanceOf(NullPointerException.class);
    }
}
