package com.turnoshift.operator.infrastructure;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Propiedades de configuración para la emisión de enlaces de configuración de credenciales (INITIAL_SETUP).
 */
@Component
@ConfigurationProperties(prefix = "turnoshift.auth")
public class InitialSetupProperties {

    public static final String DEFAULT_INITIAL_SETUP_BASE_URL = "https://app.turnoshift.example/setup";

    private String initialSetupBaseUrl = DEFAULT_INITIAL_SETUP_BASE_URL;

    @PostConstruct
    public void validate() {
        if (initialSetupBaseUrl == null || initialSetupBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "La propiedad turnoshift.auth.initial-setup-base-url no puede estar vacía."
            );
        }
        try {
            URI uri = URI.create(initialSetupBaseUrl.strip());
            if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "La propiedad turnoshift.auth.initial-setup-base-url debe ser una URL HTTP/HTTPS válida: " + initialSetupBaseUrl
            );
        }
    }

    public String getInitialSetupBaseUrl() {
        return initialSetupBaseUrl;
    }

    public void setInitialSetupBaseUrl(String initialSetupBaseUrl) {
        this.initialSetupBaseUrl = initialSetupBaseUrl;
    }
}
