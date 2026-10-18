package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.organization.NewUnit.Administrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewUnitValidationTest {
    private static NewUnit of(String name, String cep, Double lat, Double lon, String phone, String email, Administrator admin) {
        return new NewUnit(name, cep, null, null, lat, lon, phone, email, null, admin);
    }

    private static void rejects(NewUnit unit, String field) {
        assertThatThrownBy(unit::validated).isInstanceOf(ApiValidationException.class)
                .satisfies(e -> assertThat(((ApiValidationException) e).fields.get(0).field()).isEqualTo(field));
    }

    @Test
    @DisplayName("CA4: nome vazio, só espaços ou com mais de 120 caracteres é recusado")
    void name() {
        rejects(of(null, null, null, null, null, null, null), "name");
        rejects(of("   ", null, null, null, null, null, null), "name");
        rejects(of("x".repeat(121), null, null, null, null, null, null), "name");
        assertThat(of(" " + "x".repeat(120) + " ", null, null, null, null, null, null).validated().name()).hasSize(120);
    }

    @Test
    @DisplayName("CA4: e-mail, CEP e telefone inválidos são recusados; CEP e telefone voltam só com dígitos")
    void formats() {
        rejects(of("A", null, null, null, null, "sem-formato", null), "email");
        rejects(of("A", "1234", null, null, null, null, null), "postalCode");
        rejects(of("A", null, null, null, "123", null, null), "phone");
        var ok = of("A", "01001-000", null, null, "+55 (21) 98765-4321", " a@b.com ", null).validated();
        assertThat(ok.postalCode()).isEqualTo("01001000");
        assertThat(ok.phone()).isEqualTo("21987654321");
        assertThat(ok.email()).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("CA4: latitude fora de -90..90, longitude fora de -180..180 ou só uma delas é recusada")
    void coordinates() {
        rejects(of("A", null, 91.0, 0.0, null, null, null), "latitude");
        rejects(of("A", null, 0.0, -181.0, null, null, null), "longitude");
        rejects(of("A", null, 1.0, null, null, null, null), "longitude");
        rejects(of("A", null, Double.NaN, 0.0, null, null, null), "latitude");
        assertThat(of("A", null, -90.0, 180.0, null, null, null).validated().latitude()).isEqualTo(-90.0);
    }

    @Test
    @DisplayName("CA10: convite exige nome e e-mail juntos; os dois vazios viram sem convite")
    void administrator() {
        rejects(of("A", null, null, null, null, null, new Administrator("Ana", null)), "administrator.email");
        rejects(of("A", null, null, null, null, null, new Administrator(null, "a@b.com")), "administrator.name");
        rejects(of("A", null, null, null, null, null, new Administrator("Ana", "ruim")), "administrator.email");
        assertThat(of("A", null, null, null, null, null, new Administrator(" ", "")).validated().administrator()).isNull();
        assertThat(of("A", null, null, null, null, null, new Administrator(" Ana ", "a@b.com")).validated().administrator().name()).isEqualTo("Ana");
    }
}
