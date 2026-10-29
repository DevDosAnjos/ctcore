package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.storage.FileStorage;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RF-POR-06, RN-POR-01, RN-POR-02, RN-POR-06, RN-POR-08, RN-POR-09, RN-COM-08: aba Avisos do portal. Lista os comunicados enviados que alcançam o aluno
 * escolhido (mesma regra do Início e do sino), grava a leitura ao abrir (por aluno) e "Marcar todos como lidos" (uma marca por conta, que não é leitura).
 * Sem contato do professor, presença, frequência, progresso nem dado financeiro; título e texto nunca vão para a auditoria.
 */
@Service
public class PortalAnnouncements {
    public enum Filter { ALL, UNREAD, MODALITY }

    public record Link(UUID id, String name, boolean self) {}

    public record Destination(String kind, List<String> names) {}

    public record Item(UUID id, String title, String type, OffsetDateTime sentAt, boolean pinned, boolean unread, Destination destination, String author) {}

    public record Detail(UUID id, String title, String type, OffsetDateTime sentAt, boolean pinned, boolean unread, Destination destination, String author,
            String body, String imageUrl) {}

    public record View(boolean reduced, List<Link> students, UUID studentId, Filter filter, UUID modalityId, List<PortalHome.Ref> modalities, int unreadCount,
            List<Item> items, int page, int size, long total) {}

    public record Dismissed(int dismissed) {}

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    /** Fixado vale até o fim de pinned_until, no fuso da unidade do aviso (sem unidade, a da matrícula do aluno); como no Início. */
    private static final String PINNED = "coalesce(a.pinned_until >= (cast(:now as timestamptz) at time zone coalesce(ua.timezone, su.timezone))::date, false)";
    private static final String DESTINATION = "case when a.target_kind = 'CLASSES' then (select array_agg(k.name::text order by lower(k.name)) from announcement_class ac "
            + "join training_class k on k.organization_id = ac.organization_id and k.id = ac.class_id where ac.organization_id = a.organization_id and ac.announcement_id = a.id) "
            + "else array[coalesce(tc.name, md.name, ua.name)]::text[] end";
    private static final String FROM = " from announcement a join student st on st.organization_id = a.organization_id and st.id = :sid "
            + "join unit su on su.organization_id = st.organization_id and su.id = st.registered_unit_id "
            + "left join unit ua on ua.organization_id = a.organization_id and ua.id = a.unit_id "
            + "left join training_class tc on tc.organization_id = a.organization_id and tc.id = a.class_id left join modality md on md.id = a.modality_id "
            + "join account au on au.id = a.author_account_id ";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final PortalStudents portal;
    private final PortalShell shell;
    private final AuditService audit;
    private final FileStorage storage;
    private final Clock clock;

    public PortalAnnouncements(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, PortalStudents portal, PortalShell shell, AuditService audit,
            FileStorage storage, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.portal = portal;
        this.shell = shell;
        this.audit = audit;
        this.storage = storage;
        this.clock = clock;
    }

