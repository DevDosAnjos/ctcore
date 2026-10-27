package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationProfile;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RF-POR-03, RN-POR-01, RN-POR-04, RN-POR-09, RN-INA-08: carteirinha do aluno escolhido entre os ligados à conta. Marca, foto, selo (ativo se há modalidade ativa)
 * com a data do dia na unidade de cadastro, e modalidades ativas com unidade e faixa. Funciona também com o portal reduzido. Só leitura; sem presença,
 * frequência, progresso, CPF, telefone nem e-mail.
 */
@Service
public class PortalCard {
    public record Student(UUID id, String name, String photoUrl) {}

    public record Link(UUID id, String name, boolean self) {}

    public record Modality(PortalHome.Ref modality, PortalHome.Ref unit, Belt belt) {}

    public record Belt(String levelName, String color, String secondaryColor, int degree) {}

    public record View(OrganizationProfile.Brand organization, Student student, List<Link> students, String status, LocalDate today, OffsetDateTime generatedAt,
            String timezone, List<Modality> modalities) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final PortalStudents portal;
    private final PortalHome home;
    private final OrganizationProfile organization;
    private final Clock clock;

    public PortalCard(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, PortalStudents portal, PortalHome home, OrganizationProfile organization,
            Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.portal = portal;
        this.home = home;
        this.organization = organization;
        this.clock = clock;
    }

    /** Quem chama já conferiu o aceite do termo. {@code studentId} nulo: o primeiro cartão. Não ligado à conta: 404. */
    public View view(AccountPrincipal who, UUID studentId) {
        List<PortalStudents.Card> cards = portal.cards(who);
        if (cards.isEmpty()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        UUID selected = studentId != null ? studentId : cards.get(0).id();
        PortalStudents.Card card = cards.stream().filter(c -> c.id().equals(selected)).findFirst().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
        OrganizationProfile.Brand brand = organization.brand(who);
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            String zone = jdbc.sql("select u.timezone from student s join unit u on u.organization_id = s.organization_id and u.id = s.registered_unit_id "
                    + "where s.organization_id = :org and s.id = :sid").param("org", org).param("sid", selected).query(String.class).single();
            Map<UUID, PortalHome.Belt> belts = home.belts(org, selected, now).stream().collect(Collectors.toMap(b -> b.modality().id(), Function.identity()));
            List<Modality> modalities = jdbc.sql("select md.id, md.name, u.id, u.name from enrollment_modality em join modality md on md.id = em.modality_id "
                    + "join unit u on u.organization_id = em.organization_id and u.id = em.unit_id "
                    + "where em.organization_id = :org and em.student_id = :sid and em.status = 'ACTIVE' order by md.name, u.name, em.id")
                    .param("org", org).param("sid", selected)
                    .query((rs, n) -> {
                        UUID modality = rs.getObject(1, UUID.class);
                        PortalHome.Belt b = belts.get(modality);
                        return new Modality(new PortalHome.Ref(modality, rs.getString(2)), new PortalHome.Ref(rs.getObject(3, UUID.class), rs.getString(4)),
                                b == null ? null : new Belt(b.levelName(), b.color(), b.secondaryColor(), b.degree()));
                    }).list();
            List<Link> links = cards.stream().map(c -> new Link(c.id(), c.name(), c.self())).toList();
            return new View(brand, new Student(card.id(), card.name(), card.photoUrl()), links, modalities.isEmpty() ? "INACTIVE" : "ACTIVE",
                    now.atZoneSameInstant(ZoneId.of(zone)).toLocalDate(), now, zone, modalities);
        });
    }
}
