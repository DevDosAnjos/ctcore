package br.com.ctcore.teacher;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.LogoImage;
import br.com.ctcore.organization.NewUnit;
import br.com.ctcore.storage.FileStorage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-PRO-03: cadastro e edição de professor, foto e opções do formulário. Organização, perfil e alcance vêm da sessão. O
 * Administrador só mexe nas unidades dele (D4); consentimento exige a declaração da gestão (D5); a situação e a conta não mudam aqui.
 */
@Service
public class TeacherWrite {
    private static final Logger LOG = LoggerFactory.getLogger(TeacherWrite.class);

    public record ModalityInput(UUID modalityId, UUID levelId, Integer degree) {}

    public record Input(String name, String email, String phone, String bio, Boolean directoryConsent, Boolean consentDeclaredByManagement,
            List<UUID> unitIds, List<ModalityInput> modalities) {}

    public record Level(UUID id, String name, int maxDegree) {}

    public record Option(UUID id, String name, boolean graduationEnabled, List<Level> levels) {}

    public record Options(List<Option> modalities) {}

    private record Clean(String name, String email, String phone, String bio, boolean consent, boolean declared, Set<UUID> unitIds,
            List<ModalityInput> modalities) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Teachers teachers;
    private final AuditService audit;
    private final FileStorage storage;

    public TeacherWrite(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Teachers teachers, AuditService audit,
            FileStorage storage) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.teachers = teachers;
        this.audit = audit;
        this.storage = storage;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private static boolean admin(AccountPrincipal who) {
        return who.role() == Role.ADMINISTRATOR;
    }

