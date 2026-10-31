package com.turnoshift.operator.infrastructure.persistence;

import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.PlatformOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Infraestructura: JdbcPlatformOperatorRepository (Unit)")
class JdbcPlatformOperatorRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JdbcPlatformOperatorRepository repository;

    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String EMAIL = "admin@turnoshift.com";
    private static final String NORM_EMAIL = "admin@turnoshift.com";
    private static final String NAME = "Operador Plataforma";
    private static final String HASH = "$argon2id$v=19$m=65536,t=3,p=1$fakehash";
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        repository = new JdbcPlatformOperatorRepository(jdbcTemplate);
    }

    private PlatformOperator sampleOperator() {
        return new PlatformOperator(OPERATOR_ID, EMAIL, NORM_EMAIL, NAME, HASH, true, 1L, 0L, NOW, NOW);
    }

    @Test
    @DisplayName("RowMapper mapea correctamente una fila válida")
    void rowMapper_conFilaValida_retornaPlatformOperator() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(OPERATOR_ID);
        when(rs.getString("email")).thenReturn(EMAIL);
        when(rs.getString("normalized_email")).thenReturn(NORM_EMAIL);
        when(rs.getString("display_name")).thenReturn(NAME);
        when(rs.getString("password_hash")).thenReturn(HASH);
        when(rs.getBoolean("is_active")).thenReturn(true);
        when(rs.getLong("auth_version")).thenReturn(1L);
        when(rs.getLong("lock_version")).thenReturn(0L);
        when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(NOW);
        when(rs.getObject("updated_at", OffsetDateTime.class)).thenReturn(NOW);

        PlatformOperator result = repository.getRowMapper().mapRow(rs, 1);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(OPERATOR_ID);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.normalizedEmail()).isEqualTo(NORM_EMAIL);
        assertThat(result.displayName()).isEqualTo(NAME);
        assertThat(result.passwordHash()).isEqualTo(HASH);
        assertThat(result.isActive()).isTrue();
        assertThat(result.authVersion()).isEqualTo(1L);
        assertThat(result.lockVersion()).isZero();
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("RowMapper lanza SQLException si faltan campos obligatorios en la fila")
    void rowMapper_conCamposFaltantes_lanzaSQLException() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(null);

        assertThatThrownBy(() -> repository.getRowMapper().mapRow(rs, 1))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("incompleta");
    }

    @Test
    @DisplayName("findById propaga OperatorDataAccessException ante DataAccessException")
    void findById_cuandoDataAccessException_propagaOperatorDataAccessException() {
        DataAccessException dae = new DataAccessException("DB failure") {};
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(OPERATOR_ID)))
                .thenThrow(dae);

        assertThatThrownBy(() -> repository.findById(OPERATOR_ID))
                .isInstanceOf(OperatorDataAccessException.class)
                .hasCause(dae);
    }

    @Test
    @DisplayName("findByNormalizedEmail propaga OperatorDataAccessException ante DataAccessException")
    void findByNormalizedEmail_cuandoDataAccessException_propagaOperatorDataAccessException() {
        DataAccessException dae = new DataAccessException("DB failure") {};
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(NORM_EMAIL)))
                .thenThrow(dae);

        assertThatThrownBy(() -> repository.findByNormalizedEmail(NORM_EMAIL))
                .isInstanceOf(OperatorDataAccessException.class)
                .hasCause(dae);
    }

    @Test
    @DisplayName("save propaga OperatorDataAccessException ante DataAccessException")
    void save_cuandoDataAccessException_propagaOperatorDataAccessException() {
        DataAccessException dae = new DataAccessException("DB failure") {};
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(dae);

        PlatformOperator operator = sampleOperator();
        assertThatThrownBy(() -> repository.save(operator))
                .isInstanceOf(OperatorDataAccessException.class)
                .hasCause(dae);
    }

    @Test
    @DisplayName("updateAuthVersion propaga OperatorDataAccessException ante DataAccessException")
    void updateAuthVersion_cuandoDataAccessException_propagaOperatorDataAccessException() {
        DataAccessException dae = new DataAccessException("DB failure") {};
        when(jdbcTemplate.update(anyString(), eq(2L), any(OffsetDateTime.class), eq(OPERATOR_ID), eq(1L)))
                .thenThrow(dae);

        assertThatThrownBy(() -> repository.updateAuthVersion(OPERATOR_ID, 1L, 2L))
                .isInstanceOf(OperatorDataAccessException.class)
                .hasCause(dae);
    }

    @Test
    @DisplayName("updateActiveStatus propaga OperatorDataAccessException ante DataAccessException")
    void updateActiveStatus_cuandoDataAccessException_propagaOperatorDataAccessException() {
        DataAccessException dae = new DataAccessException("DB failure") {};
        when(jdbcTemplate.update(anyString(), eq(false), any(OffsetDateTime.class), eq(OPERATOR_ID)))
                .thenThrow(dae);

        assertThatThrownBy(() -> repository.updateActiveStatus(OPERATOR_ID, false))
                .isInstanceOf(OperatorDataAccessException.class)
                .hasCause(dae);
    }

    @Test
    @DisplayName("count propaga OperatorDataAccessException ante DataAccessException")
    void count_cuandoDataAccessException_propagaOperatorDataAccessException() {
        DataAccessException dae = new DataAccessException("DB failure") {};
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(dae);

        assertThatThrownBy(() -> repository.count())
                .isInstanceOf(OperatorDataAccessException.class)
                .hasCause(dae);
    }

    @Test
    @DisplayName("Parámetros nulos en métodos de repositorio lanzan NullPointerException")
    void parametrosNulos_lanzanNullPointerException() {
        assertThatThrownBy(() -> repository.findById(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> repository.findByNormalizedEmail(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> repository.updateAuthVersion(null, 1L, 2L))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> repository.updateActiveStatus(null, false))
                .isInstanceOf(NullPointerException.class);
    }
}
