package com.turnoshift.operator.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: PlatformOperatorIdentity, OperatorAuthenticationOutcome — dominio del operador")
class PlatformOperatorIdentityTest {

    @Test
    @DisplayName("getName() retorna el UUID del operador como string de 36 chars")
    void getName_retornaUuidComoString() {
        UUID id = UUID.randomUUID();
        PlatformOperatorIdentity identity = new PlatformOperatorIdentity(id, "Administrador", 1L);
        assertThat(identity.getName()).isEqualTo(id.toString());
        assertThat(identity.getName()).hasSize(36);
    }

    @Test
    @DisplayName("toString() no expone el id del operador")
    void toString_noExponeId() {
        UUID id = UUID.randomUUID();
        PlatformOperatorIdentity identity = new PlatformOperatorIdentity(id, "Admin Op", 1L);
        assertThat(identity.toString()).doesNotContain(id.toString());
    }

    @Test
    @DisplayName("toString() no expone email, @ ni hash")
    void toString_noExponeEmail() {
        PlatformOperatorIdentity identity = new PlatformOperatorIdentity(UUID.randomUUID(), "Admin", 3L);
        assertThat(identity.toString()).doesNotContain("@");
        assertThat(identity.toString()).doesNotContain("email");
        assertThat(identity.toString()).doesNotContain("hash");
    }

    @Test
    @DisplayName("Implementa Serializable")
    void implementaSerializable() {
        PlatformOperatorIdentity identity = new PlatformOperatorIdentity(UUID.randomUUID(), "Admin", 1L);
        assertThat(identity).isInstanceOf(Serializable.class);
    }

    @Test
    @DisplayName("Implementa Principal")
    void implementaPrincipal() {
        PlatformOperatorIdentity identity = new PlatformOperatorIdentity(UUID.randomUUID(), "Op", 2L);
        assertThat(identity).isInstanceOf(java.security.Principal.class);
    }

    @Test
    @DisplayName("Validacion: id nulo lanza NPE")
    void validacion_idNulo() {
        assertThatThrownBy(() -> new PlatformOperatorIdentity(null, "Op", 1L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Validacion: displayName nulo lanza NPE")
    void validacion_displayNameNulo() {
        assertThatThrownBy(() -> new PlatformOperatorIdentity(UUID.randomUUID(), null, 1L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Validacion: displayName blanco lanza IAE")
    void validacion_displayNameBlanco() {
        assertThatThrownBy(() -> new PlatformOperatorIdentity(UUID.randomUUID(), "  ", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Validacion: authVersion negativa lanza IAE")
    void validacion_authVersionNegativa() {
        assertThatThrownBy(() -> new PlatformOperatorIdentity(UUID.randomUUID(), "Op", -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("success() retorna SUCCESS con identity")
    void outcome_success() {
        PlatformOperatorIdentity identity = new PlatformOperatorIdentity(UUID.randomUUID(), "Admin", 1L);
        OperatorAuthenticationOutcome outcome = OperatorAuthenticationOutcome.success(identity);
        assertThat(outcome.result()).isEqualTo(OperatorAuthenticationResult.SUCCESS);
        assertThat(outcome.identity()).isSameAs(identity);
    }

    @Test
    @DisplayName("invalidCredentials() sin identity")
    void outcome_invalidCredentials() {
        OperatorAuthenticationOutcome outcome = OperatorAuthenticationOutcome.invalidCredentials();
        assertThat(outcome.result()).isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
        assertThat(outcome.identity()).isNull();
    }

    @Test
    @DisplayName("dataAccessError() sin identity")
    void outcome_dataAccessError() {
        OperatorAuthenticationOutcome outcome = OperatorAuthenticationOutcome.dataAccessError();
        assertThat(outcome.result()).isEqualTo(OperatorAuthenticationResult.DATA_ACCESS_ERROR);
        assertThat(outcome.identity()).isNull();
    }

    @Test
    @DisplayName("success() con identity nula lanza NPE")
    void outcome_success_identityNulaNPE() {
        assertThatThrownBy(() -> OperatorAuthenticationOutcome.success(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("outcome toString no expone id")
    void outcome_toString_seguro() {
        UUID id = UUID.randomUUID();
        PlatformOperatorIdentity identity = new PlatformOperatorIdentity(id, "Admin", 1L);
        String s = OperatorAuthenticationOutcome.success(identity).toString();
        assertThat(s).doesNotContain(id.toString());
    }
}
