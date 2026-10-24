package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.organization.UnitUpdate.Settings;
import br.com.ctcore.organization.UnitUpdate.TeacherPermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-ORG-04: validações do corpo do PUT /units/{id}, sem banco. */
class UnitUpdateValidationTest {
    private static Map<String, Long> fees() {
        Map<String, Long> m = new LinkedHashMap<>();
        UnitUpdate.FEE_COLUMNS.keySet().forEach(k -> m.put(k, 0L));
        return m;
    }

    private static Settings settings() {
        return new Settings(List.of(1, 5, 10, 15), false, 0L, fees(), null, List.of(-3, 0, 3, 10), new TeacherPermissions(true, true, true, true, false),
                false, true, true, true, 9);
    }

    private static UnitUpdate valid(UnaryOperator<Settings> change) {
        return new UnitUpdate("Unidade", null, null, null, null, null, null, null, null, null, change.apply(settings()));
    }

    private static UnitUpdate data(String name, String cep, Double lat, Double lon, String phone, String email, String cnpj) {
        return new UnitUpdate(name, cep, null, null, lat, lon, phone, email, null, cnpj, settings());
    }

    private static void refuses(UnitUpdate update, String field) {
        assertThatThrownBy(update::validated).isInstanceOfSatisfying(ApiValidationException.class,
                e -> assertThat(e.fields.get(0).field()).isEqualTo(field));
    }

    private static Settings with(Settings s, List<Integer> due, boolean feeOn, Long fee, Map<String, Long> fees, String pix, List<Integer> reminders,
            TeacherPermissions teacher, Boolean directory) {
        return new Settings(due, feeOn, fee, fees, pix, reminders, teacher, directory, true, true, true, 9);
    }

    @Test
    @DisplayName("CA6: nome, e-mail, CEP, telefone, CNPJ e coordenadas inválidos apontam o campo; CNPJ com máscara guarda só dígitos")
    void data() {
        refuses(data(" ", null, null, null, null, null, null), "name");
        refuses(data("x".repeat(121), null, null, null, null, null, null), "name");
        refuses(data("A", null, null, null, null, "ruim", null), "email");
        refuses(data("A", "12", null, null, null, null, null), "postalCode");
        refuses(data("A", null, null, null, "123", null, null), "phone");
        refuses(data("A", null, null, null, null, null, "11.222.333/0001-82"), "cnpj");
        refuses(data("A", null, null, null, null, null, "529.982.247-25"), "cnpj");
        refuses(data("A", null, 1.0, null, null, null, null), "longitude");
        refuses(data("A", null, 91.0, 0.0, null, null, null), "latitude");
        assertThat(data("A", null, null, null, null, null, "11.222.333/0001-81").validated().cnpj()).isEqualTo("11222333000181");
        refuses(new UnitUpdate("A", null, null, null, null, null, null, null, null, null, null), "settings");
    }

    @Test
    @DisplayName("CA8: dias de vencimento vazio, 0, 29, repetido ou nulo são recusados; [15, 5, 1] vira [1, 5, 15]")
    void dueDays() {
        List<List<Integer>> bad = List.of(List.of(), List.of(0), List.of(29), List.of(5, 5));
        for (List<Integer> days : bad) {
            refuses(valid(s -> with(s, days, false, 0L, s.feesCents(), null, s.reminderDays(), s.teacherPermissions(), false)), "settings.dueDays");
        }
        refuses(valid(s -> with(s, null, false, 0L, s.feesCents(), null, s.reminderDays(), s.teacherPermissions(), false)), "settings.dueDays");
        var ok = valid(s -> with(s, List.of(15, 5, 1), false, 0L, s.feesCents(), null, s.reminderDays(), s.teacherPermissions(), false));
        assertThat(ok.validated().settings().dueDays()).containsExactly(1, 5, 15);
    }

    @Test
    @DisplayName("CA9: taxa de matrícula desligada salva 0; ligada com -1 é recusada; ligada com 15000 salva")
    void enrollmentFee() {
        var off = valid(s -> with(s, s.dueDays(), false, 500L, s.feesCents(), null, s.reminderDays(), s.teacherPermissions(), false));
        assertThat(off.validated().settings().enrollmentFeeCents()).isZero();
        refuses(valid(s -> with(s, s.dueDays(), true, -1L, s.feesCents(), null, s.reminderDays(), s.teacherPermissions(), false)),
                "settings.enrollmentFeeCents");
        refuses(valid(s -> with(s, s.dueDays(), true, null, s.feesCents(), null, s.reminderDays(), s.teacherPermissions(), false)),
                "settings.enrollmentFeeCents");
        var on = valid(s -> with(s, s.dueDays(), true, 15000L, s.feesCents(), null, s.reminderDays(), s.teacherPermissions(), false));
        assertThat(on.validated().settings().enrollmentFeeCents()).isEqualTo(15000);
    }

