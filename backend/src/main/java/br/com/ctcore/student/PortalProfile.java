package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-ALU-11, RN-ALU-02, RF-POR-07: o aluno ou o responsável ligado à conta edita telefone, endereço, contato de emergência e foto. Lista fechada de campos
 * (nome, CPF, nascimento e e-mail ficam de fora); mesmas validações do cadastro; auditoria de origem portal. Qualquer conta ligada ao aluno edita (D2).
 */
@Service
public class PortalProfile {
    /** Campos de contato aceitos no corpo; qualquer outro é 422. */
    static final List<String> CONTACT_FIELDS = List.of("phone", "postalCode", "street", "number", "complement", "district", "city", "state", "emergencyName",
            "emergencyRelationship", "emergencyPhone");

    private record Current(String name, LocalDate birthDate, String cpf, String email, String phone, String postalCode, String street, String number,
            String complement, String district, String city, String state, String emergencyName, String emergencyRelationship, String emergencyPhone,
            UUID unitId, String zone) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final PortalTerms terms;
    private final StudentRegistration registration;
    private final StudentEdit edit;
    private final PortalInfo info;
    private final AuditService audit;

    public PortalProfile(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, PortalTerms terms, StudentRegistration registration, StudentEdit edit,
            PortalInfo info, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.terms = terms;
        this.registration = registration;
        this.edit = edit;
        this.info = info;
        this.audit = audit;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.PORTAL, who.accountId(), who.role().name());
    }

    /** Dentro da transação: 404 se o aluno não está ligado à conta (ou é de outra organização). */
    private void requireLinked(AccountPrincipal who, UUID id, boolean lock) {
        if (terms.candidates(who, id, lock).isEmpty()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
    }

    private static ApiStatusException unprocessable(String field, String message) {
        return new ApiStatusException(ErrorCode.UNPROCESSABLE, List.of(new ApiError.Field(field, message)));
    }

    public PortalInfo.Personal updateContact(AccountPrincipal who, UUID id, Map<String, Object> in, String ip) {
        terms.requirePortal(who);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            requireLinked(who, id, true);
            if (in == null) throw unprocessable("phone", "obrigatório");
            for (String key : in.keySet()) {
                if (!CONTACT_FIELDS.contains(key)) throw unprocessable(key, "campo não editável pelo portal");
            }
            Current old = current(org, id);
            StudentRegistration.Clean c;
            try {
                c = registration.personal(new StudentRegistration.Input(null, old.name(), old.birthDate(), text(in, "postalCode"), text(in, "street"), text(in, "number"),
                        text(in, "complement"), text(in, "district"), text(in, "city"), text(in, "state"), old.cpf(), old.email(), text(in, "phone"), null, null,
                        text(in, "emergencyName"), text(in, "emergencyRelationship"), text(in, "emergencyPhone"), null, null), ZoneId.of(old.zone()));
            } catch (ApiValidationException e) {
                throw new ApiStatusException(ErrorCode.UNPROCESSABLE, e.fields);
            }
            Map<String, Object> after = new LinkedHashMap<>();
            // Dado pessoal vai só como "alterado" (RN-LGP-03).
            if (!Objects.equals(nn(old.phone()), nn(c.phone()))) after.put("phone", "alterado");
            if (!Objects.equals(List.of(nn(old.postalCode()), nn(old.street()), nn(old.number()), nn(old.complement()), nn(old.district()), nn(old.city()), nn(old.state())),
                    List.of(nn(c.postalCode()), nn(c.street()), nn(c.number()), nn(c.complement()), nn(c.district()), nn(c.city()), nn(c.state())))) after.put("address", "alterado");
            if (!Objects.equals(List.of(nn(old.emergencyName()), nn(old.emergencyRelationship()), nn(old.emergencyPhone())),
                    List.of(nn(c.emergencyName()), nn(c.emergencyRelationship()), nn(c.emergencyPhone())))) after.put("emergencyContact", "alterado");
            if (!after.isEmpty()) {
                jdbc.sql("update student set phone = :phone, postal_code = :postal, street = :street, number = :number, complement = :complement, district = :district, "
                        + "city = :city, state = :state, emergency_name = :eName, emergency_relationship = :eRel, emergency_phone = :ePhone where organization_id = :org and id = :id")
                        .param("phone", c.phone()).param("postal", c.postalCode()).param("street", c.street()).param("number", c.number()).param("complement", c.complement())
                        .param("district", c.district()).param("city", c.city()).param("state", c.state()).param("eName", c.emergencyName())
                        .param("eRel", c.emergencyRelationship()).param("ePhone", c.emergencyPhone()).param("org", org).param("id", id).update();
                audit.record(new AuditEntry(actor(who), "student.updated", "student", id.toString(), old.unitId(), Map.of(), after, null, ip));
            }
            return info.personal(org, id);
        });
    }

    public PortalInfo.Personal replacePhoto(AccountPrincipal who, UUID id, byte[] content, String ip) {
        terms.requirePortal(who);
        try {
            edit.savePhoto(who.organizationId(), id, content, actor(who), ip, () -> transactions.execute(() -> {
                requireLinked(who, id, false);
                return null;
            }));
        } catch (ApiValidationException e) {
            throw new ApiStatusException(ErrorCode.UNPROCESSABLE, e.fields);
        }
        return transactions.execute(() -> info.personal(who.organizationId(), id));
    }

    public PortalInfo.Personal removePhoto(AccountPrincipal who, UUID id, String ip) {
        terms.requirePortal(who);
        transactions.execute(() -> {
            requireLinked(who, id, false);
            return null;
        });
        edit.clearPhoto(who.organizationId(), id, actor(who), ip);
        return transactions.execute(() -> info.personal(who.organizationId(), id));
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private static String text(Map<String, Object> in, String key) {
        Object v = in.get(key);
        if (v == null) return null;
        if (!(v instanceof String s)) throw unprocessable(key, "valor inválido");
        return s;
    }

    private Current current(UUID org, UUID id) {
        return jdbc.sql("select s.name, s.birth_date, s.cpf, s.email, s.phone, s.postal_code, s.street, s.number, s.complement, s.district, s.city, s.state, s.emergency_name, "
                + "s.emergency_relationship, s.emergency_phone, s.registered_unit_id, u.timezone from student s join unit u on u.organization_id = s.organization_id "
                + "and u.id = s.registered_unit_id where s.organization_id = :org and s.id = :id").param("org", org).param("id", id)
                .query((rs, n) -> new Current(rs.getString(1), rs.getObject(2, LocalDate.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14),
                        rs.getString(15), rs.getObject(16, UUID.class), rs.getString(17))).single();
    }
}
