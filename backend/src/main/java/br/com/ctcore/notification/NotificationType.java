package br.com.ctcore.notification;

/**
 * RN-NOT-04 (D2): catálogo fixo. {@code UNIT_ADMINS}: Administradores com acesso à unidade (e Coordenador em "receber tudo");
 * {@code COORDINATORS}: Coordenadores da organização. Urgente (RN-NOT-02) também manda e-mail na hora.
 */
public enum NotificationType {
    PAYMENT_RECEIVED(Audience.UNIT_ADMINS, false),
    STUDENT_MAJORITY(Audience.UNIT_ADMINS, false),
    EMAIL_PROBLEM(Audience.UNIT_ADMINS, false),
    HIGH_BENEFIT(Audience.COORDINATORS, false),
    INVITE_PENDING(Audience.COORDINATORS, false),
    CLOSURE_REQUEST(Audience.COORDINATORS, true),
    MP_DISCONNECTED(Audience.COORDINATORS, true),
    SUBSCRIPTION_OVERDUE(Audience.COORDINATORS, true);

    public enum Audience { UNIT_ADMINS, COORDINATORS }

    private final Audience audience;
    private final boolean urgent;

    NotificationType(Audience audience, boolean urgent) {
        this.audience = audience;
        this.urgent = urgent;
    }

    public Audience audience() {
        return audience;
    }

    public boolean urgent() {
        return urgent;
    }
}
