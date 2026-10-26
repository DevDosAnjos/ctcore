package br.com.ctcore.student;

import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import br.com.ctcore.mail.TextModel;
import br.com.ctcore.mail.TextModels;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** RN-MAT-11, #85 (D6): boas-vindas na conclusão da matrícula, com o texto próprio da organização ou o da plataforma. Falha só vai para o log. */
@Service
class WelcomeEmail {
    private static final Logger LOG = LoggerFactory.getLogger(WelcomeEmail.class);

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final TextModels texts;
    private final EmailService email;

    WelcomeEmail(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, TextModels texts, EmailService email) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.texts = texts;
        this.email = email;
    }

    /** Manda a quem recebe o convite do portal: o aluno adulto (e-mail do convite em {@code adultEmail}) ou cada responsável do menor. */
    void send(UUID org, UUID studentId, String studentName, String unitName, boolean minor, String adultEmail) {
        try {
            List<String[]> to = new ArrayList<>(); // {e-mail, nome}
            if (minor) {
                to.addAll(transactions.execute(() -> jdbc.sql("select a.email, g.name from student_guardian sg join guardian g on g.organization_id = sg.organization_id "
                        + "and g.id = sg.guardian_id join account a on a.id = g.account_id where sg.organization_id = :org and sg.student_id = :s "
                        + "and a.email is not null and a.email <> ''").param("org", org).param("s", studentId)
                        .query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)}).list()));
            } else if (adultEmail != null) {
                to.add(new String[] {adultEmail, studentName});
            }
            if (to.isEmpty()) return;
            var text = transactions.execute(() -> texts.custom(org, TextModel.WELCOME)).orElseGet(() -> texts.platform(TextModel.WELCOME));
            for (String[] r : to) {
                Map<String, String> values = Map.of("nome", r[1], "unidade", unitName);
                email.sendText(org, r[0].strip(), EmailTemplate.WELCOME, TextModels.fill(text.subject(), values), TextModels.fill(text.body(), values));
            }
        } catch (RuntimeException e) { // a matrícula já está gravada
            LOG.warn("Welcome e-mail was not sent after enrollment: {}", e.getClass().getSimpleName());
        }
    }
}
