package com.turnoshift.identity.infrastructure.persistence;

import com.turnoshift.identity.domain.PasswordSetupDataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Unit: JdbcAppUserPasswordRepository — manejo seguro de DataAccessException y validaciones")
class JdbcAppUserPasswordRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcAppUserPasswordRepository repository;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String validHash = "$argon2id$v=19$dummy";
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new JdbcAppUserPasswordRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("setInitialPassword traduce DataAccessException a PasswordSetupDataAccessException")
    void setInitialPassword_traduceDataAccessException() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> repository.setInitialPassword(orgId, userId, validHash, now))
                .isInstanceOf(PasswordSetupDataAccessException.class)
                .hasMessageContaining("[IDENTITY_PASSWORD_SETUP_DATA_ACCESS_ERROR]");
    }

    @Test
    @DisplayName("setInitialPassword rechaza hash mayor a 512 caracteres")
    void setInitialPassword_rechazaHashMayorA512() {
        String longHash = "x".repeat(513);

        assertThatThrownBy(() -> repository.setInitialPassword(orgId, userId, longHash, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("setInitialPassword rechaza parámetros nulos")
    void setInitialPassword_rechazaParametrosNulos() {
        assertThatThrownBy(() -> repository.setInitialPassword(null, userId, validHash, now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.setInitialPassword(orgId, null, validHash, now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.setInitialPassword(orgId, userId, null, now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.setInitialPassword(orgId, userId, validHash, null))
                .isInstanceOf(NullPointerException.class);
    }
}
