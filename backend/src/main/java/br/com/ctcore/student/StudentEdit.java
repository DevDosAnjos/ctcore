package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.LogoImage;
import br.com.ctcore.storage.FileStorage;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RF-ALU-04: edição dos dados pessoais e da foto do aluno pela gestão. Mesmas validações do cadastro; a unidade de cadastro e os
 * responsáveis não mudam aqui (D1). Campos fora do contrato nunca são lidos (RN-LGP-02).
 */
@Service
public class StudentEdit {
    private static final Logger LOG = LoggerFactory.getLogger(StudentEdit.class);

    public record Update(String name, LocalDate birthDate, String postalCode, String street, String number, String complement, String district,
            String city, String state, String cpf, String email, String phone, String gender, String referral, String emergencyName,
            String emergencyRelationship, String emergencyPhone, Boolean confirmDuplicate) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Students students;
    private final StudentRegistration registration;
    private final AuditService audit;
    private final FileStorage storage;

    public StudentEdit(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Students students, StudentRegistration registration,
            AuditService audit, FileStorage storage) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.students = students;
        this.registration = registration;
        this.audit = audit;
        this.storage = storage;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    public Students.Detail update(AccountPrincipal who, UUID id, Update in, String ip) {
        students.requireAccess(who);
        if (in == null) throw new ApiValidationException("name", "obrigatório");
        var c = registration.personal(new StudentRegistration.Input(null, in.name(), in.birthDate(), in.postalCode(), in.street(), in.number(),
                in.complement(), in.district(), in.city(), in.state(), in.cpf(), in.email(), in.phone(), in.gender(), in.referral(), in.emergencyName(),
                in.emergencyRelationship(), in.emergencyPhone(), in.confirmDuplicate(), null));
        try {
            return transactions.execute(() -> apply(who, id, c, ip));
        } catch (DuplicateKeyException e) {
            // Two requests with the same new CPF raced past the check: the unique index decided (RN-ALU-04).
            throw StudentRegistration.exists(ErrorCode.STUDENT_CPF_EXISTS, transactions.execute(() -> students.byCpf(who.organizationId(), c.cpf()))
                    .orElseThrow(() -> e));
        }
    }

    private Students.Detail apply(AccountPrincipal who, UUID id, StudentRegistration.Clean c, String ip) {
        Students.Detail old = students.detail(who, id);
        if (!c.adult() && old.guardians().isEmpty()) {
            throw new ApiValidationException("birthDate", "aluno menor de 18 anos precisa de ao menos um responsável");
        }
        if (c.cpf() != null) {
            var found = students.byCpf(who.organizationId(), c.cpf());
            if (found.isPresent() && !found.get().id().equals(id)) throw StudentRegistration.exists(ErrorCode.STUDENT_CPF_EXISTS, found.get());
        } else if (!c.confirm() && (!old.name().equals(c.name()) || !old.birthDate().equals(c.birthDate()))) {
            var found = students.byNameAndBirth(who.organizationId(), Modalities.normalize(c.name()), c.birthDate(), id);
            if (found.isPresent()) throw StudentRegistration.exists(ErrorCode.STUDENT_POSSIBLE_DUPLICATE, found.get());
        }
        Map<String, Object> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
        diff(before, after, "name", old.name(), c.name());
        diff(before, after, "birthDate", old.birthDate(), c.birthDate());
        diff(before, after, "gender", old.gender(), c.gender());
        diff(before, after, "referral", old.referral(), c.referral());
        // Dado pessoal vai só como "alterado" (RN-LGP-03).
        if (!Objects.equals(tuple(old.postalCode(), old.street(), old.number(), old.complement(), old.district(), old.city(), old.state()),
                tuple(c.postalCode(), c.street(), c.number(), c.complement(), c.district(), c.city(), c.state()))) after.put("address", "alterado");
        if (!Objects.equals(old.cpf(), c.cpf())) after.put("cpf", "alterado");
        if (!Objects.equals(old.email(), c.email())) after.put("email", "alterado");
        if (!Objects.equals(old.phone(), c.phone())) after.put("phone", "alterado");
        if (!Objects.equals(tuple(old.emergencyName(), old.emergencyRelationship(), old.emergencyPhone()),
                tuple(c.emergencyName(), c.emergencyRelationship(), c.emergencyPhone()))) after.put("emergencyContact", "alterado");
        if (after.isEmpty()) return old;
        jdbc.sql("update student set name = :name, name_key = :key, birth_date = :birth, cpf = :cpf, email = :email, phone = :phone, postal_code = :postal, "
                + "street = :street, number = :number, complement = :complement, district = :district, city = :city, state = :state, gender = :gender, "
                + "referral = :referral, emergency_name = :eName, emergency_relationship = :eRel, emergency_phone = :ePhone "
                + "where organization_id = :org and id = :id")
                .param("name", c.name()).param("key", Modalities.normalize(c.name())).param("birth", c.birthDate()).param("cpf", c.cpf())
                .param("email", c.email()).param("phone", c.phone()).param("postal", c.postalCode()).param("street", c.street())
                .param("number", c.number()).param("complement", c.complement()).param("district", c.district()).param("city", c.city())
                .param("state", c.state()).param("gender", c.gender()).param("referral", c.referral()).param("eName", c.emergencyName())
                .param("eRel", c.emergencyRelationship()).param("ePhone", c.emergencyPhone()).param("org", who.organizationId()).param("id", id).update();
        audit.record(new AuditEntry(actor(who), "student.updated", "student", id.toString(), old.registeredUnitId(), before, after, null, ip));
        return students.detail(who, id);
    }

