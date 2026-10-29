package br.com.ctcore.student;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.util.UUID;

/** Abre para outros pacotes os dados comuns do portal (#85, #86) que os testes do comprovante (#87) reaproveitam. */
public abstract class PortalRequestBaseAccess extends PortalRequestBase {
    @Override
    public UUID account(String email) {
        return super.account(email);
    }

    @Override
    public UUID student(Tenant t, String name, String birth, UUID account) {
        return super.student(t, name, birth, account);
    }

    @Override
    public UUID enrolledAdult(Tenant t, String name, UUID account) {
        return super.enrolledAdult(t, name, account);
    }

    @Override
    public void guardian(Tenant t, UUID account, String cpf, boolean financial, UUID... students) {
        super.guardian(t, account, cpf, financial, students);
    }

    @Override
    public void accepted(Tenant t, UUID student, UUID account) {
        super.accepted(t, student, account);
    }

    @Override
    public void publish(Tenant t, String kind, int version, boolean reaccept) {
        super.publish(t, kind, version, reaccept);
    }

    @Override
    public AccountPrincipal portal(Tenant t, UUID account, Role role) {
        return super.portal(t, account, role);
    }

    @Override
    public UUID secondUnit(Tenant t) {
        return super.secondUnit(t);
    }

    @Override
    public AccountPrincipal manager(Tenant t, Role role, UUID... units) {
        return super.manager(t, role, units);
    }
}
