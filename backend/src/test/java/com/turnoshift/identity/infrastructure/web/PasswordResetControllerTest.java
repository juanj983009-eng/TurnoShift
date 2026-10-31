package com.turnoshift.identity.infrastructure.web;

import com.turnoshift.identity.application.CompleteInitialPasswordSetup;
import com.turnoshift.identity.application.InspectResetToken;
import com.turnoshift.identity.domain.PasswordBlocklistUnavailableException;
import com.turnoshift.identity.domain.PasswordSetupDataAccessException;
import com.turnoshift.identity.domain.PasswordSetupResult;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.TokenDataAccessException;
import com.turnoshift.identity.domain.TokenInspectionResult;
import com.turnoshift.identity.domain.ViolacionPolitica;
import com.turnoshift.identity.infrastructure.security.PasswordResetRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Unit: PasswordResetController — endpoints HTTP de inspección y completado")
class PasswordResetControllerTest {

    private InspectResetToken inspectResetToken;
    private CompleteInitialPasswordSetup completeInitialPasswordSetup;
    private PasswordResetRateLimiter rateLimiter;
    private MockMvc mockMvc;

    private static final String VALID_TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP1";

    @BeforeEach
    void setUp() {
        inspectResetToken = mock(InspectResetToken.class);
        completeInitialPasswordSetup = mock(CompleteInitialPasswordSetup.class);
        rateLimiter = mock(PasswordResetRateLimiter.class);

        // Por defecto, rate limiters permiten las solicitudes
        when(rateLimiter.tryAcquireInspect(anyString()))
                .thenReturn(new PasswordResetRateLimiter.RateLimitResult.Allowed(9));
        when(rateLimiter.tryAcquireComplete(anyString()))
                .thenReturn(new PasswordResetRateLimiter.RateLimitResult.Allowed(4));

        PasswordResetController controller = new PasswordResetController(
                inspectResetToken,
                completeInitialPasswordSetup,
                rateLimiter
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // =========================================================================
    // POST /api/v1/auth/password-reset/inspect
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/auth/password-reset/inspect")
    class InspectEndpoint {

        @Test
        @DisplayName("200 OK: token válido devuelve valid=true y purpose=INITIAL_SETUP con Cache-Control: no-store")
        void inspect_tokenValido_responde200() throws Exception {
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenReturn(new TokenInspectionResult.Valid(ResetTokenPurpose.INITIAL_SETUP));

            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s"
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.purpose").value("INITIAL_SETUP"));

            verify(inspectResetToken).inspect(VALID_TOKEN);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: cuerpo ausente")
        void inspect_cuerpoAusente_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errors[0].field").value("token"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: token nulo o en blanco")
        void inspect_tokenEnBlanco_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "   "
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("token"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("422 RESET_LINK_INVALID: token inválido, expirado, consumido o revocado")
        void inspect_tokenInvalido_responde422() throws Exception {
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenReturn(new TokenInspectionResult.Invalid());

            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s"
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("/problems/reset-link-invalid"))
                    .andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"))
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.detail").value("El enlace ha vencido o ya fue utilizado."))
                    .andExpect(jsonPath("$.errors").isEmpty());
        }

        @Test
        @DisplayName("429 RATE_LIMITED: rechaza con Retry-After y no llama a inspectResetToken")
        void inspect_rateLimitExcedido_responde429() throws Exception {
            when(rateLimiter.tryAcquireInspect(anyString()))
                    .thenReturn(new PasswordResetRateLimiter.RateLimitResult.Rejected(35));

            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s"
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "35"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("/problems/rate-limited"))
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                    .andExpect(jsonPath("$.status").value(429));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("503 SERVICE_UNAVAILABLE: error de persistencia en inspección")
        void inspect_errorPersistencia_responde503() throws Exception {
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenThrow(new TokenDataAccessException(new RuntimeException("Error DB")));

            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s"
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("/problems/service-unavailable"))
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.status").value(503));
        }

        @Test
        @DisplayName("Rate limiting usa exclusivamente remoteAddr e ignora X-Forwarded-For")
        void inspect_ignoraXForwardedFor() throws Exception {
            String clientIp = "192.168.1.88";
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenReturn(new TokenInspectionResult.Valid(ResetTokenPurpose.INITIAL_SETUP));

            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .with(req -> {
                                req.setRemoteAddr(clientIp);
                                return req;
                            })
                            .header("X-Forwarded-For", "203.0.113.195")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s"
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isOk());

            verify(rateLimiter).tryAcquireInspect(clientIp);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: JSON malformado (sintaxis rota) sin eco de datos")
        void inspect_jsonMalformado_responde400SinEcoDeDatos() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"token\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: cuerpo vacío con cadena vacía")
        void inspect_cuerpoVacioString_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("token"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: cuerpo solo espacios")
        void inspect_cuerpoSoloEspacios_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("   "))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("token"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: raíz JSON no es un objeto")
        void inspect_rootJsonNoObjeto_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[1, 2, 3]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: token tipo número")
        void inspect_tokenTipoIncorrectoNumero_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": 12345
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("token"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: token tipo booleano")
        void inspect_tokenTipoIncorrectoBooleano_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": true
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("token"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: token tipo array")
        void inspect_tokenTipoIncorrectoArray_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": ["abc"]
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("token"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: token tipo objeto")
        void inspect_tokenTipoIncorrectoObjeto_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": {}
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("token"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: Content-Type no compatible text/plain")
        void inspect_contentTypeIncompatible_textPlain_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("token=%s".formatted(VALID_TOKEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: Content-Type no compatible application/xml")
        void inspect_contentTypeIncompatible_applicationXml_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<request><token>%s</token></request>".formatted(VALID_TOKEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: Content-Type ausente con cuerpo")
        void inspect_contentTypeAusente_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .content("""
                                    {
                                      "token": "%s"
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(inspectResetToken);
        }

        @Test
        @DisplayName("Contrato: token con propósito PASSWORD_RESET responde 422 RESET_LINK_INVALID y no se anuncia como válido")
        void inspect_tokenPasswordReset_noHabilitado_responde422ResetLinkInvalid() throws Exception {
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenReturn(new TokenInspectionResult.Invalid());

            mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s"
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("/problems/reset-link-invalid"))
                    .andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"))
                    .andExpect(jsonPath("$.detail").value("El enlace ha vencido o ya fue utilizado."));

            verify(inspectResetToken).inspect(VALID_TOKEN);
        }
    }

    // =========================================================================
    // POST /api/v1/auth/password-reset/complete
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/auth/password-reset/complete")
    class CompleteEndpoint {

        private static final String VALID_PASSWORD = "Correct-Horse-Battery-Staple-2026!";

        @Test
        @DisplayName("204 No Content: completado exitoso sin cuerpo, restablece rate limit de IP")
        void complete_exitoso_responde204YReseteaRateLimit() throws Exception {
            String clientIp = "192.168.1.99";
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenReturn(new TokenInspectionResult.Valid(ResetTokenPurpose.INITIAL_SETUP));
            when(completeInitialPasswordSetup.complete(VALID_TOKEN, VALID_PASSWORD))
                    .thenReturn(new PasswordSetupResult.Success());

            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .with(req -> {
                                req.setRemoteAddr(clientIp);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": "%s"
                                    }
                                    """.formatted(VALID_TOKEN, VALID_PASSWORD)))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string(""));

            verify(inspectResetToken).inspect(VALID_TOKEN);
            verify(completeInitialPasswordSetup).complete(VALID_TOKEN, VALID_PASSWORD);
            verify(rateLimiter).resetComplete(clientIp);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: cuerpo ausente genera errores para token y newPassword")
        void complete_cuerpoAusente_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors", hasSize(2)));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: token ausente")
        void complete_tokenAusente_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "newPassword": "%s"
                                    }
                                    """.formatted(VALID_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors", hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].field").value("token"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: newPassword ausente")
        void complete_passwordAusente_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s"
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors", hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("422 RESET_LINK_INVALID: pre-inspección inválida short-circuits sin invocar complete")
        void complete_preInspeccionInvalida_shortCircuits() throws Exception {
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenReturn(new TokenInspectionResult.Invalid());

            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": "%s"
                                    }
                                    """.formatted(VALID_TOKEN, VALID_PASSWORD)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.type").value("/problems/reset-link-invalid"))
                    .andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"))
                    .andExpect(jsonPath("$.detail").value("El enlace ha vencido o ya fue utilizado."));

            verify(inspectResetToken).inspect(VALID_TOKEN);
            // Crítico: no se invocó complete, protegiendo HIBP y CPU Argon2id
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("422 RESET_LINK_INVALID: complete devuelve InvalidToken (carrera tras inspección)")
        void complete_completeDevuelveInvalidToken_responde422() throws Exception {
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenReturn(new TokenInspectionResult.Valid(ResetTokenPurpose.INITIAL_SETUP));
            when(completeInitialPasswordSetup.complete(VALID_TOKEN, VALID_PASSWORD))
                    .thenReturn(new PasswordSetupResult.InvalidToken());

            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": "%s"
                                    }
                                    """.formatted(VALID_TOKEN, VALID_PASSWORD)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.type").value("/problems/reset-link-invalid"))
                    .andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"));
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: violaciones de política de contraseña tipadas en newPassword")
        void complete_violacionPolitica_responde400ConErroresTipados() throws Exception {
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenReturn(new TokenInspectionResult.Valid(ResetTokenPurpose.INITIAL_SETUP));
            when(completeInitialPasswordSetup.complete(VALID_TOKEN, "corta"))
                    .thenReturn(new PasswordSetupResult.PolicyViolation(List.of(
                            ViolacionPolitica.DEMASIADO_CORTA,
                            ViolacionPolitica.CONTRASENA_BLOQUEADA
                    )));

            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": "corta"
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors", hasSize(2)))
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.errors[0].code").value("IDENTITY_PASSWORD_TOO_SHORT"))
                    .andExpect(jsonPath("$.errors[1].field").value("newPassword"))
                    .andExpect(jsonPath("$.errors[1].code").value("IDENTITY_PASSWORD_BLOCKED"));
        }

        @Test
        @DisplayName("429 RATE_LIMITED: limitador de complete rechaza con Retry-After y no llama a use cases")
        void complete_rateLimitExcedido_responde429() throws Exception {
            when(rateLimiter.tryAcquireComplete(anyString()))
                    .thenReturn(new PasswordResetRateLimiter.RateLimitResult.Rejected(40));

            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": "%s"
                                    }
                                    """.formatted(VALID_TOKEN, VALID_PASSWORD)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "40"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("503 SERVICE_UNAVAILABLE: indisponibilidad de HIBP (PasswordBlocklistUnavailableException)")
        void complete_blocklistUnavailable_responde503() throws Exception {
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenReturn(new TokenInspectionResult.Valid(ResetTokenPurpose.INITIAL_SETUP));
            when(completeInitialPasswordSetup.complete(VALID_TOKEN, VALID_PASSWORD))
                    .thenThrow(new PasswordBlocklistUnavailableException());

            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": "%s"
                                    }
                                    """.formatted(VALID_TOKEN, VALID_PASSWORD)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.type").value("/problems/service-unavailable"))
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        }

        @Test
        @DisplayName("503 SERVICE_UNAVAILABLE: error técnico de persistencia (PasswordSetupDataAccessException)")
        void complete_dataAccessException_responde503() throws Exception {
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenReturn(new TokenInspectionResult.Valid(ResetTokenPurpose.INITIAL_SETUP));
            when(completeInitialPasswordSetup.complete(VALID_TOKEN, VALID_PASSWORD))
                    .thenThrow(new PasswordSetupDataAccessException(new RuntimeException("Error DB")));

            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": "%s"
                                    }
                                    """.formatted(VALID_TOKEN, VALID_PASSWORD)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: JSON malformado (sintaxis rota) sin eco de datos")
        void complete_jsonMalformado_responde400SinEcoDeDatos() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"token\": \"abc\", "))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: cuerpo vacío con cadena vacía genera errores para token y newPassword")
        void complete_cuerpoVacioString_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors", hasSize(2)));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: cuerpo solo espacios genera errores para token y newPassword")
        void complete_cuerpoSoloEspacios_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("   "))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors", hasSize(2)));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: raíz JSON no es un objeto")
        void complete_rootJsonNoObjeto_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[1, 2, 3]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: token tipo número")
        void complete_tokenTipoIncorrectoNumero_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": 12345,
                                      "newPassword": "%s"
                                    }
                                    """.formatted(VALID_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("token"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: newPassword tipo número")
        void complete_passwordTipoIncorrectoNumero_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": 12345
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: newPassword tipo booleano")
        void complete_passwordTipoIncorrectoBooleano_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": true
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: newPassword tipo array")
        void complete_passwordTipoIncorrectoArray_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": ["pass"]
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: newPassword tipo objeto")
        void complete_passwordTipoIncorrectoObjeto_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": {}
                                    }
                                    """.formatted(VALID_TOKEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: Content-Type no compatible text/plain")
        void complete_contentTypeIncompatible_textPlain_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("token=%s&newPassword=%s".formatted(VALID_TOKEN, VALID_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: Content-Type no compatible application/xml")
        void complete_contentTypeIncompatible_applicationXml_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<request></request>"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("400 VALIDATION_FAILED: Content-Type ausente con cuerpo")
        void complete_contentTypeAusente_responde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": "%s"
                                    }
                                    """.formatted(VALID_TOKEN, VALID_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(inspectResetToken);
            verifyNoInteractions(completeInitialPasswordSetup);
        }

        @Test
        @DisplayName("Contrato: token con propósito PASSWORD_RESET responde 422 RESET_LINK_INVALID y no ejecuta complete")
        void complete_tokenPasswordReset_noHabilitado_responde422ResetLinkInvalid() throws Exception {
            when(inspectResetToken.inspect(VALID_TOKEN))
                    .thenReturn(new TokenInspectionResult.Invalid());

            mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s",
                                      "newPassword": "%s"
                                    }
                                    """.formatted(VALID_TOKEN, VALID_PASSWORD)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.type").value("/problems/reset-link-invalid"))
                    .andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"))
                    .andExpect(jsonPath("$.detail").value("El enlace ha vencido o ya fue utilizado."));

            verify(inspectResetToken).inspect(VALID_TOKEN);
            verifyNoInteractions(completeInitialPasswordSetup);
        }
    }
}