    private static java.util.List<String> tuple(String... values) {
        return java.util.Arrays.asList(values);
    }

    private static void diff(Map<String, Object> before, Map<String, Object> after, String key, Object old, Object now) {
        if (Objects.equals(old, now)) return;
        before.put(key, old == null ? null : old.toString());
        after.put(key, now == null ? null : now.toString());
    }

    public Students.Detail replacePhoto(AccountPrincipal who, UUID id, byte[] content, String ip) {
        students.requireAccess(who);
        LogoImage image;
        try {
            image = LogoImage.parse(content);
        } catch (IllegalArgumentException e) {
            throw new ApiValidationException("file", e.getMessage());
        }
        // Sem alcance ou fora da organização: 404 antes de gravar o arquivo.
        transactions.execute(() -> students.detail(who, id));
        String name = "student-" + UUID.randomUUID() + "." + image.extension();
        storage.put(name, image.bytes(), image.contentType());
        String previous;
        try {
            previous = transactions.execute(() -> setPhoto(who, id, name, ip));
        } catch (RuntimeException e) {
            discard(name);
            throw e;
        }
        if (previous != null) discard(previous);
        return transactions.execute(() -> students.detail(who, id));
    }

    public Students.Detail removePhoto(AccountPrincipal who, UUID id, String ip) {
        students.requireAccess(who);
        String previous = transactions.execute(() -> setPhoto(who, id, null, ip));
        if (previous != null) discard(previous);
        return transactions.execute(() -> students.detail(who, id));
    }

    /** Dentro da transação: troca a coluna e audita; devolve o arquivo anterior. */
    private String setPhoto(AccountPrincipal who, UUID id, String name, String ip) {
        UUID unit = students.detail(who, id).registeredUnitId();
        String before = jdbc.sql("select photo from student where organization_id = :org and id = :id").param("org", who.organizationId())
                .param("id", id).query((rs, n) -> rs.getString(1)).list().get(0); // the row exists: detail() above found it
        if (Objects.equals(before, name)) return null;
        jdbc.sql("update student set photo = :photo where organization_id = :org and id = :id").param("photo", name)
                .param("org", who.organizationId()).param("id", id).update();
        Map<String, Object> old = new LinkedHashMap<>(), next = new LinkedHashMap<>();
        old.put("photo", before);
        next.put("photo", name);
        audit.record(new AuditEntry(actor(who), name == null ? "student.photo_removed" : "student.photo_updated", "student", id.toString(), unit, old, next,
                null, ip));
        return before;
    }

    // Arquivo antigo (ou novo, se a transação falhou): falha ao apagar vai para o log e não desfaz a troca.
    private void discard(String name) {
        try {
            storage.delete(name);
        } catch (RuntimeException e) {
            LOG.warn("Could not delete student file {}: {}", name, e.getClass().getSimpleName());
        }
    }
}
