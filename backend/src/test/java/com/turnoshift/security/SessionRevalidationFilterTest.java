package com.turnoshift.security;

import com.turnoshift.identity.application.RevalidateSession;
import com.turnoshift.identity.application.RevalidationOutcome;
import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.identity.domain.SessionSecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Unit: SessionRevalidationFilter — filtro de revalidación por petición protegida")
class SessionRevalidationFilterTest {

    private RevalidateSession revalidateSession;
    private com.turnoshift.operator.application.RevalidateOperatorSession revalidateOperatorSession;
    private SecurityContextRepository securityContextRepository;
    private ObjectMapper objectMapper;
    private FilterChain filterChain;

    private SessionRevalidationFilter filterSecure;
    private SessionRevalidationFilter filterInsecure;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID orgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID employeeId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private AuthenticatedIdentity baseIdentity;

    @BeforeEach
    void setUp() {
        revalidateSession = mock(RevalidateSession.class);
        revalidateOperatorSession = mock(com.turnoshift.operator.application.RevalidateOperatorSession.class);
        securityContextRepository = mock(SecurityContextRepository.class);
        objectMapper = JsonMapper.builder().build();
        filterChain = mock(FilterChain.class);

        filterSecure = new SessionRevalidationFilter(revalidateSession, revalidateOperatorSession, securityContextRepository, objectMapper, true);
        filterInsecure = new SessionRevalidationFilter(revalidateSession, revalidateOperatorSession, securityContextRepository, objectMapper, false);

        baseIdentity = new AuthenticatedIdentity(
                userId,
                orgId,
                "ana@empresa.example",
                "Ana López",
                "ADMINISTRADOR",
                employeeId,
                1L,
                "Empresa Demo S.A.",
                "America/Bogota"
        );

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(AuthenticatedIdentity identity) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                identity,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + identity.role()))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    // =========================================================================
    // 1. Exclusión de rutas públicas y logout (shouldNotFilter)
    // =========================================================================

    @Test
    @DisplayName("Rutas públicas y logout se excluyen de la revalidación")
    void rutasPublicasYLogout_seExcluyenDeRevalidacion() {
        for (String uri : List.of(
                "/api/health",
                "/api/v1/auth/csrf",
                "/api/v1/auth/login",
                "/api/v1/auth/logout",
                "/api/v1/auth/password-reset/inspect",
                "/api/v1/auth/password-reset/complete",
                "/api/v1/operator/auth/login",
                "/api/v1/operator/auth/logout")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            request.setRequestURI(uri);
            assertThat(filterSecure.shouldNotFilter(request))
                    .as("URI " + uri + " debe ser excluida del filtro")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Rutas protegidas no se excluyen de la revalidación")
    void rutaProtegida_noSeExcluye() {
        for (String uri : List.of("/api/v1/auth/session", "/api/v1/operator/auth/session")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            request.setRequestURI(uri);
            assertThat(filterSecure.shouldNotFilter(request)).isFalse();
        }
    }

    // =========================================================================
    // 2. Solicitudes sin identidad autenticada
    // =========================================================================

    @Test
    @DisplayName("Petición sin autenticación: continúa en la cadena sin revalidar")
    void sinAutenticacion_continuaEnCadena() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
        request.setRequestURI("/api/v1/usuarios");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterSecure.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(revalidateSession);
    }

    @Test
    @DisplayName("Petición con usuario anónimo: continúa en la cadena sin revalidar")
    void usuarioAnonimo_continuaEnCadena() throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        SecurityContextHolder.setContext(context);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
        request.setRequestURI("/api/v1/usuarios");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterSecure.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(revalidateSession);
    }

    // =========================================================================
    // 3. Revalidación Exitosa (VALID)
    // =========================================================================

    @Test
    @DisplayName("Revalidación VALID: continúa en la cadena y no modifica sesión ni contexto")
    void revalidacionExitosa_continuaEnCadena() throws Exception {
        authenticateAs(baseIdentity);
        MockHttpSession session = new MockHttpSession();
        long authAt = System.currentTimeMillis();
        session.setAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE, authAt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        request.setRequestURI("/api/v1/auth/session");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(revalidateSession.execute(baseIdentity, authAt))
                .thenReturn(RevalidationOutcome.valid(baseIdentity));

        filterSecure.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(session.isInvalid()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200); // no error status set by filter
    }

    // =========================================================================
    // 4. Expiración de 8 Horas o Invalidez (401 SESSION_REQUIRED)
    // =========================================================================

    @Test
    @DisplayName("Revalidación EXPIRED: invalida sesión, limpia contexto, limpia cookie y responde 401")
    void revalidacionExpired_invalidaSesionYLimpiaCookie() throws Exception {
        authenticateAs(baseIdentity);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE, 1000L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        request.setRequestURI("/api/v1/auth/session");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(revalidateSession.execute(baseIdentity, 1000L))
                .thenReturn(RevalidationOutcome.expired());

        filterSecure.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("TSESSIONID=");
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).contains("Path=/api");
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("SameSite=Lax");
        assertThat(setCookie).containsIgnoringCase("Secure");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asString()).isEqualTo("SESSION_REQUIRED");
    }

    @Test
    @DisplayName("Revalidación INVALID (usuario inactivo o auth_version alterada): invalida sesión y responde 401")
    void revalidacionInvalid_invalidaSesionYResponde401() throws Exception {
        authenticateAs(baseIdentity);
        MockHttpSession session = new MockHttpSession();
        long authAt = System.currentTimeMillis();
        session.setAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE, authAt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        request.setRequestURI("/api/v1/auth/session");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(revalidateSession.execute(baseIdentity, authAt))
                .thenReturn(RevalidationOutcome.invalid());

        filterSecure.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asString()).isEqualTo("SESSION_REQUIRED");
    }

    // =========================================================================
    // 5. Organización Suspendida (403 ACCESS_DENIED)
    // =========================================================================

    @Test
    @DisplayName("Revalidación ORGANIZATION_ACCESS_DENIED: invalida sesión, limpia cookie y responde 403")
    void revalidacionOrgSuspendida_invalidaSesionYResponde403() throws Exception {
        authenticateAs(baseIdentity);
        MockHttpSession session = new MockHttpSession();
        long authAt = System.currentTimeMillis();
        session.setAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE, authAt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        request.setRequestURI("/api/v1/auth/session");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(revalidateSession.execute(baseIdentity, authAt))
                .thenReturn(RevalidationOutcome.organizationAccessDenied());

        filterSecure.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("TSESSIONID=");
        assertThat(setCookie).contains("Max-Age=0");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asString()).isEqualTo("ACCESS_DENIED");
    }

    // =========================================================================
    // 6. Error de Base de Datos (503 SERVICE_UNAVAILABLE)
    // =========================================================================

    @Test
    @DisplayName("Revalidación DATA_ACCESS_ERROR: responde 503 sin invalidar la sesión del cliente")
    void revalidacionErrorBd_responde503YNoInvalidaSesion() throws Exception {
        authenticateAs(baseIdentity);
        MockHttpSession session = new MockHttpSession();
        long authAt = System.currentTimeMillis();
        session.setAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE, authAt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        request.setRequestURI("/api/v1/auth/session");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(revalidateSession.execute(baseIdentity, authAt))
                .thenReturn(RevalidationOutcome.dataAccessError());

        filterSecure.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        // NO debe invalidar la sesión por una falla transitoria de BD
        assertThat(session.isInvalid()).isFalse();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    // =========================================================================
    // 7. Cambio de Rol (ROLE_CHANGED)
    // =========================================================================

    @Test
    @DisplayName("Revalidación ROLE_CHANGED: actualiza SecurityContext, persiste y continúa sin rotar sesión")
    void revalidacionRolCambiado_actualizaContextoYPersiste() throws Exception {
        authenticateAs(baseIdentity);
        MockHttpSession session = new MockHttpSession();
        long authAt = System.currentTimeMillis();
        session.setAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE, authAt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        request.setRequestURI("/api/v1/auth/session");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthenticatedIdentity nuevoRol = new AuthenticatedIdentity(
                userId, orgId, "ana@empresa.example", "Ana López", "COLABORADOR",
                employeeId, 1L, "Empresa Demo S.A.", "America/Bogota"
        );

        when(revalidateSession.execute(baseIdentity, authAt))
                .thenReturn(RevalidationOutcome.roleChanged(nuevoRol));

        filterSecure.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(session.isInvalid()).isFalse();

        // Verificar que el contexto fue actualizado con ROLE_COLABORADOR
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(currentAuth).isNotNull();
        assertThat(currentAuth.getPrincipal()).isEqualTo(nuevoRol);
        assertThat(currentAuth.getAuthorities()).extracting("authority").containsExactly("ROLE_COLABORADOR");

        // Verificar persistencia explícita en SecurityContextRepository
        ArgumentCaptor<SecurityContext> contextCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(securityContextRepository).saveContext(contextCaptor.capture(), eq(request), eq(response));
        assertThat(contextCaptor.getValue().getAuthentication().getPrincipal()).isEqualTo(nuevoRol);
    }

    // =========================================================================
    // 8. Revalidación de Operador de Plataforma (PlatformOperatorIdentity)
    // =========================================================================

    @Test
    @DisplayName("Operador: revalidación VALID continúa en la cadena sin alterar sesión ni contexto")
    void operadorRevalidacionValida_continuaEnCadena() throws Exception {
        UUID opId = UUID.randomUUID();
        com.turnoshift.operator.domain.PlatformOperatorIdentity opIdentity =
                new com.turnoshift.operator.domain.PlatformOperatorIdentity(opId, "Operador Test", 1L);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                opIdentity, null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_OPERATOR"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        MockHttpSession session = new MockHttpSession();
        long authAt = System.currentTimeMillis();
        session.setAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE, authAt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/operator/auth/session");
        request.setRequestURI("/api/v1/operator/auth/session");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(revalidateOperatorSession.execute(opIdentity, authAt))
                .thenReturn(com.turnoshift.operator.application.OperatorRevalidationOutcome.VALID);

        filterSecure.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(session.isInvalid()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(auth);
    }

    @Test
    @DisplayName("Operador: revalidación INVALID o EXPIRED invalida sesión, limpia contexto, borra cookie y responde 401")
    void operadorRevalidacionInvalida_invalidaSesionYResponde401() throws Exception {
        UUID opId = UUID.randomUUID();
        com.turnoshift.operator.domain.PlatformOperatorIdentity opIdentity =
                new com.turnoshift.operator.domain.PlatformOperatorIdentity(opId, "Operador Invalido", 1L);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                opIdentity, null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_OPERATOR"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        MockHttpSession session = new MockHttpSession();
        long authAt = System.currentTimeMillis();
        session.setAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE, authAt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/operator/auth/session");
        request.setRequestURI("/api/v1/operator/auth/session");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(revalidateOperatorSession.execute(opIdentity, authAt))
                .thenReturn(com.turnoshift.operator.application.OperatorRevalidationOutcome.INVALID);

        filterSecure.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(org.springframework.http.HttpHeaders.SET_COOKIE)).contains("TSESSIONID=");
        assertThat(response.getHeader(org.springframework.http.HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("Operador: revalidación DATA_ACCESS_ERROR responde 503 sin invalidar sesión")
    void operadorRevalidacionErrorDatos_responde503YPreservaSesion() throws Exception {
        UUID opId = UUID.randomUUID();
        com.turnoshift.operator.domain.PlatformOperatorIdentity opIdentity =
                new com.turnoshift.operator.domain.PlatformOperatorIdentity(opId, "Operador Error", 1L);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                opIdentity, null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_OPERATOR"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        MockHttpSession session = new MockHttpSession();
        long authAt = System.currentTimeMillis();
        session.setAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE, authAt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/operator/auth/session");
        request.setRequestURI("/api/v1/operator/auth/session");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(revalidateOperatorSession.execute(opIdentity, authAt))
                .thenReturn(com.turnoshift.operator.application.OperatorRevalidationOutcome.DATA_ACCESS_ERROR);

        filterSecure.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(session.isInvalid()).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
    }
}
