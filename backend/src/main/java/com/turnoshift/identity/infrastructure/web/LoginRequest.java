package com.turnoshift.identity.infrastructure.web;

/**
 * Petición para inicio de sesión en POST /api/v1/auth/login.
 */
public record LoginRequest(
        String organizationCode,
        String email,
        String password
) {}
