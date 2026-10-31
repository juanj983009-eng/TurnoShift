package com.turnoshift.identity.infrastructure.web;

import com.turnoshift.identity.application.AuthenticateUser;
import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.identity.domain.AuthenticationCandidate;
import com.turnoshift.identity.domain.AuthenticationDataAccessException;
import com.turnoshift.identity.domain.AuthenticationOutcome;
import com.turnoshift.identity.domain.LoginValidationError;
import com.turnoshift.identity.infrastructure.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Unit: LoginController — endpoint POST /api/v1/auth/login")
class LoginControllerTest {

    private AuthenticateUser authenticateUser;
    private LoginRateLimiter rateLimiter;
    private SecurityContextRepository securityContextRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authenticateUser = mock(AuthenticateUser.class);
        rateLimiter = mock(LoginRateLimiter.class);
        securityContextRepository = mock(SecurityContextRepository.class);

        // Por defecto, rate limiter permite la petición
        when(rateLimiter.tryAcquire(anyString()))
                .thenReturn(new LoginRateLimiter.RateLimitResult.Allowed(4));

        LoginController controller = new LoginController(
                authenticateUser,
                rateLimiter,
                securityContextRepository
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // =========================================================================
    // 1. Rate Limiting (429) y Protección contra Manipulación de Cabeceras
    // =========================================================================

    @Test
    @DisplayName("429 RATE_LIMITED: si el limitador rechaza, responde 429 con Retry-After y no llama a AuthenticateUser")
    void rateLimitExcedido_responde429YNoLlamaAuthenticateUser() throws Exception {
        when(rateLimiter.tryAcquire(anyString()))
                .thenReturn(new LoginRateLimiter.RateLimitResult.Rejected(45));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "CAFE-DEMO",
                                  "email": "ana@cafedemo.example",
                                  "password": "clave-correcta-123"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "45"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/rate-limited"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Demasiados intentos. Espera antes de volver a intentarlo."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verifyNoInteractions(authenticateUser);
        verifyNoInteractions(securityContextRepository);
    }

    @Test
    @DisplayName("Rate limiting: dos solicitudes con distinto X-Forwarded-For pero mismo remoteAddr comparten el mismo límite")
    void rateLimit_ignoraXForwardedFor_usaExclusivamenteRemoteAddr() throws Exception {
        String realClientIp = "192.168.1.100";
        when(authenticateUser.execute(anyString(), anyString(), anyString()))
                .thenReturn(AuthenticationOutcome.invalidCredentials());

        // Solicitud 1 con X-Forwarded-For "10.0.0.1"
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(realClientIp);
                            return request;
                        })
                        .header("X-Forwarded-For", "10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "CAFE-DEMO",
                                  "email": "ana@cafedemo.example",
                                  "password": "clave-correcta-123"
                                }
                                """));

        // Solicitud 2 con X-Forwarded-For "203.0.113.195" pero el mismo remoteAddr
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(realClientIp);
                            return request;
                        })
                        .header("X-Forwarded-For", "203.0.113.195")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "CAFE-DEMO",
                                  "email": "ana@cafedemo.example",
                                  "password": "clave-correcta-123"
                                }
                                """));

        // Verificar que ambas solicitudes evaluaron el rate limiter con realClientIp ("192.168.1.100")
        // y ninguna utilizó los valores de X-Forwarded-For
        verify(rateLimiter, times(2)).tryAcquire(realClientIp);
        verify(rateLimiter, never()).tryAcquire("10.0.0.1");
        verify(rateLimiter, never()).tryAcquire("203.0.113.195");
    }

    @Test
    @DisplayName("Rate limiting: evasión con X-Forwarded-For es bloqueada si remoteAddr alcanzó el límite")
    void rateLimit_evasionConXForwardedForEsBloqueada() throws Exception {
        String realClientIp = "192.168.1.100";
        when(rateLimiter.tryAcquire(realClientIp))
                .thenReturn(new LoginRateLimiter.RateLimitResult.Rejected(30));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(realClientIp);
                            return request;
                        })
                        .header("X-Forwarded-For", "spoofed-attacker-ip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "CAFE-DEMO",
                                  "email": "ana@cafedemo.example",
                                  "password": "clave-correcta-123"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        verify(rateLimiter).tryAcquire(realClientIp);
        verify(rateLimiter, never()).tryAcquire("spoofed-attacker-ip");
        verifyNoInteractions(authenticateUser);
    }

    // =========================================================================
    // 2. Validación de Entrada (400 VALIDATION_FAILED)
    // =========================================================================

    @Test
    @DisplayName("400 VALIDATION_FAILED: cuerpo de petición ausente responde 400 con los tres campos obligatorios")
    void validacionRequestAusente_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field == 'organizationCode')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].code").value("REQUIRED"));

        verifyNoInteractions(authenticateUser);
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED: campos en blanco devuelven Problem Details con REQUIRED en cada campo")
    void validacionCamposAusentes_responde400ConListaErrores() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "",
                                  "email": "   ",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(3));

        verifyNoInteractions(authenticateUser);
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED: organizationCode menor a 2 caracteres responde 400 con INVALID_LENGTH")
    void validacionOrganizationCodeDemasiadoCorto_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "A",
                                  "email": "ana@cafedemo.example",
                                  "password": "clave-correcta-123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("organizationCode"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_LENGTH"));

        verifyNoInteractions(authenticateUser);
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED: organizationCode mayor a 32 caracteres responde 400 con INVALID_LENGTH")
    void validacionOrganizationCodeDemasiadoLargo_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "%s",
                                  "email": "ana@cafedemo.example",
                                  "password": "clave-correcta-123"
                                }
                                """.formatted("A".repeat(33))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("organizationCode"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_LENGTH"));

        verifyNoInteractions(authenticateUser);
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED: organizationCode con caracteres no permitidos responde 400 con INVALID_FORMAT")
    void validacionOrganizationCodeCaracteresInvalidos_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "ORG INVALID!",
                                  "email": "ana@cafedemo.example",
                                  "password": "clave-correcta-123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("organizationCode"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

        verifyNoInteractions(authenticateUser);
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED: email con formato inválido responde 400 con INVALID_FORMAT")
    void validacionEmailFormatoInvalido_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "CAFE-DEMO",
                                  "email": "not-an-email",
                                  "password": "clave-correcta-123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

        verifyNoInteractions(authenticateUser);
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED: email que supera la capacidad UTF-16 responde 400 con MAX_LENGTH_EXCEEDED")
    void validacionEmailCapacidadExcedida_responde400() throws Exception {
        String longEmail = "a".repeat(250) + "@cafedemo.example";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "CAFE-DEMO",
                                  "email": "%s",
                                  "password": "clave-correcta-123"
                                }
                                """.formatted(longEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("MAX_LENGTH_EXCEEDED"));

        verifyNoInteractions(authenticateUser);
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED: contraseña mayor a 128 caracteres es rechazada")
    void validacionPasswordExcesiva_responde400() throws Exception {
        String longPassword = "a".repeat(129);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "CAFE-DEMO",
                                  "email": "ana@cafedemo.example",
                                  "password": "%s"
                                }
                                """.formatted(longPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value("MAX_LENGTH_EXCEEDED"));

        verifyNoInteractions(authenticateUser);
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED: AuthenticateUser devuelve VALIDATION_FAILED estructurado y se mapea a 400")
    void validacionCasoDeUso_retorna400Estructurado() throws Exception {
        when(authenticateUser.execute(anyString(), anyString(), anyString()))
                .thenReturn(AuthenticationOutcome.validationFailed(List.of(
                        new LoginValidationError("email", "INVALID_FORMAT", "El correo electrónico no tiene un formato válido.")
                )));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "CAFE-DEMO",
                                  "email": "ana@cafedemo.example",
                                  "password": "clave-correcta-123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    // =========================================================================
    // 3. Credenciales Inválidas (401)
    // =========================================================================

    @Test
    @DisplayName("401 AUTHENTICATION_FAILED: fallo genérico indistinguible, sin sesión creada")
    void credencialesInvalidas_responde401Indistinguible() throws Exception {
        when(authenticateUser.execute(anyString(), anyString(), anyString()))
                .thenReturn(AuthenticationOutcome.invalidCredentials());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "CAFE-DEMO",
                                  "email": "ana@cafedemo.example",
                                  "password": "clave-incorrecta"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/authentication-failed"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Las credenciales son incorrectas o la cuenta no puede iniciar sesión."))
                .andExpect(jsonPath("$.errors").isEmpty());

        verifyNoInteractions(securityContextRepository);
    }

    // =========================================================================
    // 4. Organización Denegada (403)
    // =========================================================================

    @Test
    @DisplayName("403 ACCESS_DENIED: organización suspendida o cerrada")
    void organizacionSuspendida_responde403() throws Exception {
        when(authenticateUser.execute(anyString(), anyString(), anyString()))
                .thenReturn(AuthenticationOutcome.organizationAccessDenied());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "ORG-SUSP",
                                  "email": "ana@susp.example",
                                  "password": "clave-valida"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/access-denied"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(securityContextRepository);
    }

    // =========================================================================
    // 5. Indisponibilidad de Base de Datos (503)
    // =========================================================================

    @Test
    @DisplayName("503 SERVICE_UNAVAILABLE: AuthenticationDataAccessException se mapea a 503 sin exponer SQL ni credenciales")
    void indisponibilidadBD_responde503SinFugas() throws Exception {
        when(authenticateUser.execute(anyString(), anyString(), anyString()))
                .thenThrow(new AuthenticationDataAccessException(
                        "El servicio de autenticación no está disponible temporalmente",
                        new RuntimeException("SECRET_SQL_SERVER_CONNECTION_TIMEOUT_TABLE_APP_USERS")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "CAFE-DEMO",
                                  "email": "ana@cafedemo.example",
                                  "password": "mi-clave-secreta"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/service-unavailable"))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("El servicio no está disponible temporalmente. Inténtalo más tarde."))
                // Seguridad: no debe exponer detalles de excepción ni SQL ni credenciales
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET_SQL"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("mi-clave-secreta"))));

        verifyNoInteractions(securityContextRepository);
    }

    // =========================================================================
    // 6. Autenticación Exitosa (200)
    // =========================================================================

    @Test
    @DisplayName("200 OK: éxito para COLABORADOR retorna SessionResponse con landingPage /app/mi-horario y persiste SecurityContext")
    void autenticacionExitosa_colaborador_retorna200YPersisteContexto() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();

        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                userId,
                orgId,
                "ana@cafedemo.example",
                "Ana García",
                "COLABORADOR",
                empId,
                1L,
                "Café Demo S.A.",
                "America/Lima"
        );

        when(authenticateUser.execute(eq("CAFE-DEMO"), eq("ana@cafedemo.example"), eq("clave-correcta-123")))
                .thenReturn(AuthenticationOutcome.success(identity));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "CAFE-DEMO",
                                  "email": "ana@cafedemo.example",
                                  "password": "clave-correcta-123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.displayName").value("Ana García"))
                .andExpect(jsonPath("$.user.role").value("COLABORADOR"))
                .andExpect(jsonPath("$.user.employeeId").value(empId.toString()))
                .andExpect(jsonPath("$.organization.id").value(orgId.toString()))
                .andExpect(jsonPath("$.organization.displayName").value("Café Demo S.A."))
                .andExpect(jsonPath("$.organization.timeZone").value("America/Lima"))
                .andExpect(jsonPath("$.landingPage").value("/app/mi-horario"));

        // Verificar que SecurityContext fue guardado en SecurityContextRepository
        ArgumentCaptor<SecurityContext> contextCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(securityContextRepository).saveContext(contextCaptor.capture(), any(HttpServletRequest.class), any(HttpServletResponse.class));

        SecurityContext savedContext = contextCaptor.getValue();
        assertThat(savedContext.getAuthentication()).isNotNull();
        Authentication auth = savedContext.getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo(identity);
        assertThat(auth.getCredentials()).isNull(); // Invariante de seguridad: credenciales nulas
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_COLABORADOR");

        // Rate limiter reseteado tras éxito
        verify(rateLimiter).reset(anyString());
    }

    @Test
    @DisplayName("200 OK: éxito para ADMINISTRADOR retorna landingPage /app/inicio")
    void autenticacionExitosa_administrador_landingPageInicio() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                userId,
                orgId,
                "admin@empresa.com",
                "Admin General",
                "ADMINISTRADOR",
                null,
                1L,
                "Empresa S.A.",
                "UTC"
        );

        when(authenticateUser.execute(anyString(), anyString(), anyString()))
                .thenReturn(AuthenticationOutcome.success(identity));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "EMPRESA",
                                  "email": "admin@empresa.com",
                                  "password": "admin-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.user.employeeId").doesNotExist())
                .andExpect(jsonPath("$.landingPage").value("/app/inicio"));
    }

    @Test
    @DisplayName("200 OK: éxito registra el atributo authenticatedAt en la sesión del servidor")
    void autenticacionExitosa_estableceAuthenticatedAtEnSesion() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                userId,
                orgId,
                "admin@empresa.com",
                "Admin General",
                "ADMINISTRADOR",
                null,
                1L,
                "Empresa S.A.",
                "UTC"
        );

        when(authenticateUser.execute(anyString(), anyString(), anyString()))
                .thenReturn(AuthenticationOutcome.success(identity));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "EMPRESA",
                                  "email": "admin@empresa.com",
                                  "password": "admin-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        Object authenticatedAt = session.getAttribute(com.turnoshift.identity.domain.SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE);
        assertThat(authenticatedAt).isNotNull();
        assertThat(authenticatedAt).isInstanceOf(Long.class);
    }
}
