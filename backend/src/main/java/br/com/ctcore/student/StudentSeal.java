package br.com.ctcore.student;

import java.util.Optional;

/** RF-ALU-02: selos calculados na leitura; a ordem do enum é a ordem de {@code seals}. O selo Bolsista é o do item #65; os demais entram com seus itens (#70). */
public enum StudentSeal {
    INCOMPLETE_REGISTRATION, EMAIL_PROBLEM, BIRTHDAY_TODAY, NEW, SCHOLARSHIP;

    static Optional<StudentSeal> parse(String code) {
        for (StudentSeal s : values()) if (s.name().equals(code)) return Optional.of(s);
        return Optional.empty();
    }
}
