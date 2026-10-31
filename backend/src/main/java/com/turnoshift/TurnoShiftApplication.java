package com.turnoshift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Punto de entrada de la aplicación TurnoShift.
 *
 * <p><b>exclude = UserDetailsServiceAutoConfiguration:</b>
 * Desactiva la generación del usuario predeterminado de Spring Security.
 * Clase verificada en el JAR de {@code spring-boot-security:4.0.0}
 * ({@code org.springframework.boot.security.autoconfigure}).
 * Sin esta exclusión, Spring Boot genera una contraseña aleatoria en el log al arrancar.
 * La autenticación de usuarios empresariales se implementa mediante {@code app_users}
 * y el flujo de identidad de I1; no hay {@code InMemoryUserDetailsManager}.
 */
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class TurnoShiftApplication {
    public static void main(String[] args) {
        SpringApplication.run(TurnoShiftApplication.class, args);
    }
}
