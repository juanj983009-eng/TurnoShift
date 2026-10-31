package com.turnoshift.identity.infrastructure.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Benchmark reproducible para los parámetros candidatos de Argon2id.
 *
 * <p>Ejecuta un calentamiento seguido de varias mediciones sobre una contraseña
 * sintética fija. Registra mediana y rango sin imprimir contraseña ni hash.
 *
 * <p><b>No es un test de CI</b>; no se invoca desde Surefire ni Failsafe.
 * Ejecutar manualmente para verificar el costo en el hardware objetivo.
 *
 * <p>Si la mediana está fuera de 500–1000 ms, ajustar los parámetros en
 * {@link Argon2PasswordService} y documentar en DECISIONS.md.
 * No bajar silenciosamente memoria o iteraciones.
 *
 * <pre>{@code
 * # Ejecución manual desde D:\Proyectos\TurnoShift\backend
 * $env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
 * cmd /c "mvnw.cmd exec:java -Dexec.mainClass=com.turnoshift.identity.infrastructure.security.Argon2Benchmark"
 * }</pre>
 */
public final class Argon2Benchmark {

    /** Contraseña sintética de longitud fija; nunca una contraseña real de usuario. */
    private static final String CONTRASENA_SINTETICA =
            "BenchmarkSyntheticPassword-TurnoShift-2026-XXXXXX";

    private static final int CALENTAMIENTO = 3;
    private static final int MEDICIONES    = 7;

    private Argon2Benchmark() {}

    public static void main(String[] args) {
        Argon2PasswordService servicio = new Argon2PasswordService();

        System.out.printf("%nBenchmark Argon2id — TurnoShift%n");
        System.out.printf("Parámetros: memoria=%d KiB (%d MiB), iteraciones=%d, paralelismo=%d, hash=%d bytes%n%n",
                Argon2PasswordService.MEMORIA_KIB,
                Argon2PasswordService.MEMORIA_KIB / 1024,
                Argon2PasswordService.ITERACIONES,
                Argon2PasswordService.PARALELISMO,
                Argon2PasswordService.LONGITUD_HASH_BYTES);

        System.out.printf("Calentamiento (%d iteraciones)...%n", CALENTAMIENTO);
        for (int i = 0; i < CALENTAMIENTO; i++) {
            servicio.codificar(CONTRASENA_SINTETICA);
            System.out.print(".");
        }
        System.out.println();

        List<Long> medicionesMs = new ArrayList<>();
        System.out.printf("%nMediciones (%d):%n", MEDICIONES);
        for (int i = 0; i < MEDICIONES; i++) {
            long inicio = System.nanoTime();
            servicio.codificar(CONTRASENA_SINTETICA);
            long ms = (System.nanoTime() - inicio) / 1_000_000L;
            medicionesMs.add(ms);
            System.out.printf("  Iteración %d: %d ms%n", i + 1, ms);
        }

        Collections.sort(medicionesMs);
        long mediana = medicionesMs.get(MEDICIONES / 2);
        long minimo  = medicionesMs.get(0);
        long maximo  = medicionesMs.get(MEDICIONES - 1);

        System.out.printf("%nResultados: mediana=%d ms, rango=[%d, %d] ms%n", mediana, minimo, maximo);
        System.out.printf("Objetivo: 500–1000 ms. ");
        if (mediana >= 500 && mediana <= 1000) {
            System.out.printf("DENTRO del objetivo.%n");
        } else if (mediana < 500) {
            System.out.printf("POR DEBAJO del objetivo — considerar aumentar memoria o iteraciones.%n");
        } else {
            System.out.printf("POR ENCIMA del objetivo — considerar reducir memoria o iteraciones.%n");
        }
        System.out.printf("%nDocumentar resultado en DECISIONS.md (DEC-08-ARGON2ID).%n%n");
    }
}
