package com.turnoshift.identity.domain;

import java.util.Set;

/**
 * Implementación de prueba de {@link PasswordBlocklist} con un conjunto fijo.
 * Solo para pruebas unitarias — nunca en producción.
 */
public final class FakeBlocklist implements PasswordBlocklist {

    private final Set<String> bloqueadas;

    public FakeBlocklist(Set<String> bloqueadas) {
        this.bloqueadas = Set.copyOf(bloqueadas);
    }

    @Override
    public boolean estaBloquada(String contrasena) {
        return bloqueadas.contains(contrasena);
    }
}
