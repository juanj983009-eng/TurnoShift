package com.turnoshift.unavailability.infrastructure.config;

import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.team.domain.EmployeeRepository;
import com.turnoshift.unavailability.application.CancelUnavailabilityUseCase;
import com.turnoshift.unavailability.application.CreateUnavailabilityUseCase;
import com.turnoshift.unavailability.application.GetUnavailabilityUseCase;
import com.turnoshift.unavailability.application.ListEmployeeUnavailabilitiesUseCase;
import com.turnoshift.unavailability.application.ListOrganizationUnavailabilitiesUseCase;
import com.turnoshift.unavailability.application.UpdateUnavailabilityUseCase;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.EmployeeValidationPort;
import com.turnoshift.unavailability.domain.OrganizationTimezonePort;
import com.turnoshift.unavailability.infrastructure.adapter.OrganizationTimezoneAdapter;
import com.turnoshift.unavailability.infrastructure.adapter.TeamEmployeeValidationAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Configuración Spring para la inyección de dependencias del módulo de indisponibilidades.
 */
@Configuration
public class UnavailabilityConfiguration {

    @Bean
    public OrganizationTimezonePort organizationTimezonePort(OrganizationConfigurationRepository organizationConfigurationRepository) {
        return new OrganizationTimezoneAdapter(organizationConfigurationRepository);
    }

    @Bean
    public EmployeeValidationPort unavailabilityEmployeeValidationPort(EmployeeRepository employeeRepository) {
        return new TeamEmployeeValidationAdapter(employeeRepository);
    }

    @Bean
    public GetUnavailabilityUseCase getUnavailabilityUseCase(EmployeeUnavailabilityRepository repository) {
        return new GetUnavailabilityUseCase(repository);
    }

    @Bean
    public ListEmployeeUnavailabilitiesUseCase listEmployeeUnavailabilitiesUseCase(
            EmployeeUnavailabilityRepository repository,
            EmployeeValidationPort unavailabilityEmployeeValidationPort
    ) {
        return new ListEmployeeUnavailabilitiesUseCase(repository, unavailabilityEmployeeValidationPort);
    }

    @Bean
    public ListOrganizationUnavailabilitiesUseCase listOrganizationUnavailabilitiesUseCase(
            EmployeeUnavailabilityRepository repository
    ) {
        return new ListOrganizationUnavailabilitiesUseCase(repository);
    }

    @Bean
    public CreateUnavailabilityUseCase createUnavailabilityUseCase(
            EmployeeUnavailabilityRepository repository,
            EmployeeValidationPort unavailabilityEmployeeValidationPort,
            Clock clock
    ) {
        return new CreateUnavailabilityUseCase(repository, unavailabilityEmployeeValidationPort, clock);
    }

    @Bean
    public UpdateUnavailabilityUseCase updateUnavailabilityUseCase(
            EmployeeUnavailabilityRepository repository,
            Clock clock
    ) {
        return new UpdateUnavailabilityUseCase(repository, clock);
    }

    @Bean
    public CancelUnavailabilityUseCase cancelUnavailabilityUseCase(
            EmployeeUnavailabilityRepository repository,
            Clock clock
    ) {
        return new CancelUnavailabilityUseCase(repository, clock);
    }
}
