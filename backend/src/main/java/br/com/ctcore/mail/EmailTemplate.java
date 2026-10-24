package br.com.ctcore.mail;

/**
 * RN-COM-11 registry: message key prefix ({@code .subject} and {@code .body} in messages.properties), whether the text carries a secret code,
 * whether the first argument is a link, and the category (RN-COM-13: only {@link Kind#ANNOUNCEMENT} can be turned off by the receiver).
 */
public enum EmailTemplate {
    INVITE("mail.invite", true, true, Kind.ALWAYS),
    PASSWORD_RESET("mail.reset", true, true, Kind.ALWAYS),
    EMAIL_CHANGE("mail.email-change", true, true, Kind.ALWAYS),
    EMAIL_CHANGED("mail.email-changed", false, false, Kind.ALWAYS),
    PASSWORD_CHANGED("mail.password-changed", false, false, Kind.ALWAYS),
    NEW_ACCESS("mail.new-access", false, false, Kind.ALWAYS),
    COORDINATION_TRANSFERRED("mail.coordination-transferred", false, false, Kind.ALWAYS),
    /** RN-RSP-05: neutro, sem nome do aluno nem motivo. */
    GUARDIAN_UNLINKED("mail.guardian-unlinked", false, false, Kind.ALWAYS),
    /** RN-RSP-07: o aluno cadastrado menor completou 18 anos. */
    STUDENT_MAJORITY("mail.student-majority", false, false, Kind.ALWAYS),
    /** RF-COB-04: recibo do pagamento no balcão; o primeiro argumento é o link do PDF. */
    PAYMENT_CONFIRMED("mail.payment-confirmed", false, true, Kind.ALWAYS),
    /** RN-COM-11: cobrança em aberto (descrição, valor, vencimento). */
    CHARGE_CREATED("mail.charge-created", false, false, Kind.ALWAYS),
    /** RN-PLA-04: novo valor do plano (nome do plano, valor). */
    PLAN_RATE_ADJUSTED("mail.plan-rate-adjusted", false, false, Kind.ALWAYS),
    /** Cobrança da diferença de reajuste (descrição, valor, vencimento). */
    READJUSTMENT_DIFF("mail.readjustment-diff", false, false, Kind.ALWAYS),
    /** RN-COM-13: comunicado da academia, o único que o aluno ou responsável desliga (texto). */
    ANNOUNCEMENT("mail.announcement", false, false, Kind.ANNOUNCEMENT);

    public enum Kind { ALWAYS, ANNOUNCEMENT }

    final String key;
    final boolean secret;
    /** The first argument is a link, shown also as a full-width button. */
    final boolean link;
    final Kind kind;

    EmailTemplate(String key, boolean secret, boolean link, Kind kind) {
        this.key = key;
        this.secret = secret;
        this.link = link;
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
