package br.com.ctcore.terms;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-MAT-05 (RN-MAT-14): termos de matrícula e de uso de imagem, com versões imutáveis e escolha de reaceite a cada publicação. */
@Service
public class Terms {
    static final int MIN_BODY = 20;
    static final int MAX_BODY = 20000;

    // ponytail: modelo fixo no servidor (versão 0, sem linha no banco); o modelo editável é do painel da plataforma (RF-PNL-07).
    static final String ENROLLMENT_TEMPLATE = "TERMO DE MATRÍCULA\n\nAo matricular-se, o aluno (ou o seu responsável legal) declara conhecer e aceitar o regulamento da academia, "
            + "as condições de pagamento das mensalidades, as regras de frequência e as normas de uso das instalações e dos equipamentos.\n\n"
            + "O aluno declara estar apto à prática das modalidades contratadas e se compromete a informar à academia qualquer mudança em sua saúde.";
    static final String IMAGE_USE_TEMPLATE = "TERMO DE USO DE IMAGEM\n\nO aluno (ou o seu responsável legal) autoriza a academia a registrar e usar fotos e vídeos das aulas, "
            + "dos eventos e das graduações em seus canais de divulgação, sem fins comerciais de terceiros.\n\n"
            + "A autorização pode ser revogada a qualquer momento pelo portal do aluno, sem prejuízo da matrícula.";

    public enum Kind {
        ENROLLMENT(ENROLLMENT_TEMPLATE), IMAGE_USE(IMAGE_USE_TEMPLATE);

        final String template;

        Kind(String template) {
            this.template = template;
        }

        static Kind parse(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                throw new ApiStatusException(ErrorCode.NOT_FOUND);
            }
        }
    }

    public record Version(int version, String body, boolean reaccept, Instant publishedAt, String publishedBy) {}

    public record View(boolean canManage, Version current, List<Version> versions) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;

    public Terms(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
    }

    public View view(AccountPrincipal who, String kindName) {
        boolean manage = access(who) == Access.FULL;
        Kind kind = Kind.parse(kindName);
        return transactions.execute(() -> {
            var versions = versions(who.organizationId(), kind);
            return new View(manage, versions.isEmpty() ? model(kind) : versions.get(0), versions);
        });
    }

    /** Versão atual (a do modelo, 0, se a organização não publicou): é o que o aceite do portal (#85) consulta. Exige o escopo da organização. */
    public Version current(UUID organizationId, Kind kind) {
        var versions = versions(organizationId, kind);
        return versions.isEmpty() ? model(kind) : versions.get(0);
    }

    public Version publish(AccountPrincipal who, String kindName, String body, Boolean reaccept, String ip) {
        requireFull(who);
        Kind kind = Kind.parse(kindName);
        String text = body == null ? "" : body.strip();
        if (text.length() < MIN_BODY || text.length() > MAX_BODY) throw new ApiValidationException("body", "de 20 a 20000 caracteres");
        if (reaccept == null) throw new ApiValidationException("reaccept", "obrigatório");
        return transactions.execute(() -> {
            Version current = current(who.organizationId(), kind);
            if (text.equals(current.body().strip())) throw new ApiValidationException("body", "igual ao texto atual");
            int next = current.version() + 1;
            try {
                jdbc.sql("insert into terms_version (id, organization_id, kind, version, body, reaccept, published_by_account_id) "
                        + "values (:id, :org, :kind, :version, :body, :reaccept, :by)").param("id", UUID.randomUUID())
                        .param("org", who.organizationId()).param("kind", kind.name()).param("version", next).param("body", text)
                        .param("reaccept", reaccept).param("by", who.accountId()).update();
            } catch (DuplicateKeyException e) {
                throw new ApiStatusException(ErrorCode.TERMS_VERSION_CONFLICT);
            }
            var after = new LinkedHashMap<String, Object>();
            after.put("kind", kind.name());
            after.put("version", next);
            after.put("reaccept", reaccept);
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "terms.published",
                    "terms_version", kind.name() + ":" + next, null, Map.of(), after, null, ip));
            return versions(who.organizationId(), kind).get(0);
        });
    }

    private List<Version> versions(UUID organizationId, Kind kind) {
        return jdbc.sql("select t.version, t.body, t.reaccept, t.published_at, a.name from terms_version t "
                + "left join account a on a.id = t.published_by_account_id where t.organization_id = :org and t.kind = :kind order by t.version desc")
                .param("org", organizationId).param("kind", kind.name()).query(Terms::map).list();
    }

    private static Version model(Kind kind) {
        return new Version(0, kind.template, false, null, null);
    }

    private static Version map(ResultSet rs, int row) throws SQLException {
        return new Version(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getObject(4, OffsetDateTime.class).toInstant(), rs.getString(5));
    }

    private Access access(AccountPrincipal who) {
        Access access = permissions.grant(who, Permission.ORGANIZATION_SETTINGS, null);
        if (access == Access.NONE) throw new AccessDeniedException("Permission denied");
        return access;
    }

    private void requireFull(AccountPrincipal who) {
        if (access(who) != Access.FULL) throw new AccessDeniedException("Permission denied");
    }
}
