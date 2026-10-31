package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUnavailabilityUseCaseTest {

    @Mock
    private EmployeeUnavailabilityRepository repository;

    private GetUnavailabilityUseCase useCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID unavId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetUnavailabilityUseCase(repository);
    }

    @Test
    @DisplayName("Retorna la indisponibilidad cuando existe para la organización")
    void retornaIndisponibilidadExistente() {
        EmployeeUnavailability expected = EmployeeUnavailability.reconstruct(
                unavId, orgId, UUID.randomUUID(),
                TimeInterval.of(Instant.parse("2026-10-15T08:00:00Z"), Instant.parse("2026-10-15T16:00:00Z")),
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                Instant.now(), Instant.now(), 0L, null
        );
        when(repository.findById(orgId, unavId)).thenReturn(Optional.of(expected));

        EmployeeUnavailability actual = useCase.execute(orgId, unavId);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("Lanza UnavailabilityNotFoundException cuando no existe")
    void lanzaExcepcionCuandoNoExiste() {
        when(repository.findById(orgId, unavId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, unavId))
                .isInstanceOf(UnavailabilityNotFoundException.class)
                .hasMessageContaining("no encontrada");
    }
}