    /** Quem chama já conferiu o aceite do termo. {@code studentId} nulo: o primeiro cartão; {@code size} é limitado a 50. */
    public View list(AccountPrincipal who, UUID studentId, String filter, UUID modalityId, Integer page, Integer size) {
        Filter chosen = parse(filter);
        List<PortalStudents.Card> cards = portal.cards(who);
        UUID selected = select(cards, studentId);
        List<Link> links = cards.stream().map(c -> new Link(c.id(), c.name(), c.self())).toList();
        int p = page == null ? 1 : Math.max(page, 1);
        int s = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            if (shell.reduced(who)) return new View(true, links, selected, chosen, null, List.of(), 0, List.of(), p, s, 0);
            List<PortalHome.Ref> modalities = jdbc.sql("select distinct md.id, md.name from enrollment_modality em join modality md on md.id = em.modality_id where "
                    + PortalHome.ACTIVE_ROW + " order by md.name, md.id").param("org", org).param("sid", selected)
                    .query((rs, n) -> new PortalHome.Ref(rs.getObject(1, UUID.class), rs.getString(2))).list();
            if (chosen == Filter.MODALITY && (modalityId == null || modalities.stream().noneMatch(m -> m.id().equals(modalityId)))) {
                throw new ApiValidationException("modalityId", "modalidade que o aluno não pratica");
            }
            UUID modality = chosen == Filter.MODALITY ? modalityId : null;
            String where = " where a.organization_id = :org and a.sent_at is not null and exists (select 1 from enrollment_modality em where " + PortalHome.ACTIVE_ROW
                    + (modality == null ? "" : " and em.modality_id = :mod") + " and " + PortalShell.REACH + ")"
                    + (chosen == Filter.UNREAD ? " and " + PortalShell.unread(":sid") : "");
            var base = jdbc.sql("select count(*)" + FROM + where).param("org", org).param("sid", selected).param("acc", who.accountId()).param("mod", modality);
            long total = base.query(Long.class).single();
            List<Item> items = jdbc.sql("select * from (select a.id, a.title, a.type, a.sent_at, " + PINNED + " as pinned, " + PortalShell.unread(":sid") + " as unread, "
                    + "a.target_kind, " + DESTINATION + " as names, au.name as author" + FROM + where + ") x order by x.pinned desc, x.sent_at desc, x.id limit :limit offset :offset")
                    .param("org", org).param("sid", selected).param("acc", who.accountId()).param("mod", modality).param("now", now).param("limit", s)
                    .param("offset", (long) (p - 1) * s).query((rs, n) -> new Item(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            rs.getObject(4, OffsetDateTime.class), rs.getBoolean(5), rs.getBoolean(6), destination(rs.getString(7), rs.getArray(8)), rs.getString(9))).list();
            int unread = jdbc.sql("select count(*)" + FROM + " where a.organization_id = :org and a.sent_at is not null and exists (select 1 from enrollment_modality em where "
                    + PortalHome.ACTIVE_ROW + " and " + PortalShell.REACH + ") and " + PortalShell.unread(":sid")).param("org", org).param("sid", selected)
                    .param("acc", who.accountId()).query(Integer.class).single();
            return new View(false, links, selected, chosen, modality, modalities, unread, items, p, s, total);
        });
    }

    /** RN-COM-08: abrir grava a leitura do aluno escolhido (repetir não muda nada). Aviso que o aluno não alcança, agendado ou de outra organização: 404. */
    public Detail open(AccountPrincipal who, UUID id, UUID studentId, String ip) {
        UUID selected = select(portal.cards(who), studentId);
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Detail detail = transactions.execute(() -> {
            UUID org = who.organizationId();
            if (shell.reduced(who)) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            Detail found = jdbc.sql("select a.id, a.title, a.type, a.sent_at, " + PINNED + ", false, a.target_kind, " + DESTINATION + ", au.name, a.body, a.image_name" + FROM
                    + " where a.organization_id = :org and a.id = :id and a.sent_at is not null and exists (select 1 from enrollment_modality em where " + PortalHome.ACTIVE_ROW
                    + " and " + PortalShell.REACH + ")").param("org", org).param("sid", selected).param("id", id).param("now", now)
                    .query((rs, n) -> new Detail(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class), rs.getBoolean(5),
                            rs.getBoolean(6), destination(rs.getString(7), rs.getArray(8)), rs.getString(9), rs.getString(10), rs.getString(11))).optional()
                    .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            int inserted = jdbc.sql("insert into announcement_read (organization_id, announcement_id, student_id, account_id) values (:org, :id, :sid, :acc) on conflict do nothing")
                    .param("org", org).param("id", id).param("sid", selected).param("acc", who.accountId()).update();
            if (inserted > 0) {
                audit.record(new AuditEntry(actor(who), "portal_announcement.opened", "announcement", id.toString(), null, Map.of(), Map.of("studentId", selected), null, ip));
            }
            return found;
        });
        return new Detail(detail.id(), detail.title(), detail.type(), detail.sentAt(), detail.pinned(), false, detail.destination(), detail.author(), detail.body(),
                imageUrl(detail.imageUrl()));
    }

    /** D3: limpa, para a conta, os avisos não lidos de qualquer aluno ligado a ela; não grava leitura. Devolve quantos foram limpos. */
    public Dismissed readAll(AccountPrincipal who, String ip) {
        portal.cards(who);
        return transactions.execute(() -> {
            if (shell.reduced(who)) return new Dismissed(0);
            int done = jdbc.sql("insert into announcement_dismissal (organization_id, announcement_id, account_id) select a.organization_id, a.id, :acc " + PortalShell.UNREAD_FROM
                    + " on conflict do nothing").param("org", who.organizationId()).param("acc", who.accountId()).update();
            if (done > 0) {
                audit.record(new AuditEntry(actor(who), "portal_announcement.read_all", "account", who.accountId().toString(), null, Map.of(), Map.of("count", done), null, ip));
            }
            return new Dismissed(done);
        });
    }

    private static UUID select(List<PortalStudents.Card> cards, UUID studentId) {
        if (cards.isEmpty()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        UUID selected = studentId != null ? studentId : cards.get(0).id();
        if (cards.stream().noneMatch(c -> c.id().equals(selected))) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        return selected;
    }

    private static Filter parse(String filter) {
        if (filter == null || filter.isBlank()) return Filter.ALL;
        try {
            return Filter.valueOf(filter);
        } catch (IllegalArgumentException e) {
            throw new ApiValidationException("filter", "use ALL, UNREAD ou MODALITY");
        }
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private static Destination destination(String kind, java.sql.Array names) throws SQLException {
        String[] all = names == null ? new String[0] : (String[]) names.getArray();
        List<String> list = java.util.Arrays.stream(all).filter(java.util.Objects::nonNull).toList();
        return new Destination(kind, "ORGANIZATION".equals(kind) ? List.of() : list);
    }

    // O arquivo ausente vira nulo: o aviso abre sem imagem.
    private String imageUrl(String name) {
        if (name == null) return null;
        try {
            return storage.readUrl(name);
        } catch (FileStorage.NotFound e) {
            return null;
        }
    }
}
