package com.turnoshift.operator.infrastructure.web;

import com.turnoshift.operator.application.AuthenticateOperator;
import com.turnoshift.operator.domain.OperatorAuthenticationOutcome;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.PlatformOperatorIdentity;
import com.turnoshift.operator.infrastructure.security.OperatorLoginRateLimiter;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Unit: OperatorLoginController — endpoint POST /api/v1/operator/auth/login")
class OperatorLoginControllerTest {

    private AuthenticateOperator authenticateOperator;
    private OperatorLoginRateLimiter rateLimiter;
    private SecurityContextRepository securityContextRepository;
    private Clock clock;
    private MockMvc mockMvc;

    private final UUID operatorId = UUID.randomUUID();
    private final Instant fixedInstant = Instant.parse("2026-09-16T12:00:00Z");

    @BeforeEach
    void setUp() {
        authenticateOperator = mock(AuthenticateOperator.class);
        rateLimiter = mock(OperatorLoginRateLimiter.class);
        securityContextRepository = mock(SecurityContextRepository.class);
        clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        when(rateLimiter.tryAcquire(anyString()))
                .thenReturn(new OperatorLoginRateLimiter.RateLimitResult.Allowed(4));

        OperatorLoginController controller = new OperatorLoginController(
                authenticateOperator,
                rateLimiter,
                securityContextRepository,
                clock
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("429 RATE_LIMITED: si el limitador rechaza, responde 429 con Retry-After y no llama a AuthenticateOperator")
    void rateLimitExcedido_responde429YNoLlamaAuthenticateOperator() throws Exception {
        when(rateLimiter.tryAcquire(anyString()))
                .thenReturn(new OperatorLoginRateLimiter.RateLimitResult.Rejected(30));

        mockMvc.perform(post("/api/v1/operator/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "operador@turnoshift.example",
                                  "password": "ClaveSegura12345!"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/rate-limited"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429));

        verifyNoInteractions(authenticateOperator);
        verifyNoInteractions(securityContextRepository);
    }

    @Test
    @DisplayName("Rate limiting ignora cabecera X-Forwarded-For y usa exclusivamente getRemoteAddr()")
    void rateLimit_ignoraXForwardedFor_usaExclusivamenteRemoteAddr() throws Exception {
        when(authenticateOperator.execute(anyString(), any(char[].class)))
                .thenReturn(OperatorAuthenticationOutcome.invalidCredentials());

        mockMvc.perform(post("/api/v1/operator/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.20.30.40");
                            return request;
                        })
                        .header("X-Forwarded-For", "203.0.113.195, 198.51.100.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "operador@turnoshift.example",
                                  "password": "ClaveSegura12345!"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(rateLimiter).tryAcquire("10.20.30.40");
        verify(rateLimiter, never()).tryAcquire(contains("203.0.113.195"));
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED: cuerpo ausente responde con errores REQUIRED")
    void cuerpoAusente_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/operator/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(authenticateOperator);
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED: email con formato inválido")
    void emailInvalido_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/operator/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "no-es-correo",
                                  "password": "ClaveSegura12345!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

        verifyNoInteractions(authenticateOperator);
    }

    @Test
    @DisplayName("401 AUTHENTICATION_FAILED: credenciales inválidas no crean sesión")
    void credencialesInvalidas_responde401SinCrearSesion() throws Exception {
        when(authenticateOperator.execute(eq("operador@turnoshift.example"), any(char[].class)))
                .thenReturn(OperatorAuthenticationOutcome.invalidCredentials());

        MvcResult result = mockMvc.perform(post("/api/v1/operator/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "operador@turnoshift.example",
                                  "password": "ClaveErronea123!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/authentication-failed"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.status").value(401))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        verifyNoInteractions(securityContextRepository);
    }

    @Test
    @DisplayName("503 SERVICE_UNAVAILABLE: error de persistencia no filtra SQL ni detalles")
    void errorDePersistencia_responde503() throws Exception {
        when(authenticateOperator.execute(eq("operador@turnoshift.example"), any(char[].class)))
                .thenThrow(new OperatorDataAccessException());

        mockMvc.perform(post("/api/v1/operator/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "operador@turnoshift.example",
                                  "password": "ClaveSegura12345!"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/service-unavailable"))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503));

        verifyNoInteractions(securityContextRepository);
    }

    @Test
    @DisplayName("200 OK: autenticación exitosa crea sesión, rota ID, persiste SecurityContext con ROLE_PLATFORM_OPERATOR y resetea rate limiter")
    void loginExitoso_flujoCompleto() throws Exception {
        PlatformOperatorIdentity identity = new PlatformOperatorIdentity(operatorId, "Operador Root", 1L);
        when(authenticateOperator.execute(eq("operador@turnoshift.example"), any(char[].class)))
                .thenReturn(OperatorAuthenticationOutcome.success(identity));

        mockMvc.perform(post("/api/v1/operator/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("192.168.1.50");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "operador@turnoshift.example",
                                  "password": "ClaveSegura12345!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.operator.id").value(operatorId.toString()))
                .andExpect(jsonPath("$.operator.displayName").value("Operador Root"))
                .andExpect(jsonPath("$.landingPage").value("/operator/inicio"));

        ArgumentCaptor<SecurityContext> contextCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(securityContextRepository).saveContext(contextCaptor.capture(), any(HttpServletRequest.class), any(HttpServletResponse.class));

        SecurityContext savedContext = contextCaptor.getValue();
        assertThat(savedContext.getAuthentication()).isNotNull();
        Authentication auth = savedContext.getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo(identity);
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_PLATFORM_OPERATOR");

        verify(rateLimiter).reset("192.168.1.50");
    }
}
