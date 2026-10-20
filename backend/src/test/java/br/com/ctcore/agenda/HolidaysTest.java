package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HolidaysTest {
    @Test
    @DisplayName("CA1: Páscoa e Sexta-feira Santa de 2026 e 2027")
    void easterAndGoodFriday() {
        assertThat(Holidays.easter(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(Holidays.easter(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        assertThat(Holidays.of(2026)).contains(new Holidays.Holiday("GOOD_FRIDAY", LocalDate.of(2026, 4, 3)));
        assertThat(Holidays.of(2027)).contains(new Holidays.Holiday("GOOD_FRIDAY", LocalDate.of(2027, 3, 26)));
    }

    @Test
    @DisplayName("CA1: dez feriados por ano, em ordem, e janela de 12 meses")
    void window() {
        assertThat(Holidays.of(2026)).hasSize(10).isSortedAccordingTo(java.util.Comparator.comparing(Holidays.Holiday::date));
        var next = Holidays.next12Months(LocalDate.of(2026, 10, 19));
        assertThat(next).extracting(Holidays.Holiday::date).contains(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 25), LocalDate.of(2027, 3, 26))
                .doesNotContain(LocalDate.of(2026, 10, 12), LocalDate.of(2027, 10, 19));
        assertThat(next.get(0).date()).isEqualTo(LocalDate.of(2026, 11, 2));
    }
}
