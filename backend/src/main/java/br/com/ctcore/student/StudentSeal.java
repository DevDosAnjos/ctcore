package br.com.ctcore.student;

import java.util.Optional;

/** RF-ALU-02: selos calculados na leitura; a ordem do enum é a ordem de {@code seals}. O selo Bolsista é o do item #65; o Termo pendente é o da #85 (RN-MAT-11). */
public enum StudentSeal {
    INCOMPLETE_REGISTRATION, EMAIL_PROBLEM, BIRTHDAY_TODAY, NEW, SCHOLARSHIP, TERM_PENDING;

    static Optional<StudentSeal> parse(String code) {
        for (StudentSeal s : values()) if (s.name().equals(code)) return Optional.of(s);
        return Optional.empty();
    }
}
