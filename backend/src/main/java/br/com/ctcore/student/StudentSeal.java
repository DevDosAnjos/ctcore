package br.com.ctcore.student;

import java.util.Optional;

/** RF-ALU-02: selos calculados na leitura; a ordem do enum é a ordem de {@code seals}. Os demais entram com seus itens (#70, #65). */
public enum StudentSeal {
    INCOMPLETE_REGISTRATION, EMAIL_PROBLEM, BIRTHDAY_TODAY, NEW;

    static Optional<StudentSeal> parse(String code) {
        for (StudentSeal s : values()) if (s.name().equals(code)) return Optional.of(s);
        return Optional.empty();
    }
}
