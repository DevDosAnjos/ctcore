package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.InviteRules;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.HolderDocument;
import br.com.ctcore.organization.NewUnit;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-ALU-01: cadastro do aluno com dados validados e regras de duplicidade (RN-ALU-01 a 06). Organização, perfil e alcance vêm da
 * sessão. Só os campos conhecidos são lidos do corpo: dado de saúde não existe no cadastro (RN-LGP-02).
 */
@Service
public class StudentRegistration {
    private static final Set<String> STATES = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR",
            "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY");
    private static final Set<String> REFERRALS = Set.of("INSTAGRAM", "REFERRAL", "DIRECTORY", "WALK_BY", "OTHER");

    public record Input(UUID registeredUnitId, String name, LocalDate birthDate, String postalCode, String street, String number, String complement,
            String district, String city, String state, String cpf, String email, String phone, String gender, String referral,
            String emergencyName, String emergencyRelationship, String emergencyPhone, Boolean confirmDuplicate,
            List<StudentGuardians.Input> guardians) {}

    record Clean(UUID unitId, String name, LocalDate birthDate, boolean adult, String postalCode, String street, String number, String complement,
            String district, String city, String state, String cpf, String email, String phone, String gender, String referral,
            String emergencyName, String emergencyRelationship, String emergencyPhone, boolean confirm,
            List<StudentGuardians.Clean> guardians) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Students students;
    private final AuditService audit;
    private final StudentGuardians guardians;
    private final Clock clock;

    public StudentRegistration(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Students students, AuditService audit, StudentGuardians guardians, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.students = students;
        this.audit = audit;
        this.guardians = guardians;
        this.clock = clock;
    }

    /** CPF só com dígitos (11, com dígitos verificadores); em branco vira null. */
    static String cpf(String field, String raw) {
        try {
            String d = HolderDocument.normalize(raw);
            if (d != null && d.length() != 11) throw new IllegalArgumentException("CPF inválido");
            return d;
        } catch (IllegalArgumentException e) {
            throw new ApiValidationException(field, e.getMessage());
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String required(String field, String value, int max) {
        String v = blank(value);
        if (v == null) throw new ApiValidationException(field, "obrigatório");
        if (v.length() > max) throw new ApiValidationException(field, "no máximo " + max + " caracteres");
        return v;
    }

    private static String optional(String field, String value, int max) {
        String v = blank(value);
        if (v != null && v.length() > max) throw new ApiValidationException(field, "no máximo " + max + " caracteres");
        return v;
    }

    private static String oneOf(String field, String value, Set<String> allowed) {
        String v = blank(value);
        if (v != null && !allowed.contains(v)) throw new ApiValidationException(field, "valor inválido");
        return v;
    }

    private Clean clean(Input in) {
        if (in == null) throw new ApiValidationException("name", "obrigatório");
        if (in.registeredUnitId() == null) throw new ApiValidationException("registeredUnitId", "obrigatório");
        Clean c = personal(in);
        return new Clean(in.registeredUnitId(), c.name(), c.birthDate(), c.adult(), c.postalCode(), c.street(), c.number(), c.complement(), c.district(),
                c.city(), c.state(), c.cpf(), c.email(), c.phone(), c.gender(), c.referral(), c.emergencyName(), c.emergencyRelationship(),
                c.emergencyPhone(), c.confirm(), StudentGuardians.prepare(c.adult(), in.guardians()));
    }

    /** Validação dos dados pessoais, igual no cadastro e na edição (RF-ALU-04); unidade e responsáveis não entram. */
    Clean personal(Input in) {
        if (in == null) throw new ApiValidationException("name", "obrigatório");
        String name = in.name() == null ? "" : in.name().strip();
        if (name.length() < 2 || name.length() > 80) throw new ApiValidationException("name", "de 2 a 80 caracteres");
        LocalDate today = LocalDate.now(clock);
        if (in.birthDate() == null) throw new ApiValidationException("birthDate", "obrigatório");
        if (!in.birthDate().isBefore(today)) throw new ApiValidationException("birthDate", "informe uma data no passado");
        String postal = in.postalCode() == null ? "" : in.postalCode().replaceAll("\\D", "");
        if (postal.length() != 8) throw new ApiValidationException("postalCode", "CEP inválido");
        String state = in.state() == null ? "" : in.state().strip().toUpperCase(Locale.ROOT);
        if (!STATES.contains(state)) throw new ApiValidationException("state", "UF inválida");
        boolean adult = InviteRules.adult(in.birthDate(), today);
        String cpf = cpf("cpf", in.cpf());
        String email = NewUnit.email("email", blank(in.email()));
        String phone = NewUnit.phone("phone", in.phone());
        if (adult) {
            if (cpf == null) throw new ApiValidationException("cpf", "obrigatório");
            if (email == null) throw new ApiValidationException("email", "obrigatório");
            if (phone == null) throw new ApiValidationException("phone", "obrigatório");
        }
        String eName = optional("emergencyName", in.emergencyName(), 80), eRel = optional("emergencyRelationship", in.emergencyRelationship(), 40);
        String ePhone = NewUnit.phone("emergencyPhone", in.emergencyPhone());
        int given = (eName == null ? 0 : 1) + (eRel == null ? 0 : 1) + (ePhone == null ? 0 : 1);
        if (given != 0 && given != 3) {
            String missing = eName == null ? "emergencyName" : eRel == null ? "emergencyRelationship" : "emergencyPhone";
            throw new ApiValidationException(missing, "informe nome, parentesco e telefone do contato de emergência, ou nenhum");
        }
        if (eName != null && eName.length() < 2) throw new ApiValidationException("emergencyName", "de 2 a 80 caracteres");
        return new Clean(null, name, in.birthDate(), adult, postal, required("street", in.street(), 120), required("number", in.number(), 20),
                optional("complement", in.complement(), 60), required("district", in.district(), 80), required("city", in.city(), 80), state, cpf, email,
                phone, oneOf("gender", in.gender(), GENDERS), oneOf("referral", in.referral(), REFERRALS), eName, eRel, ePhone,
                Boolean.TRUE.equals(in.confirmDuplicate()), List.of());
    }

    static ApiStatusException exists(ErrorCode code, Students.Ref ref) {
        return new ApiStatusException(code, List.of(new ApiError.Field("id", ref.id().toString()), new ApiError.Field("name", ref.name()),
                new ApiError.Field("photoUrl", ref.photoUrl() == null ? "" : ref.photoUrl())));
    }

    public Students.Detail create(AccountPrincipal who, Input input, String ip) {
        students.requireAccess(who);
        Clean c = clean(input);
        try {
            var mails = new java.util.ArrayList<StudentGuardians.Mail>();
            Students.Detail created = transactions.execute(() -> insert(who, c, ip, mails));
            guardians.send(mails);
            return created;
        } catch (DuplicateKeyException e) {
            // Two requests with the same CPF raced past the check: the unique index decided (RN-ALU-04).
            throw exists(ErrorCode.STUDENT_CPF_EXISTS, transactions.execute(() -> students.byCpf(who.organizationId(), c.cpf()))
                    .orElseThrow(() -> e));
        }
    }

    private Students.Detail insert(AccountPrincipal who, Clean c, String ip, List<StudentGuardians.Mail> mails) {
        boolean unitOk = jdbc.sql("select count(*) from unit where organization_id = :org and id = :id and active").param("org", who.organizationId())
                .param("id", c.unitId()).query(Long.class).single() > 0;
        if (!unitOk) throw new ApiValidationException("registeredUnitId", "unidade inválida");
        if (who.role() == Role.ADMINISTRATOR && (who.units() == null || !who.units().contains(c.unitId()))) {
            throw new AccessDeniedException("Permission denied");
        }
        if (c.cpf() != null) {
            var found = students.byCpf(who.organizationId(), c.cpf());
            if (found.isPresent()) throw exists(ErrorCode.STUDENT_CPF_EXISTS, found.get());
        } else if (!c.confirm()) {
            var found = students.byNameAndBirth(who.organizationId(), Modalities.normalize(c.name()), c.birthDate(), null);
            if (found.isPresent()) throw exists(ErrorCode.STUDENT_POSSIBLE_DUPLICATE, found.get());
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, phone, postal_code, street, number, "
                + "complement, district, city, state, gender, referral, emergency_name, emergency_relationship, emergency_phone) values (:id, :org, :unit, "
                + ":name, :key, :birth, :cpf, :email, :phone, :postal, :street, :number, :complement, :district, :city, :state, :gender, :referral, "
                + ":eName, :eRel, :ePhone)")
                .param("id", id).param("org", who.organizationId()).param("unit", c.unitId()).param("name", c.name())
                .param("key", Modalities.normalize(c.name())).param("birth", c.birthDate()).param("cpf", c.cpf()).param("email", c.email())
                .param("phone", c.phone()).param("postal", c.postalCode()).param("street", c.street()).param("number", c.number())
                .param("complement", c.complement()).param("district", c.district()).param("city", c.city()).param("state", c.state())
                .param("gender", c.gender()).param("referral", c.referral()).param("eName", c.emergencyName()).param("eRel", c.emergencyRelationship())
                .param("ePhone", c.emergencyPhone()).update();
        audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "student.created", "student",
                id.toString(), c.unitId(), null, afterOf(c), null, ip));
        for (var g : c.guardians()) {
            var mail = guardians.attach(who, id, g, null, ip);
            if (mail != null) mails.add(mail);
        }
        return students.detail(who, id);
    }

    /** CPF, e-mail, telefone e endereço vão só como "informado" (R16). */
    private static Map<String, Object> afterOf(Clean c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.name());
        m.put("birthDate", c.birthDate().toString());
        m.put("registeredUnitId", c.unitId().toString());
        if (c.gender() != null) m.put("gender", c.gender());
        if (c.referral() != null) m.put("referral", c.referral());
        m.put("address", "informado");
        if (c.cpf() != null) m.put("cpf", "informado");
        if (c.email() != null) m.put("email", "informado");
        if (c.phone() != null) m.put("phone", "informado");
        if (c.emergencyName() != null) m.put("emergencyContact", "informado");
        return m;
    }
}
