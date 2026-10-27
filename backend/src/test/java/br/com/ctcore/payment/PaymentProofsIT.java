package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import br.com.ctcore.storage.FileStorage;
import br.com.ctcore.student.PortalRequestBaseAccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.access.AccessDeniedException;

/** RF-COB-05 (RN-COB-13, RN-COB-19): envio, lista, confirmação e recusa do comprovante de Pix, com banco e armazenamento reais. */
class PaymentProofsIT extends PortalRequestBaseAccess {
    static final String IP = "127.0.0.1";

    @Autowired PaymentProofs proofs;
    @Autowired FileStorage storage;
    @MockitoSpyBean EmailService email;

    @AfterEach
    void cleanProofs() {
        reset(email);
        BillingFixtures.clean(adminJdbc());
        adminJdbc().update("delete from notification");
    }

    /** PNG sintético de {@code size} bytes. */
    private static byte[] png(int size) {
        byte[] b = new byte[size];
        b[0] = (byte) 0x89;
        b[1] = 'P';
        b[2] = 'N';
        b[3] = 'G';
        return b;
    }

    private record Fx(Tenant t, UUID student, UUID charge, AccountPrincipal who, UUID account) {}

    /** Aluno adulto com conta, termo aceito, unidade com chave Pix e mensalidade avulsa aberta de R$ 150,00. */
    private Fx adult(Tenant t, String name, String email) {
        adminJdbc().update("update unit_settings set pix_key = '+5511987654321' where unit_id = ?", t.unitId());
        UUID acc = account(email);
        UUID s = enrolledAdult(t, name, acc);
        accepted(t, s, acc);
        UUID charge = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), s, 15000, "OPEN");
        return new Fx(t, s, charge, portal(t, acc, Role.STUDENT), acc);
    }

    private <T> T as(AccountPrincipal who, Supplier<T> action) {
        try (var ignored = OrganizationContext.open(who)) {
            return action.get();
        }
    }

    private PaymentProofs.Sent send(Fx f, byte[] content) {
        return as(f.who(), () -> proofs.send(f.who(), f.charge(), content, IP));
    }

    private String chargeStatus(UUID charge) {
        return adminJdbc().queryForObject("select status from charge where id = ?", String.class, charge);
    }

    private int count(String sql, Object... args) {
        return adminJdbc().queryForObject(sql, Integer.class, args);
    }

    private ErrorCode code(Runnable action) {
        return ((ApiStatusException) catchThrowable(action)).code;
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            return e;
        }
        throw new AssertionError("esperava uma recusa");
    }

    @Test
    @DisplayName("CA1, CA12: o aluno adulto envia o PNG, a cobrança vai a IN_REVIEW, o arquivo fica na pasta da organização e a auditoria não leva a chave")
    void sendsProof() {
        Tenant t = tenant();
        Fx f = adult(t, "Aluno Um", "um@portal.example");

        var sent = send(f, png(100_000));

        assertThat(sent.status()).isEqualTo("PENDING");
        assertThat(chargeStatus(f.charge())).isEqualTo("IN_REVIEW");
        String key = adminJdbc().queryForObject("select file_key from payment_proof where id = ?", String.class, sent.id());
        assertThat(key).contains(f.student().toString());
        assertThat(as(f.who(), () -> storage.get(key))).hasSize(100_000);
        assertThat(count("select count(*) from audit_log where action = 'payment_proof.sent' and entity_id = ?", sent.id().toString())).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select coalesce(after_data::text, '') || coalesce(before_data::text, '') from audit_log where action = 'payment_proof.sent' and entity_id = ?", String.class, sent.id().toString()))
                .contains("IN_REVIEW").doesNotContain("payment-proof-");
    }

    @Test
    @DisplayName("CA1: as instruções trazem a chave Pix, o valor e a unidade de uma cobrança aberta")
    void instructions() {
        Tenant t = tenant();
        Fx f = adult(t, "Aluno Dois", "dois@portal.example");

        var info = as(f.who(), () -> proofs.instructions(f.who(), f.charge()));

        assertThat(info.pixKey()).isEqualTo("+5511987654321");
        assertThat(info.amountCents()).isEqualTo(15000);
        assertThat(info.unit()).isNotBlank();
    }

    @Test
    @DisplayName("CA2: menor com dois responsáveis, o financeiro envia, o outro recebe 403, conta de outro aluno e de outra organização recebem 404")
    void whoPays() {
        Tenant t = tenant();
        adminJdbc().update("update unit_settings set pix_key = '+5511987654321' where unit_id = ?", t.unitId());
        UUID minor = student(t, "Menor Três", "2015-01-01", null);
        UUID financial = account("fin@portal.example"), other = account("naofin@portal.example"), stranger = account("estranho@portal.example");
        guardian(t, financial, "11144477735", true, minor);
        guardian(t, other, "52998224725", false, minor);
        UUID charge = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), minor, 9000, "OPEN");

        var notFinancial = portal(t, other, Role.GUARDIAN);
        assertThatThrownBy(() -> as(notFinancial, () -> proofs.send(notFinancial, charge, png(1000), IP))).isInstanceOf(AccessDeniedException.class);
        var outsider = portal(t, stranger, Role.GUARDIAN);
        assertThat(code(() -> as(outsider, () -> proofs.send(outsider, charge, png(1000), IP)))).isEqualTo(ErrorCode.NOT_FOUND);
        Tenant otherOrg = tenant();
        var foreign = portal(otherOrg, financial, Role.GUARDIAN);
        assertThat(code(() -> as(foreign, () -> proofs.send(foreign, charge, png(1000), IP)))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(count("select count(*) from payment_proof")).isZero();

        var payer = portal(t, financial, Role.GUARDIAN);
        assertThat(as(payer, () -> proofs.send(payer, charge, png(1000), IP)).status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("CA3: vazio, acima de 5 MB ou que não é JPG, PNG, WebP nem PDF (mesmo chamado .png) dá 422 no campo file e não grava nada")
    void invalidFile() {
        Tenant t = tenant();
        Fx f = adult(t, "Aluno Quatro", "quatro@portal.example");
        byte[] gif = "GIF89a............".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        for (byte[] bad : new byte[][] {new byte[0], png(FileStorage.MAX_BYTES + 1), gif}) {
            var e = (ApiStatusException) catchThrowable(() -> send(f, bad));
            assertThat(e.code).isEqualTo(ErrorCode.UNPROCESSABLE);
            assertThat(e.fields.get(0).field()).isEqualTo("file");
        }
        assertThat(chargeStatus(f.charge())).isEqualTo("OPEN");
        assertThat(count("select count(*) from payment_proof")).isZero();
    }

    @Test
    @DisplayName("CA4: fora de aberta dá 409 CHARGE_NOT_PAYABLE; unidade sem chave dá 422 PIX_KEY_MISSING; 4 chamadas paralelas deixam um só pendente")
    void statesAndRace() throws Exception {
        Tenant t = tenant();
        Fx f = adult(t, "Aluno Cinco", "cinco@portal.example");
        for (String status : new String[] {"PAID", "CANCELED", "RENEGOTIATED"}) {
            adminJdbc().update("update charge set status = ? where id = ?", status, f.charge());
            assertThat(code(() -> send(f, png(500)))).isEqualTo(ErrorCode.CHARGE_NOT_PAYABLE);
        }
        adminJdbc().update("update charge set status = 'OPEN' where id = ?", f.charge());
        adminJdbc().update("update unit_settings set pix_key = null where unit_id = ?", t.unitId());
        assertThat(code(() -> send(f, png(500)))).isEqualTo(ErrorCode.PIX_KEY_MISSING);
        assertThat(code(() -> as(f.who(), () -> proofs.instructions(f.who(), f.charge())))).isEqualTo(ErrorCode.PIX_KEY_MISSING);
        adminJdbc().update("update unit_settings set pix_key = '+5511987654321' where unit_id = ?", t.unitId());

        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                calls.add(() -> {
                    try {
                        send(f, png(800));
                        return "OK";
                    } catch (ApiStatusException e) {
                        return e.code.name();
                    }
                });
            }
            List<String> results = new ArrayList<>();
            for (Future<String> r : pool.invokeAll(calls)) results.add(r.get());
            assertThat(results.stream().filter("OK"::equals)).hasSize(1);
            assertThat(results.stream().filter(r -> !r.equals("OK"))).containsOnly("CHARGE_NOT_PAYABLE");
        } finally {
            pool.shutdown();
        }
        assertThat(count("select count(*) from payment_proof where status = 'PENDING'")).isEqualTo(1);
        assertThat(chargeStatus(f.charge())).isEqualTo("IN_REVIEW");
        assertThat(code(() -> send(f, png(500)))).isEqualTo(ErrorCode.CHARGE_NOT_PAYABLE);
    }

    @Test
    @DisplayName("CA6: o envio avisa só os Administradores da unidade (PAYMENT_PROOF, nome e valor), sem e-mail na hora")
    void notifies() {
        Tenant t = tenant();
        UUID unitB = secondUnit(t);
        Fx f = adult(t, "Aluno Seis", "seis@portal.example");
        var adminA = manager(t, Role.ADMINISTRATOR, t.unitId());
        var adminB = manager(t, Role.ADMINISTRATOR, unitB);
        var teacher = manager(t, Role.TEACHER, t.unitId());

        var sent = send(f, png(500));

        assertThat(count("select count(*) from notification where link_id = ? and type = 'PAYMENT_PROOF'", adminA.activeLinkId())).isEqualTo(1);
        assertThat(count("select count(*) from notification where link_id = ?", adminB.activeLinkId())).isZero();
        assertThat(count("select count(*) from notification where link_id = ?", teacher.activeLinkId())).isZero();
        assertThat(adminJdbc().queryForMap("select params->>'studentName' as n, params->>'amountCents' as a, ref_id::text as r from notification where type = 'PAYMENT_PROOF'"))
                .containsEntry("n", "Aluno Seis").containsEntry("a", "15000").containsEntry("r", sent.id().toString());
        assertThat(count("select count(*) from email_message where template = 'NOTIFICATION_URGENT'")).isZero();
    }

    @Test
    @DisplayName("CA7: a lista traz só os pendentes do alcance, do mais antigo ao mais novo, com imagem assinada; Coordenador vê todos; Professor 403; outra organização nunca")
    void lists() {
        Tenant t = tenant();
        UUID unitB = secondUnit(t);
        Fx a = adult(t, "Aluno Sete", "sete@portal.example");
        Fx b = adult(t, "Aluno Oito", "oito@portal.example");
        UUID otherStudent = student(t, "Aluno Nove", "1990-01-01", account("nove@portal.example"));
        adminJdbc().update("update student set registered_unit_id = ? where id = ?", unitB, otherStudent);
        adminJdbc().update("insert into unit_settings (organization_id, unit_id, pix_key) values (?, ?, '+5511987654321') on conflict (unit_id) do update set pix_key = excluded.pix_key", t.orgId(), unitB);
        UUID chargeB = BillingFixtures.oneOff(adminJdbc(), t.orgId(), unitB, otherStudent, 7000, "OPEN");
        enrollAccepted(t, otherStudent, unitB);
        var later = send(b, png(600));
        var first = send(a, png(600));
        adminJdbc().update("update payment_proof set sent_at = now() - interval '2 days' where id = ?", first.id());
        adminJdbc().update("update payment_proof set sent_at = now() - interval '1 day' where id = ?", later.id());
        UUID accountB = adminJdbc().queryForObject("select account_id from student where id = ?", UUID.class, otherStudent);
        var payerB = portal(t, accountB, Role.STUDENT);
        var sentB = as(payerB, () -> proofs.send(payerB, chargeB, png(600), IP));
        UUID decidedCharge = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), a.student(), 100, "OPEN");
        send(new Fx(t, a.student(), decidedCharge, a.who(), a.account()), png(600));
        adminJdbc().update("update payment_proof set status = 'REFUSED', decided_at = now(), refusal_reason = 'motivo' where charge_id = ?", decidedCharge);

        var admin = manager(t, Role.ADMINISTRATOR, t.unitId());
        var page = PageRequest.of(null, null, null, java.util.Set.of());
        var mine = as(admin, () -> proofs.list(admin, null, page));
        assertThat(mine.items()).extracting(PaymentProofs.Proof::id).containsExactly(first.id(), later.id());
        assertThat(mine.total()).isEqualTo(2);
        assertThat(mine.items().get(0).imageUrl()).isNotBlank();
        assertThat(mine.items().get(0).studentName()).isEqualTo("Aluno Sete");
        assertThat(mine.items().get(0).amountCents()).isEqualTo(15000);
        var coordinator = manager(t, Role.COORDINATOR, t.unitId());
        assertThat(as(coordinator, () -> proofs.list(coordinator, null, page)).total()).isEqualTo(3);
        assertThat(as(coordinator, () -> proofs.list(coordinator, unitB, page)).items()).extracting(PaymentProofs.Proof::id).containsExactly(sentB.id());
        assertThat(as(admin, () -> proofs.list(admin, unitB, page)).items()).isEmpty();
        // Arquivo que sumiu do armazenamento (ex.: o seed): a lista segue, sem imagem.
        adminJdbc().update("update payment_proof set file_key = 'missing-gone.png' where id = ?", first.id());
        assertThat(as(admin, () -> proofs.list(admin, null, page)).items().get(0).imageUrl()).isNull();

        var teacher = manager(t, Role.TEACHER, t.unitId());
        assertThatThrownBy(() -> as(teacher, () -> proofs.list(teacher, null, page))).isInstanceOf(AccessDeniedException.class);
        Tenant other = tenant();
        var foreign = manager(other, Role.COORDINATOR, other.unitId());
        assertThat(as(foreign, () -> proofs.list(foreign, null, page)).items()).isEmpty();
        assertThat(code(() -> as(foreign, () -> proofs.get(foreign, first.id())))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(as(coordinator, () -> proofs.get(coordinator, first.id())).status()).isEqualTo("PENDING");
    }

    private void enrollAccepted(Tenant t, UUID student, UUID unit) {
        br.com.ctcore.student.EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, unit, "ACTIVE", 1, null);
        accepted(t, student, adminJdbc().queryForObject("select account_id from student where id = ?", UUID.class, student));
    }

    @Test
    @DisplayName("CA8, CA12: confirmar cria o pagamento PIX_KEY/PORTAL com recibo, paga a cobrança, confirma o comprovante e a repetição dá 409 sem segundo pagamento")
    void confirms() {
        Tenant t = tenant();
        Fx f = adult(t, "Aluno Dez", "dez@portal.example");
        adminJdbc().update("update student set email = ? where id = ?", "aluno-dez@pay.example", f.student());
        var sent = send(f, png(500));
        var admin = manager(t, Role.ADMINISTRATOR, t.unitId());

        var done = as(admin, () -> proofs.confirm(admin, sent.id(), true, IP));

        assertThat(done.proof().status()).isEqualTo("CONFIRMED");
        assertThat(done.payment().receipt().number()).isEqualTo(1);
        var pay = adminJdbc().queryForMap("select method, origin, amount_cents, status, registered_by_account_id, (paid_at = (select sent_at from payment_proof)) as at_sent from payment");
        assertThat(pay).containsEntry("method", "PIX_KEY").containsEntry("origin", "PORTAL").containsEntry("amount_cents", 15000L).containsEntry("at_sent", true)
                .containsEntry("registered_by_account_id", admin.accountId());
        assertThat(chargeStatus(f.charge())).isEqualTo("PAID");
        assertThat(count("select count(*) from payment_proof where status = 'CONFIRMED' and payment_id is not null")).isEqualTo(1);
        assertThat(count("select count(*) from email_message where template = 'PAYMENT_CONFIRMED'")).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where action = 'payment_proof.confirmed' and entity_id = ?", sent.id().toString())).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where action = 'payment.registered' and entity_id = ?", done.payment().id().toString())).isEqualTo(1);

        assertThat(code(() -> as(admin, () -> proofs.confirm(admin, sent.id(), true, IP)))).isEqualTo(ErrorCode.PROOF_NOT_PENDING);
        assertThat(code(() -> as(admin, () -> proofs.refuse(admin, sent.id(), "tarde demais", IP)))).isEqualTo(ErrorCode.PROOF_NOT_PENDING);
        assertThat(count("select count(*) from payment")).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where action = 'payment_proof.confirmed' and entity_id = ?", sent.id().toString())).isEqualTo(1);
        assertThat(count("select count(*) from notification where type = 'PAYMENT_RECEIVED'")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA8, CA13: confirmar sem marcar o recibo não envia e-mail; Administrador sem alcance recebe 404; confirmar não mexe nos dias pausados")
    void confirmsScope() {
        Tenant t = tenant();
        UUID unitB = secondUnit(t);
        Fx f = adult(t, "Aluno Onze", "onze@portal.example");
        var sent = send(f, png(500));
        var outside = manager(t, Role.ADMINISTRATOR, unitB);
        assertThat(code(() -> as(outside, () -> proofs.confirm(outside, sent.id(), false, IP)))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(code(() -> as(outside, () -> proofs.refuse(outside, sent.id(), "motivo", IP)))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(count("select count(*) from payment_proof where status = 'PENDING'")).isEqualTo(1);

        var admin = manager(t, Role.ADMINISTRATOR, t.unitId());
        as(admin, () -> proofs.confirm(admin, sent.id(), false, IP));
        assertThat(count("select count(*) from email_message where template = 'PAYMENT_CONFIRMED'")).isZero();
        assertThat(count("select block_pause_days from charge where id = ?", f.charge())).isZero();
        var teacher = manager(t, Role.TEACHER, t.unitId());
        assertThatThrownBy(() -> as(teacher, () -> proofs.confirm(teacher, sent.id(), false, IP))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("CA9, CA11: recusar exige motivo de 3 a 200 caracteres; com motivo volta a aberta, avisa por e-mail quem enviou e permite novo envio")
    void refuses() {
        Tenant t = tenant();
        Fx f = adult(t, "Aluno Doze", "doze@portal.example");
        var sent = send(f, png(500));
        var admin = manager(t, Role.ADMINISTRATOR, t.unitId());

        for (String bad : new String[] {null, "", "  ab ", "x".repeat(201)}) {
            assertThatThrownBy(() -> as(admin, () -> proofs.refuse(admin, sent.id(), bad, IP))).isInstanceOf(ApiValidationException.class);
        }
        assertThat(count("select count(*) from payment_proof where status = 'PENDING'")).isEqualTo(1);

        var refused = as(admin, () -> proofs.refuse(admin, sent.id(), "Valor diferente do combinado", IP));

        assertThat(refused.status()).isEqualTo("REFUSED");
        assertThat(refused.refusalReason()).isEqualTo("Valor diferente do combinado");
        assertThat(chargeStatus(f.charge())).isEqualTo("OPEN");
        var mail = adminJdbc().queryForMap("select to_email from email_message where template = 'PROOF_REFUSED'");
        assertThat(mail.get("to_email")).isEqualTo("doze@portal.example");
        verify(email).sendText(any(), eq("doze@portal.example"), eq(EmailTemplate.PROOF_REFUSED), anyString(), contains("Valor diferente do combinado"));
        // CA11: e-mail que falha não desfaz a recusa.
        var again = send(f, png(500));
        doThrow(new IllegalStateException("smtp")).when(email).sendText(any(), anyString(), eq(EmailTemplate.PROOF_REFUSED), anyString(), anyString());
        assertThat(as(admin, () -> proofs.refuse(admin, again.id(), "Sem comprovação", IP)).status()).isEqualTo("REFUSED");
        assertThat(chargeStatus(f.charge())).isEqualTo("OPEN");
        assertThat(count("select count(*) from audit_log where action = 'payment_proof.refused' and entity_id = ?", sent.id().toString())).isEqualTo(1);
        assertThat(count("select count(*) from payment")).isZero();
    }

    @Test
    @DisplayName("CA10: os dias em conferência (data local da unidade) somam em block_pause_days; duas recusas somam")
    void pausesBlockDays() {
        Tenant t = tenant();
        Fx f = adult(t, "Aluno Treze", "treze@portal.example");
        var admin = manager(t, Role.ADMINISTRATOR, t.unitId());

        var first = send(f, png(500));
        adminJdbc().update("update payment_proof set sent_at = sent_at - interval '4 days' where id = ?", first.id());
        as(admin, () -> proofs.refuse(admin, first.id(), "Ilegível", IP));
        assertThat(count("select block_pause_days from charge where id = ?", f.charge())).isEqualTo(4);

        var second = send(f, png(500));
        adminJdbc().update("update payment_proof set sent_at = sent_at - interval '2 days' where id = ?", second.id());
        as(admin, () -> proofs.refuse(admin, second.id(), "Ilegível de novo", IP));
        assertThat(count("select block_pause_days from charge where id = ?", f.charge())).isEqualTo(6);
        assertThat(count("select count(*) from audit_log where action = 'payment_proof.refused' and entity_id in (?, ?) and after_data::text like '%pausedDays%'", first.id().toString(), second.id().toString())).isEqualTo(2);
    }
}