    private static Set<UUID> reach(AccountPrincipal who) {
        return who.units() == null ? Set.of() : who.units();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static Clean clean(Input in) {
        if (in == null) throw new ApiValidationException("name", "obrigatório");
        String name = in.name() == null ? "" : in.name().strip();
        if (name.length() < 2 || name.length() > 80) throw new ApiValidationException("name", "de 2 a 80 caracteres");
        String bio = blank(in.bio());
        if (bio != null && bio.codePointCount(0, bio.length()) > 300) throw new ApiValidationException("bio", "no máximo 300 caracteres");
        boolean consent = Boolean.TRUE.equals(in.directoryConsent());
        boolean declared = Boolean.TRUE.equals(in.consentDeclaredByManagement());
        if (consent && !declared) throw new ApiValidationException("consentDeclaredByManagement", "declare que o professor autorizou");
        Set<UUID> units = new LinkedHashSet<>(in.unitIds() == null ? List.of() : in.unitIds());
        if (units.isEmpty() || units.contains(null)) throw new ApiValidationException("unitIds", "escolha ao menos uma unidade");
        List<ModalityInput> modalities = in.modalities() == null ? List.of() : in.modalities();
        Set<UUID> seen = new HashSet<>();
        for (ModalityInput m : modalities) {
            if (m == null || m.modalityId() == null) throw new ApiValidationException("modalities", "modalidade obrigatória");
            if (!seen.add(m.modalityId())) throw new ApiValidationException("modalities", "modalidade repetida");
        }
        return new Clean(name, NewUnit.email("email", blank(in.email())), NewUnit.phone("phone", in.phone()), bio, consent, declared,
                units, modalities);
    }

    public Teachers.Detail create(AccountPrincipal who, Input input, String ip) {
        teachers.requireAccess(who);
        Clean c = clean(input);
        return transactions.execute(() -> {
            checkUnits(who, c.unitIds());
            List<ModalityInput> modalities = checkModalities(who, c.modalities());
            UUID id = UUID.randomUUID();
            jdbc.sql("insert into teacher (id, organization_id, name, name_key, email, phone, bio, directory_consent, "
                    + "consent_declared_by_management) values (:id, :org, :name, :key, :email, :phone, :bio, :consent, :declared)")
                    .param("id", id).param("org", who.organizationId()).param("name", c.name()).param("key", Modalities.normalize(c.name()))
                    .param("email", c.email()).param("phone", c.phone()).param("bio", c.bio()).param("consent", c.consent())
                    .param("declared", c.declared()).update();
            replaceUnits(who, id, c.unitIds());
            replaceModalities(who, id, modalities);
            Map<String, Object> after = fields(c.name(), c.bio(), c.consent(), c.declared(), c.unitIds(), modalities);
            if (c.email() != null) after.put("email", "informado");
            if (c.phone() != null) after.put("phone", "informado");
            audit.record(new AuditEntry(actor(who), "teacher.created", "teacher", id.toString(), null, null, after, null, ip));
            return teachers.detail(who, id);
        });
    }

    public Teachers.Detail update(AccountPrincipal who, UUID id, Input input, String ip) {
        teachers.requireAccess(who);
        Clean c = clean(input);
        return transactions.execute(() -> {
            Teachers.Basic old = teachers.find(who, id);
            Set<UUID> current = unitIds(who, id);
            checkUnits(who, c.unitIds());
            List<ModalityInput> modalities = checkModalities(who, c.modalities());
            Map<String, Object> before = fields(old.name(), old.bio(), old.consent(), old.declared(), current, currentModalities(who, id));
            jdbc.sql("update teacher set name = :name, name_key = :key, email = :email, phone = :phone, bio = :bio, "
                    + "directory_consent = :consent, consent_declared_by_management = :declared where organization_id = :org and id = :id")
                    .param("name", c.name()).param("key", Modalities.normalize(c.name())).param("email", c.email()).param("phone", c.phone())
                    .param("bio", c.bio()).param("consent", c.consent()).param("declared", c.declared()).param("org", who.organizationId())
                    .param("id", id).update();
            replaceUnits(who, id, c.unitIds());
            replaceModalities(who, id, modalities);
            Map<String, Object> after = fields(c.name(), c.bio(), c.consent(), c.declared(), unitIds(who, id), modalities);
            if (!Objects.equals(old.email(), c.email())) after.put("email", "alterado");
            if (!Objects.equals(old.phone(), c.phone())) after.put("phone", "alterado");
            audit.record(new AuditEntry(actor(who), "teacher.updated", "teacher", id.toString(), null, before, after, null, ip));
            return teachers.detail(who, id);
        });
    }

    public Teachers.Detail replacePhoto(AccountPrincipal who, UUID id, byte[] content, String ip) {
        teachers.requireAccess(who);
        LogoImage image;
        try {
            image = LogoImage.parse(content);
        } catch (IllegalArgumentException e) {
            throw new ApiValidationException("file", e.getMessage());
        }
        // Sem alcance ou fora da organização: 404 antes de gravar o arquivo.
        transactions.execute(() -> teachers.find(who, id));
        String name = "teacher-" + UUID.randomUUID() + "." + image.extension();
        storage.put(name, image.bytes(), image.contentType());
        String previous;
        try {
            previous = transactions.execute(() -> setPhoto(who, id, name, ip));
        } catch (RuntimeException e) {
            discard(name);
            throw e;
        }
        if (previous != null) discard(previous);
        return transactions.execute(() -> teachers.detail(who, id));
    }

    public Teachers.Detail removePhoto(AccountPrincipal who, UUID id, String ip) {
        teachers.requireAccess(who);
        String previous = transactions.execute(() -> setPhoto(who, id, null, ip));
        if (previous != null) discard(previous);
        return transactions.execute(() -> teachers.detail(who, id));
    }

    /** Dentro da transação: troca a coluna e audita; devolve o arquivo anterior. */
    private String setPhoto(AccountPrincipal who, UUID id, String name, String ip) {
        Teachers.Basic before = teachers.find(who, id);
        if (Objects.equals(before.photo(), name)) return null;
        jdbc.sql("update teacher set photo = :photo where organization_id = :org and id = :id").param("photo", name)
                .param("org", who.organizationId()).param("id", id).update();
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("photo", before.photo());
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("photo", name);
        audit.record(new AuditEntry(actor(who), name == null ? "teacher.photo_removed" : "teacher.photo_updated", "teacher", id.toString(),
                null, old, next, null, ip));
        return before.photo();
    }

    // Arquivo antigo (ou novo, se a transação falhou): falha ao apagar vai para o log e não desfaz a troca.
    private void discard(String name) {
        try {
            storage.delete(name);
        } catch (RuntimeException e) {
            LOG.warn("Could not delete teacher file {}: {}", name, e.getClass().getSimpleName());
        }
    }

    /** RF-PRO-03 (D7): modalidades ativas da organização com os níveis, para o formulário. */
    public Options options(AccountPrincipal who) {
        teachers.requireAccess(who);
        return transactions.execute(() -> {
            Map<UUID, List<Level>> levels = new LinkedHashMap<>();
            jdbc.sql("select modality_id, id, name, max_degree from graduation_level where organization_id = :org order by modality_id, position")
                    .param("org", who.organizationId())
                    .query((rs, n) -> levels.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                            .add(new Level(rs.getObject(2, UUID.class), rs.getString(3), rs.getInt(4)))).list();
            return new Options(jdbc.sql("select m.id, m.name, o.graduation_enabled from organization_modality o join modality m on m.id = o.modality_id "
                    + "where o.organization_id = :org and o.active order by m.name, m.id").param("org", who.organizationId())
                    .query((rs, n) -> {
                        UUID id = rs.getObject(1, UUID.class);
                        boolean on = rs.getBoolean(3);
                        return new Option(id, rs.getString(2), on, on ? levels.getOrDefault(id, List.of()) : List.of());
                    }).list());
        });
    }

    /** 403 para unidade fora do alcance do Administrador; 400 para unidade de outra organização, inativa ou inexistente. */
    private void checkUnits(AccountPrincipal who, Set<UUID> asked) {
        if (admin(who) && !reach(who).containsAll(asked)) throw new AccessDeniedException("Unit out of reach");
        int found = jdbc.sql("select count(*) from unit where organization_id = :org and active and id in (:ids)")
                .param("org", who.organizationId()).param("ids", asked).query(Integer.class).single();
        if (found != asked.size()) throw new ApiValidationException("unitIds", "escolha unidades ativas da organização");
    }

    /** O Coordenador troca o conjunto inteiro; o Administrador, só as unidades dele: as de fora do alcance ficam (D4). */
    private void replaceUnits(AccountPrincipal who, UUID id, Set<UUID> asked) {
        var delete = jdbc.sql("delete from teacher_unit where organization_id = :org and teacher_id = :id"
                + (admin(who) ? " and unit_id in (:reach)" : "")).param("org", who.organizationId()).param("id", id);
        if (admin(who)) delete = delete.param("reach", reach(who));
        delete.update();
        for (UUID unit : asked) {
            jdbc.sql("insert into teacher_unit (organization_id, teacher_id, unit_id) values (:org, :id, :unit) on conflict do nothing")
                    .param("org", who.organizationId()).param("id", id).param("unit", unit).update();
        }
    }

    /** RN-PRO-03 (D3): dentro da transação; devolve as entradas com o grau já definido (0 sem graduação). */
    private List<ModalityInput> checkModalities(AccountPrincipal who, List<ModalityInput> asked) {
        List<ModalityInput> out = new ArrayList<>();
        for (ModalityInput m : asked) {
            Boolean graduation = jdbc.sql("select graduation_enabled from organization_modality where organization_id = :org "
                    + "and modality_id = :m and active").param("org", who.organizationId()).param("m", m.modalityId())
                    .query(Boolean.class).optional().orElse(null);
            if (graduation == null) throw new ApiValidationException("modalities", "modalidade inativa ou inexistente");
            if (!graduation) {
                if (m.levelId() != null || m.degree() != null && m.degree() != 0) {
                    throw new ApiValidationException("modalities", "a modalidade não tem graduação");
                }
                out.add(new ModalityInput(m.modalityId(), null, 0));
                continue;
            }
            if (m.levelId() == null) throw new ApiValidationException("modalities", "escolha o nível");
            Integer max = jdbc.sql("select max_degree from graduation_level where organization_id = :org and modality_id = :m and id = :l")
                    .param("org", who.organizationId()).param("m", m.modalityId()).param("l", m.levelId()).query(Integer.class)
                    .optional().orElse(null);
            if (max == null) throw new ApiValidationException("modalities", "nível inválido para a modalidade");
            if (m.degree() == null || m.degree() < 0 || m.degree() > max) throw new ApiValidationException("modalities", "grau de 0 a " + max);
            out.add(m);
        }
        return out;
    }

    private void replaceModalities(AccountPrincipal who, UUID id, List<ModalityInput> modalities) {
        jdbc.sql("delete from teacher_modality where organization_id = :org and teacher_id = :id").param("org", who.organizationId())
                .param("id", id).update();
        for (ModalityInput m : modalities) {
            jdbc.sql("insert into teacher_modality (organization_id, teacher_id, modality_id, level_id, degree) values (:org, :id, :m, :l, :d)")
                    .param("org", who.organizationId()).param("id", id).param("m", m.modalityId()).param("l", m.levelId())
                    .param("d", m.degree()).update();
        }
    }

    private Set<UUID> unitIds(AccountPrincipal who, UUID id) {
        return new LinkedHashSet<>(jdbc.sql("select unit_id from teacher_unit where organization_id = :org and teacher_id = :id")
                .param("org", who.organizationId()).param("id", id).query(UUID.class).list());
    }

    private List<ModalityInput> currentModalities(AccountPrincipal who, UUID id) {
        return jdbc.sql("select modality_id, level_id, degree from teacher_modality where organization_id = :org and teacher_id = :id")
                .param("org", who.organizationId()).param("id", id)
                .query((rs, n) -> new ModalityInput(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3))).list();
    }

    // Sem e-mail nem telefone: esses dois entram só como "informado" ou "alterado".
    private static Map<String, Object> fields(String name, String bio, boolean consent, boolean declared, Set<UUID> units,
            List<ModalityInput> modalities) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("bio", bio);
        m.put("directoryConsent", consent);
        m.put("consentDeclaredByManagement", declared);
        m.put("unitIds", new TreeSet<>(units).stream().map(UUID::toString).toList());
        m.put("modalities", modalities.stream().map(x -> x.modalityId() + ":" + x.levelId() + ":" + x.degree()).sorted().toList());
        return m;
    }
}
