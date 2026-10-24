package br.com.ctcore.communication;

import br.com.ctcore.communication.AnnouncementTargets.Destination;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * R7: envia o comunicado (agora ou pela tarefa). Reivindica a linha com {@code update ... where sent_at is null}, grava {@code sent_at} e
 * {@code recipient_count} e manda o e-mail, uma vez por endereço; o {@code EmailService} pula quem desligou os avisos ou está "Com problema". O portal
 * não tem envio: o aluno lê pela tela (Sprint 10). Roda dentro de uma transação da organização; o remetente nunca faz o envio lançar (RNF-14).
 */
@Component
public class AnnouncementSender {
    private final JdbcClient jdbc;
    private final AnnouncementRecipients recipients;
    private final EmailService email;

    public AnnouncementSender(@Lazy JdbcClient jdbc, AnnouncementRecipients recipients, EmailService email) {
        this.jdbc = jdbc;
        this.recipients = recipients;
        this.email = email;
    }

    private record Row(Destination destination, String title, String body, boolean byEmail) {}

    /** Número de destinatários, ou -1 se outra execução já enviou. */
    public int dispatch(UUID org, UUID id, OffsetDateTime now) {
        Row row = jdbc.sql("select target_kind, class_id, modality_id, unit_id, title, body, 'EMAIL' = any(channels), "
                + "array(select class_id from announcement_class c where c.organization_id = a.organization_id and c.announcement_id = a.id) "
                + "from announcement a where a.organization_id = :org and a.id = :id and a.sent_at is null").param("org", org).param("id", id)
                .query((rs, n) -> {
                    List<UUID> classes = List.of((UUID[]) rs.getArray(8).getArray());
                    return new Row(new Destination(rs.getString(1), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), classes),
                            rs.getString(5), rs.getString(6), rs.getBoolean(7));
                }).optional().orElse(null);
        if (row == null) return -1;
        var found = recipients.find(org, row.destination(), now);
        if (jdbc.sql("update announcement set sent_at = :now, recipient_count = :n where organization_id = :org and id = :id and sent_at is null")
                .param("now", now).param("n", found.recipients()).param("org", org).param("id", id).update() != 1) {
            return -1;
        }
        jdbc.sql("update no_class_day set notice_status = 'Enviado' where organization_id = :org and id = "
                + "(select no_class_day_id from announcement where organization_id = :org and id = :id)").param("org", org).param("id", id).update();
        if (row.byEmail()) {
            String text = row.title() + "\n\n" + AnnouncementText.plain(row.body());
            for (String to : found.addresses()) email.send(org, to, EmailTemplate.ANNOUNCEMENT, text);
        }
        return found.recipients();
    }
}
