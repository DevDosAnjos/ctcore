package br.com.ctcore.student;

import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-COM-14, RN-ALU-10: a SENT message that came back (bounce) becomes BOUNCED, is never resent and marks the address "Com problema" in its
 * organization. A send failure is not a bounce. The real provider calls {@link #register} in Sprint 22.
 */
@Service
public class EmailBounces {
    private final JdbcClient jdbc;
    private final EmailProblems problems;

    public EmailBounces(@Lazy JdbcClient jdbc, EmailProblems problems) {
        this.jdbc = jdbc;
        this.problems = problems;
    }

    /** Idempotent: only a SENT message changes; a second call does nothing. */
    public void register(UUID messageId) {
        var row = jdbc.sql("update email_message set status = 'BOUNCED' where id = :id and status = 'SENT' returning organization_id, to_email")
                .param("id", messageId).query().listOfRows();
        if (!row.isEmpty() && row.get(0).get("organization_id") != null) {
            problems.mark((UUID) row.get(0).get("organization_id"), (String) row.get(0).get("to_email"));
        }
    }
}
