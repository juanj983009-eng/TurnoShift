package com.turnoshift.identity.infrastructure.client;

import com.turnoshift.identity.domain.PasswordBlocklist;
import com.turnoshift.identity.domain.PasswordBlocklistUnavailableException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Implementación de producción de {@link PasswordBlocklist} basada en la
 * API de rangos k-anonymity de Have I Been Pwned (Pwned Passwords).
 *
 * <p><b>Privacidad e invariantes de seguridad:</b>
 * <ul>
 *   <li>Usa SHA-1 exclusivamente porque el protocolo de rangos de HIBP lo exige.
 *       Esto NO reemplaza a Argon2id, el cual se utiliza para el almacenamiento seguro.</li>
 *   <li>La contraseña en texto plano JAMÁS se transmite fuera del servidor.</li>
 *   <li>El hash SHA-1 completo de 40 caracteres JAMÁS se transmite fuera del servidor.</li>
 *   <li>Solo se envía a HIBP el prefijo de los primeros 5 caracteres hexadecimales del SHA-1.</li>
 *   <li>La búsqueda del sufijo (últimos 35 caracteres) se ejecuta localmente en memoria.</li>
 *   <li>Se envía la cabecera {@code Add-Padding: true} para prevenir inferencias por tamaño de respuesta.</li>
 *   <li>No se registran en logs contraseñas, hashes, prefijos ni URLs completas.</li>
 * </ul>
 *
 * <p><b>Política de fallo cerrado (fail-closed):</b>
 * Si la API externa no está disponible, agota el timeout, devuelve un código HTTP no 200,
 * sufre un error de red o entrega una respuesta corrupta, se lanza
 * {@link PasswordBlocklistUnavailableException}. Nunca se asume una contraseña como segura
 * ante una falla de verificación externa.
 */
public final class HibpPasswordBlocklist implements PasswordBlocklist {

    private static final String DEFAULT_BASE_URL = "https://api.pwnedpasswords.com/range/";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(2000);
    private static final String DEFAULT_USER_AGENT = "TurnoShift/0.1";

    private static final int SHA1_HEX_LENGTH = 40;
    private static final int PREFIX_LENGTH = 5;
    private static final int SUFFIX_LENGTH = 35;

    private static final Pattern HEX_SUFFIX_PATTERN = Pattern.compile("^[0-9A-Fa-f]{35}$");
    private static final Pattern COUNT_PATTERN = Pattern.compile("^[0-9]+$");

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;
    private final String userAgent;

    public HibpPasswordBlocklist() {
        this(DEFAULT_BASE_URL, DEFAULT_TIMEOUT, DEFAULT_USER_AGENT);
    }

    public HibpPasswordBlocklist(String baseUrl, Duration timeout, String userAgent) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(timeout != null ? timeout : DEFAULT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                baseUrl,
                timeout,
                userAgent
        );
    }

    public HibpPasswordBlocklist(HttpClient httpClient, String baseUrl, Duration timeout, String userAgent) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient no puede ser nulo");
        this.baseUrl = normalizeBaseUrl(baseUrl != null ? baseUrl : DEFAULT_BASE_URL);
        this.timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
        this.userAgent = (userAgent != null && !userAgent.isBlank()) ? userAgent : DEFAULT_USER_AGENT;
    }

    @Override
    public boolean estaBloquada(String contrasenaEnTextoPlano) {
        Objects.requireNonNull(contrasenaEnTextoPlano, "contrasenaEnTextoPlano no puede ser nula");

        String sha1Hex = calcularSha1Hex(contrasenaEnTextoPlano);
        String prefix = sha1Hex.substring(0, PREFIX_LENGTH);
        String suffix = sha1Hex.substring(PREFIX_LENGTH);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + prefix))
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Add-Padding", "true")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PasswordBlocklistUnavailableException(e);
        } catch (IOException e) {
            throw new PasswordBlocklistUnavailableException(e);
        }

        if (response.statusCode() != 200) {
            throw new PasswordBlocklistUnavailableException();
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new PasswordBlocklistUnavailableException();
        }

        return buscarSufijoEnRespuesta(body, suffix);
    }

    private boolean buscarSufijoEnRespuesta(String body, String suffixTarget) {
        boolean matchFound = false;
        int validLinesCount = 0;

        String[] lines = body.split("\\R");
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }

            int colonIdx = line.indexOf(':');
            if (colonIdx != SUFFIX_LENGTH) {
                throw new PasswordBlocklistUnavailableException();
            }

            String lineSuffix = line.substring(0, SUFFIX_LENGTH);
            String countStr = line.substring(SUFFIX_LENGTH + 1);

            if (!HEX_SUFFIX_PATTERN.matcher(lineSuffix).matches() || !COUNT_PATTERN.matcher(countStr).matches()) {
                throw new PasswordBlocklistUnavailableException();
            }

            validLinesCount++;
            if (lineSuffix.equalsIgnoreCase(suffixTarget)) {
                matchFound = true;
            }
        }

        if (validLinesCount == 0) {
            throw new PasswordBlocklistUnavailableException();
        }

        return matchFound;
    }

    private String calcularSha1Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo criptográfico SHA-1 no disponible en la plataforma JVM", e);
        }
    }

    private static String normalizeBaseUrl(String url) {
        return url.endsWith("/") ? url : url + "/";
    }
}
