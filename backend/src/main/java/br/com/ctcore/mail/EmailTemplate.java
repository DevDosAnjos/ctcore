package br.com.ctcore.mail;

/** Message key prefix ({@code .subject} and {@code .body} in messages.properties) and whether the text carries a secret code. */
public enum EmailTemplate {
    INVITE("mail.invite", true, true),
    PASSWORD_RESET("mail.reset", true, true),
    EMAIL_CHANGE("mail.email-change", true, true),
    EMAIL_CHANGED("mail.email-changed", false, false),
    PASSWORD_CHANGED("mail.password-changed", false, false),
    NEW_ACCESS("mail.new-access", false, false),
    COORDINATION_TRANSFERRED("mail.coordination-transferred", false, false),
    /** RN-RSP-05: neutro, sem nome do aluno nem motivo. */
    GUARDIAN_UNLINKED("mail.guardian-unlinked", false, false),
    /** RN-RSP-07: o aluno cadastrado menor completou 18 anos. */
    STUDENT_MAJORITY("mail.student-majority", false, false);

    final String key;
    final boolean secret;
    /** The first argument is a link, shown also as a full-width button. */
    final boolean link;

    EmailTemplate(String key, boolean secret, boolean link) {
        this.key = key;
        this.secret = secret;
        this.link = link;
    }
}
