package com.turnoshift.operator.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: OrganizationProperties — validación de configuración al arranque")
class OrganizationPropertiesTest {

    @Test
    @DisplayName("Valor por defecto es 50 y pasa validación")
    void valorPorDefecto_es50YValida() {
        OrganizationProperties props = new OrganizationProperties();
        props.validate();
        assertThat(props.getDefaultMaxActiveEmployees()).isEqualTo(50);
    }

    @Test
    @DisplayName("Valor positivo configurado pasa validación")
    void valorPositivo_pasaValidacion() {
        OrganizationProperties props = new OrganizationProperties();
        props.setDefaultMaxActiveEmployees(100);
        props.validate();
        assertThat(props.getDefaultMaxActiveEmployees()).isEqualTo(100);
    }

    @Test
    @DisplayName("Valor cero o negativo lanza IllegalStateException en validate()")
    void valorCeroONegativo_lanzaExcepcion() {
        OrganizationProperties propsCero = new OrganizationProperties();
        propsCero.setDefaultMaxActiveEmployees(0);
        assertThatThrownBy(propsCero::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("debe ser un entero positivo > 0");

        OrganizationProperties propsNeg = new OrganizationProperties();
        propsNeg.setDefaultMaxActiveEmployees(-5);
        assertThatThrownBy(propsNeg::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("debe ser un entero positivo > 0");
    }
}
