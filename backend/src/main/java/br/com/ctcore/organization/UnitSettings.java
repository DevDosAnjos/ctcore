package br.com.ctcore.organization;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.integration.MapLocator;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.UnitUpdate.TeacherPermissions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.PermissionTable;
import br.com.ctcore.permission.Permissions;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-ORG-04: dados e configurações de uma unidade. Coordenador em qualquer unidade da organização, Administrador só nas suas
 * (linha UNIT_SETTINGS). Unidade de outra organização responde 404, igual a id inexistente.
 */
@Service
public class UnitSettings {
    public record UnitChoice(UUID id, String name, String shortName, String city, boolean active) {}

    public enum PaymentAccount { CONNECTED, NOT_CONNECTED }

    public record Settings(List<Integer> dueDays, boolean enrollmentFeeEnabled, long enrollmentFeeCents, Map<String, Long> feesCents,
            String pixKey, PaymentAccount paymentAccount, List<Integer> reminderDays, TeacherPermissions teacherPermissions,
            boolean directoryEnabled, boolean trialLinkEnabled, boolean selfRegistrationLinkEnabled) {}

    public record UnitDetail(UUID id, String name, String shortName, String postalCode, String address, String city, Double latitude,
            Double longitude, String phone, String email, String whatsapp, String cnpj, boolean active, Settings settings, int version,
            LastChange lastChange) {
        UnitDetail with(LastChange last) {
            return new UnitDetail(id, name, shortName, postalCode, address, city, latitude, longitude, phone, email, whatsapp, cnpj, active,
                    settings, version, last);
        }
    }

    // Valores diferentes nos dois lados, senão o AuditService descartaria o campo como "sem mudança".
    private static final String CHANGED = "[alterado]";
    private static final String PREVIOUS = "[anterior]";
    private static final Set<String> HIDDEN = Set.of("pixKey", "cnpj");
    private static final String SELECT = """
            select u.id, u.name, u.short_name, u.postal_code, u.address, u.city, u.latitude, u.longitude, u.phone, u.email, u.whatsapp,
                   u.cnpj, u.active, s.due_days, s.enrollment_fee_enabled, s.enrollment_fee_cents, %s, s.pix_key,
                   s.payment_account_reference is not null, s.reminder_days, s.teacher_permissions, s.directory_enabled,
                   s.trial_link_enabled, s.self_registration_link_enabled, u.version
            from unit u join unit_settings s on s.unit_id = u.id and s.organization_id = u.organization_id
            """.formatted(String.join(", ", UnitUpdate.FEE_COLUMNS.values().stream().map(c -> "s." + c).toList()));

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;
    private final MapLocator locator;

