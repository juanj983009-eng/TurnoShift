package com.turnoshift.operator.infrastructure;

import com.turnoshift.operator.application.BootstrapConfigurationException;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.PlatformOperator;
import com.turnoshift.operator.domain.PlatformOperatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Unit: TransactionalOperatorInserter — insercion transaccional bajo sp_getapplock")
class TransactionalOperatorInserterTest {

    private PlatformOperatorRepository repository;
    private JdbcTemplate jdbcTemplate;
    private TransactionalOperatorInserter inserter;

    private static final String EMAIL = "admin@turnoshift.com";
    private static final String NORMALIZED_EMAIL = "admin@turnoshift.com";
    private static final String DISPLAY_NAME = "Operador Plataforma";
    private static final String HASH = "$argon2id$v=19$m=131072,t=3,p=1$fakehash$fakesalt";

    @BeforeEach
    void setUp() {
        repository = mock(PlatformOperatorRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        inserter = new TransactionalOperatorInserter(repository, jdbcTemplate);

        // Por defecto, sp_getapplock concede el bloqueo con retorno 0
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
    }

    @Test
    @DisplayName("repository.count() falla con OperatorDataAccessException: termina con BootstrapConfigurationException sin causa ni detalles internos")
    void countFallaConOperatorDataAccessException_lanzaBootstrapConfigurationExceptionSinCausa() {
        when(repository.count()).thenThrow(new OperatorDataAccessException(
                new RuntimeException("secret-connection-string jdbc:sqlserver://secret-host:1433;user=sa;password=sensitive-pass")));

        assertThatThrownBy(() -> inserter.insertFirstOperatorIfNone(EMAIL, NORMALIZED_EMAIL, DISPLAY_NAME, HASH))
                .isInstanceOf(BootstrapConfigurationException.class)
                .hasMessage("[OPERATOR_BOOTSTRAP_CONFIG_ERROR] El bootstrap del operador de plataforma falló. Revisa la configuración de las variables de entorno requeridas.")
                .hasNoCause();

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("repository.save() falla con OperatorDataAccessException: termina con BootstrapConfigurationException sin causa ni detalles internos")
    void saveFallaConOperatorDataAccessException_lanzaBootstrapConfigurationExceptionSinCausa() {
        when(repository.count()).thenReturn(0L);
        when(repository.save(any(PlatformOperator.class))).thenThrow(new OperatorDataAccessException(
                new RuntimeException("SQL syntax error near SELECT * FROM secret_table")));

        assertThatThrownBy(() -> inserter.insertFirstOperatorIfNone(EMAIL, NORMALIZED_EMAIL, DISPLAY_NAME, HASH))
                .isInstanceOf(BootstrapConfigurationException.class)
                .hasMessage("[OPERATOR_BOOTSTRAP_CONFIG_ERROR] El bootstrap del operador de plataforma falló. Revisa la configuración de las variables de entorno requeridas.")
                .hasNoCause();

        verify(repository).save(any(PlatformOperator.class));
    }

    @Test
    @DisplayName("Si ya existe operador: omite la insercion silenciosamente sin error")
    void operadorExiste_omiteInsercionSinError() {
        when(repository.count()).thenReturn(1L);

        inserter.insertFirstOperatorIfNone(EMAIL, NORMALIZED_EMAIL, DISPLAY_NAME, HASH);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Si no existe operador: inserta el primer operador exitosamente")
    void operadorNoExiste_insertaExitosamente() {
        when(repository.count()).thenReturn(0L);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PlatformOperator saved = new PlatformOperator(UUID.randomUUID(), EMAIL, NORMALIZED_EMAIL, DISPLAY_NAME, HASH, true, 1L, 0L, now, now);
        when(repository.save(any(PlatformOperator.class))).thenReturn(saved);

        inserter.insertFirstOperatorIfNone(EMAIL, NORMALIZED_EMAIL, DISPLAY_NAME, HASH);

        verify(repository).save(any(PlatformOperator.class));
    }

    @Test
    @DisplayName("sp_getapplock falla: termina con BootstrapConfigurationException sin causa")
    void spGetapplockFalla_lanzaBootstrapConfigurationExceptionSinCausa() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(-1);

        assertThatThrownBy(() -> inserter.insertFirstOperatorIfNone(EMAIL, NORMALIZED_EMAIL, DISPLAY_NAME, HASH))
                .isInstanceOf(BootstrapConfigurationException.class)
                .hasMessage("[OPERATOR_BOOTSTRAP_CONFIG_ERROR] El bootstrap del operador de plataforma falló. Revisa la configuración de las variables de entorno requeridas.")
                .hasNoCause();

        verify(repository, never()).count();
        verify(repository, never()).save(any());
    }
}
