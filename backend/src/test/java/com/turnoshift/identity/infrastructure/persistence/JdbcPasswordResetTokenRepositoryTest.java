package com.turnoshift.identity.infrastructure.persistence;

import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.TokenDataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Unit: JdbcPasswordResetTokenRepository — manejo seguro de DataAccessException")
class JdbcPasswordResetTokenRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcPasswordResetTokenRepository repository;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();
    private final byte[] hash = new byte[32];
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new JdbcPasswordResetTokenRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("findEligibility traduce DataAccessException a TokenDataAccessException")
    void findEligibility_traduceDataAccessException() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> repository.findEligibility(orgId, userId))
                .isInstanceOf(TokenDataAccessException.class)
                .hasMessageContaining("[IDENTITY_TOKEN_DATA_ACCESS_ERROR]");
    }

    @Test
    @DisplayName("findEligibilityForEmission traduce DataAccessException a TokenDataAccessException")
    void findEligibilityForEmission_traduceDataAccessException() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> repository.findEligibilityForEmission(orgId, userId))
                .isInstanceOf(TokenDataAccessException.class)
                .hasMessageContaining("[IDENTITY_TOKEN_DATA_ACCESS_ERROR]");
    }

    @Test
    @DisplayName("revokeUnconsumedTokens traduce DataAccessException a TokenDataAccessException")
    void revokeUnconsumedTokens_traduceDataAccessException() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> repository.revokeUnconsumedTokens(orgId, userId, ResetTokenPurpose.INITIAL_SETUP, now))
                .isInstanceOf(TokenDataAccessException.class)
                .hasMessageContaining("[IDENTITY_TOKEN_DATA_ACCESS_ERROR]");
    }

    @Test
    @DisplayName("insertToken traduce DataAccessException a TokenDataAccessException")
    void insertToken_traduceDataAccessException() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> repository.insertToken(tokenId, orgId, userId, hash, ResetTokenPurpose.INITIAL_SETUP, now, now))
                .isInstanceOf(TokenDataAccessException.class)
                .hasMessageContaining("[IDENTITY_TOKEN_DATA_ACCESS_ERROR]");
    }

    @Test
    @DisplayName("findCandidateForInspection traduce DataAccessException a TokenDataAccessException")
    void findCandidateForInspection_traduceDataAccessException() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> repository.findCandidateForInspection(hash))
                .isInstanceOf(TokenDataAccessException.class)
                .hasMessageContaining("[IDENTITY_TOKEN_DATA_ACCESS_ERROR]");
    }

    @Test
    @DisplayName("consumeToken traduce DataAccessException a TokenDataAccessException")
    void consumeToken_traduceDataAccessException() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> repository.consumeToken(hash, ResetTokenPurpose.INITIAL_SETUP, now))
                .isInstanceOf(TokenDataAccessException.class)
                .hasMessageContaining("[IDENTITY_TOKEN_DATA_ACCESS_ERROR]");
    }
}
