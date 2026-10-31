package com.turnoshift.operator.infrastructure;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de configuración para la creación y gestión de organizaciones.
 */
@Component
@ConfigurationProperties(prefix = "turnoshift.organization")
public class OrganizationProperties {

    public static final int DEFAULT_MAX_ACTIVE_EMPLOYEES = 50;

    private int defaultMaxActiveEmployees = DEFAULT_MAX_ACTIVE_EMPLOYEES;

    @PostConstruct
    public void validate() {
        if (defaultMaxActiveEmployees <= 0) {
            throw new IllegalStateException(
                    "La propiedad turnoshift.organization.default-max-active-employees debe ser un entero positivo > 0 (valor actual: "
                            + defaultMaxActiveEmployees + ")"
            );
        }
    }

    public int getDefaultMaxActiveEmployees() {
        return defaultMaxActiveEmployees;
    }

    public void setDefaultMaxActiveEmployees(int defaultMaxActiveEmployees) {
        this.defaultMaxActiveEmployees = defaultMaxActiveEmployees;
    }
}
