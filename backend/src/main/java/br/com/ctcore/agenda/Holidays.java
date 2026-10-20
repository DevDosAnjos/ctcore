package br.com.ctcore.agenda;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** RN-AGE-09 (D1): feriados nacionais calculados no código, sem tabela nem fornecedor. Estadual e municipal a unidade lança à mão. */
final class Holidays {
    record Holiday(String code, LocalDate date) {}

    private Holidays() {}

    /** Domingo de Páscoa pelo algoritmo gregoriano anônimo (Meeus/Jones/Butcher). */
    static LocalDate easter(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }

    static List<Holiday> of(int year) {
        List<Holiday> all = new ArrayList<>(List.of(
                new Holiday("NEW_YEAR", LocalDate.of(year, Month.JANUARY, 1)),
                new Holiday("GOOD_FRIDAY", easter(year).minusDays(2)),
                new Holiday("TIRADENTES", LocalDate.of(year, Month.APRIL, 21)),
                new Holiday("LABOUR_DAY", LocalDate.of(year, Month.MAY, 1)),
                new Holiday("INDEPENDENCE", LocalDate.of(year, Month.SEPTEMBER, 7)),
                new Holiday("APARECIDA", LocalDate.of(year, Month.OCTOBER, 12)),
                new Holiday("ALL_SOULS", LocalDate.of(year, Month.NOVEMBER, 2)),
                new Holiday("REPUBLIC", LocalDate.of(year, Month.NOVEMBER, 15)),
                new Holiday("BLACK_AWARENESS", LocalDate.of(year, Month.NOVEMBER, 20)),
                new Holiday("CHRISTMAS", LocalDate.of(year, Month.DECEMBER, 25))));
        all.sort(Comparator.comparing(Holiday::date));
        return all;
    }

    /** Feriados de {@code from} (inclusive) até 12 meses depois (exclusive), em ordem de data. */
    static List<Holiday> next12Months(LocalDate from) {
        LocalDate to = from.plusMonths(12);
        List<Holiday> out = new ArrayList<>();
        for (int y = from.getYear(); y <= to.getYear(); y++) {
            for (Holiday h : of(y)) if (!h.date().isBefore(from) && h.date().isBefore(to)) out.add(h);
        }
        return out;
    }
}
