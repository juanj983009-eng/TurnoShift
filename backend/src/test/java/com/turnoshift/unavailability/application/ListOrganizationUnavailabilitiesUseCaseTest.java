package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListOrganizationUnavailabilitiesUseCaseTest {

    @Mock
    private EmployeeUnavailabilityRepository repository;

    private ListOrganizationUnavailabilitiesUseCase useCase;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ListOrganizationUnavailabilitiesUseCase(repository);
    }

    @Test
    @DisplayName("Lista las indisponibilidades de la organización dentro de una ventana temporal válida")
    void listaIndisponibilidadesDeOrganizacion() {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-31T23:59:59Z");

        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                UUID.randomUUID(), orgId, UUID.randomUUID(),
                TimeInterval.of(Instant.parse("2026-10-15T08:00:00Z"), Instant.parse("2026-10-15T16:00:00Z")),
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                Instant.now(), Instant.now(), 0L, null
        );
        when(repository.findByOrganizationAndInterval(orgId, from, to, false)).thenReturn(List.of(unav));

        List<EmployeeUnavailability> results = useCase.execute(orgId, from, to, false);

        assertThat(results).containsExactly(unav);
    }

    @Test
    @DisplayName("Rechaza si from no es anterior a to")
    void rechazaCronologiaInvalida() {
        Instant from = Instant.parse("2026-10-15T00:00:00Z");
        Instant to = Instant.parse("2026-10-10T00:00:00Z");

        assertThatThrownBy(() -> useCase.execute(orgId, from, to, false))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("anterior");
    }
}
