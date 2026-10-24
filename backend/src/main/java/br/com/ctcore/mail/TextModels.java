package br.com.ctcore.mail;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.communication.AnnouncementText;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-COM-03 (RN-COM-12): o texto próprio (assunto e corpo) dos modelos editáveis da organização, igual para todas as unidades; sem texto próprio vale o da
 * plataforma ({@code text.<CHAVE>.*} em messages.properties). Salvar e voltar ao padrão vão para a auditoria com o antes e o depois.
 */
@Service
public class TextModels {
    static final int MAX_SUBJECT = 150;
    static final int MAX_ANNOUNCEMENT_TITLE = 120;
    static final int MAX_BODY = 2000;
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final Pattern FIELD = Pattern.compile("\\{([^{}]*)}");

    public record Text(String subject, String body) {}

    public record Item(String key, TextModel.Group group, List<String> fields, Text platform, Text custom, Instant updatedAt, String updatedBy) {}

    public record View(boolean canManage, List<Item> models) {}

    public record Template(String title, String body) {}

    public record Templates(Map<String, Template> templates) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;
    private final MessageSource messages;

    public TextModels(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit, MessageSource messages) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
        this.messages = messages;
    }

    public View view(AccountPrincipal who) {
        boolean manage = access(who) == Access.FULL;
        return transactions.execute(() -> new View(manage, items(who.organizationId(), null)));
    }

    public Item save(AccountPrincipal who, String keyName, String subject, String body, String ip) {
        requireFull(who);
        TextModel model = TextModel.parse(keyName);
        String s = subject == null ? "" : subject.strip();
        String b = body == null ? "" : body.strip();
        validate(model, "subject", s, model.group == TextModel.Group.ANNOUNCEMENTS ? MAX_ANNOUNCEMENT_TITLE : MAX_SUBJECT);
        validate(model, "body", b, MAX_BODY);
        if (model.group == TextModel.Group.ANNOUNCEMENTS) AnnouncementText.validate(b);
        Text platform = platform(model);
        if (s.equals(platform.subject().strip()) && b.equals(platform.body().strip())) throw new ApiValidationException("body", "igual ao texto da plataforma");
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            Optional<Text> before = custom(org, model);
            jdbc.sql("insert into text_model (id, organization_id, key, subject, body, updated_by_account_id, updated_at) values (:id, :org, :key, :s, :b, :by, now()) "
                    + "on conflict (organization_id, key) do update set subject = excluded.subject, body = excluded.body, "
                    + "updated_by_account_id = excluded.updated_by_account_id, updated_at = excluded.updated_at")
                    .param("id", UUID.randomUUID()).param("org", org).param("key", model.name()).param("s", s).param("b", b).param("by", who.accountId()).update();
            audit.record(entry(who, "texts.updated", model, before.map(TextModels::map).orElse(Map.of()), map(new Text(s, b)), ip));
            return items(org, model).get(0);
        });
    }

    /** Sem texto próprio, não faz nada e não audita. */
    public void reset(AccountPrincipal who, String keyName, String ip) {
        requireFull(who);
        TextModel model = TextModel.parse(keyName);
        transactions.execute(() -> {
            UUID org = who.organizationId();
            Optional<Text> before = custom(org, model);
            if (before.isPresent()) {
                jdbc.sql("delete from text_model where organization_id = :org and key = :key").param("org", org).param("key", model.name()).update();
                audit.record(entry(who, "texts.reset", model, map(before.get()), Map.of(), ip));
            }
            return null;
        });
    }

    /** RN-COM-04, RN-COM-12: o título e o texto em uso de cada tipo de comunicado (o da organização ou o da plataforma). */
    public Templates announcementTemplates(UUID org) {
        var out = new LinkedHashMap<String, Template>();
        for (Item item : transactions.execute(() -> items(org, null))) {
            if (item.group() != TextModel.Group.ANNOUNCEMENTS) continue;
            Text t = item.custom() != null ? item.custom() : item.platform();
            out.put(item.key().substring("ANNOUNCEMENT_".length()), new Template(t.subject(), t.body()));
        }
        return new Templates(out);
    }

    /** O texto próprio do modelo; exige o escopo da organização (dentro da transação dela). */
    public Optional<Text> custom(UUID org, TextModel model) {
        return jdbc.sql("select subject, body from text_model where organization_id = :org and key = :key").param("org", org).param("key", model.name())
                .query((rs, n) -> new Text(rs.getString(1), rs.getString(2))).optional();
    }

    public Text platform(TextModel model) {
        return new Text(messages.getMessage("text." + model.name() + ".subject", null, PT_BR), messages.getMessage("text." + model.name() + ".body", null, PT_BR));
    }

    /** Preenche os campos em uma só passada: o valor de um campo nunca é lido como modelo. Campo sem valor fica como está. */
    public static String fill(String text, Map<String, String> values) {
        Matcher m = FIELD.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(v != null ? v : m.group()));
        }
        return m.appendTail(out).toString();
    }

    /** Lança 400 no campo: vazio, grande demais, chave sem fecho ou campo automático que o modelo não aceita. */
    static void validate(TextModel model, String field, String text, int max) {
        if (text.isEmpty()) throw new ApiValidationException(field, "obrigatório");
        if (text.length() > max) throw new ApiValidationException(field, "no máximo " + max + " caracteres");
        Matcher m = FIELD.matcher(text);
        while (m.find()) {
            if (!model.fields.contains(m.group(1))) throw new ApiValidationException(field, "campo automático desconhecido: {" + m.group(1) + "}");
        }
        String rest = FIELD.matcher(text).replaceAll("");
        if (rest.indexOf('{') >= 0 || rest.indexOf('}') >= 0) throw new ApiValidationException(field, "chave { } sem fecho");
    }

    private List<Item> items(UUID org, TextModel only) {
        var rows = new LinkedHashMap<String, Map<String, Object>>();
        for (var r : jdbc.sql("select t.key, t.subject, t.body, t.updated_at, a.name from text_model t left join account a on a.id = t.updated_by_account_id "
                + "where t.organization_id = :org").param("org", org).query().listOfRows()) {
            rows.put((String) r.get("key"), r);
        }
        var out = new ArrayList<Item>();
        for (TextModel m : TextModel.values()) {
            if (only != null && only != m) continue;
            var r = rows.get(m.name());
            out.add(new Item(m.name(), m.group, m.fields, platform(m), r == null ? null : new Text((String) r.get("subject"), (String) r.get("body")),
                    r == null ? null : ((java.sql.Timestamp) r.get("updated_at")).toInstant(), r == null ? null : (String) r.get("name")));
        }
        return out;
    }

    private static Map<String, Object> map(Text t) {
        var m = new LinkedHashMap<String, Object>();
        m.put("subject", t.subject());
        m.put("body", t.body());
        return m;
    }

    private static AuditEntry entry(AccountPrincipal who, String action, TextModel model, Map<String, Object> before, Map<String, Object> after, String ip) {
        return new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), action, "text_model", model.name(), null, before, after, null, ip);
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
