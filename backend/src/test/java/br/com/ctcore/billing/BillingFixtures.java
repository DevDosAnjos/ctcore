package br.com.ctcore.billing;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Cobranças direto no banco para os testes de pagamento e de recibo; sem passar pela API. */
public final class BillingFixtures {
    /** Vencimento padrão: fora da janela do agendador real (10 dias a partir de hoje) em qualquer data de execução. */
    public static final String FAR_DUE = "2100-01-10";

    private BillingFixtures() {}

    public static UUID student(JdbcTemplate db, UUID org, UUID unit, String name) {
        UUID id = UUID.randomUUID();
        db.update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state) "
                + "values (?, ?, ?, ?, ?, date '1990-01-01', '01001000', 'Rua', '1', 'Centro', 'Cidade', 'SP')", id, org, unit, name, name.toLowerCase());
        return id;
    }


    public static UUID student(JdbcTemplate db, UUID org, UUID unit, String name, String birth) {
        UUID id = UUID.randomUUID();
        db.update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state) "
                + "values (?, ?, ?, ?, ?, ?::date, '01001000', 'Rua', '1', 'Centro', 'Cidade', 'SP')", id, org, unit, name, name.toLowerCase() + id, birth);
        return id;
    }

    /** Modalidade ativa do aluno na unidade, com o dia de vencimento e o valor do plano dados. */
    public static UUID modality(JdbcTemplate db, UUID org, UUID unit, UUID student, int dueDay, long planCents) {
        var e = br.com.ctcore.student.EnrollmentFixtures.enroll(db, org, student, unit, "ACTIVE", 0, null);
        db.update("update enrollment_modality set due_day = ? where id = ?", dueDay, e.row());
        db.update("update plan set value_cents = ? where id = ?", planCents, e.plan());
        return e.row();
    }

    /** Plano novo da mesma modalidade da linha, para a troca agendada. */
    public static UUID otherPlan(JdbcTemplate db, UUID org, UUID unit, UUID row, long planCents) {
        UUID id = UUID.randomUUID();
        db.update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) "
                + "select ?, organization_id, unit_id, modality_id, 'Novo ' || ?, 'novo ' || ?, ?, 'ACTIVE' from enrollment_modality where id = ?", id, id, id, planCents, row);
        return id;
    }

    public static UUID benefit(JdbcTemplate db, UUID org, UUID unit, UUID student, UUID row, String type, long value, String validUntil, String status) {
        UUID id = UUID.randomUUID(), account = UUID.randomUUID();
        db.update("insert into account (id, email, name, status) values (?, ?, 'Concedente', 'ACTIVE')", account, "gen-" + account + "@example.com");
        db.update("insert into benefit (id, organization_id, enrollment_modality_id, student_id, unit_id, type, value, reason, decided_by, granted_by_account_id, valid_until, status, ended_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, 'Motivo', 'Diretor', ?, ?::date, ?, case when ? = 'ENDED' then now() end)", id, org, row, student, unit, type, value, account, validUntil, status, status);
        return id;
    }

    public static UUID guardian(JdbcTemplate db, UUID org, UUID student, boolean financial) {
        UUID account = UUID.randomUUID(), guardian = UUID.randomUUID();
        db.update("insert into account (id, email, status) values (?, ?, 'INVITED')", account, "gen-" + account + "@example.com");
        db.update("insert into guardian (id, organization_id, account_id, name, cpf, phone) values (?, ?, ?, 'Responsável', ?, '11987654321')", guardian, org, account,
                String.valueOf(10000000000L + Math.abs(guardian.hashCode()) % 1000000000L));
        db.update("insert into student_guardian (organization_id, student_id, guardian_id, financial) values (?, ?, ?, ?)", org, student, guardian, financial);
        return guardian;
    }

    public static void cleanAll(JdbcTemplate db) {
        clean(db);
        br.com.ctcore.student.EnrollmentFixtures.clean(db);
        db.update("delete from account where email like 'gen-%@example.com'");
    }

    /** Cobrança avulsa (descrição própria) ou mensalidade, conforme {@code modalityRow}. */
    public static UUID charge(JdbcTemplate db, UUID org, UUID unit, UUID student, UUID modalityRow, LocalDate reference, String description, long amount, String status) {
        UUID id = UUID.randomUUID();
        db.update("insert into charge (id, organization_id, unit_id, student_id, type, enrollment_modality_id, reference_month, description, due_on, plan_cents, amount_cents, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, date '" + FAR_DUE + "', ?, ?, ?)", id, org, unit, student, modalityRow == null ? "ONE_OFF" : "MONTHLY", modalityRow,
                reference, description, amount, amount, status);
        return id;
    }

    public static UUID oneOff(JdbcTemplate db, UUID org, UUID unit, UUID student, long amount, String status) {
        return charge(db, org, unit, student, null, null, "Kimono", amount, status);
    }

    /** Apaga o que os testes criaram (as tabelas não têm DELETE para o papel de execução; aqui é o administrador). */
    public static void clean(JdbcTemplate db) {
        for (String table : new String[] {"payment_alert", "payment_duplicate", "payment_attempt_charge", "payment_notice", "payment_attempt", "charge_reminder", "sms_log", "receipt", "payment_proof", "payment", "receipt_counter", "charge"}) db.update("delete from " + table);
    }
}