    @Test
    @DisplayName("CA10: taxa de meio negativa, acima do teto ou ausente aponta o campo; valores válidos passam")
    void feesPerMethod() {
        for (long bad : new long[] {-1, UnitUpdate.MAX_FEE_CENTS + 1}) {
            Map<String, Long> m = fees();
            m.put("pix", bad);
            refuses(valid(s -> with(s, s.dueDays(), false, 0L, m, null, s.reminderDays(), s.teacherPermissions(), false)), "settings.feesCents.pix");
        }
        Map<String, Long> missing = fees();
        missing.remove("transfer");
        refuses(valid(s -> with(s, s.dueDays(), false, 0L, missing, null, s.reminderDays(), s.teacherPermissions(), false)),
                "settings.feesCents.transfer");
        Map<String, Long> ok = fees();
        ok.put("creditCard", UnitUpdate.MAX_FEE_CENTS);
        var result = valid(s -> with(s, s.dueDays(), false, 0L, ok, null, s.reminderDays(), s.teacherPermissions(), false));
        assertThat(result.validated().settings().feesCents()).isEqualTo(ok);
    }

    @Test
    @DisplayName("CA11: chave Pix inválida é recusada com o campo; em branco apaga")
    void pixKey() {
        refuses(valid(s -> with(s, s.dueDays(), false, 0L, s.feesCents(), "abc", s.reminderDays(), s.teacherPermissions(), false)), "settings.pixKey");
        var blank = valid(s -> with(s, s.dueDays(), false, 0L, s.feesCents(), "  ", s.reminderDays(), s.teacherPermissions(), false));
        assertThat(blank.validated().settings().pixKey()).isNull();
    }

    @Test
    @DisplayName("CA13: lembretes [-3, 0, 3, 10] e vazio passam; repetido, fora de -30..60 ou mais de 8 são recusados")
    void reminders() {
        for (List<Integer> bad : List.of(List.of(1, 1), List.of(-31), List.of(61), List.of(1, 2, 3, 4, 5, 6, 7, 8, 9))) {
            refuses(valid(s -> with(s, s.dueDays(), false, 0L, s.feesCents(), null, bad, s.teacherPermissions(), false)), "settings.reminderDays");
        }
        var empty = valid(s -> with(s, s.dueDays(), false, 0L, s.feesCents(), null, List.of(), s.teacherPermissions(), false));
        assertThat(empty.validated().settings().reminderDays()).isEmpty();
        assertThat(valid(s -> s).validated().settings().reminderDays()).containsExactly(-3, 0, 3, 10);
    }

    @Test
    @DisplayName("CA14: permissões do Professor nulas ou incompletas e liga/desliga ausentes são recusados")
    void flags() {
        refuses(valid(s -> with(s, s.dueDays(), false, 0L, s.feesCents(), null, s.reminderDays(), null, false)), "settings.teacherPermissions");
        refuses(valid(s -> with(s, s.dueDays(), false, 0L, s.feesCents(), null, s.reminderDays(), new TeacherPermissions(true, null, true, true, false), false)),
                "settings.teacherPermissions.agendaEdit");
        refuses(valid(s -> with(s, s.dueDays(), false, 0L, s.feesCents(), null, s.reminderDays(), new TeacherPermissions(true, true, true, true, null), false)),
                "settings.teacherPermissions.studentRecord");
        refuses(valid(s -> with(s, s.dueDays(), false, 0L, s.feesCents(), null, s.reminderDays(), s.teacherPermissions(), null)),
                "settings.directoryEnabled");
    }

    @Test
    @DisplayName("RF-COB-09: hora de lembrete 0 e 23 passam; 24, -1 e ausente, ou interruptor ausente, apontam o campo")
    void reminderSwitch() {
        for (int ok : new int[] {0, 23}) {
            assertThat(valid(s -> new Settings(s.dueDays(), false, 0L, s.feesCents(), null, s.reminderDays(), s.teacherPermissions(), false, true, true,
                    false, ok)).validated().settings().remindersHour()).isEqualTo(ok);
        }
        for (Integer bad : new Integer[] {24, -1, null}) {
            refuses(valid(s -> new Settings(s.dueDays(), false, 0L, s.feesCents(), null, s.reminderDays(), s.teacherPermissions(), false, true, true,
                    true, bad)), "settings.remindersHour");
        }
        refuses(valid(s -> new Settings(s.dueDays(), false, 0L, s.feesCents(), null, s.reminderDays(), s.teacherPermissions(), false, true, true,
                null, 9)), "settings.remindersEnabled");
    }
}
