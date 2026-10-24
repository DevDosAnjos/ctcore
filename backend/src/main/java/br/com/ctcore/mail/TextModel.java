package br.com.ctcore.mail;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import java.util.List;

/** RN-COM-12 (D1): o catálogo fixo de modelos que a organização pode editar, com o grupo e os campos automáticos que cada um aceita. Nenhum outro e-mail é editável. */
public enum TextModel {
    REMINDER_BEFORE(Group.REMINDERS, Fields.REMINDER),
    REMINDER_DUE(Group.REMINDERS, Fields.REMINDER),
    REMINDER_OVERDUE(Group.REMINDERS, Fields.REMINDER),
    WELCOME(Group.WELCOME, List.of("nome", "unidade")),
    ANNOUNCEMENT_CANCELED_CLASS(Group.ANNOUNCEMENTS, List.of("unidade", "turma", "data", "horario")),
    ANNOUNCEMENT_TEACHER_CHANGE(Group.ANNOUNCEMENTS, List.of("unidade", "turma", "data")),
    ANNOUNCEMENT_SCHEDULE_CHANGE(Group.ANNOUNCEMENTS, List.of("unidade", "turma", "data", "horario")),
    ANNOUNCEMENT_NO_CLASS_DAY(Group.ANNOUNCEMENTS, List.of("unidade", "periodo", "motivo")),
    ANNOUNCEMENT_GENERAL(Group.ANNOUNCEMENTS, List.of("unidade"));

    public enum Group { REMINDERS, WELCOME, ANNOUNCEMENTS }

    private static final class Fields {
        static final List<String> REMINDER = List.of("nome", "valor", "vencimento", "unidade", "descricao", "link_pagamento");
    }

    public final Group group;
    public final List<String> fields;

    TextModel(Group group, List<String> fields) {
        this.group = group;
        this.fields = fields;
    }

    /** O modelo de lembrete de um e-mail de cobrança; nulo para os demais (o aviso de bloqueio não é editável). */
    public static TextModel reminder(EmailTemplate template) {
        return switch (template) {
            case REMINDER_BEFORE -> REMINDER_BEFORE;
            case REMINDER_DUE -> REMINDER_DUE;
            case REMINDER_OVERDUE -> REMINDER_OVERDUE;
            default -> null;
        };
    }

    /** Chave fora do catálogo é 404. */
    public static TextModel parse(String key) {
        try {
            return valueOf(key);
        } catch (IllegalArgumentException e) {
            throw new ApiStatusException(ErrorCode.NOT_FOUND);
        }
    }
}