    public UnitSettings(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit,
            MapLocator locator) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
        this.locator = locator;
    }

    /** Unidades sobre as quais o vínculo tem UNIT_SETTINGS: todas para o Coordenador, só as suas para o Administrador. */
    public List<UnitChoice> mine(AccountPrincipal who) {
        requireRole(who);
        List<UnitChoice> all = transactions.execute(() -> jdbc
                .sql("select id, name, short_name, city, active from unit where organization_id = :org order by name, id")
                .param("org", who.organizationId())
                .query((rs, i) -> new UnitChoice(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)))
                .list());
        return all.stream().filter(u -> permissions.grant(who, Permission.UNIT_SETTINGS, u.id()) != Access.NONE).toList();
    }

    /** Acesso do vínculo a UNIT_SETTINGS na unidade; 404 se a unidade não é da organização da sessão (para quem guarda dados por unidade). */
    public Access access(AccountPrincipal who, UUID unitId) {
        requireRole(who);
        int found = transactions.execute(() -> jdbc.sql("select count(*) from unit where id = :id and organization_id = :org")
                .param("id", unitId).param("org", who.organizationId()).query(Integer.class).single());
        if (found == 0) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        return permissions.grant(who, Permission.UNIT_SETTINGS, unitId);
    }

    public UnitDetail read(AccountPrincipal who, UUID unitId) {
        UnitDetail detail = locate(who, unitId);
        if (permissions.grant(who, Permission.UNIT_SETTINGS, unitId) == Access.NONE) throw new AccessDeniedException("Permission denied");
        return detail;
    }

    public UnitDetail update(AccountPrincipal who, UUID unitId, String ifMatch, UnitUpdate request, String ip) {
        UnitDetail current = locate(who, unitId);
        if (permissions.grant(who, Permission.UNIT_SETTINGS, unitId) != Access.FULL) throw new AccessDeniedException("Permission denied");
        int expectedVersion = IfMatch.version(ifMatch);
        UnitUpdate next = request.validated();
        double[] coordinates = coordinates(current, next);
        return transactions.execute(() -> {
            // RNF-15: a trava vem antes da leitura, para a checagem de versão e a gravação serem atômicas.
            jdbc.sql("select id from unit where id = :id for update").param("id", unitId).query(UUID.class).optional();
            UnitDetail before = load(unitId).orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            if (before.version() != expectedVersion) throw new ApiStatusException(ErrorCode.EDIT_CONFLICT);
            UnitUpdate.Settings s = next.settings();
            jdbc.sql("""
                    update unit set name = :name, postal_code = :cep, address = :address, city = :city, latitude = :lat, longitude = :lon,
                        phone = :phone, email = :email, whatsapp = :whatsapp, cnpj = :cnpj where id = :id""")
                    .param("name", next.name()).param("cep", next.postalCode()).param("address", next.address()).param("city", next.city())
                    .param("lat", coordinates == null ? null : BigDecimal.valueOf(coordinates[0]))
                    .param("lon", coordinates == null ? null : BigDecimal.valueOf(coordinates[1]))
                    .param("phone", next.phone()).param("email", next.email()).param("whatsapp", next.whatsapp()).param("cnpj", next.cnpj())
                    .param("id", unitId).update();
            StringBuilder fees = new StringBuilder();
            UnitUpdate.FEE_COLUMNS.forEach((key, column) -> fees.append(", ").append(column).append(" = :").append(key));
            var statement = jdbc.sql("""
                    update unit_settings set due_days = cast(:due as integer[]), enrollment_fee_enabled = :feeOn, enrollment_fee_cents = :fee,
                        pix_key = :pixKey, reminder_days = cast(:reminders as integer[]), teacher_permissions = cast(:teacher as boolean[]),
                        directory_enabled = :directory, trial_link_enabled = :trial, self_registration_link_enabled = :self"""
                    + fees + " where unit_id = :id")
                    .param("due", literal(s.dueDays())).param("feeOn", s.enrollmentFeeEnabled()).param("fee", s.enrollmentFeeCents())
                    .param("pixKey", s.pixKey()).param("reminders", literal(s.reminderDays())).param("teacher", literal(teacher(s.teacherPermissions())))
                    .param("directory", s.directoryEnabled()).param("trial", s.trialLinkEnabled())
                    .param("self", s.selfRegistrationLinkEnabled()).param("id", unitId);
            for (var fee : s.feesCents().entrySet()) statement = statement.param(fee.getKey(), fee.getValue());
            statement.update();
            UnitDetail after = load(unitId).orElseThrow();
            Map<String, Object> was = new LinkedHashMap<>();
            Map<String, Object> now = new LinkedHashMap<>();
            Map<String, Object> flatBefore = flat(before);
            flat(after).forEach((key, value) -> {
                if (Objects.equals(value, flatBefore.get(key))) return;
                boolean hidden = HIDDEN.contains(key);
                was.put(key, hidden ? PREVIOUS : flatBefore.get(key));
                now.put(key, hidden ? CHANGED : value);
            });
            if (!now.isEmpty()) {
                jdbc.sql("update unit set version = version + 1 where id = :id").param("id", unitId).update();
                audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "unit.update", "unit",
                        unitId.toString(), unitId, was, now, null, ip));
            }
            return loadWithLast(who, unitId).orElseThrow();
        });
    }

    /** Papéis sem a linha UNIT_SETTINGS (Professor, portal) são recusados sem consultar a unidade. */
    private void requireRole(AccountPrincipal who) {
        if (who == null || who.organizationId() == null || PermissionTable.grant(who.role(), Permission.UNIT_SETTINGS).access() == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
    }

    private UnitDetail locate(AccountPrincipal who, UUID unitId) {
        requireRole(who);
        return transactions.execute(() -> loadWithLast(who, unitId)).orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    private Optional<UnitDetail> loadWithLast(AccountPrincipal who, UUID unitId) {
        return load(unitId).map(d -> d.with(LastChange.find(jdbc, who.organizationId(), "unit", unitId, LastChange.UNIT_ACTIONS)));
    }

    // Latitude e longitude enviadas valem; senão o CEP novo é geocodificado pela porta; CEP igual mantém as atuais (D3).
    private double[] coordinates(UnitDetail current, UnitUpdate next) {
        if (next.latitude() != null) return new double[] {next.latitude(), next.longitude()};
        if (Objects.equals(current.postalCode(), next.postalCode())) {
            return current.latitude() == null ? null : new double[] {current.latitude(), current.longitude()};
        }
        Optional<MapLocator.Coordinates> found = next.postalCode() == null ? Optional.empty() : locator.locate(next.postalCode());
        return found.map(c -> new double[] {c.latitude(), c.longitude()}).orElse(null);
    }

    private Optional<UnitDetail> load(UUID unitId) {
        return jdbc.sql(SELECT + "where u.id = :id").param("id", unitId).query(UnitSettings::detail).optional();
    }

    private static UnitDetail detail(ResultSet rs, int i) throws SQLException {
        Map<String, Long> fees = new LinkedHashMap<>();
        int column = 17;
        for (String key : UnitUpdate.FEE_COLUMNS.keySet()) fees.put(key, rs.getLong(column++));
        Boolean[] teacher = (Boolean[]) rs.getArray(29).getArray();
        Settings settings = new Settings(ints(rs.getArray(14)), rs.getBoolean(15), rs.getLong(16), fees, rs.getString(26),
                rs.getBoolean(27) ? PaymentAccount.CONNECTED : PaymentAccount.NOT_CONNECTED, ints(rs.getArray(28)),
                new TeacherPermissions(teacher[PermissionTable.teacherFlag(Permission.ANNOUNCEMENTS)],
                        teacher[PermissionTable.teacherFlag(Permission.AGENDA_EDIT)],
                        teacher[PermissionTable.teacherFlag(Permission.STUDENT_CONTACTS)],
                        teacher[PermissionTable.teacherFlag(Permission.TRIAL_CLASSES)],
                        teacher[PermissionTable.teacherFlag(Permission.STUDENT_RECORD)]),
                rs.getBoolean(30), rs.getBoolean(31), rs.getBoolean(32));
        return new UnitDetail(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                number(rs.getBigDecimal(7)), number(rs.getBigDecimal(8)), rs.getString(9), rs.getString(10), rs.getString(11),
                rs.getString(12), rs.getBoolean(13), settings, rs.getInt(33), null);
    }

    private static Double number(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static List<Integer> ints(Array array) throws SQLException {
        return Arrays.asList((Integer[]) array.getArray());
    }

    private static List<Boolean> teacher(TeacherPermissions t) {
        Boolean[] flags = new Boolean[5];
        flags[PermissionTable.teacherFlag(Permission.ANNOUNCEMENTS)] = t.announcements();
        flags[PermissionTable.teacherFlag(Permission.AGENDA_EDIT)] = t.agendaEdit();
        flags[PermissionTable.teacherFlag(Permission.STUDENT_CONTACTS)] = t.studentContacts();
        flags[PermissionTable.teacherFlag(Permission.TRIAL_CLASSES)] = t.trialClasses();
        flags[PermissionTable.teacherFlag(Permission.STUDENT_RECORD)] = t.studentRecord();
        return Arrays.asList(flags);
    }

    private static String literal(List<?> values) {
        return "{" + String.join(",", values.stream().map(String::valueOf).toList()) + "}";
    }

    private static Map<String, Object> flat(UnitDetail d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", d.name());
        m.put("postalCode", d.postalCode());
        m.put("address", d.address());
        m.put("city", d.city());
        m.put("latitude", d.latitude());
        m.put("longitude", d.longitude());
        m.put("phone", d.phone());
        m.put("email", d.email());
        m.put("whatsapp", d.whatsapp());
        m.put("cnpj", d.cnpj());
        Settings s = d.settings();
        m.put("dueDays", s.dueDays());
        m.put("enrollmentFeeEnabled", s.enrollmentFeeEnabled());
        m.put("enrollmentFeeCents", s.enrollmentFeeCents());
        s.feesCents().forEach((key, cents) -> m.put("fee." + key, cents));
        m.put("pixKey", s.pixKey());
        m.put("reminderDays", s.reminderDays());
        TeacherPermissions t = s.teacherPermissions();
        m.put("teacher.announcements", t.announcements());
        m.put("teacher.agendaEdit", t.agendaEdit());
        m.put("teacher.studentContacts", t.studentContacts());
        m.put("teacher.trialClasses", t.trialClasses());
        m.put("teacher.studentRecord", t.studentRecord());
        m.put("directoryEnabled", s.directoryEnabled());
        m.put("trialLinkEnabled", s.trialLinkEnabled());
        m.put("selfRegistrationLinkEnabled", s.selfRegistrationLinkEnabled());
        return m;
    }
}
