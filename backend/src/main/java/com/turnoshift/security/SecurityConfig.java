package com.turnoshift.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import tools.jackson.databind.ObjectMapper;

/**
 * Configuración explícita de Spring Security para TurnoShift.
 *
 * <p><b>Estado PoC-02:</b> infraestructura lista; sin autenticación de usuarios empresariales.
 * Las rutas protegidas devuelven 401 SESSION_REQUIRED hasta que se implemente el login real.
 *
 * <p><b>Rutas públicas declaradas:</b>
 * <ul>
 *   <li>{@code GET /api/health} — salud del servicio (openapi.yaml, security: []).</li>
 *   <li>{@code GET /api/v1/auth/csrf} — obtención del token CSRF (openapi.yaml, security: []).</li>
 * </ul>
 *
 * <p><b>Rutas con CSRF requerido (anónimas hasta login):</b>
 * <ul>
 *   <li>{@code POST /api/v1/auth/login} — requiere CSRF; sesión anónima válida.</li>
 *   <li>{@code POST /api/v1/auth/password-reset/inspect} — requiere CSRF.</li>
 *   <li>{@code POST /api/v1/auth/password-reset/complete} — requiere CSRF.</li>
 * </ul>
 *
 * <p><b>Rutas protegidas (requieren sesión autenticada):</b>
 * <ul>
 *   <li>{@code GET /api/v1/auth/session}</li>
 *   <li>{@code POST /api/v1/auth/logout} — requiere sesión + CSRF.</li>
 *   <li>{@code POST /api/v1/operator/**} — PROP-OP-01 pendiente.</li>
 * </ul>
 *
 * <p><b>Desactivado:</b> formLogin, httpBasic, rememberMe, logout predeterminado,
 * request cache, redirecciones HTML, SessionCreationPolicy.STATELESS.
 *
 * <p><b>CSRF:</b> HttpSessionCsrfTokenRepository; token entregado como atributo de petición.
 * CsrfTokenRequestAttributeHandler evita que el token quede expuesto como cookie readable.
 *
 * <p><b>Fijación de sesión:</b> changeSessionId (OWASP: session fixation protection).
 *
 * <p><b>Sin CORS permisivo:</b> mismo origen frontend+API; no se habilita allowedOrigins("*").
 *
 * <p><b>Pendientes:</b>
 * <ul>
 *   <li>Login real (AuthenticationManager, persistencia del SecurityContext).</li>
 *   <li>Rotación de ID verificada de extremo a extremo tras login.</li>
 *   <li>authenticatedAt y límite absoluto de 8 horas (HttpSession attribute).</li>
 *   <li>Revalidación de usuario, empresa, rol y estado en cada petición.</li>
 *   <li>PROP-OP-01: mecanismo del operador.</li>
 *   <li>Rate limiting de autenticación.</li>
 *   <li>Invalidación de sesiones al cambiar auth_version.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecurityProblemHandlers.SessionRequiredEntryPoint sessionRequired;
    private final SecurityProblemHandlers.TurnoShiftAccessDeniedHandler accessDenied;
    private final com.turnoshift.identity.application.RevalidateSession revalidateSession;
    private final com.turnoshift.operator.application.RevalidateOperatorSession revalidateOperatorSession;
    private final ObjectMapper objectMapper;
    private final boolean cookieSecure;

    public SecurityConfig(
            ObjectMapper objectMapper,
            com.turnoshift.identity.application.RevalidateSession revalidateSession,
            com.turnoshift.operator.application.RevalidateOperatorSession revalidateOperatorSession,
            @org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.secure:true}") boolean cookieSecure) {
        this.objectMapper = objectMapper;
        this.sessionRequired = new SecurityProblemHandlers.SessionRequiredEntryPoint(objectMapper);
        this.accessDenied = new SecurityProblemHandlers.TurnoShiftAccessDeniedHandler(objectMapper);
        this.revalidateSession = revalidateSession;
        this.revalidateOperatorSession = revalidateOperatorSession;
        this.cookieSecure = cookieSecure;
    }

    /**
     * Publica eventos de ciclo de vida de HttpSession para que Spring Session
     * pueda gestionar la expiración y el listeners de sesión.
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * Repositorio de contexto de seguridad basado en sesión HTTP.
     * Gestiona la lectura y persistencia explícita del SecurityContext
     * serializado en Spring Session JDBC.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // -- Repositorio explícito de SecurityContext -------------------------
        http.securityContext(securityContext -> securityContext
                .securityContextRepository(securityContextRepository())
        );

        // -- CSRF con HttpSessionCsrfTokenRepository ---------------------------
        // HttpSessionCsrfTokenRepository: el token se almacena en la sesión del servidor.
        // No se usa CookieCsrfTokenRepository (evita exposición del token en cookie JS-readable).
        // CsrfTokenRequestAttributeHandler: el token se lee como atributo de petición; compatible
        // con el patrón de endpoint GET /api/v1/auth/csrf que materializa el token diferido.
        HttpSessionCsrfTokenRepository csrfRepo = new HttpSessionCsrfTokenRepository();
        csrfRepo.setHeaderName("X-CSRF-TOKEN"); // Nombre del header según openapi.yaml

        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http.csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(csrfHandler)
        );

        // -- Autorización de rutas con frontera estricta -----------------------
        http.authorizeHttpRequests(auth -> auth
                // Rutas públicas definidas en openapi.yaml con security: []
                .requestMatchers(HttpMethod.GET,  "/api/health").permitAll()
                .requestMatchers(HttpMethod.GET,  "/api/v1/auth/csrf").permitAll()

                // Rutas que solo requieren CSRF (accesibles antes del login)
                // Verificación de CSRF la hace CsrfFilter antes de este punto.
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset/inspect").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset/complete").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/operator/auth/login").permitAll()

                // Rutas exclusivas del operador de plataforma (ROLE_PLATFORM_OPERATOR)
                .requestMatchers("/api/v1/operator/**").hasAuthority("ROLE_PLATFORM_OPERATOR")

                // Rutas de sesión empresarial (solo roles empresariales)
                .requestMatchers(HttpMethod.GET,  "/api/v1/auth/session").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR", "ROLE_COLABORADOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR", "ROLE_COLABORADOR")

                // Rutas de puestos de trabajo (HU-06)
                .requestMatchers(HttpMethod.GET,   "/api/v1/positions", "/api/v1/positions/**").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR")
                .requestMatchers(HttpMethod.POST,  "/api/v1/positions", "/api/v1/positions/**").hasAuthority("ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT,   "/api/v1/positions/**").hasAuthority("ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/positions/**").hasAuthority("ROLE_ADMINISTRADOR")

                // Rutas de configuración de organización y sede única (HU-05)
                .requestMatchers(HttpMethod.GET,   "/api/v1/organization/configuration").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/organization/configuration/display-name",
                                                   "/api/v1/organization/configuration/active-site/name").hasAuthority("ROLE_ADMINISTRADOR")

                // Rutas de indisponibilidades (HU-08 / I2.4)
                .requestMatchers(HttpMethod.GET,   "/api/v1/employees/*/unavailabilities",
                                                   "/api/v1/unavailabilities",
                                                   "/api/v1/unavailabilities/**").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR")
                .requestMatchers(HttpMethod.POST,  "/api/v1/employees/*/unavailabilities").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR")
                .requestMatchers("/api/v1/unavailabilities", "/api/v1/unavailabilities/**").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR")

                // Rutas de colaboradores / empleados (HU-02)
                .requestMatchers(HttpMethod.GET,   "/api/v1/employees", "/api/v1/employees/**").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR")
                .requestMatchers(HttpMethod.POST,  "/api/v1/employees", "/api/v1/employees/**").hasAuthority("ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/employees/**").hasAuthority("ROLE_ADMINISTRADOR")

                // Rutas de política operativa inicial (HU-07 / I2.3)
                .requestMatchers(HttpMethod.GET,   "/api/v1/policies/current", "/api/v1/policies/history").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR")
                .requestMatchers(HttpMethod.POST,  "/api/v1/policies/preview", "/api/v1/policies/activate").hasAuthority("ROLE_ADMINISTRADOR")
                .requestMatchers("/api/v1/policies/**").hasAuthority("ROLE_ADMINISTRADOR")

                // Rutas empresariales generales (evita acceso cruzado accidental de operadores)
                .requestMatchers("/api/v1/**").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR", "ROLE_COLABORADOR")

                // Cualquier otra petición no contemplada se deniega
                .anyRequest().denyAll()
        );

        // -- Filtro de revalidación de sesión (8h y estado en BD) -------------
        SessionRevalidationFilter sessionRevalidationFilter = new SessionRevalidationFilter(
                revalidateSession,
                revalidateOperatorSession,
                securityContextRepository(),
                objectMapper,
                cookieSecure
        );
        http.addFilterBefore(sessionRevalidationFilter, org.springframework.security.web.access.intercept.AuthorizationFilter.class);

        // -- Manejadores de errores: 401 y 403 como Problem+JSON ---------------
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(sessionRequired)
                .accessDeniedHandler(accessDenied)
        );

        // -- Sesión: no STATELESS; Spring Session JDBC gestiona la sesión ------
        // changeSessionId: protección contra fijación de sesión (session fixation).
        http.sessionManagement(session -> session
                .sessionFixation().changeSessionId()
        );

        // -- Deshabilitar mecanismos no usados --------------------------------
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());
        http.rememberMe(remember -> remember.disable());

        // Logout predeterminado deshabilitado: TurnoShift implementará su contrato en I1.
        // POST /api/v1/auth/logout con invalidación de sesión y limpieza de cookie.
        http.logout(logout -> logout.disable());

        // Sin request cache: no redirigir al recurso original tras login.
        // La API devuelve 401 y el frontend gestiona la redirección.
        http.requestCache(cache -> cache.requestCache(new NullRequestCache()));

        // Sin CORS permisivo: mismo origen frontend+API (Nginx en producción).
        // Habilitar CORS requeriría corsConfigurationSource; se deja por defecto (DENY).
        http.cors(cors -> cors.disable());

        return http.build();
    }
}
