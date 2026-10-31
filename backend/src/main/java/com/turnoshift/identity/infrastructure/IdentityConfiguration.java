package com.turnoshift.identity.infrastructure;

import com.turnoshift.identity.application.AuthenticateUser;
import com.turnoshift.identity.application.AuthenticationCandidateRepository;
import com.turnoshift.identity.infrastructure.security.Argon2PasswordService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Configuración Spring para los componentes del módulo de identidad.
 *
 * <p>El caso de uso {@link AuthenticateUser} requiere un {@code decoyHash}
 * válido para la mitigación de diferencias de coste. Este hash se genera una sola vez
 * al arrancar el contexto usando un secreto efímero aleatorio.
 */
@Configuration
public class IdentityConfiguration {

    @Bean
    public Argon2PasswordService argon2PasswordService() {
        return new Argon2PasswordService();
    }

    @Bean
    public AuthenticateUser authenticateUser(
            AuthenticationCandidateRepository candidateRepository,
            Argon2PasswordService passwordService) {
        // Hash señuelo generado una sola vez por instancia del servicio.
        // No corresponde a credenciales reales ni se almacena en base de datos.
        String ephemeralSecret = UUID.randomUUID().toString();
        String decoyHash = passwordService.codificar(ephemeralSecret);
        return new AuthenticateUser(candidateRepository, passwordService, decoyHash);
    }


    @Bean
    public java.time.Clock clock() {
        return java.time.Clock.systemUTC();
    }

    @Bean
    public com.turnoshift.identity.application.RevalidateSession revalidateSession(
            com.turnoshift.identity.domain.SessionRevalidationRepository revalidationRepository,
            java.time.Clock clock) {
        return new com.turnoshift.identity.application.RevalidateSession(revalidationRepository, clock);
    }

    @Bean
    public com.turnoshift.identity.domain.PasswordBlocklist passwordBlocklist(
            @org.springframework.beans.factory.annotation.Value("${turnoshift.security.password-blocklist.hibp.url:https://api.pwnedpasswords.com/range/}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${turnoshift.security.password-blocklist.hibp.timeout-ms:2000}") long timeoutMs,
            @org.springframework.beans.factory.annotation.Value("${turnoshift.security.password-blocklist.hibp.user-agent:TurnoShift/0.1}") String userAgent) {
        return new com.turnoshift.identity.infrastructure.client.HibpPasswordBlocklist(
                baseUrl,
                java.time.Duration.ofMillis(timeoutMs),
                userAgent
        );
    }

    @Bean
    public com.turnoshift.identity.domain.SecureTokenGenerator secureTokenGenerator() {
        return new com.turnoshift.identity.domain.SecureTokenGenerator();
    }
}
