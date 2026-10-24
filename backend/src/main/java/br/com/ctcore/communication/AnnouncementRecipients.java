package br.com.ctcore.communication;

import br.com.ctcore.communication.AnnouncementTargets.Destination;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * RN-COM-02, R5: as pessoas que recebem o comunicado, numa consulta só que a prévia e o envio usam (o número prometido nunca difere do enviado).
 * Aluno com 18 anos ou mais (na data da unidade da matrícula) recebe; do menor, recebem os responsáveis. A mesma pessoa conta uma vez.
 * Roda dentro de uma transação da organização.
 */
@Component
public class AnnouncementRecipients {
    /** {@code email} em minúsculas ou nulo; {@code eligible}: tem endereço, não desligou os avisos da academia e não está "Com problema". */
    record Person(String key, String email, boolean eligible) {}

    /** Pessoas distintas e endereços (todos os que existem, para o {@code EmailService} registrar também os pulados). */
    record Result(int recipients, int emails, List<String> addresses) {}

    /** S1: hoje só ACTIVE; "Encerramento solicitado" e as bloqueadas entram aqui quando existirem (Sprint 12). */
    private static final String RECEIVING = "('ACTIVE')";

    private final JdbcClient jdbc;

    public AnnouncementRecipients(@Lazy JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Result find(UUID org, Destination d, OffsetDateTime now) {
        Map<String, Object> params = new HashMap<>();
        params.put("org", org);
        params.put("now", now);
        String where = switch (d.kind()) {
            case "CLASS" -> {
                params.put("cls", d.classId());
                yield " and (em.primary_class_id = :cls or exists (select 1 from enrollment_modality_class x where x.organization_id = em.organization_id "
                        + "and x.modality_row_id = em.id and x.class_id = :cls))";
            }
            case "CLASSES" -> {
                params.put("classes", d.classIds());
                yield " and (em.primary_class_id in (:classes) or exists (select 1 from enrollment_modality_class x where x.organization_id = em.organization_id "
                        + "and x.modality_row_id = em.id and x.class_id in (:classes)))";
            }
            case "MODALITY" -> {
                params.put("unit", d.unitId());
                params.put("mod", d.modalityId());
                yield " and em.unit_id = :unit and em.modality_id = :mod";
            }
            case "UNIT" -> {
                params.put("unit", d.unitId());
                yield " and em.unit_id = :unit";
            }
            default -> "";
        };
        String adult = "s.birth_date <= ((cast(:now as timestamptz) at time zone coalesce(u.timezone, 'America/Sao_Paulo'))::date - interval '18 years')";
        String base = " from enrollment_modality em join student s on s.organization_id = em.organization_id and s.id = em.student_id "
                + "join unit u on u.organization_id = em.organization_id and u.id = em.unit_id ";
        String sql = "select 'S:' || s.id, lower(btrim(s.email)), s.email_problem_at is not null" + base + "where em.organization_id = :org and em.status in " + RECEIVING + where
                + " and " + adult
                + " union all select 'G:' || g.id, lower(btrim(a.email)), g.email_problem_at is not null" + base
                + "join student_guardian sg on sg.organization_id = s.organization_id and sg.student_id = s.id "
                + "join guardian g on g.organization_id = sg.organization_id and g.id = sg.guardian_id join account a on a.id = g.account_id "
                + "where em.organization_id = :org and em.status in " + RECEIVING + where + " and not (" + adult + ")";
        List<String[]> rows = jdbc.sql(sql).params(params).query((rs, n) -> new String[] {rs.getString(1), rs.getString(2), rs.getBoolean(3) ? "1" : "0"}).list();
        // Quem desligou os avisos da academia (por conta do vínculo da organização).
        List<String> optedOut = jdbc.sql("select lower(a.email) from account_link l join account a on a.id = l.account_id where l.organization_id = :org and not l.announcement_email")
                .param("org", org).query(String.class).list();
        Map<String, Person> people = new LinkedHashMap<>();
        for (String[] r : rows) {
            String email = r[1] != null && r[1].contains("@") ? r[1] : null;
            people.putIfAbsent(r[0], new Person(r[0], email, email != null && "0".equals(r[2]) && !optedOut.contains(email)));
        }
        List<String> addresses = people.values().stream().map(Person::email).filter(e -> e != null).distinct().toList();
        int emails = (int) people.values().stream().filter(Person::eligible).map(Person::email).distinct().count();
        return new Result(people.size(), emails, new ArrayList<>(addresses));
    }
}
