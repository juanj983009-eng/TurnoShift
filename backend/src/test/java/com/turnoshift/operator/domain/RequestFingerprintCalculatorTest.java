package com.turnoshift.operator.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: RequestFingerprintCalculator — huella criptográfica de comandos")
class RequestFingerprintCalculatorTest {

    private static final String METHOD = "POST";
    private static final String PATH = "/api/v1/operator/setup/organizations";
    private static final String SCHEMA_VERSION = "1.0";

    @Test
    @DisplayName("Cálculo determinista: el mismo comando produce idénticos 32 bytes")
    void calculoDeterminista_produceMismosBytes() {
        IncorporateOrganizationCommand cmd1 = new IncorporateOrganizationCommand(
                "CAFE-DEMO",
                "Café Demo S.A.",
                ZoneId.of("America/Lima"),
                "Sede Central Lima",
                "admin@cafedemo.example",
                "Ana García"
        );

        IncorporateOrganizationCommand cmd2 = new IncorporateOrganizationCommand(
                "CAFE-DEMO",
                "Café Demo S.A.",
                ZoneId.of("America/Lima"),
                "Sede Central Lima",
                "admin@cafedemo.example",
                "Ana García"
        );

        byte[] fp1 = RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, cmd1);
        byte[] fp2 = RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, cmd2);

        assertThat(fp1).hasSize(32);
        assertThat(fp2).hasSize(32);
        assertThat(Arrays.equals(fp1, fp2)).isTrue();
    }

    @Test
    @DisplayName("Cualquier variación en los campos del comando produce una huella distinta")
    void variacionEnCampo_produceHuellaDistinta() {
        IncorporateOrganizationCommand base = new IncorporateOrganizationCommand(
                "CAFE-DEMO",
                "Café Demo S.A.",
                ZoneId.of("America/Lima"),
                "Sede Central Lima",
                "admin@cafedemo.example",
                "Ana García"
        );
        byte[] baseFp = RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, base);

        // Diferente código
        IncorporateOrganizationCommand diffCode = new IncorporateOrganizationCommand(
                "CAFE-DEMO2", base.orgDisplayName(), base.timeZone(), base.siteDisplayName(), base.adminEmail(), base.adminDisplayName());
        assertThat(RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, diffCode)).isNotEqualTo(baseFp);

        // Diferente nombre org
        IncorporateOrganizationCommand diffOrgName = new IncorporateOrganizationCommand(
                base.orgCode(), "Café Demo Diferente", base.timeZone(), base.siteDisplayName(), base.adminEmail(), base.adminDisplayName());
        assertThat(RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, diffOrgName)).isNotEqualTo(baseFp);

        // Diferente timezone
        IncorporateOrganizationCommand diffTz = new IncorporateOrganizationCommand(
                base.orgCode(), base.orgDisplayName(), ZoneId.of("America/Bogota"), base.siteDisplayName(), base.adminEmail(), base.adminDisplayName());
        assertThat(RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, diffTz)).isNotEqualTo(baseFp);

        // Diferente sede
        IncorporateOrganizationCommand diffSite = new IncorporateOrganizationCommand(
                base.orgCode(), base.orgDisplayName(), base.timeZone(), "Sede Norte", base.adminEmail(), base.adminDisplayName());
        assertThat(RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, diffSite)).isNotEqualTo(baseFp);

        // Diferente admin email
        IncorporateOrganizationCommand diffEmail = new IncorporateOrganizationCommand(
                base.orgCode(), base.orgDisplayName(), base.timeZone(), base.siteDisplayName(), "otro@cafedemo.example", base.adminDisplayName());
        assertThat(RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, diffEmail)).isNotEqualTo(baseFp);

        // Diferente admin nombre
        IncorporateOrganizationCommand diffAdminName = new IncorporateOrganizationCommand(
                base.orgCode(), base.orgDisplayName(), base.timeZone(), base.siteDisplayName(), base.adminEmail(), "Carlos López");
        assertThat(RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, diffAdminName)).isNotEqualTo(baseFp);

        // Diferente método o versión
        assertThat(RequestFingerprintCalculator.calculate("PUT", PATH, SCHEMA_VERSION, base)).isNotEqualTo(baseFp);
        assertThat(RequestFingerprintCalculator.calculate(METHOD, PATH, "2.0", base)).isNotEqualTo(baseFp);
    }

    @Test
    @DisplayName("Codificación inequívoca evita colisiones ante delimitadores o saltos de línea internos")
    void codificacionInequivoca_evitaColisionesConDelimitadores() {
        // Dos comandos que con separadores ingenuos como '\n' o ':' podrían colisionar
        IncorporateOrganizationCommand cmdA = new IncorporateOrganizationCommand(
                "CAFE-DEMO",
                "Café:Demo",
                ZoneId.of("America/Lima"),
                "Sede Central",
                "admin@cafedemo.example",
                "Ana García"
        );
        IncorporateOrganizationCommand cmdB = new IncorporateOrganizationCommand(
                "CAFE-DEMO",
                "Café",
                ZoneId.of("America/Lima"),
                "Demo:Sede Central",
                "admin@cafedemo.example",
                "Ana García"
        );

        byte[] fpA = RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, cmdA);
        byte[] fpB = RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, cmdB);

        assertThat(fpA).isNotEqualTo(fpB);
    }

    @Test
    @DisplayName("Rechazo de argumentos nulos con NullPointerException defensiva")
    void argumentosNulos_lanzanNullPointerException() {
        IncorporateOrganizationCommand cmd = new IncorporateOrganizationCommand(
                "CAFE-DEMO", "Café Demo", ZoneId.of("UTC"), "Sede", "admin@demo.example", "Admin");

        assertThatThrownBy(() -> RequestFingerprintCalculator.calculate(null, PATH, SCHEMA_VERSION, cmd))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RequestFingerprintCalculator.calculate(METHOD, null, SCHEMA_VERSION, cmd))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RequestFingerprintCalculator.calculate(METHOD, PATH, null, cmd))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, (IncorporateOrganizationCommand) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RequestFingerprintCalculator.calculate(METHOD, PATH, SCHEMA_VERSION, (EmitInitialAccessLinkCommand) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("EmitInitialAccessLinkCommand: cálculo determinista y diferenciación por campo")
    void emitInitialAccessLink_calculoDeterministaYDiferenciacion() {
        java.util.UUID orgId = java.util.UUID.randomUUID();
        java.util.UUID adminId = java.util.UUID.randomUUID();
        String path = "/api/v1/operator/setup/organizations/" + orgId + "/initial-access-link";

        EmitInitialAccessLinkCommand cmd1 = new EmitInitialAccessLinkCommand(orgId, adminId, 0L);
        EmitInitialAccessLinkCommand cmd2 = new EmitInitialAccessLinkCommand(orgId, adminId, 0L);

        byte[] fp1 = RequestFingerprintCalculator.calculate(METHOD, path, SCHEMA_VERSION, cmd1);
        byte[] fp2 = RequestFingerprintCalculator.calculate(METHOD, path, SCHEMA_VERSION, cmd2);

        assertThat(fp1).hasSize(32);
        assertThat(fp2).hasSize(32);
        assertThat(Arrays.equals(fp1, fp2)).isTrue();

        // Diferente versión esperada produce distinta huella
        EmitInitialAccessLinkCommand diffVer = new EmitInitialAccessLinkCommand(orgId, adminId, 1L);
        assertThat(RequestFingerprintCalculator.calculate(METHOD, path, SCHEMA_VERSION, diffVer)).isNotEqualTo(fp1);

        // Diferente adminId produce distinta huella
        EmitInitialAccessLinkCommand diffAdmin = new EmitInitialAccessLinkCommand(orgId, java.util.UUID.randomUUID(), 0L);
        assertThat(RequestFingerprintCalculator.calculate(METHOD, path, SCHEMA_VERSION, diffAdmin)).isNotEqualTo(fp1);

        // Diferente orgId produce distinta huella
        EmitInitialAccessLinkCommand diffOrg = new EmitInitialAccessLinkCommand(java.util.UUID.randomUUID(), adminId, 0L);
        assertThat(RequestFingerprintCalculator.calculate(METHOD, path, SCHEMA_VERSION, diffOrg)).isNotEqualTo(fp1);
    }
}
