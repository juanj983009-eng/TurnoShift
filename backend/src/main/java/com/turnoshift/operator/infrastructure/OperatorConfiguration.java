package com.turnoshift.operator.infrastructure;

import com.turnoshift.identity.domain.PasswordBlocklist;
import com.turnoshift.identity.infrastructure.security.Argon2PasswordService;
import com.turnoshift.operator.application.OperatorBootstrapRunner;
import com.turnoshift.operator.domain.PlatformOperatorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion Spring para el modulo de operadores de plataforma.
 *
 * <p>Registra el {@link OperatorBootstrapRunner} como {@code ApplicationRunner}.
 * Spring Boot lo invoca automaticamente al arrancar el contexto, despues de que
 * las migraciones Flyway han aplicado el esquema.
 *
 * <p>La subclase anonima sobreescribe {@code insertFirstOperatorIfNone} para
 * delegar al {@link TransactionalOperatorInserter}, resolviendo el problema
 * de self-invocation de Spring AOP.
 */
@Configuration
public class OperatorConfiguration {

    /**
     * Bootstrap runner del primer operador.
     *
     * <p>Solo actua cuando {@code turnoshift.operator.bootstrap.enabled=true}.
     * Por defecto esta desactivado; no lee variables de entorno ni toca la BD.
     */
    @Bean
    public OperatorBootstrapRunner operatorBootstrapRunner(
            @Value("${turnoshift.operator.bootstrap.enabled:false}") boolean enabled,
            PlatformOperatorRepository repository,
            PasswordBlocklist blocklist,
            Argon2PasswordService argon2,
            TransactionalOperatorInserter inserter) {

        return new OperatorBootstrapRunner(enabled, repository, blocklist, argon2, inserter);
    }

    /**
     * Caso de uso para la autenticación de operadores de plataforma.
     * Genera un hash señuelo Argon2id único por instancia para mitigar ataques de temporización.
     */
    @Bean
    public com.turnoshift.operator.application.AuthenticateOperator authenticateOperator(
            PlatformOperatorRepository repository,
            Argon2PasswordService argon2) {
        String decoyHash = argon2.codificar(java.util.UUID.randomUUID().toString());
        return new com.turnoshift.operator.application.AuthenticateOperator(repository, argon2, decoyHash);
    }

    /**
     * Caso de uso para revalidación de sesión del operador de plataforma.
     */
    @Bean
    public com.turnoshift.operator.application.RevalidateOperatorSession revalidateOperatorSession(
            PlatformOperatorRepository repository,
            java.time.Clock clock) {
        return new com.turnoshift.operator.application.RevalidateOperatorSession(repository, clock);
    }

    /**
     * Caso de uso orquestador para la incorporación de organizaciones por el operador de plataforma.
     */
    @Bean
    public com.turnoshift.operator.application.IncorporateOrganization incorporateOrganization(
            com.turnoshift.operator.domain.ApiOperationRepository apiOperationRepository,
            com.turnoshift.operator.domain.OrganizationOnboardingRepository onboardingRepository,
            OrganizationProperties organizationProperties,
            tools.jackson.databind.ObjectMapper objectMapper,
            java.time.Clock clock) {
        return new com.turnoshift.operator.application.IncorporateOrganization(
                apiOperationRepository,
                onboardingRepository,
                organizationProperties,
                objectMapper,
                clock
        );
    }

    /**
     * Caso de uso orquestador para la emisión de enlace de acceso inicial de contraseña.
     */
    @Bean
    public com.turnoshift.operator.application.EmitInitialAccessLink emitInitialAccessLink(
            com.turnoshift.operator.domain.ApiOperationRepository apiOperationRepository,
            com.turnoshift.operator.domain.OperatorInitialLinkRepository initialLinkRepository,
            com.turnoshift.identity.application.IssueInitialSetupToken issueInitialSetupToken,
            InitialSetupProperties initialSetupProperties,
            tools.jackson.databind.ObjectMapper objectMapper,
            java.time.Clock clock) {
        return new com.turnoshift.operator.application.EmitInitialAccessLink(
                apiOperationRepository,
                initialLinkRepository,
                issueInitialSetupToken,
                initialSetupProperties,
                objectMapper,
                clock
        );
    }
}

