package com.turnoshift.identity.infrastructure.client;

import com.turnoshift.identity.domain.PasswordBlocklistUnavailableException;
import com.turnoshift.identity.domain.PasswordPolicy;
import com.turnoshift.identity.domain.ViolacionPolitica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Unit: HibpPasswordBlocklist — cliente k-anonymity Have I Been Pwned")
class HibpPasswordBlocklistTest {

    private static final String BASE_URL = "https://api.pwnedpasswords.com/range/";
    private static final Duration TIMEOUT = Duration.ofMillis(2000);
    private static final String USER_AGENT = "TurnoShift/0.1";

    private HttpClient httpClient;
    private HttpResponse<String> httpResponse;
    private HibpPasswordBlocklist blocklist;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        httpClient = mock(HttpClient.class);
        httpResponse = mock(HttpResponse.class);
        blocklist = new HibpPasswordBlocklist(httpClient, BASE_URL, TIMEOUT, USER_AGENT);
    }

    private String sha1HexUpper(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest).toUpperCase(Locale.ROOT);
    }

    @Test
    @DisplayName("1. Contraseña comprometida presente en el rango: retorna true y PasswordPolicy devuelve CONTRASENA_BLOQUEADA")
    void contrasenaComprometida_retornaTrueYViolacionBlocked() throws Exception {
        String contrasena = "password12345678";
        String fullSha1 = sha1HexUpper(contrasena);
        String prefix = fullSha1.substring(0, 5);
        String suffix = fullSha1.substring(5);

        String mockResponseBody = "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n"
                + suffix + ":42\r\n"
                + "00D4F6E8DA6EEC1059AC801DAC7524F556A:3\r\n";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(mockResponseBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        boolean estaBloqueada = blocklist.estaBloquada(contrasena);
        assertThat(estaBloqueada).isTrue();

        List<ViolacionPolitica> violaciones = PasswordPolicy.validar(contrasena, blocklist);
        assertThat(violaciones).containsExactly(ViolacionPolitica.CONTRASENA_BLOQUEADA);
    }

    @Test
    @DisplayName("2. Contraseña ausente en respuesta válida de HIBP: retorna false y PasswordPolicy es válida")
    void contrasenaNoComprometida_retornaFalseYPasswordPolicyValida() throws Exception {
        String contrasena = "contraseñaSeguraCon15Caracteres!";
        String fullSha1 = sha1HexUpper(contrasena);
        String suffix = fullSha1.substring(5);

        // Respuesta sin el sufijo de la contraseña
        String mockResponseBody = "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n"
                + "00D4F6E8DA6EEC1059AC801DAC7524F556A:3\r\n";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(mockResponseBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        boolean estaBloqueada = blocklist.estaBloquada(contrasena);
        assertThat(estaBloqueada).isFalse();

        List<ViolacionPolitica> violaciones = PasswordPolicy.validar(contrasena, blocklist);
        assertThat(violaciones).isEmpty();
    }

    @Test
    @DisplayName("3 y 4. Solicitud usa exclusivamente prefijo de 5 caracteres; jamás envía contraseña ni SHA-1 completo")
    void solicitudUsaExclusivamentePrefijo5Caracteres() throws Exception {
        String contrasena = "MiContrasenaEmpresarial2026";
        String fullSha1 = sha1HexUpper(contrasena);
        String prefix = fullSha1.substring(0, 5);
        String suffix = fullSha1.substring(5);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("0018A45C4D1DEF81644B54AB7F969B88D65:1\n");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        blocklist.estaBloquada(contrasena);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        HttpRequest captured = requestCaptor.getValue();
        URI uri = captured.uri();

        assertThat(uri.toString()).isEqualTo(BASE_URL + prefix);
        assertThat(uri.toString()).doesNotContain(suffix);
        assertThat(uri.toString()).doesNotContain(contrasena);
        assertThat(captured.method()).isEqualTo("GET");
        assertThat(captured.headers().firstValue("Add-Padding")).hasValue("true");
        assertThat(captured.headers().firstValue("User-Agent")).hasValue(USER_AGENT);
    }

    @Test
    @DisplayName("5. Comparación de sufijos funciona con mayúsculas y minúsculas")
    void comparacionSufijos_insensibleAMayusculasYMinusculas() throws Exception {
        String contrasena = "password12345678";
        String fullSha1 = sha1HexUpper(contrasena);
        String suffixLowercase = fullSha1.substring(5).toLowerCase(Locale.ROOT);

        String mockResponseBody = suffixLowercase + ":10\n";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(mockResponseBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThat(blocklist.estaBloquada(contrasena)).isTrue();
    }

    @Test
    @DisplayName("6. Respuesta corrupta o que no cumple SUFFIX:COUNT falla cerrado con PasswordBlocklistUnavailableException")
    void respuestaCorrupta_fallaCerrado() throws Exception {
        String contrasena = "contraseñaValida15";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("LINEA_CORRUPTA_SIN_DOS_PUNTOS\n");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> blocklist.estaBloquada(contrasena))
                .isInstanceOf(PasswordBlocklistUnavailableException.class)
                .hasMessageContaining("[IDENTITY_BLOCKLIST_UNAVAILABLE]");
    }

    @Test
    @DisplayName("6b. Respuesta con sufijo que no tiene 35 caracteres hex falla cerrado")
    void respuestaConSufijoInvalido_fallaCerrado() throws Exception {
        String contrasena = "contraseñaValida15";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("SHORT_SUFFIX:5\n");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> blocklist.estaBloquada(contrasena))
                .isInstanceOf(PasswordBlocklistUnavailableException.class);
    }

    @Test
    @DisplayName("6c. Respuesta con conteo no numérico falla cerrado")
    void respuestaConConteoNoNumerico_fallaCerrado() throws Exception {
        String contrasena = "contraseñaValida15";
        String suffix35 = "0".repeat(35);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(suffix35 + ":NO_ES_NUMERO\n");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> blocklist.estaBloquada(contrasena))
                .isInstanceOf(PasswordBlocklistUnavailableException.class);
    }

    @Test
    @DisplayName("7a. Timeout de conexión o lectura falla cerrado con PasswordBlocklistUnavailableException")
    void timeout_fallaCerrado() throws Exception {
        String contrasena = "contraseñaValida15";

        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new HttpTimeoutException("request timed out"));

        assertThatThrownBy(() -> blocklist.estaBloquada(contrasena))
                .isInstanceOf(PasswordBlocklistUnavailableException.class)
                .hasMessageContaining("[IDENTITY_BLOCKLIST_UNAVAILABLE]");
    }

    @Test
    @DisplayName("7b. Error de E/S de red falla cerrado con PasswordBlocklistUnavailableException")
    void errorRedIoException_fallaCerrado() throws Exception {
        String contrasena = "contraseñaValida15";

        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("connection reset by peer"));

        assertThatThrownBy(() -> blocklist.estaBloquada(contrasena))
                .isInstanceOf(PasswordBlocklistUnavailableException.class)
                .hasMessageContaining("[IDENTITY_BLOCKLIST_UNAVAILABLE]");
    }

    @Test
    @DisplayName("7c. InterruptedException restaura flag de interrupción y falla cerrado")
    void interrupted_restauraFlagYFallaCerrado() throws Exception {
        String contrasena = "contraseñaValida15";

        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new InterruptedException("interrupted"));

        Thread.interrupted(); // Limpia estado previo por si acaso
        assertThatThrownBy(() -> blocklist.estaBloquada(contrasena))
                .isInstanceOf(PasswordBlocklistUnavailableException.class)
                .hasMessageContaining("[IDENTITY_BLOCKLIST_UNAVAILABLE]");

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // Limpia el flag para no afectar otras pruebas
    }

    @Test
    @DisplayName("8. Respuestas HTTP no exitosas (400, 429, 500, 503) fallan cerrado")
    void httpNoExitoso_fallaCerrado() throws Exception {
        String contrasena = "contraseñaValida15";

        for (int statusCode : List.of(400, 429, 500, 502, 503)) {
            when(httpResponse.statusCode()).thenReturn(statusCode);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            assertThatThrownBy(() -> blocklist.estaBloquada(contrasena))
                    .isInstanceOf(PasswordBlocklistUnavailableException.class)
                    .hasMessageContaining("[IDENTITY_BLOCKLIST_UNAVAILABLE]");
        }
    }

    @Test
    @DisplayName("9. PasswordPolicy propaga el fallo cerrado sin capturarlo ni considerarlo válido")
    void passwordPolicy_propagaFalloCerrado() throws Exception {
        String contrasena = "validaContrasena123456";

        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new HttpConnectTimeoutException("connect timeout"));

        assertThatThrownBy(() -> PasswordPolicy.validar(contrasena, blocklist))
                .isInstanceOf(PasswordBlocklistUnavailableException.class);
    }

    @Test
    @DisplayName("10. Mensaje de excepción nunca contiene la contraseña, hash completo ni prefijo")
    void excepcionNoFiltraDatosSensibles() throws Exception {
        String contrasena = "SuperSecreta1234!";
        String fullSha1 = sha1HexUpper(contrasena);
        String prefix = fullSha1.substring(0, 5);

        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("sensitive remote error"));

        try {
            blocklist.estaBloquada(contrasena);
        } catch (PasswordBlocklistUnavailableException e) {
            assertThat(e.getMessage()).doesNotContain(contrasena);
            assertThat(e.getMessage()).doesNotContain(fullSha1);
            assertThat(e.getMessage()).doesNotContain(prefix);
            assertThat(e.getMessage()).doesNotContain("sensitive remote error");
        }
    }

    @Test
    @DisplayName("11a. Respuesta HTTP 200 con body vacío (\"\") falla cerrado")
    void respuesta200BodyVacio_fallaCerrado() throws Exception {
        String contrasena = "contraseñaValida15";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> blocklist.estaBloquada(contrasena))
                .isInstanceOf(PasswordBlocklistUnavailableException.class);
    }

    @Test
    @DisplayName("11b. Respuesta HTTP 200 con body con solo espacios falla cerrado")
    void respuesta200BodyConEspacios_fallaCerrado() throws Exception {
        String contrasena = "contraseñaValida15";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("     \t  ");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> blocklist.estaBloquada(contrasena))
                .isInstanceOf(PasswordBlocklistUnavailableException.class);
    }

    @Test
    @DisplayName("11c. Respuesta HTTP 200 con body solo con saltos de línea falla cerrado")
    void respuesta200BodySoloSaltosDeLinea_fallaCerrado() throws Exception {
        String contrasena = "contraseñaValida15";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("\r\n\n\r\n\r");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> blocklist.estaBloquada(contrasena))
                .isInstanceOf(PasswordBlocklistUnavailableException.class);
    }

    @Test
    @DisplayName("12. PasswordBlocklistUnavailableException solo expone mensaje fijo predeterminado y rechaza mensajes externos")
    void excepcionInmutableSinMensajeExterno() {
        PasswordBlocklistUnavailableException eDefault = new PasswordBlocklistUnavailableException();
        assertThat(eDefault.getMessage()).isEqualTo(
                "[IDENTITY_BLOCKLIST_UNAVAILABLE] El servicio de verificación de contraseñas no está disponible temporalmente.");

        Throwable causeConDatosSensibles = new RuntimeException("password=superSecret123&hash=sensitiveData");
        PasswordBlocklistUnavailableException eWithCause = new PasswordBlocklistUnavailableException(causeConDatosSensibles);

        assertThat(eWithCause.getMessage()).isEqualTo(
                "[IDENTITY_BLOCKLIST_UNAVAILABLE] El servicio de verificación de contraseñas no está disponible temporalmente.");
        assertThat(eWithCause.getMessage()).doesNotContain("superSecret123");
        assertThat(eWithCause.getMessage()).doesNotContain("sensitiveData");
        assertThat(eWithCause.getCause()).isSameAs(causeConDatosSensibles);

        // Verifica que no existen constructores públicos que acepten String
        boolean hasStringConstructor = java.util.Arrays.stream(PasswordBlocklistUnavailableException.class.getConstructors())
                .anyMatch(c -> java.util.Arrays.asList(c.getParameterTypes()).contains(String.class));
        assertThat(hasStringConstructor).isFalse();
    }
}
