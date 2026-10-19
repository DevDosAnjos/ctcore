package br.com.ctcore.trainingclass;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** RN-TUR-03: desativar a turma só muda a situação (nada é apagado) e só se nenhum aluno a tem como turma principal. */
@Service
public class TrainingClassDeactivation {
    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final TrainingClasses classes;
    private final AuditService audit;

    public TrainingClassDeactivation(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, TrainingClasses classes, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.classes = classes;
        this.audit = audit;
    }

    public TrainingClasses.Detail deactivate(AccountPrincipal who, UUID id, String ip) {
        return transactions.execute(() -> {
            TrainingClasses.Detail before = classes.get(who, id); // permissão, alcance e 404
            UUID unit = before.unit().id();
            jdbc.sql("select id from training_class where id = :id and organization_id = :org for update")
                    .param("id", id).param("org", who.organizationId()).query(UUID.class).single();
            if (before.status().equals("INACTIVE")) throw new ApiStatusException(ErrorCode.CONFLICT);
            int blocking = classes.primaryStudents(id);
            if (blocking > 0) {
                throw new ApiStatusException(ErrorCode.HAS_PRIMARY_STUDENTS, List.of(new ApiError.Field("primaryStudents", String.valueOf(blocking))));
            }
            jdbc.sql("update training_class set status = 'INACTIVE' where id = :id and organization_id = :org")
                    .param("id", id).param("org", who.organizationId()).update();
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "class.deactivated",
                    "training_class", id.toString(), unit, Map.of("status", "ACTIVE"), Map.of("status", "INACTIVE"), null, ip));
            return classes.get(who, id);
        });
    }
}
